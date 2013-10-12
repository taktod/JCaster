package com.ttProject.jcaster.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.decode.FlvDecoder;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.jcaster.plugin.module.IViewerModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.swing.component.GroupLayoutEx;

/**
 * 表示用のモジュール
 * @author taktod
 * TODO チェックがはずれたときに、データをはずしたい。
 */
public class PlayViewerModule implements IViewerModule, IOutputModule {
	/** ロガー */
	private Logger logger = Logger.getLogger(PlayViewerModule.class);
	private final FlvDecoder decoder = new FlvDecoder();
	private final JCheckBox videoCheckBox;
	private final JCheckBox audioCheckBox;
	private final JSlider volumeSlider;
	private final JProgressBar vuMeterProgressBar;
	
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private boolean isSendAudioMshTag = false;
	private boolean isSendVideoMshTag = false;
	private int processPos = -1;
	private int savePos = 0;
	private int lastAudioTimestamp = -1;
	private int lastVideoTimestamp = -1;
	/**
	 * コンストラクタ
	 */
	public PlayViewerModule() {
		videoCheckBox = new JCheckBox("video");
//		videoCheckBox.addActionListener(this);
		audioCheckBox = new JCheckBox("audio");
//		audioCheckBox.addActionListener(this);
		volumeSlider = new JSlider(JSlider.VERTICAL);
		volumeSlider.setMaximum(100);
		volumeSlider.setMinimum(0);
		volumeSlider.setValue(100);
		volumeSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				// ここで変更を読み取る必要あるっぽい。
				if(decoder != null) {
					decoder.setVolume(volumeSlider.getValue());
				}
			}
		});
		vuMeterProgressBar = new JProgressBar(JProgressBar.VERTICAL);
		vuMeterProgressBar.setMaximum(100);
		vuMeterProgressBar.setMinimum(0);
	}
	/**
	 * vuMeterの値をいれておく。
	 * @param val
	 */
	public void setVuValue(int val) {
		vuMeterProgressBar.setValue(val);
	}
//	@Override
//	public void actionPerformed(ActionEvent e) {
//		logger.info("", e);
//		logger.info(videoCheckBox.isSelected());
//	}
	public void setup(JPanel panel) {
		logger.info("転送中のデータを表示するviewをセットアップします。");
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			// CUI動作はありえない。
			throw new RuntimeException("swingでのみ動作する部分です。");
		}
		else {
			setupSwingComponent(panel);
			mainbase.registerModule(this);
		}
	}
	private void setupSwingComponent(JPanel panel) {
		// とりあえずパネル上の配置をつくっておく。
		JComponent component = decoder.getComponent();
		JScrollPane scrollPane = new JScrollPane(component);
		panel.setLayout(new BorderLayout());
		panel.add(scrollPane, BorderLayout.CENTER);
		// 右にコントロール部をあつめておく。
		JPanel controlBase = new JPanel();
		JPanel control = new JPanel();
		scrollPane = new JScrollPane(controlBase, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		panel.add(scrollPane, BorderLayout.LINE_END);
		controlBase.setLayout(new BorderLayout());
		controlBase.add(control, BorderLayout.NORTH);
		GroupLayoutEx layout = new GroupLayoutEx(control);
		control.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		Dimension d = volumeSlider.getSize();
		d.height = 200;
		volumeSlider.setSize(d);
		volumeSlider.setPreferredSize(d);
		Object[][] components = {
				{videoCheckBox},
				{audioCheckBox},
				{new JLabel("volume")},
				{new JComponent[]{volumeSlider, vuMeterProgressBar}},
		};
		layout.addComponents(components);
		control.validate();
		control.repaint();
	}
	@Override
	public void onTimerEvent() {
		// タイマーによる動作
		if(decoder == null) {
			vuMeterProgressBar.setValue(0);
		}
		else {
			vuMeterProgressBar.setValue(decoder.getVolumeLevel());
		}
	}
	@Override
	public void setMixedData(Tag tag) {
		if(tag == null) {
			return;
		}
		if(tag instanceof AudioTag) {
			AudioTag aTag = (AudioTag) tag;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
				isSendAudioMshTag = false;
				return;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
				isSendVideoMshTag = false;
				return;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		// 再生中でなければ、スキップすればよし。
		if(processPos == -1) {
			processPos = tag.getTimestamp();
			savePos = 0;
		}
		// やり直しになったと見る必要なさそう。
		// ただしtimestamp = 0になったらやり直しとみてよさそう。
		else if(Math.abs((processPos - tag.getTimestamp())) > 100) {
			// 前後のticが100ミリsec以上はなれている場合はやり直しになったとみる。
			// デコーダーを作り直す必要あり。
			decoder.resetDecoder();
			lastVideoTimestamp = -1;
			lastAudioTimestamp = -1;
			// やり直しにするべき
			processPos = tag.getTimestamp();
			savePos = 0;
			isSendAudioMshTag = false;
			isSendVideoMshTag = false;
		}
		else {
			savePos += tag.getTimestamp() - processPos;
			processPos = tag.getTimestamp();
			// TODO このリセット処理は自信なし
			if(lastVideoTimestamp != -1 && lastVideoTimestamp < savePos - 1000) {
				logger.info("映像リセットがはしります。:" + lastVideoTimestamp + " / " + savePos);
				decoder.resetVideoDecoder();
				lastVideoTimestamp = -1;
			}
			else if(lastAudioTimestamp != -1 && lastAudioTimestamp < savePos - 1000) {
				logger.info("音声リセットがはしります。:" + lastAudioTimestamp + " / " + savePos);
				decoder.resetAudioDecoder();
				// audioをリセットしたら再生位置は変更したい。(もしくは再生位置をずらした応答をaudioDecoderで実施したいところ。)
				lastAudioTimestamp = -1;
			}
			// 中途でcheckboxの変更によるリセット処理も自信なし
/*			if(tag instanceof AudioTag && !audioCheckBox.isSelected()) {
				isSendAudioMshTag = false;
				lastAudioTimestamp = -1;
				return;
			}
			if(tag instanceof VideoTag && !videoCheckBox.isSelected()) {
				isSendVideoMshTag = false;
				lastVideoTimestamp = -1;
				return;
			}*/
		}
		tag.setTimestamp(savePos);
		// timestampの書き換えを実行する。
		if(tag instanceof AudioTag) {
			if(audioMshTag != null && !isSendAudioMshTag) {
				// 送る
				audioMshTag.setTimestamp(savePos);
				decoder.addTag(audioMshTag);
			}
			isSendAudioMshTag = true;
			lastAudioTimestamp = tag.getTimestamp();
			decoder.addTag(tag);
		}
		else if(tag instanceof VideoTag) {
			if(videoMshTag != null && !isSendVideoMshTag) {
				// 送る
				videoMshTag.setTimestamp(savePos);
				decoder.addTag(videoMshTag);
			}
			isSendVideoMshTag = true;
			lastVideoTimestamp = tag.getTimestamp();
			decoder.addTag(tag);
		}
	}
	public void onShutdown() {
		decoder.onShutdown();
	}
}
