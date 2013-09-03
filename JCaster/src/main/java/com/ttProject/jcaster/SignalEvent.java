package com.ttProject.jcaster;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.controller.MainController;

/**
 * 一定時間ごとにイベントを発行するプログラム
 * @author taktod
 */
public class SignalEvent {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(SignalEvent.class);
	/** シングルトンインスタンス */
	private static final SignalEvent instance = new SignalEvent();
	/** timerの間隔 */
	private static long duration = 100; // 100ミリ秒ごとに、イベントを発行することにします。
	/** 動作 */
	private boolean running = true;
	/** 動作thread */
	private Thread thread = null;
	/** コントローラー */
	private MainController mainController;
	/**
	 * コンストラクタ
	 */
	private SignalEvent() {
	}
	/**
	 * シングルトンインスタンス取得
	 * @return
	 */
	public static SignalEvent getInstance() {
		return instance;
	}
	/**
	 * コントローラーを設定する。
	 * @param controller
	 */
	public void setController(MainController controller) {
		this.mainController = controller;
	}
	/**
	 * threadを開始させます。
	 */
	public void start() {
		if(thread == null || thread.isInterrupted()) {
			thread = new Thread(new SignalProvider());
			thread.setName("signalEventThread");
			thread.setDaemon(true);
			thread.start();
		}
	}
	/**
	 * タイマーを停止させます。
	 */
	public void stop() {
		running = false;
		thread.interrupt();
	}
	/**
	 * 内部クラス
	 * @author taktod
	 */
	private class SignalProvider implements Runnable {
		@Override
		public void run() {
			try {
				while(running) {
					Thread.sleep(duration);
					// 処理を実行
					mainController.fireTimerEvent();
				}
			}
			catch (InterruptedException e) {
			}
			catch (Exception e) {
				logger.error("シグナル動作上で予期しないエラーが発生しました。", e);
			}
			running = false;
		}
	}
}
