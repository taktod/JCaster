package com.ttProject.jcaster.player.decode;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.swing.VideoComponent;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * flvのデコードを実行して再生する処理
 * @author taktod
 */
public class FlvDecoder {
	/** 動作ロガー */
	private final Logger logger = Logger.getLogger(FlvDecoder.class);
	private FlvVideoDecoder videoDecoder;
	private FlvAudioDecoder audioDecoder;
	/**
	 * コンストラクタ
	 */
	public FlvDecoder() {
		audioDecoder = new FlvAudioDecoder();
		videoDecoder = new FlvVideoDecoder(audioDecoder);
	}
	public VideoComponent getComponent() {
		return videoDecoder.getComponent();
	}
	/**
	 * 処理するタグを追加
	 * @param tag
	 * TODO できたらこのタイミングで表示tagのtimestampを0から順に並べるようにしておきたい。
	 */
	public void addTag(Tag tag) {
		if(tag instanceof AudioTag) {
			audioDecoder.addTag(tag);
		}
		else if(tag instanceof VideoTag) {
			videoDecoder.addTag(tag);
		}
	}
}
