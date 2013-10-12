package com.ttProject.jcaster.mp4reader.core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.media.extra.flv.FlvOrderModel;
import com.ttProject.media.extra.mp4.IndexFileCreator;
import com.ttProject.media.extra.mp4.Meta;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.TmpFile;

/**
 * mp4のデータをシステムに送信する動作
 * @author taktod
 */
public class Mp4Feeder {
	/** ロガー */
	private final Logger logger = Logger.getLogger(Mp4Feeder.class);
	/** 処理対象ファイル */
	private String target;
	/** 処理対象 */
	private IFileReadChannel source = null;
	/** 解析一時データ */
	private IFileReadChannel tmp = null;
	/** 解析一時データ */
	private TmpFile tmpFile;
	/** FlvTag取り出しモデル */
	private FlvOrderModel orderModel = null;
	/** 全体の長さ */
	private long totalDuration = 0;
	/** 処理済みtimestamp */
	private long passedTimestamp = 0;
	/** 処理待ちデータ(時間軸を合わせるために、待ちになっているデータ保持) */
	private LinkedList<Tag> tagList;
	/** 処理開始時間 */
	private long startTime = -1;
	/** 開始タグtimestamp */
	private int startTimestamp = -1;
	/** 変換モジュール */
	private IMixerModule targetModule;
	/**
	 * 全体の情報応答
	 * @return
	 */
	public long getTotalDuration() {
		return totalDuration;
	}
	/**
	 * 現在位置応答
	 * @return
	 */
	public long getCurrentDuration() {
		return passedTimestamp;
	}
	/**
	 * 変換モジュールを登録する
	 * @param module
	 */
	public void setMixerModule(IMixerModule module) {
		if(module == null) {
			targetModule = null;
			return;
		}
		targetModule = module;
	}
	/**
	 * コンストラクタ
	 */
	public Mp4Feeder(String targetFile) throws Exception {
		target = targetFile;
		source = FileReadChannel.openFileReadChannel(target);
		tagList = new LinkedList<Tag>();
	}
	/**
	 * 初期化実行
	 */
	public void initialize() throws Exception {
		// indexファイルの解析を実行する。
		File file = new File(target);
		tmpFile = new TmpFile("/" + file.getName() + "_" + System.currentTimeMillis() + ".tmp");
		IndexFileCreator analyzer = new IndexFileCreator(tmpFile);
		Atom atom = null;
		while((atom = analyzer.analyze(source)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		Meta meta = analyzer.getMeta();
		long duration = 0;
		if(meta != null) {
			logger.info("読み込もうとしたファイルのduration:" + meta.getDuration());
			duration = meta.getDuration();
		}
		totalDuration = duration;
		analyzer.updatePrevTag();
		analyzer.checkDataSize();
		analyzer.close();
	}
	public void start(int startPos) throws Exception {
		this.startTimestamp = -1;
		// 現在の再生位置を調査して、動作させておく。
		// metaデータがわからない場合はエラーを返しておく。
		tmp = FileReadChannel.openFileReadChannel(tmpFile.toString());
		int startTimestamp = (int)(startPos * 1000);
		System.out.println("orderModelをつくりなおします:" + startTimestamp);
		orderModel = new FlvOrderModel(tmp, true, true, startTimestamp);
		passedTimestamp = startTimestamp;
		if(startTimestamp == 0) {
			startTime = -1;
		}
		else {
			startTime =  - startTimestamp;
		}
	}
	/**
	 * タイマー処理上での動作
	 * @return trueなら問題のない処理 falseなら問題がでて終わる処理
	 */
	public synchronized boolean onTimerEvent() {
		// tagを取得してデータを応答する
		// orderModelの中からデータを取り出していく。
		// 現在の時刻から応答すべきflvTagのデータをさぐる。
		if(source == null || orderModel == null || tmp == null) {
			// データの準備ができていなければ処理しない
			return false;
		}
		// データを取り出します。
		// 自分が送りたいとおもった時刻以前である場合は、listにaddFirstしておわりにしておく。
		// 開始時刻が設定されていない場合はそのまま保持しておく。
		if(startTime < 0) {
			// 初期時間が設定されていない場合は設定を実行する。
			startTime += System.currentTimeMillis();
		}
		try {
			// 経過時間について調査しておく。
			long currentPos = System.currentTimeMillis() - startTime;
			// 取得すべきtagのデータを保持する必要あり。
			// まず前のデータが残っているか確認する必要がある。(tagListから取り出す)
			if(tagList.size() != 0) {
				// 前のデータが残っている場合はそっちから処理する。
				Tag tag = null;
				while(tagList.size() != 0 && (tag = tagList.removeFirst()) != null) {
					// TODO ここの先行させるデータ量をふやすと、十分なcacheがplayerに送られるっぽいけど・・・
					if(tag.getTimestamp() < currentPos + 1000) {
						sendTarget(tag);
					}
					else {
						// 判定につかったtagはもどしておく。
						tagList.addFirst(tag);
						// この部分で抜けたということはデータがもうこれ以上必要ないので、処理はここでおわってもいいと思う
						return true;
					}
				}
			}
			// ここまできたら、前のデータは残っていないので次のデータを送る必要あり。
			// 残っていない場合はorderModelからあたらしく取得する必要あり。
			List<Tag> list = null;
			while((list = orderModel.nextTagList(source)) != null) {
				if(list.size() == 0) {
					// データがとれていない場合は再送するのが正解っぽい。
					continue;
				}
				for(Tag tag : list) {
					if(tag.getTimestamp() < currentPos + 1000) {
						sendTarget(tag);
					}
					else {
						// tagListの追記すべきデータ
						tagList.add(tag);
					}
				}
				if(tagList.size() > 0) {
					return true;
				}
			}
			// データが枯渇したので、もうおわり。
			return false;
		}
		catch (Exception e) {
			logger.error("データやり取り上でエラーがありました。", e);
		}
		return false;
	}
	/**
	 * targetModuleにタグデータを送ります。
	 * @param tag
	 */
	private void sendTarget(Tag tag) {
		// モジュールに送るべきデータ
		passedTimestamp = tag.getTimestamp();
		// timestampを変更する。
		if(startTimestamp == -1) {
			startTimestamp = tag.getTimestamp() - 1;
			tag.setTimestamp(0);
		}
		else {
			tag.setTimestamp(tag.getTimestamp() - startTimestamp);
		}
		if(targetModule != null) {
			targetModule.setData(tag);
		}
//		System.out.println("t:" + tag);
	}
	/**
	 * 停止処理
	 */
	public void close() {
		if(tmp != null) {
			try {
				tmp.close();
			}
			catch (Exception e) {
			}
			tmp = null;
		}
		if(source != null) {
			try {
				source.close();
			}
			catch (Exception e) {
			}
			source = null;
		}
	}
}
