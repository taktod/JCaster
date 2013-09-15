package com.ttProject.jcaster.player.decode;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.swing.VideoComponent;
import com.ttProject.media.flv.CodecType;
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
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private boolean isSendAudioMshTag = false;
	private boolean isSendVideoMshTag = false;
	private int processPos = -1;
	private int savePos = 0;
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
	 * TODO できたらある程度データがたまるまで、実行開始しないようにしておきたい。(そうすれば再生の開始時が滑らかになるはず。)
	 */
	public void addTag(Tag tag) {
		if(tag == null) {
			return;
		}
		if(tag instanceof AudioTag) {
			AudioTag aTag = (AudioTag) tag;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
				return;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
				return;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		// 再生中でなければ、スキップすればよし。
		if(processPos == -1) {
			processPos = tag.getTimestamp();
			savePos = 0;
		}
		else if(Math.abs((processPos - tag.getTimestamp())) > 100) {
			// 前後のticが100ミリsec以上はなれている場合はやり直しになったとみる。
			// デコーダーを作り直す必要あり。
			VideoComponent component = null;
			if(videoDecoder != null) {
				component = videoDecoder.getComponent();
				videoDecoder.close();
				videoDecoder = null;
			}
			if(audioDecoder != null) {
				audioDecoder.close();
				audioDecoder = null;
			}
			audioDecoder = new FlvAudioDecoder();
			videoDecoder = new FlvVideoDecoder(audioDecoder, component);
			// やり直しにするべき
			processPos = tag.getTimestamp();
			savePos = 0;
			isSendAudioMshTag = false;
			isSendVideoMshTag = false;
//			processPos = tag.getTimestamp();
//			savePos += 100;
			// この書き直しの場合にもaudioMshを送りなおす必要あり？→コーデックによるけどあり
			// この書き直しの場合にもvideoMshを送りなおす必要あり？→コーデックによるけどあり
		}
		else {
			savePos += tag.getTimestamp() - processPos;
			processPos = tag.getTimestamp();
		}
		tag.setTimestamp(savePos);
		// timestampの書き換えを実行する。
		if(tag instanceof AudioTag) {
			if(audioMshTag != null && !isSendAudioMshTag) {
				// 送る
				audioMshTag.setTimestamp(savePos);
				audioDecoder.addTag(audioMshTag);
			}
			isSendAudioMshTag = true;
			audioDecoder.addTag(tag);
		}
		else if(tag instanceof VideoTag) {
			if(videoMshTag != null && !isSendVideoMshTag) {
				// 送る
				videoMshTag.setTimestamp(savePos);
				videoDecoder.addTag(videoMshTag);
			}
			isSendVideoMshTag = true;
			videoDecoder.addTag(tag);
		}
	}
}
