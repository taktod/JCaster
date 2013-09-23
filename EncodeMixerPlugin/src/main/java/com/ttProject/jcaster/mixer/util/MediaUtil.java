package com.ttProject.jcaster.mixer.util;

import com.ttProject.media.flv.Tag;
import com.ttProject.media.raw.AudioData;
import com.ttProject.media.raw.VideoData;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IVideoPicture;

public class MediaUtil {
	/**
	 * タイムスタンプを応答する。
	 * @param obj
	 * @return
	 */
	public static long getTimestamp(Object obj) {
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
}
