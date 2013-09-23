package com.ttProject.jcaster.module;

import java.util.HashSet;
import java.util.Set;

import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * mixerモジュールを管理するモジュール
 * @author taktod
 * TODO 入力モジュールからのデータを受け取る前にデータのソートとmsh対策はやっておきたい。
 * msh対策とは・・・
 * aacとavcの場合はmshという解析に必要なタグがはじめだけ流れてくるという仕様がある。
 * そのデータをトラップしておいて、次にきたモジュールにきちんと流してやること。
 * 
 * flip対策
 * 映像と音声のデータがflipした場合きちんとしたオーダーに戻して動作させてやる必要がある。
 * 
 * この２点はここでもOutputModuleでも実行する必要があるので、適当なモジュールをつくって対策しておきたいところ。
 */
public class MixerModule implements IMixerModule {
	/** 通常のmixerModule */
	private IMixerModule mixerModule;
	/** viewerでうけとりたい場合のmixerModule */
	private Set<IMixerModule> viewerModules = new HashSet<IMixerModule>();
	// 転送データがtagでavcやaacだったときのmshTag保持
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private int lastTimestamp = 0;
	public void setMixerModule(IMixerModule module) {
		mixerModule = module;
		if(audioMshTag != null) {
			audioMshTag.setTimestamp(lastTimestamp);
			mixerModule.setData(audioMshTag);
		}
		if(videoMshTag != null) {
			videoMshTag.setTimestamp(lastTimestamp);
			mixerModule.setData(videoMshTag);
		}
	}
	public void removeMixerModule(IMixerModule module) {
		if(mixerModule == module) {
			mixerModule = null;
		}
	}
	public void setViewerModule(IMixerModule module) {
		viewerModules.add(module);
		if(audioMshTag != null) {
			audioMshTag.setTimestamp(lastTimestamp);
			module.setData(audioMshTag);
		}
		if(videoMshTag != null) {
			videoMshTag.setTimestamp(lastTimestamp);
			module.setData(videoMshTag);
		}
	}
	public void removeViewerModule(IMixerModule module) {
		viewerModules.remove(module);
	}
	@Override
	public void onTimerEvent() {
		// タイマーイベントは本家
		if(mixerModule != null) {
			mixerModule.onTimerEvent();
		}
	}
	@Override
	public void registerOutputModule(IOutputModule outputModule) {
		// 出力モジュールへの受け渡しは本家のmixerModuleのみ
		if(mixerModule != null) {
			mixerModule.registerOutputModule(outputModule);
		}
	}
	@Override
	public void setData(Object mediaData) {
		// 中途でregisterしたらmediaSequenceHeaderを送るという処理がありえるので、ここで管理しておいて、あたらしく登録した場合には送り直す必要がでてくる。面倒だね。
		// データのhookは本家もviewerも実行
		if(mediaData instanceof AudioTag) {
			AudioTag aTag = (AudioTag) mediaData;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		else if(mediaData instanceof VideoTag) {
			VideoTag vTag = (VideoTag) mediaData;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		else if(mediaData instanceof Tag) {
			lastTimestamp = ((Tag)mediaData).getTimestamp();
		}
		else {
			videoMshTag = null;
			audioMshTag = null;
		}
		if(mixerModule != null) {
			mixerModule.setData(mediaData);
		}
	}
}
