package com.ttProject.jcaster.plugin.module;

/**
 * 入力モジュール
 * @author taktod
 */
public interface IInputModule extends IModule {
	/**
	 * データを受け渡す先のmixerモジュールを設定する。
	 * @param mixerModule
	 */
	public void registerMixerModule(IMixerModule mixerModule);
}
