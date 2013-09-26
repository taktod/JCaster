package com.ttProject.jcaster.outputplugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * rtmp配信用のプラグインの基本部
 * @author taktod
 */
public class RtmpPublishPlugin implements IPlugin {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(RtmpPublishPlugin.class);
	/**
	 * 動作モジュール
	 */
	private final RtmpPublishModule module = new RtmpPublishModule();
	/**
	 * 動作バージョン
	 */
	@Override
	public String versionId() {
		return "0.0.1";
	}
	/**
	 * プラグインタイプ設定
	 */
	@Override
	public Type getType() {
		return Type.Output;
	}
	/**
	 * 有効化されたときのイベント
	 */
	@Override
	public void onActivated() {
		logger.info("activateされました。");
		module.setup();
	}
	/**
	 * 無効化されたときのイベント
	 */
	@Override
	public void onDeactivated() {
		module.stop();
	}
	/**
	 * 名前設定
	 */
	@Override
	public String toString() {
		return "RtmpPublish";
	}
	@Override
	public void onShutdown() {
		
	}
}
