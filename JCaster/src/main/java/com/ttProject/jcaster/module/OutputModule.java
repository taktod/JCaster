package com.ttProject.jcaster.module;

import java.util.HashSet;
import java.util.Set;

import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * 出力モジュールを管理するモジュール
 * @author taktod
 */
public class OutputModule implements IOutputModule {
	/** 通常のoutputModule */
	private IOutputModule outputModule;
	/** viewerでうけとりたい場合のoutputModule */
	private Set<IOutputModule> outputModules = new HashSet<IOutputModule>();
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private int lastTimestamp = 0;
	public void setOutputModule(IOutputModule module) {
		outputModule = module;
		if(audioMshTag != null) {
			audioMshTag.setTimestamp(lastTimestamp);
			outputModule.setMixedData(audioMshTag);
		}
		if(videoMshTag != null) {
			videoMshTag.setTimestamp(lastTimestamp);
			outputModule.setMixedData(videoMshTag);
		}
	}
	public void removeOutputModule(IOutputModule module) {
		if(outputModule == module) {
			outputModule = null;
		}
	}
	@Override
	public void onTimerEvent() {
		// タイマーは本家のみ実行
		if(outputModule != null) {
			outputModule.onTimerEvent();
		}
	}
	@Override
	public void setMixedData(Tag tag) {
		// データのhookも本家もviewerも実行
		if(tag instanceof AudioTag) {
			AudioTag aTag = (AudioTag) tag;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		lastTimestamp = tag.getTimestamp();
		if(outputModule != null) {
			outputModule.setMixedData(tag);
		}
	}
}
