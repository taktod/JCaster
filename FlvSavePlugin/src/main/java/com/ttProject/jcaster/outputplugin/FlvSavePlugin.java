package com.ttProject.jcaster.outputplugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * flvの保存用のプラグイン
 * @author taktod
 */
public class FlvSavePlugin implements IPlugin {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(FlvSavePlugin.class);
	private FlvSaveModule module = new FlvSaveModule();
	@Override
	public String versionId() {
		return "0.0.1";
	}

	@Override
	public Type getType() {
		return Type.Output;
	}
	@Override
	public void onActivated() {
		logger.info("activateされました。");
		module.setup();
	}
	@Override
	public String toString() {
		return "flv";
	}
}
