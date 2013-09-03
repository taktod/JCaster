package com.ttProject.jcaster.plugin.base;

/**
 * コントローラーのベース動作
 * @author taktod
 */
public abstract class Base {
	/**
	 * ベース動作
	 */
	private static Base instance = null;
	/**
	 * コンストラクタ
	 */
	public Base() {
		if(instance == null) {
			instance = this;
			initialize();
		}
	}
	/**
	 * 初期化処理
	 */
	protected abstract void initialize();
	/**
	 * すでに生成済みのオブジェクトを応答します。
	 * @return
	 */
	public Base intern() {
		return instance;
	}
}
