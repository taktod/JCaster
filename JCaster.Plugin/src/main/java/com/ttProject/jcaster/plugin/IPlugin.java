package com.ttProject.jcaster.plugin;

public interface IPlugin {
	/**
	 * バージョンデータ
	 * @return
	 */
	public String versionId();
	/**
	 * プラグインのタイプ定義
	 * @return
	 */
	public Type getType();
	/**
	 * プラグインの名称定義
	 * @return
	 */
	public String toString();
	/**
	 * 有効になったときの動作
	 */
	public void onActivated();
	/**
	 * 無効になったときの動作
	 */
	public void onDeactivated();
	/**
	 * プラグインのタイプ定義
	 * @author taktod
	 */
	public enum Type {
		Input,
		Output,
		Mixer,
		Viewer
	}
	/**
	 * 終了時の処理
	 */
	public void onShutdown();
	/**
	 * プラグインの有効順設定
	 * 数値が大きい方が前にきます。
	 * @return
	 */
	public int getOrder();
}
