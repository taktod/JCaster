package com.ttProject.jcast.viewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.log4j.Level;

import com.ttProject.jcast.viewer.log4j.ConsoleViewerAppender;
import com.ttProject.jcaster.plugin.IViewerPlugin;

/**
 * コンソール出力をGUI上に表示するviewerプラグイン
 * @author taktod
 */
public class ConsoleViewer implements IViewerPlugin, ActionListener {
	/** ひも付けしてあるappenderの保持 */
	private static ConsoleViewerAppender appender = null;
	/** textareaの変更 */
	private static JTextArea textarea = new JTextArea();
	/**
	 * バージョン情報応答
	 */
	@Override
	public String versionId() {
		return "0.0.1";
	}
	/**
	 * タイプの応答
	 */
	@Override
	public Type getType() {
		return Type.Viewer;
	}
	@Override
	public String toString() {
		return "Console";
	}
	/**
	 * 選択されたときの動作
	 */
	@Override
	public void onActivated() {
	}
	/**
	 * データの追加処理
	 * @param data
	 */
	public static void append(String data) {
		textarea.append(data);
		textarea.setCaretPosition(textarea.getDocument().getLength());
	}
	/**
	 * Appenderを登録する。
	 * @param appender
	 */
	public static void setAppender(ConsoleViewerAppender appender) {
		ConsoleViewer.appender = appender;
		if(appender.getThreshold() == null) {
			appender.setThreshold(Level.INFO);
		}
	}
	/**
	 * 表示するパネルをうけとったときの動作
	 */
	@Override
	public void setViewerPanel(JPanel panel) {
		// ここに今後のlog4jでの出力を出力していくことにしたいと思う。
		panel.setLayout(new BorderLayout());
		// このtextをappenderとひもづけておけばよさそうだな。
		JScrollPane scroll = new JScrollPane(textarea);
		panel.add(scroll, BorderLayout.CENTER);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JComboBox levelSelector = new JComboBox(new Object[]{"ALL", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF"});
		// デフォルト設定データをINFOにしておきます。
		levelSelector.setSelectedItem(appender.getThreshold().toString());
		levelSelector.addActionListener(this);
		buttonPanel.add(levelSelector);
		JButton clearButton = new JButton("clear");
		clearButton.addActionListener(this);
		buttonPanel.add(clearButton);
		panel.add(buttonPanel, BorderLayout.PAGE_END);
	}
	/**
	 * 各イベントの実行時
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if("comboBoxChanged".equals(e.getActionCommand())) {
			String levelString = (String)((JComboBox)e.getSource()).getSelectedItem();
			appender.setThreshold(Level.toLevel(levelString));
		}
		else if("clear".equals(e.getActionCommand())) {
			textarea.setText("");
		}
	}
}
