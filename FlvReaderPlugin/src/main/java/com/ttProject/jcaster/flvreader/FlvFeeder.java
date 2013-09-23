package com.ttProject.jcaster.flvreader;

import java.util.LinkedList;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.TagAnalyzer;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * flvの送信用Feeder
 * @author taktod
 */
public class FlvFeeder {
	/** ロガー */
	private final Logger logger = Logger.getLogger(FlvFeeder.class);
	private String target;
	private IFileReadChannel source = null;
	private long totalDuration = 0;
	private long passedTimestamp = 0;
	private LinkedList<Tag> tagList;
	private long startTime = -1;
	private int startTimestamp = -1;
	/** 変換モジュール */
	private IMixerModule targetModule;
	private FlvTagAnalyzer analyzer = null;
	private boolean videoFlg = false; // 映像があるかどうかフラグ
	/**
	 * 全体の情報応答
	 * @return
	 */
	public long getTotalDuration() {
		return totalDuration;
	}
	public long getCurrentDuration() {
		return passedTimestamp;
	}
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
	public FlvFeeder(String targetFile) throws Exception {
		target = targetFile;
		source = FileReadChannel.openFileReadChannel(target);
		tagList = new LinkedList<Tag>();
	}
	/**
	 * 初期化実行
	 */
	public void initialize() throws Exception {
		// ファイルを調べて全体のデータ長だけしっておきたい。できたらシーク用のポジションデータも解析しておきたいところ。(シーク高速化するため。)
		source.position(source.size() - 4);
		int lastTagSize = BufferUtil.safeRead(source, 4).getInt();
		int lastTagPos = source.size() - 4 - lastTagSize;
		source.position(lastTagPos);
		TagAnalyzer analyzer = new TagAnalyzer();
		Tag tag = analyzer.analyze(source);
		totalDuration = tag.getTimestamp(); // 最終タグの時刻位置を最終サイズとします。
	}
	public synchronized void start(int startPos) throws Exception {
		// 現在の再生位置を調整して、動作させておく。
		// sourceのデータを読み込んで開始位置まで処理をすすめておく。
		int startTimestamp = (int)(startPos * 1000);
		source.position(0);
		// flvのヘッダを解析します。
		FlvHeader flvHeader = new FlvHeader();
		flvHeader.analyze(source);
		videoFlg = flvHeader.hasVideo();
		analyzer = new FlvTagAnalyzer(startTimestamp);
		Tag tag = null;
		while((tag = analyzer.analyze(source)) != null) {
			if(tag instanceof AudioTag) {
				AudioTag aTag = (AudioTag) tag;
				if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
					tagList.addLast(aTag);
					continue;
				}
			}
			if(tag instanceof VideoTag) {
				videoFlg = true;
				VideoTag vTag = (VideoTag) tag;
				if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
					tagList.addLast(vTag);
					continue;
				}
			}
			// mshをみつけたら保持しておく必要あり。
			// もしvideoFlgがたっている場合は動画のkeyFrameから始めるべき
			if(tag.getTimestamp() >= startTimestamp) {
				if(videoFlg) {
					// 映像がある場合
					// キーフレームからはじめたい。
					if(tag instanceof VideoTag) {
						VideoTag vTag = (VideoTag) tag;
						if(vTag.isKeyFrame()) {
							this.startTimestamp = tag.getTimestamp();
							tagList.addLast(tag);
							break;
						}
					}
				}
				else {
					// 映像がない場合
					this.startTimestamp = tag.getTimestamp();
					tagList.addLast(tag);
					break;
				}
			}
		}
		if(startTimestamp == 0) {
			startTime = -1;
		}
		else {
			startTime = - startTimestamp;
		}
	}
	/**
	 * タイマー上での動作
	 * @return
	 */
	public synchronized boolean onTimerEvent() {
		if(source == null || analyzer == null) {
			return false;
		}
		if(startTime < 0) {
			startTime += System.currentTimeMillis();
		}
		try {
			// 現在の時刻を取得する。
			long currentPos = System.currentTimeMillis() - startTime;
			Tag tag = null;
			while((tag = analyzer.analyze(source)) != null) {
				tagList.addLast(tag);
				if(tag.getTimestamp() > currentPos) {
					// 今回送信すべきデータ分送信がおわっているなら、ここまでとする。
					break;
				}
			}
			// tagListの中からデータを飛ばしていって、データがなくなるまで送信しておく。
			Tag listedTag = null;
			while(tagList.size() != 0 && (listedTag = tagList.removeFirst()) != null) {
				// データがtagListにある場合はこのデータを送信しておく。
				passedTimestamp = listedTag.getTimestamp();
				if(targetModule != null) {
					int ts = listedTag.getTimestamp() - startTimestamp;
					if(ts < 0) {
						listedTag.setTimestamp(0);
					}
					else {
						listedTag.setTimestamp(listedTag.getTimestamp() - startTimestamp);
					}
					System.out.println(listedTag);
					targetModule.setData(Media.FlvTag, listedTag);
				}
			}
			if(tag == null) {
				return false;
			}
			return true;
		}
		catch (Exception e) {
			logger.error("データやり取り上でエラーがありました。", e);
		}
		return false;
	}
	public void close() {
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
