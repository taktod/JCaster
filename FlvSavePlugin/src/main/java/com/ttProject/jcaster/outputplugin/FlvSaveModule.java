package com.ttProject.jcaster.outputplugin;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.flvsave.core.FlvSaver;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.swing.component.GroupLayoutEx;

/**
 * flvの保存を実行するモジュール
 * @author taktod
 */
public class FlvSaveModule implements IOutputModule, ActionListener, KeyListener {
	/** 動作ロガー */
	private Logger logger = Logger.getLogger(FlvSaveModule.class);
	private final JTextField fileField;
	private final JButton fileButton;
	private final JButton saveButton;
	private final JLabel posInfoLabel;
	private VideoTag videoMshTag = null;
	private AudioTag audioMshTag = null;
	// 最終で取得したデータとあたらしいデータが100tic以上はなれている場合はあたらしいデータがきたとして処理した方がよさそう。
	// だが１つのファイルとしたいと思う。
	private FlvSaver saver = null;
	private SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
	{
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	public FlvSaveModule() {
		fileField = new JTextField(10);
		fileField.addKeyListener(this);
		fileButton = new JButton("file");
		fileButton.addActionListener(this);
		saveButton = new JButton("save");
		saveButton.addActionListener(this);
		saveButton.setEnabled(false);
		posInfoLabel = new JLabel("00:00:00");
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
				{new JLabel("Output"), mainbase.getComboBox(getClass())},
				{new JLabel("File"), new JComponent[]{fileField, fileButton}},
				{new JLabel(), posInfoLabel},
				{new JLabel(), saveButton}
		};
		layout.addComponents(components);
		panel.validate();
		panel.repaint();
	}
	@Override
	public void onTimerEvent() {

	}
	/**
	 * mixerで変換されたデータを受け入れる。
	 */
	@Override
	public void setMixedData(Tag tag) {
		logger.info("受け入れTag:" + tag);
		if(tag instanceof AudioTag) {
			AudioTag aTag = (AudioTag) tag;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		if(saver != null) {
			try {
				saver.write(tag);
			}
			catch (Exception e) {
				logger.error("データ書き込みに失敗しました。", e);
			}
		}
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			logger.error("swingのbaseが取得できませんでした。");
			return;
		}
		if("file".equals(e.getActionCommand())) {
			logger.info("file選択ダイアログがクリックされたときの動作");
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setAcceptAllFileFilterUsed(true);
			int selected = fileChooser.showSaveDialog(mainbase.getMainFrame());
			if(selected == JFileChooser.APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				fileField.setText(file.toString());
				saveButton.setEnabled(true);
			}
		}
		else if("save".equals(e.getActionCommand())) {
			logger.info("保存ボタンがおされたとき");
			try {
				saver = new FlvSaver(fileField.getText());
				saver.write(audioMshTag);
				saver.write(videoMshTag);
				saveButton.setText("stop");
			}
			catch (Exception ex) {
				logger.error("保存開始できませんでした。", ex);
			}
		}
		else if("stop".equals(e.getActionCommand())) {
			logger.info("保存がおわったとき");
			saver.close();
			saveButton.setText("save");
		}
	}
	@Override
	public void keyPressed(KeyEvent e) {}
	@Override
	public void keyReleased(KeyEvent e) {};
	@Override
	public void keyTyped(KeyEvent e) {
		if("".equals(fileField.getText())) {
			saveButton.setEnabled(false);
		}
		else {
			saveButton.setEnabled(true);
		}
	}
}
