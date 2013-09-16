package com.ttProject.jcaster.encode;

import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.encode.worker.AudioEncodeWorker;
import com.ttProject.jcaster.encode.worker.FlvAudioTagDecodeWorker;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.swing.component.GroupLayoutEx;
import com.ttProject.swing.component.JPlaceholderTextField;
import com.ttProject.util.HexUtil;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IVideoPicture;

/**
 * パラメーターとして必要なものメモ：
 * h263にする場合
 * GOP
 * bitrate
 * bitrateTorelance
 * pixelType(YUV420P)
 * width
 * height
 * globalquality
 * frameRate
 * timebase(1/1000)
 * 
 * avcにする場合
 * GOP
 * bitrate
 * bitratetolerance
 * pixelType(YUV420P)
 * width
 * height
 * globalQuality
 * frameRate
 * timebase(1/1000)
 * 以下h264のパラメーター？
 * level coder qmin bf wprefp cmp partitions me_method subq merange keyintmin sc_threshold iqfactor bstrategy qcomp qmax qdiff directpred cqp LoopFilter ClosedGOP
 * 
 * mp3 & aac
 * sampleRate
 * channels
 * bitrate
 * @author taktod
 */
public class EncodeMixerModule implements IMixerModule {
	/** ロガー */
	private final Logger logger = Logger.getLogger(EncodeMixerModule.class);
	private IOutputModule targetModule;
	
	private FlvAudioTagDecodeWorker flvAudioTagDecoder = new FlvAudioTagDecodeWorker();
	private AudioEncodeWorker audioEncodeWorker;
	public EncodeMixerModule() {
		try {
			audioEncodeWorker = new AudioEncodeWorker();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * セットアップ
	 */
	public void setup() {
		logger.info("エンコードするmixer動作の初期化");
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
	private void setupSwingComponent(ISwingMainBase mainbase) {
		JPanel panel = mainbase.getComponentPanel(getClass());
		panel.removeAll();
		GroupLayoutEx layout = new GroupLayoutEx(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		JPlaceholderTextField ptfWidth = new JPlaceholderTextField(5);
		ptfWidth.setPlaceholder("Width");
		JPlaceholderTextField ptfHeight = new JPlaceholderTextField(5);
		ptfHeight.setPlaceholder("Height");
		Object[][] components = {
				{new JLabel("Mixer"), mainbase.getComboBox(getClass())},
				{new JLabel("Video"), new JComboBox(new String[]{"avc", "h263"})},
				{new JLabel("Size"), new JComponent[]{ptfWidth, new JLabel("x"), ptfHeight}},
				{new JLabel("Fps"), new JTextField(10)},
				{new JLabel("Bitrate"), new JTextField(10)},
				{new JLabel("Audio"), new JComboBox(new String[]{"aac", "mp3"})},
				{new JLabel("Channels"), new JComboBox(new String[]{"stereo", "monoral"})},
				{new JLabel("Bitrate"), new JTextField(10)},
		};
		layout.addComponents(components);
		panel.validate();
		panel.repaint();
	}
	@Override
	public void onTimerEvent() {
	}

	@Override
	public void registerOutputModule(IOutputModule outputModule) {
		targetModule = outputModule;
	}

	@Override
	public void setData(Media media, Object mediaData) {
		switch(media) {
		case FlvTag:
			if(mediaData instanceof AudioTag) {
				AudioTag aTag = (AudioTag) mediaData;
				try {
					// この方法で戻していると、処理に時間がかかる場合詰まってしまう。
					List<IAudioSamples> samplesList = flvAudioTagDecoder.getSamples(aTag);
					for(IAudioSamples samples : samplesList) {
						setData(Media.AudioSamples, samples);
					}
				}
				catch (Exception e) {
					logger.error("flvのaudioサンプル化に失敗しました。", e);
				}
			}
			return;
		case AudioData:
			// audioSamplesに戻す必要あり
			break;
		case VideoData:
			// videoDataに戻す必要あり
			break;
		case VideoPicture:
			break;
		case AudioSamples:
			if(mediaData instanceof IAudioSamples) {
				try {
					for(Tag tag : audioEncodeWorker.makeTags((IAudioSamples) mediaData)) {
						System.out.println(HexUtil.toHex(tag.getBuffer(), true));
						if(targetModule != null) {
							targetModule.setMixedData(tag);
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			break;
		default:
			break;
		}
	}
	public void onShutdown() {
		flvAudioTagDecoder.onShutdown();
		audioEncodeWorker.onShutdown();
	}
}
