package com.ttProject.jcaster.plugin.base;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

public interface ISwingMainBase extends IMainBase {
	/**
	 * ベースとなるフレームを応答する。
	 * @return
	 */
	public abstract JFrame getMainFrame();
	/**
	 * 対象コンポーネントのパネルを取得
	 * @param targetClass
	 * @return
	 */
	public abstract JPanel getComponentPanel(Class<?> targetClass);
	/**
	 * 対象コンポーネントが保持すべきコンボボックスデータを応答する。
	 * @param targetClass
	 * @return
	 */
	public abstract JComboBox getComboBox(Class<?> targetClass);
}
