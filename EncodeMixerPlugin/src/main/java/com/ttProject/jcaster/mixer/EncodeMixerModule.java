package com.ttProject.jcaster.mixer;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.mixer.encode.EncodeWorker;
import com.ttProject.jcaster.mixer.util.MediaUtil;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.AudioData;
import com.ttProject.media.raw.VideoData;
import com.ttProject.swing.component.GroupLayoutEx;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IStreamCoder.Direction;
import com.xuggle.xuggler.IVideoPicture;

public class EncodeMixerModule implements IMixerModule {
	/** ロガー */
	private final Logger logger = Logger.getLogger(EncodeMixerModule.class);
	
	/** 受け渡しを実行する出力モジュール */
	private IOutputModule targetModule;
	private EncodeWorker videoWorker = null;
	private EncodeWorker audioWorker = null;
	
	private boolean zeroReset = true;
	public void remove() {
		if(videoWorker != null) {
			videoWorker.close();
		}
		if(audioWorker != null) {
			audioWorker.close();
		}
	}
	/**
	 * セットアップ
	 */
	public void setup() {
		EncodeWorker.startTimestamp = -1;
		videoWorker = new EncodeWorker();
		videoWorker.setOutputTarget(targetModule);
		
		IStreamCoder encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_FLV1);
		IRational frameRate = IRational.make(15, 1); // 15fps
		encoder.setNumPicturesInGroupOfPictures(30); // gopを30にしておく。keyframeが30枚ごとになる。
		encoder.setBitRate(650000); // 650kbps
		encoder.setBitRateTolerance(9000);
		encoder.setPixelType(IPixelFormat.Type.YUV420P);
		encoder.setWidth(320);
		encoder.setHeight(240);
		encoder.setGlobalQuality(10);
		encoder.setFrameRate(frameRate);
		encoder.setTimeBase(IRational.make(1, 1000)); // 1/1000設定(flvはこうなるべき)
		if(encoder.open(null, null) < 0) {
			throw new RuntimeException("エンコーダーが開けませんでした。");
		}
		videoWorker.setEncoder(encoder);
		
		audioWorker = new EncodeWorker();
		audioWorker.setOutputTarget(targetModule);
		
		encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_AAC);
		encoder.setSampleRate(44100);
		encoder.setChannels(2);
		encoder.setBitRate(96000);
		if(encoder.open(null, null) < 0) {
			throw new RuntimeException("変換コーダーが開けませんでした。");
		}
		audioWorker.setEncoder(encoder);
		
		logger.info("データを変換するmixerの初期化");
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
				{new JLabel("Mixer"), mainbase.getComboBox(getClass())},
				{new JLabel("Video"), new JComboBox(new String[]{"Avc(H264)", "H263"})},
				{new JLabel("Size"), new JComboBox(new String[]{"1024x768", "1024x576", "800x600", "800x450", "640x480", "640x360", "512x384", "512x288", "480x360", "480x270", "320x240", "320x180", "160x120", "160x90"})},
				{new JLabel("Bitrate"), new JComponent[] {new JComboBox(new String[]{"960", "800", "640", "320", "240", "120"}), new JLabel("Kbps")}},
				{new JLabel("Fps"), new JComboBox(new String[]{"30.00", "29.97", "20.00", "15.00", "14.48", "8.00", "5.00", "3.00", "1.00"})},
				{new JLabel("Audio"), new JComboBox(new String[]{"Aac", "Mp3"})},
				{new JLabel("Bitrate"), new JComponent[] {new JComboBox(new String[]{"96", "64", "48", "40", "32"}), new JLabel("Kbps")}},
				{new JLabel("Channel"), new JComboBox(new String[]{"Stereo", "Mono"})},
				{new JLabel("SampleRate"), new JComponent[]{new JComboBox(new String[]{"44100", "22050", "11025"}), new JLabel("Hz")}},
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
		videoWorker.setOutputTarget(targetModule);
		audioWorker.setOutputTarget(targetModule);
	}
	// 出力データのオーダーを並べ替えるには、どういうデータがながれているか知っておく必要あり。
	/**
	 * データを入力モジュールから受け取ったときの動作
	 */
	@Override
	public synchronized void setData(Object mediaData) {
		// TODO ここでデータを監視して、リセットされたときに元に戻す必要がありそうだ。timestampが大きくずれているときの処理
		if(MediaUtil.getTimestamp(mediaData) == 0) {
			logger.info("timestamp = 0");
			if(zeroReset) {
				// リセットがはいったので、なんとかしておかないとだめ。
				videoWorker.close();
				audioWorker.close();
				//
				EncodeWorker.startTimestamp = -1;
				// TODO とりあえずworkerはリセットすべき。
				videoWorker = new EncodeWorker();
				videoWorker.setOutputTarget(targetModule);
				
				IStreamCoder encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_FLV1);
				IRational frameRate = IRational.make(15, 1); // 15fps
				encoder.setNumPicturesInGroupOfPictures(30); // gopを30にしておく。keyframeが30枚ごとになる。
				encoder.setBitRate(650000); // 650kbps
				encoder.setBitRateTolerance(9000);
				encoder.setPixelType(IPixelFormat.Type.YUV420P);
				encoder.setWidth(320);
				encoder.setHeight(240);
				encoder.setGlobalQuality(10);
				encoder.setFrameRate(frameRate);
				encoder.setTimeBase(IRational.make(1, 1000)); // 1/1000設定(flvはこうなるべき)
				if(encoder.open(null, null) < 0) {
					throw new RuntimeException("エンコーダーが開けませんでした。");
				}
				videoWorker.setEncoder(encoder);
				
				audioWorker = new EncodeWorker();
				audioWorker.setOutputTarget(targetModule);
				
				encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_AAC);
				encoder.setSampleRate(44100);
				encoder.setChannels(2);
				encoder.setBitRate(96000);
				if(encoder.open(null, null) < 0) {
					throw new RuntimeException("変換コーダーが開けませんでした。");
				}
				audioWorker.setEncoder(encoder);
			}
			zeroReset = false;
		}
		else {
			zeroReset = true;
		}
		// モジュールの受け渡し(変換が必要な場合)は各threadにやらせる。(大本のthreadには悪影響を与えたくないため。)
		if(mediaData instanceof VideoTag) {
			if(videoWorker != null) {
				videoWorker.setData(mediaData);
			}
		}
		else if(mediaData instanceof AudioTag) {
			if(audioWorker != null) {
				audioWorker.setData(mediaData);
			}
		}
		else if(mediaData instanceof VideoData) {
			if(videoWorker != null) {
				videoWorker.setData(mediaData);
			}
		}
		else if(mediaData instanceof AudioData) {
			if(audioWorker != null) {
				audioWorker.setData(mediaData);
			}
		}
		else if(mediaData instanceof IVideoPicture) {
			if(videoWorker != null) {
				videoWorker.setData(mediaData);
			}
		}
		else if(mediaData instanceof IAudioSamples) {
			if(audioWorker != null) {
				audioWorker.setData(mediaData);
			}
		}
		// ただしthreadをつくりまくって影響をあたえるのもいや。
	}
}
