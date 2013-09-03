package com.ttProject.jcaster.plugin.module;

/**
 * 各動作モジュールのベース
 * @author taktod
 *
 */
public interface IModule {
	/**
	 * タイマー動作のイベント(100ミリ秒ごとにシグナルがくるものとする。)
	 */
	public void onTimerEvent();
}
