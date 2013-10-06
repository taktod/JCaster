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
import com.ttProject.media.flv.tag.MetaTag;
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
		orderModel = new FlvOrderModel(tmp, true, true, startTimestamp);
		if(startTimestamp == 0) {
			startTime = -1;
		}
		else {
			startTime =  - startTimestamp;
		}
	}
	/**
	 * タイマー処理上での動作
	 */
	public synchronized boolean onTimerEvent() {
		// tagを取得してデータを応答する
		// orderModelの中からデータを取り出していく。
		// 現在の時刻から応答すべきflvTagのデータをさぐる。
		if(source == null || orderModel == null || tmp == null) {
			return false;
		}
		// データを取り出します。
		// 自分が送りたいとおもった時刻以前である場合は、listにaddFirstしておわりにしておく。
		// 開始時刻が設定されていない場合はそのまま保持しておく。
		if(startTime < 0) {
			startTime += System.currentTimeMillis();
		}
		try {
			long currentPos = System.currentTimeMillis() - startTime;
			System.out.println(currentPos);
			// 取得すべきtagのデータを保持する必要あり。
			Tag lastTag = null;
			while(true) {
				// tagListに入っている最終データを確認して現在時刻より前だったら必要なデータを取りに行く。
				if(tagList.size() != 0) {
					lastTag = tagList.getLast();
					// TODO １秒分だけ先に送信してOKということにしておきます。
					if(lastTag.getTimestamp() > currentPos + 1000) {
						break;
					}
				}
				// 取得したtagListはいったんlistにいれる。
				List<Tag> list = orderModel.nextTagList(source);
				if(list == null) {
					// 再生がおわっているので、その処理を実行する。
					break;
				}
				// 取得したデータはいったん再生待ちリストにいれます。
				for(Tag tag : list) {
					tagList.addLast(tag);
				}
			}
			System.out.println(lastTag);
			// 先頭から確認していって、転送すべきデータを送っておく。
			Tag tag = null;
			while(tagList.size() != 0 && (tag = tagList.removeFirst()) != null) {
				// metaTagは捨てておく
				if(tag instanceof MetaTag) {
					continue;
				}
				// mediaSequenceHeaderがある場合はそのデータをコピーしておく
				// このtagデータは先頭にmediaSequenceHeaderとmetaTagがある。
				// とりあえずtimestampの問題はoutputPluginでなんとかするので、必要なのは送信データのみ。
				// mainBaseにデータを送信する。
				// tagをみつけた場合はbaseにおくっておく。
				passedTimestamp = tag.getTimestamp();
				if(passedTimestamp > currentPos + 1000) {
					break;
				}
				if(targetModule != null) {
					if(startTimestamp == -1) {
						startTimestamp = tag.getTimestamp() - 1;
						tag.setTimestamp(0);
					}
					else {
						tag.setTimestamp(tag.getTimestamp() - startTimestamp);
					}
					targetModule.setData(tag);
				}
			}
			if(tagList.size() == 0 && lastTag == null) {
				// tagListも空であたらしく取得できたデータもない場合はデータがない
				return false;
			}
			return true;
		}
		catch (Exception e) {
			logger.error("データやり取り上でエラーがありました。", e);
		}
		return false;
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
