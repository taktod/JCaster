package com.ttProject.jcaster.mp4reader.core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.media.extra.flv.FlvOrderModel;
import com.ttProject.media.extra.mp4.IndexFileCreator;
import com.ttProject.media.extra.mp4.Meta;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
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
	private final Logger logger = Logger.getLogger(Mp4Feeder.class);
	private String target;
	private IFileReadChannel source = null;
	private IFileReadChannel tmp = null;
	private FlvOrderModel orderModel = null;
	private TmpFile tmpFile;
	private long totalDuration = 0;
	private long passedTimestamp = 0;
	private LinkedList<Tag> tagList;
	private long startTime = -1;
	/** 現在実行中のデータのmshタグ */
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
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
		if(audioMshTag != null) {
			audioMshTag.setTimestamp((int)passedTimestamp);
			module.setData(Media.FlvTag, audioMshTag);
		}
		if(videoMshTag != null) {
			videoMshTag.setTimestamp((int)passedTimestamp);
			module.setData(Media.FlvTag, videoMshTag);
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
		tmpFile = new TmpFile(file.getName() + "_" + System.currentTimeMillis() + ".tmp");
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
			// 取得すべきtagのデータを保持する必要あり。
			Tag lastTag = null;
			while(true) {
				List<Tag> list = orderModel.nextTagList(source);
				if(list == null) {
					// 再生がおわっているので、その処理を実行する。
					break;
				}
				// 取得したtagListはいったんlistにいれる。
				lastTag = null;
				for(Tag tag : list) {
					tagList.addLast(tag);
					lastTag = tag;
				}
				if(lastTag == null) {
					continue;
				}
				if(lastTag.getTimestamp() > currentPos) {
					break;
				}
			}
			// 先頭から確認していって、転送すべきデータを送っておく。
			Tag tag = null;
			while(tagList.size() != 0 && (tag = tagList.removeFirst()) != null) {
				// mediaSequenceHeaderがある場合はそのデータをコピーしておく
				if(tag instanceof AudioTag) {
					AudioTag aTag = (AudioTag) tag;
					if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
						audioMshTag = aTag;
					}
					if(aTag.getCodec() != CodecType.AAC) {
						audioMshTag = null;
					}
				}
				if(tag instanceof VideoTag) {
					VideoTag vTag = (VideoTag) tag;
					if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
						videoMshTag = vTag;
					}
					if(vTag.getCodec() != CodecType.AVC) {
						videoMshTag = null;
					}
				}
				// このtagデータは先頭にmediaSequenceHeaderとmetaTagがある。
				// とりあえずtimestampの問題はoutputPluginでなんとかするので、必要なのは送信データのみ。
				// mainBaseにデータを送信する。
				// tagをみつけた場合はbaseにおくっておく。
//				logger.info(tag);
				passedTimestamp = tag.getTimestamp();
				if(targetModule != null) {
					targetModule.setData(Media.FlvTag, tag);
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
		videoMshTag = null;
		audioMshTag = null;
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
