package com.ttProject.jcaster.flvsave.core;

import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * flvの保存用動作
 * @author taktod
 * saverはデータを順次うけいれていって、ファイルに落とし込む。
 * この方式だと、中途でやめたあとに、再開したときに、再開後にmshheaderがこなければ、アウトになりそう。
 */
public class FlvSaver {
	/** ロガー */
//	private final Logger logger = Logger.getLogger(FlvSaver.class);
//	private String target;
	private FileChannel channel;
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private int processPos = -1; // 処理中のデータの位置
	private int savePos = 0; // 保存しているデータの位置(ファイルの長さでもある。)
	public FlvSaver(String target) throws Exception {
//		this.target = target;
		channel = new FileOutputStream(target).getChannel();
		FlvHeader header = new FlvHeader();
		header.setAudioFlg(true);
		header.setVideoFlg(true);
		channel.write(header.getBuffer());
	}
	// ここでは、特になにも考えずにデータを受け入れる
	public void write(Tag tag) throws Exception {
		// 書き込みがおわってしまったので、なにもしない。
		if(channel == null) {
			return;
		}
		if(tag == null) {
			return;
		}
		// mshデータは保持しておく。
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
		// データを登録する
		if(processPos == -1) {
			// 始めのデータである場合
			// 現在の位置を開始位置に設定して、動作をさせる。
			processPos = tag.getTimestamp();
		}
		else if(Math.abs((processPos - tag.getTimestamp())) > 200){
			// 前後のticが200以上離れている場合は、なんらかの理由でやり直しになった。
			processPos = tag.getTimestamp();
			savePos += 100; // 100 ticだけすすめて、続きを保存させる。
		}
		else {
			// 前からのずれ分を加える必要あり。
			savePos += tag.getTimestamp() - processPos;
			processPos = tag.getTimestamp();
		}
		// 時間の調整を実施する。
		tag.setTimestamp(savePos);
		// データを調整して入力させる。
		if(tag instanceof AudioTag) {
			if(audioMshTag != null) {
				audioMshTag.setTimestamp(tag.getTimestamp());
				channel.write(audioMshTag.getBuffer());
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			if(videoMshTag != null) {
				videoMshTag.setTimestamp(tag.getTimestamp());
				channel.write(videoMshTag.getBuffer());
				videoMshTag = null;
			}
		}
		// データbufferを書き込みます。
		channel.write(tag.getBuffer());
	}
	public void close() {
		if(channel != null) {
			try {
				channel.close();
			}
			catch (Exception e) {
			}
			channel = null;
		}
	}
	public int getSavePos() {
		return savePos;
	}
}
