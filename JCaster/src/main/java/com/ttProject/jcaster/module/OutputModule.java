package com.ttProject.jcaster.module;

import java.util.HashSet;
import java.util.Set;

import com.ttProject.jcaster.model.MixedMediaOrderModel;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.Tag;

/**
 * 出力モジュールを管理するモジュール
 * @author taktod
 * 整列はここでやった方がわかりやすいかも・・・
 */
public class OutputModule implements IOutputModule {
	/** 通常のoutputModule */
	private IOutputModule outputModule;
	/** viewerでうけとりたい場合のoutputModule */
	private Set<IOutputModule> viewerModules = new HashSet<IOutputModule>();
	
	private final MixedMediaOrderModel mixedMediaOrderModel = new MixedMediaOrderModel();
	private boolean zeroReset = true;
	public void setOutputModule(IOutputModule module) {
		outputModule = module;
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			outputModule.setMixedData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			outputModule.setMixedData(tag);
		}
	}
	public void removeOutputModule(IOutputModule module) {
		if(outputModule == module) {
			outputModule = null;
		}
	}
	public void setViewerModule(IOutputModule module) {
		viewerModules.add(module);
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			module.setMixedData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			module.setMixedData(tag);
		}
	}
	public void removeViewerModule(IOutputModule module) {
		viewerModules.remove(module);
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
		if(tag.getTimestamp() == 0) {
			// 映像のリセットがあってから、音声のリセットが遅れてやってくる・・・みたいなことが発生するらしい。
			if(zeroReset) {
				mixedMediaOrderModel.reset();
			}
			zeroReset = false;
		}
		else {
			zeroReset = true;
		}
		mixedMediaOrderModel.addData(tag);
		for(Object data : mixedMediaOrderModel.getCompleteData()) {
			if(data instanceof Tag) {
				tag = (Tag) data;
				// TODO outputModuleでデータの変更を実行してしまうと、その影響がviewerModuleに渡したTagにも影響でてしまうということか・・・なるほど
				if(outputModule != null) {
					outputModule.setMixedData(tag);
				}
				for(IOutputModule module : viewerModules) {
					module.setMixedData(tag);
				}
			}
		}
	}
}
