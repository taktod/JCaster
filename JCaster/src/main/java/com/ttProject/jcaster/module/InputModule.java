package com.ttProject.jcaster.module;

import com.ttProject.jcaster.plugin.module.IInputModule;
import com.ttProject.jcaster.plugin.module.IMixerModule;

/**
 * 入力モジュールを管理するモジュール
 * @author taktod
 *
 */
public class InputModule implements IInputModule {
	/** 通常の入力モジュール */
	private IInputModule inputModule;
	public void setInputModule(IInputModule module) {
		inputModule = module;
	}
	public void removeInputModule(IInputModule module) {
		if(inputModule == module) {
			inputModule = null;
		}
	}

	@Override
	public void onTimerEvent() {
		if(inputModule != null) {
			inputModule.onTimerEvent();
		}
	}

	@Override
	public void registerMixerModule(IMixerModule mixerModule) {
		if(inputModule != null) {
			inputModule.registerMixerModule(mixerModule);
		}
	}
}
