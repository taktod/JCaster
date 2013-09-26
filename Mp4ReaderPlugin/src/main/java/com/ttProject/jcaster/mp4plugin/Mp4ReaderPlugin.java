package com.ttProject.jcaster.mp4plugin;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * mp4の読み込みplugin
 * @author taktod
 *
 */
public class Mp4ReaderPlugin implements IPlugin {
	/** 動作ロガー */
	private static final Logger logger = Logger.getLogger(Mp4ReaderPlugin.class);
	/** 動作モジュール */
	private final Mp4ReaderModule module = new Mp4ReaderModule();
	/**
	 * バージョン番号を応答する。
	 */
	@Override
	public String versionId() {
		return "0.0.1";
	}
	/**
	 * データタイプを応答する。
	 */
	@Override
	public Type getType() {
		return Type.Input;
	}
	/**
	 * 有効になったときの動作
	 */
	@Override
	public void onActivated() {
		// 有効になったときの動作をつくっておく。
		logger.info("有効になりました。");
		// 有効になったので、IInputModuleをつくって、登録とpanelの構築をすすめる必要があります。
		// データのセットアップを実行
		module.setup();
	}
	/**
	 * 無効になったときの動作
	 */
	@Override
	public void onDeactivated() {
		module.stop();
	}
	/**
	 * 名前応答
	 */
	@Override
	public String toString() {
		return "Mp4Reader";
	}
	@Override
	public void onShutdown() {
		
	}
}
