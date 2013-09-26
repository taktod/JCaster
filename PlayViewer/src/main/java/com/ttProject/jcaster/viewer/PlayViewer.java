package com.ttProject.jcaster.viewer;

import javax.swing.JPanel;

import com.ttProject.jcaster.plugin.IViewerPlugin;

public class PlayViewer implements IViewerPlugin {
	/** 動作モジュール */
	private PlayViewerModule module = new PlayViewerModule();
	@Override
	public String versionId() {
		return "0.0.1";
	}
	@Override
	public Type getType() {
		return Type.Viewer;
	}
	@Override
	public void onActivated() {
	}
	@Override
	public void onDeactivated() {
	}
	@Override
	public void setViewerPanel(JPanel panel) {
		module.setup(panel);
	}
	@Override
	public String toString() {
		return "Player";
	}
	@Override
	public void onShutdown() {
		module.onShutdown();
	}
}
