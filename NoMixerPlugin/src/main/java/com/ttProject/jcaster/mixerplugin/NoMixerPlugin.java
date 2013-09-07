package com.ttProject.jcaster.mixerplugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * なにも混ぜないplugin
 * @author taktod
 */
public class NoMixerPlugin implements IPlugin {
	/** ロガー */
	private Logger logger = Logger.getLogger(NoMixerPlugin.class);
	/** 動作主体モジュール */
	private NoMixerModule module = new NoMixerModule();
	/**
	 * バージョン
	 */
	@Override
	public String versionId() {
		return "0.0.1";
	}
	/**
	 * プラグインタイプ応答
	 */
	@Override
	public Type getType() {
		return Type.Mixer;
	}
	/**
	 * 有効になったときの処理
	 */
	@Override
	public void onActivated() {
		logger.info("nomixerPluginがactivateされました。");
		module.setup();
	}
	/**
	 * 名前応答
	 */
	@Override
	public String toString() {
		return "None";
	}
}