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
	 * プラグインのタイプ定義
	 * @author taktod
	 */
	public enum Type {
		Input,
		Output,
		Mixer,
		Viewer
	}
}
