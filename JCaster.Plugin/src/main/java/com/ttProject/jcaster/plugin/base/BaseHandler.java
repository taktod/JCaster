package com.ttProject.jcaster.plugin.base;

/**
 * 中央のベースハンドラー
 * @author taktod
 *
 */
public class BaseHandler {
	/**
	 * ベースを取得
	 * @return
	 */
	private static Base getBase() {
		return new Base() {
			@Override
			protected void initialize() {
			}
		}.intern();
	}
	/**
	 * MainBaseを取得
	 * @return
	 */
	public static IMainBase getIMainBase() {
		Base base = getBase();
		if(base instanceof IMainBase) {
			return (IMainBase) base;
		}
		return null;
	}
	/**
	 * Swing用のbaseを取得
	 * @return
	 */
	public static ISwingMainBase getISwingMainBase() {
		Base base = getBase();
		if(base instanceof ISwingMainBase) {
			return (ISwingMainBase)base;
		}
		return null;
	}
}
