package com.ttProject.jcaster.plugin;

import javax.swing.JPanel;

/**
 * viewer用のplugin
 * @author taktod
 *
 */
public interface IViewerPlugin extends IPlugin {
	/** viewerに始めから提供されるパネルデータを登録する */
	public void setViewerPanel(JPanel panel);
}
