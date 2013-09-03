package com.ttProject.jcaster.outputplugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

public class RtmpPublishPlugin implements IPlugin {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(RtmpPublishPlugin.class);
	private RtmpPublishModule module = new RtmpPublishModule();
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
		return "rtmp";
	}
}
