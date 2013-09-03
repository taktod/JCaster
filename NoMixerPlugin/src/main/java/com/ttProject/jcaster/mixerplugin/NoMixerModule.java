package com.ttProject.jcaster.mixerplugin;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.swing.component.GroupLayoutEx;

/**
 * mixer用のモジュール(なにもせずにスルーする動作)
 * @author taktod
 */
public class NoMixerModule implements IMixerModule {
	/** 動作ロガー */
	private final Logger logger = Logger.getLogger(NoMixerModule.class);

	private VideoTag videoMshTag = null;
	private AudioTag audioMshTag = null;
	private int passedTime;
	private IOutputModule targetModule;
	/**
	 * セットアップ
	 */
	public void setup() {
		logger.info("スルーするmixer動作の初期化");
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			// CUIの動作なのでCUIの動作を実施すべき
		}
		else {
			setupSwingComponent(mainbase);
			mainbase.registerModule(this);
		}
	}
	/**
	 * swingのコンポーネントを構築します。
	 * @param mainbase
	 */
	public void setupSwingComponent(ISwingMainBase mainbase) {
		JPanel panel = mainbase.getComponentPanel(getClass());
		panel.removeAll();
		GroupLayoutEx layout = new GroupLayoutEx(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		
		Object[][] components = {
				{new JLabel("Mixer"), mainbase.getComboBox(getClass())},
		};
		layout.addComponents(components);
		panel.validate();
		panel.repaint();
	}
	/**
	 * タイマーによる処理
	 */
	@Override
	public void onTimerEvent() {

	}
	/**
	 * メディアデータの受け入れ口
	 */
	@Override
	public void setData(Media media, Object mediaData) {
		if(media == Media.FlvTag && mediaData instanceof Tag) {
			Tag tag = (Tag)mediaData;
			if(tag instanceof AudioTag) {
				AudioTag aTag = (AudioTag) tag;
				if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
					audioMshTag = aTag;
				}
				if(aTag.getCodec() != CodecType.AAC) {
					audioMshTag = null;
				}
			}
			if(tag instanceof VideoTag) {
				VideoTag vTag = (VideoTag) tag;
				if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
					videoMshTag = vTag;
				}
				if(vTag.getCodec() != CodecType.AVC) {
					videoMshTag = null;
				}
			}
			passedTime = tag.getTimestamp();
			// videoMshかaudioMshである場合はデータを保持する必要あり。
			if(targetModule != null) {
				targetModule.setMixedData(tag);
			}
		}
		else {
			// flvデータではないので、受け入れられません。
		}
	}
	@Override
	public void registerOutputModule(IOutputModule outputModule) {
		targetModule = outputModule;
		// 登録したときにすでにmshデータがある場合は登録する必要あり。
		if(targetModule != null) {
			if(audioMshTag != null) {
				audioMshTag.setTimestamp(passedTime);
				targetModule.setMixedData(audioMshTag);
			}
			if(videoMshTag != null) {
				videoMshTag.setTimestamp(passedTime);
				targetModule.setMixedData(videoMshTag);
			}
		}
	}
}
