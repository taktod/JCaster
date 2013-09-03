package com.ttProject.jcaster;

import javax.swing.JFrame;
import javax.swing.UIManager;

import com.ttProject.jcaster.swing.MainFrame;

/**
 * jcastのメインエントリー
 * @author taktod
 */
public class JCaster {
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		setupForMac();
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		MainFrame frame = new MainFrame();
		frame.setup();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}
	/**
	 * mac用の特殊処理
	 * @return
	 */
	private static void setupForMac() {
		String lowCaseOSName = System.getProperty("os.name").toLowerCase();
		if(lowCaseOSName.startsWith("mac os x")) {
			// macの場合
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", "JCaster");
		}
	}
}
