package com.ttProject.jcaster.reader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.flvreader.FlvFeeder;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IInputModule;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.swing.component.GroupLayoutEx;

/**
 * データの入力モジュール
 * @author taktod
 */
public class FlvReaderModule implements IInputModule, ActionListener, KeyListener, ChangeListener, FocusListener {
	/** 動作ロガー */
	private final Logger logger = Logger.getLogger(FlvReaderModule.class);
	/** swingのフィールド設定 */
	private final JTextField fileField;
	private final JButton fileButton;
	private final JButton playButton;
	private final JSlider positionSlider;
	private final JLabel posInfoLabel;
	/** データやりとり用 */
	FlvFeeder feeder = null;
	/** 引き継ぎ用のモジュール */
	IMixerModule targetModule = null;
	/** 表示用のフォーマットデータ */
	private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
	/**
	 * コンストラクタ
	 */
	public FlvReaderModule() {
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		fileField = new JTextField(10);
		fileField.addKeyListener(this);
		fileField.addFocusListener(this);
		fileButton = new JButton("file");
		fileButton.addActionListener(this);
		playButton = new JButton("play");
		playButton.setEnabled(false);
		playButton.addActionListener(this);
		positionSlider = new JSlider(0, 0, 0);
		positionSlider.setEnabled(false);
		positionSlider.addChangeListener(this);
		posInfoLabel = new JLabel("00:00:00 / 00:00:00");
	}
	public void setup() {
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			
		}
		else {
			setupSwingComponent(mainbase);
			mainbase.registerModule(this);
		}
	}
	private void setupSwingComponent(ISwingMainBase mainbase) {
		JPanel panel = mainbase.getComponentPanel(getClass());
		panel.removeAll();
		GroupLayoutEx layout = new GroupLayoutEx(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		Object[][] components = {
				{new JLabel("Input"), mainbase.getComboBox(getClass())},
				{new JLabel("File"), new JComponent[]{fileField, fileButton}},
				{new JLabel("Pos"), positionSlider},
				{new JLabel(), posInfoLabel},
				{new JLabel(), playButton}
		};
		layout.addComponents(components);
		panel.validate();
		panel.repaint();
	}
	@Override
	public void onTimerEvent() {
		if(feeder != null) {
			if(feeder.onTimerEvent()) {
				if(feeder.getTotalDuration() != 0) {
					positionSlider.setValue((int)(feeder.getCurrentDuration() / 1000));
				}
				else {
					posInfoLabel.setText(format.format(new Date(feeder.getCurrentDuration())) + " / 00:00:00");
				}
			}
			else {
				// データがなくなった。
			}
		}
	}
	@Override
	public void registerMixerModule(IMixerModule mixerModule) {
		// すでにmediaSequenceHeaderが送られている場合はデータの登録が必要。
		targetModule = mixerModule;
		if(feeder != null) {
			feeder.setMixerModule(targetModule);
		}
	}
	@Override
	public void focusGained(FocusEvent e) {}
	@Override
	public void focusLost(FocusEvent e) {
		setupFeeder();
	}
	@Override
	public void stateChanged(ChangeEvent e) {
		if(!positionSlider.getValueIsAdjusting()) {
			// 表示位置がきりかわったら再生中ならそこから再生を再開したいところ。
			// このままだと再生していないときでもシークしたら再生はじまっちゃうね。
			if(playButton.getText().equals("stop") && Math.abs(positionSlider.getValue() - feeder.getCurrentDuration() / 1000) > 2) {
				logger.info("再生をやりなおしやってみる。");
				try {
					feeder.start(positionSlider.getValue());
				}
				catch (Exception ex) {
					logger.error("再送信失敗", ex);
				}
			}
		}
		// 位置がかわったら、posTextの表示変更
		posInfoLabel.setText(format.format(new Date(positionSlider.getValue() * 1000)) + " / " + format.format(new Date(feeder.getTotalDuration())));
		posInfoLabel.validate();
		posInfoLabel.repaint();
	}
	@Override
	public void keyPressed(KeyEvent e) {}
	@Override
	public void keyReleased(KeyEvent e) {}
	@Override
	public void keyTyped(KeyEvent e) {
		if("".equals(fileField.getText())) {
			playButton.setEnabled(false);
			positionSlider.setEnabled(false);
		}
		else {
			playButton.setEnabled(true);
			positionSlider.setEnabled(true);
			positionSlider.setValue(0);
		}
	}
	@Override
	public void actionPerformed(ActionEvent event) {
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			logger.error("swingのbaseが取得できませんでした。");
			return;
		}
		if("file".equals(event.getActionCommand())) {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("flv file", "flv"));
			int selected = fileChooser.showOpenDialog(mainbase.getMainFrame());
			if(selected == JFileChooser.APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				fileField.setText(file.toString());
				playButton.setEnabled(true);
				positionSlider.setEnabled(true);
				positionSlider.setValue(0);
				setupFeeder();
			}
		}
		if("play".equals(event.getActionCommand())) {
			logger.info("playボタンが押されました。");
			try {
				feeder.start(positionSlider.getValue());
				fileField.setEnabled(false);
				fileButton.setEnabled(false);
				playButton.setText("stop");
			}
			catch (Exception e) {
				logger.error("例外が発生しました。", e);
			}
		}
		if("stop".equals(event.getActionCommand())) {
			stop();
		}
	}
	public void stop() {
		if(feeder != null) {
			feeder.close();
			feeder = null;
		}
		// このタイミングでもう一度初期化しておかないと再開始できない。
		setupFeeder();
		logger.info("stopボタンが押されました。");
		fileField.setEnabled(true);
		fileButton.setEnabled(true);
		playButton.setText("play");
	}
	private void setupFeeder() {
		try {
			feeder = new FlvFeeder(fileField.getText());
			feeder.initialize();
			feeder.setMixerModule(targetModule);
			positionSlider.setMaximum((int)feeder.getTotalDuration() / 1000);
		}
		catch (Exception ex) {
			logger.error("エラーが発生しました。", ex);
		}
	}
}
