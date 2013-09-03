package com.ttProject.jcaster.mixerplugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * なにも混ぜないplugin
 * @author taktod
 *
 */
public class NoMixerPlugin implements IPlugin {
	private Logger logger = Logger.getLogger(NoMixerPlugin.class);
	@Override
	public String versionId() {
		return "0.0.1";
	}

	@Override
	public Type getType() {
		return Type.Mixer;
	}

	@Override
	public void onActivated() {
		logger.info("nomixerPluginがactivateされました。");
		NoMixerModule module = new NoMixerModule();
		module.setup();
	}
	@Override
	public String toString() {
		return "None";
	}
}