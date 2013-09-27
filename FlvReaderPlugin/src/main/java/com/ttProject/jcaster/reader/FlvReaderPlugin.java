package com.ttProject.jcaster.reader;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * flvの読み込み用プラグイン
 * @author taktod
 *
 */
public class FlvReaderPlugin implements IPlugin {
	private final FlvReaderModule module = new FlvReaderModule();
	@Override
	public String versionId() {
		return "0.0.1";
	}
	@Override
	public Type getType() {
		return Type.Input;
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
		return "FlvReader";
	}
	@Override
	public void onShutdown() {
		
	}
	@Override
	public int getOrder() {
		return 0;
	}
}
