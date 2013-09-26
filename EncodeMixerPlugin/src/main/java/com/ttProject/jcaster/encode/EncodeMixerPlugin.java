package com.ttProject.jcaster.encode;

import com.ttProject.jcaster.plugin.IPlugin;

public class EncodeMixerPlugin implements IPlugin {
	private final EncodeMixerModule module = new EncodeMixerModule();
	@Override
	public Type getType() {
		return Type.Mixer;
	}
	@Override
	public void onActivated() {
		module.setup();
	}
	@Override
	public void onDeactivated() {
	}
	@Override
	public String toString() {
		return "Encode";
	}
	@Override
	public String versionId() {
		return "0.0.1";
	}
	@Override
	public void onShutdown() {
		module.onShutdown();
	}
}
