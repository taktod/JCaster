package com.ttProject.jcaster.outputplugin;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.flazr.core.RtmpSender;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.swing.component.GroupLayoutEx;
import com.ttProject.swing.component.JPlaceholderTextField;

/**
 * rtmpの出力モジュール
 * @author taktod
 */
public class RtmpPublishModule implements IOutputModule, ActionListener {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(RtmpPublishModule.class);
	/** 送信動作部 */
	private RtmpSender sender = null;
	/** GUI設定(swing) */
	private JPlaceholderTextField urlField;
	private JPlaceholderTextField nameField;
	private JButton connectButton;
	private JButton publishButton;
	/** flvのmshTag保持動作 */
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	/**
	 * コンストラクタ
	 */
	public RtmpPublishModule() {
		urlField = new JPlaceholderTextField(10);
		urlField.setPlaceholder("rtmpAddress");
		nameField = new JPlaceholderTextField(5);
		nameField.setPlaceholder("streamName");
		connectButton = new JButton("connect");
		connectButton.addActionListener(this);
		publishButton = new JButton("publish");
		publishButton.addActionListener(this);
		publishButton.setEnabled(false);
	}
	/**
	 * タイマーでのイベント駆動
	 */
	@Override
	public void onTimerEvent() {
	}
	/**
	 * データが転送されてきたときの動作
	 */
	@Override
	public void setMixedData(Tag tag) {
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
		if(sender != null) {
			try {
				sender.send(tag);
			}
			catch (Exception e) {
				logger.error("エラーが発生しました。", e);
			}
		}
	}
	/**
	 * 構築をすすめておく。
	 */
	public void setup() {
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			
		}
		else {
			setupSwingComponent(mainbase);
			mainbase.registerModule(this);
		}
	}
	/**
	 * swing用のセットアップ処理
	 * @param mainbase
	 */
	private void setupSwingComponent(ISwingMainBase mainbase) {
		JPanel panel = mainbase.getComponentPanel(getClass());
		panel.removeAll();
		GroupLayoutEx layout = new GroupLayoutEx(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		Object[][] components = {
				{new JLabel("Output"), mainbase.getComboBox(getClass())},
				{new JLabel("Url"), urlField},
				{new JLabel("Name"), new JComponent[]{nameField, new JLabel()}},
				{new JLabel(), new JComponent[]{connectButton, publishButton}}
		};
		layout.addComponents(components);
		panel.validate();
		panel.repaint();
	}
	/**
	 * ボタン等の入力時
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if("connect".equals(e.getActionCommand())) {
			logger.info("コネクトがおされました。");
			// rtmpの接続を開始します。
			sender = new RtmpSender(urlField.getText(), this);
			try {
				sender.open();
				connectButton.setText("close");
				urlField.setEnabled(false);
				publishButton.setEnabled(true);
			}
			catch (Exception ex) {
				logger.error(ex);
			}
		}
		if("close".equals(e.getActionCommand())) {
			logger.info("closeがおされました。");
			sender.stop();
			publishButton.setText("publish");
			publishButton.setEnabled(false);
			urlField.setEnabled(true);
			nameField.setEnabled(true);
			connectButton.setText("connect");
		}
		if("publish".equals(e.getActionCommand())) {
			logger.info("publishがおされました。");
			sender.publish();
			publishButton.setText("unpublish");
			nameField.setEnabled(false);
		}
		if("unpublish".equals(e.getActionCommand())) {
			logger.info("unpublishがおされました。");
			sender.unpublish();
			publishButton.setText("publish");
			nameField.setEnabled(true);
		}
	}
	/**
	 * mshのデータを取得する動作
	 * @throws Exception
	 */
	public void requestMshData() throws Exception {
		if(sender != null) {
			sender.send(audioMshTag);
			sender.send(videoMshTag);
		}
	}
	/**
	 * streamNameにはいっているデータを参照する動作
	 * @return
	 */
	public String getStreamName() {
		return nameField.getText();
	}
}
