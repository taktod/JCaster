package com.ttProject.jcaster.player.decode;

import java.util.ArrayList;
import java.util.List;

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
	private final VideoComponent component = new VideoComponent();
	private FlvVideoDecoder videoDecoder;
	private FlvAudioDecoder audioDecoder;
	// ここにデータをある程度保持させる。
	private List<Tag> tagList = new ArrayList<Tag>();
	private boolean started = false;
	private int volume = 100;
	/**
	 * コンストラクタ
	 */
	public FlvDecoder() {
		resetAudioDecoder();
		resetVideoDecoder();
	}
	public VideoComponent getComponent() {
		return component;
	}
	public int getVolumeLevel() {
		if(audioDecoder == null) {
			return 0;
		}
		return audioDecoder.getVolumeLevel();
	}
	/**
	 * 音量を設定する
	 * @param volume 0 - 100
	 */
	public void setVolume(int volume) {
		this.volume = volume;
		if(audioDecoder != null) {
			audioDecoder.setVolume(volume);
		}
	}
	public void resetDecoder() {
		resetAudioDecoder();
		resetVideoDecoder();
		started = false;
		tagList.clear();
	}
	public void resetAudioDecoder() {
		if(audioDecoder != null) {
			audioDecoder.close();
			audioDecoder = null;
		}
		audioDecoder = new FlvAudioDecoder();
		audioDecoder.setVolume(volume);
		// audioDecoderを更新しておく。
		if(videoDecoder != null) {
			videoDecoder.setAudioDecoder(audioDecoder);
		}
	}
	public void resetVideoDecoder() {
		if(videoDecoder != null) {
			videoDecoder.close();
			videoDecoder = null;
		}
		videoDecoder = new FlvVideoDecoder(audioDecoder, component);
	}
	/**
	 * 処理するタグを追加
	 * @param tag
	 */
	public void addTag(Tag tag) {
		if(!started) {
			tagList.add(tag);
			if(tagList.size() > 20) {
				started = true;
				for(Tag t : tagList) {
					addTag(t);
				}
				tagList.clear();
			}
		}
		if(tag instanceof AudioTag) {
			audioDecoder.addTag(tag);
		}
		else if(tag instanceof VideoTag) {
			videoDecoder.addTag(tag);
		}
	}
}
