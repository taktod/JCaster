package com.ttProject.jcaster.mixer;

import com.ttProject.jcaster.plugin.IPlugin;

public class EncodeMixerPlugin implements IPlugin {
	private final EncodeMixerModule module = new EncodeMixerModule();
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
		module.setup();
	}
	@Override
	public void onDeactivated() {
		module.remove();
	}
	@Override
	public String toString() {
		return "Encode";
	}
}
