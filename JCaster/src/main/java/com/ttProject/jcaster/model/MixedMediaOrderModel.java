package com.ttProject.jcaster.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * 音声と映像のデータが入り乱れているデータを整列させるモデル
 * とりあえずこちらはMixerModule用
 * @author taktod
 */
public class MixedMediaOrderModel {
	/** ロガー */
	private final Logger logger = Logger.getLogger(MixedMediaOrderModel.class);
	private List<Object> audioList = new ArrayList<Object>();
	private List<Object> videoList = new ArrayList<Object>();
	private final DataComparator dataSort = new DataComparator();
	// このindex値以上たまったら強制的に射出しておく。
	private final int videoCompIndex = 5;
	private final int audioCompIndex = 20;
	private void reset() {
		audioList.clear();
		videoList.clear();
	}
	/**
	 * データを追加しておく
	 * 扱うデータはVideoData VideoTag AudioData AudioTag IVideoPicture IAudioSamplesの６つ
	 * @param data
	 */
	public synchronized void addData(Object data) {
		
	}
	/**
	 * すでにソートできた部分のデータを応答する。
	 * @return
	 */
	public synchronized List<Object> getCompleteData() {
		List<Object> result = new ArrayList<Object>();
		return result;
	}
	private class DataComparator implements Comparator<Object> {
		@Override
		public int compare(Object arg0, Object arg1) {
			return 0;
		}
	}
}
