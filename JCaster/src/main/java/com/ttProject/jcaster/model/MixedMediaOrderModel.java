package com.ttProject.jcaster.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.AudioData;
import com.ttProject.media.raw.VideoData;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IVideoPicture;

/**
 * 音声と映像のデータが入り乱れているデータを整列させるモデル
 * とりあえずこちらはMixerModule用
 * @author taktod
 */
public class MixedMediaOrderModel {
	/** ロガー */
//	private final Logger logger = Logger.getLogger(MixedMediaOrderModel.class);
	private List<Object> audioList = new ArrayList<Object>();
	private List<Object> videoList = new ArrayList<Object>();
	private final DataComparator dataSort = new DataComparator();
	// このindex以内のデータは終端がこない限り保持しておく。
	private final int videoCompIndex = 5; // videoデータは5個
	private final int audioCompIndex = 20; // audioデータは20個
	private boolean videoEndFlg = false;
	private boolean audioEndFlg = false;
	// flvのmshデータだけは保持しておいて、参照できるようにしておく。
	private VideoTag videoMshTag = null;
	private AudioTag audioMshTag = null;
	private int lastTimestamp = 0;
	public void reset() {
		audioList.clear();
		videoList.clear();
		videoEndFlg = false;
		audioEndFlg = false;
		videoMshTag = null;
		audioMshTag = null;
		lastTimestamp = 0;
	}
	public VideoTag getVideoMshTag() {
		if(videoMshTag == null) {
			return null;
		}
		videoMshTag.setTimestamp(lastTimestamp);
		return videoMshTag;
	}
	public AudioTag getAudioMshTag() {
		if(audioMshTag == null) {
			return null;
		}
		audioMshTag.setTimestamp(lastTimestamp);
		return audioMshTag;
	}
	/**
	 * データを追加しておく
	 * 扱うデータはVideoData VideoTag AudioData AudioTag IVideoPicture IAudioSamplesの６つ
	 * @param data
	 */
	public synchronized void addData(Object data) {
		if(data instanceof VideoTag) {
			VideoTag vTag = (VideoTag) data;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
			lastTimestamp = vTag.getTimestamp();
			videoList.add(data);
			Collections.sort(videoList, dataSort);
		}
		else if(data instanceof AudioTag) {
			AudioTag aTag = (AudioTag) data;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
			lastTimestamp = aTag.getTimestamp();
			audioList.add(data);
			Collections.sort(audioList, dataSort);
		}
		else if(data instanceof VideoData
			|| data instanceof IVideoPicture) {
			videoMshTag = null;
			videoList.add(data);
			Collections.sort(videoList, dataSort);
		}
		else if(data instanceof AudioData
			|| data instanceof IAudioSamples) {
			audioMshTag = null;
			audioList.add(data);
			Collections.sort(audioList, dataSort);
		}
	}
	/**
	 * すでにソートできた部分のデータを応答する。
	 * @return
	 */
	public synchronized List<Object> getCompleteData() {
		List<Object> result = new ArrayList<Object>();
		Object videoObj = null;
		Object audioObj = null;
		while(true) {
			if(videoObj == null) {
				if(videoList.size() < videoCompIndex && !audioEndFlg) {
					break;
				}
				videoObj = videoList.get(0);
			}
			if(audioObj == null) {
				if(audioList.size() < audioCompIndex && !videoEndFlg) {
					break;
				}
				audioObj = audioList.get(0);
			}
			if(getTimestamp(videoObj) <= getTimestamp(audioObj)) {
				result.add(videoList.remove(0));
				videoObj = null;
			}
			else {
				result.add(audioList.remove(0));
				audioObj = null;
			}
		}
		if(audioEndFlg && audioList.size() == 0) {
			result.addAll(videoList);
			videoList.clear();
		}
		if(videoEndFlg && videoList.size() == 0) {
			result.addAll(audioList);
			audioList.clear();
		}
		return result;
	}
	/**
	 * タイムスタンプを応答する。
	 * @param obj
	 * @return
	 */
	public long getTimestamp(Object obj) {
		if(obj instanceof Tag) {
			return ((Tag)obj).getTimestamp();
		}
		if(obj instanceof AudioData) {
			return ((AudioData) obj).getTimestamp();
		}
		if(obj instanceof VideoData) {
			return ((VideoData) obj).getTimestamp();
		}
		if(obj instanceof IVideoPicture) {
			return ((IVideoPicture) obj).getTimeStamp();
		}
		if(obj instanceof IAudioSamples) {
			return ((IAudioSamples) obj).getTimeStamp();
		}
		throw new RuntimeException("解釈不能なオブジェクトを発見しました。:" + obj.getClass().getName());
	}
	/**
	 * 映像データがもうないというフラグ設定
	 */
	public void setNomoreVideo() {
		setVideoEndFlg(true);
	}
	public void setVideoEndFlg(boolean flg) {
		videoEndFlg = flg;
	}
	/**
	 * 音声データがもうないというフラグ設定
	 */
	public void setNomoreAudio() {
		setAudioEndFlg(true);
	}
	public void setAudioEndFlg(boolean flg) {
		audioEndFlg = flg;
	}
	/**
	 * 比較クラス
	 * @author taktod
	 *
	 */
	private class DataComparator implements Comparator<Object> {
		@Override
		public int compare(Object obj1, Object obj2) {
			return (int)(getTimestamp(obj1) - getTimestamp(obj2));
		}
	}
}
