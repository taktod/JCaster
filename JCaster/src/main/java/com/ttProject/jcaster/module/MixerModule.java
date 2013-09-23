package com.ttProject.jcaster.module;

import java.util.HashSet;
import java.util.Set;

import com.ttProject.jcaster.model.MixedMediaOrderModel;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.Tag;

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
	private final MixedMediaOrderModel mixedMediaOrderModel = new MixedMediaOrderModel();
	/** viewerでうけとりたい場合のmixerModule */
	private Set<IMixerModule> viewerModules = new HashSet<IMixerModule>();
	public void setMixerModule(IMixerModule module) {
		mixerModule = module;
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			mixerModule.setData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			mixerModule.setData(tag);
		}
	}
	public void removeMixerModule(IMixerModule module) {
		if(mixerModule == module) {
			mixerModule = null;
		}
	}
	public void setViewerModule(IMixerModule module) {
		viewerModules.add(module);
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			module.setData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			module.setData(tag);
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
		mixedMediaOrderModel.addData(mediaData);
		for(Object data : mixedMediaOrderModel.getCompleteData()) {
			if(mixerModule != null) {
				mixerModule.setData(data);
			}
		}
	}
}
