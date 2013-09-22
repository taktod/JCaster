package com.ttProject.jcaster.mixer.encode;

import java.util.concurrent.LinkedBlockingQueue;

import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.FlvTagOrderManager;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.AudioData;
import com.ttProject.media.raw.VideoData;
import com.ttProject.xuggle.flv.FlvDepacketizer;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;

/**
 * エンコードを実行する処理
 * @author taktod
 * とりあえず変換できて再生もできたけど、timestampのswapとかあるのでソートしてやらないとだめ。
 * データのソートは出力モジュールでやればいいかも・・・っておもった。
 */
public class EncodeWorker implements Runnable {
	/** データを保持しておくqueue */
	private LinkedBlockingQueue<Object> mediaDataQueue = new LinkedBlockingQueue<Object>();
	private boolean workingFlg = true;
	private Thread workingThread = null;
	// flvのデコード処理用
	private FlvPacketizer flvPacketizer = new FlvPacketizer();
	private IStreamCoder decoder = null;

	private FlvDepacketizer flvDepacketizer = new FlvDepacketizer();
	private IStreamCoder encoder = null;
	private IOutputModule target = null;
	
	public static FlvTagOrderManager orderManager = null;
	/**
	 * コンストラクタ
	 */
	public EncodeWorker() {
		workingThread = new Thread(this);
		workingThread.setName("EncoderWorker");
		workingThread.setDaemon(true);
		// もう初期化時に起動しておく。
		workingThread.start();
	}
	public void setOutputTarget(IOutputModule target) {
		this.target = target;
	}
	public void setEncoder(IStreamCoder coder) {
		this.encoder = coder;
	}
	/**
	 * 停止する
	 */
	public void close() {
		if(encoder != null) {
			encoder.close();
			encoder = null;
		}
		workingFlg = false;
		workingThread.interrupt();
		mediaDataQueue.clear();
	}
	/**
	 * 入力データをうけいれます。
	 * @param media
	 * @param mediaData
	 */
	public void setData(Media media, Object mediaData) {
		if(workingFlg) {
			mediaDataQueue.add(mediaData);
		}
	}
	/**
	 * 処理母体
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				Object mediaData = mediaDataQueue.take();
				if(mediaData instanceof VideoTag) {
					orderManager.setVideoEndFlg(false);
					// videoTagなのでVideoPictureに変換したいところ
					videoTagToVideoPicture((VideoTag) mediaData);
				}
				else if(mediaData instanceof AudioTag) {
					orderManager.setAudioEndFlg(false);
					audioTagToAudioSamples((AudioTag) mediaData);
				}
				else if(mediaData instanceof VideoData) {
					orderManager.setVideoEndFlg(false);
					// 今度はここやりたい。
					videoDataToVideoPicture((VideoData) mediaData);
				}
				else if(mediaData instanceof AudioData) {
					orderManager.setAudioEndFlg(false);
					audioDataToAudioSamples((AudioData) mediaData);
				}
				else if(mediaData instanceof IVideoPicture) {
					orderManager.setVideoEndFlg(false);
					// TODO まだ未検証
					videoPictureToFlvTag((IVideoPicture) mediaData);
				}
				else if(mediaData instanceof IAudioSamples) {
					orderManager.setAudioEndFlg(false);
					// TODO まだ未検証
					audioSampleToFlvTag((IAudioSamples) mediaData);
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			workingFlg = false;
			mediaDataQueue.clear();
			if(decoder != null) {
				decoder.close();
				decoder = null;
			}
			if(encoder != null) {
				encoder.close();
				encoder = null;
			}
		}
	}
	private void videoTagToVideoPicture(VideoTag tag) throws Exception {
		IPacket packet = flvPacketizer.getPacket(tag);
		if(packet == null) {
			return;
		}
		if(decoder == null || !FlvPacketizer.isSameCodec(tag, decoder)) {
			if(decoder != null) {
				decoder.close();
				decoder = null;
			}
			decoder = flvPacketizer.createVideoDecoder();
		}
		// packetに対してデコードを実行
		int offset = 0;
		IVideoPicture picture = IVideoPicture.make(decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());
		while(offset < packet.getSize()) {
			int bytesDecoded = decoder.decodeVideo(picture, packet, offset);
			if(bytesDecoded <= 0) {
				throw new Exception("デコード中に失敗しました。");
			}
			offset += bytesDecoded;
			if(picture.isComplete()) {
				// videoPictureができたので、flvTagにまわす。
				picture.setTimeStamp(tag.getTimestamp() * 1000); // ここでtimestampをいれておかないと狂うらしい。
				// つづきをよろしく。
				videoPictureToFlvTag(picture);
			}
		}
	}
	private void audioTagToAudioSamples(AudioTag tag) throws Exception {
		IPacket packet = flvPacketizer.getPacket(tag);
		if(packet == null) {
			return;
		}
		if(decoder == null || !FlvPacketizer.isSameCodec(tag, decoder)) {
			if(decoder != null) {
				decoder.close();
				decoder = null;
			}
			decoder = flvPacketizer.createAudioDecoder();
		}
		// packetに対してデコードを実行
		int offset = 0;
		IAudioSamples samples = IAudioSamples.make(1024, decoder.getChannels());
		while(offset < packet.getSize()) {
			int bytesDecoded = decoder.decodeAudio(samples, packet, offset);
			if(bytesDecoded < 0) {
				throw new Exception("デコード中にエラーが発生しました。");
			}
			offset += bytesDecoded;
			if(samples.isComplete()) {
				samples.setTimeStamp(tag.getTimestamp() * 1000);
				audioSampleToFlvTag(samples);
			}
		}
	}
	private void audioDataToAudioSamples(AudioData data) {
		
	}
	private void videoDataToVideoPicture(VideoData data) {
		
	}
	private void videoPictureToFlvTag(IVideoPicture picture) throws Exception {
		if(encoder == null) {
			return;
		}
		// サイズを確認して、サイズが違う場合はリサンプルしてやる必要あり。
		IVideoPicture pic = picture;
		// 入力videoPictureとターゲットvideoPictureが一致するかわからないので、合わせる必要あり。
		if(picture.getHeight() != encoder.getHeight()
			|| picture.getWidth() != encoder.getWidth()
			|| picture.getPixelType() != encoder.getPixelType()) {
			// どれか１つ違う場合はリサンプルする必要あり。
			IVideoResampler resampler = IVideoResampler.make(encoder.getWidth(), encoder.getHeight(), encoder.getPixelType(), picture.getWidth(), picture.getHeight(), picture.getPixelType());
			pic = IVideoPicture.make(encoder.getPixelType(), encoder.getWidth(), encoder.getHeight());
			resampler.resample(pic, picture);
		}
		// このpictureをエンコードにかける。
		IPacket packet = IPacket.make();
		if(encoder.encodeVideo(packet, pic, 0) < 0) {
			throw new Exception("変換失敗");
		}
		if(packet.isComplete()) {
			for(Tag tag : flvDepacketizer.getTag(encoder, packet)) {
				if(target != null) {
					synchronized(orderManager) {
//						orderManager.addTag(tag);
//						for(Tag t : orderManager.getCompleteTags()) {
							target.setMixedData(tag);
//						}
					}
				}
			}
		}
	}
	private void audioSampleToFlvTag(IAudioSamples samples) throws Exception {
		if(encoder == null) {
			return;
		}
		IAudioSamples spl = samples;
		if(samples.getChannels() != encoder.getChannels()
			|| samples.getSampleRate() != encoder.getSampleRate()) {
			IAudioResampler resampler = IAudioResampler.make(encoder.getChannels(), samples.getChannels(), encoder.getSampleRate(), samples.getSampleRate());
			spl = IAudioSamples.make(samples.getNumSamples(), encoder.getChannels());
			resampler.resample(spl, samples, samples.getNumSamples());
		}
		IPacket packet = IPacket.make();
		int samplesConsumed = 0;
		while(samplesConsumed < spl.getNumSamples()) {
			int retval = encoder.encodeAudio(packet, spl, samplesConsumed);
			if(retval < 0) {
				throw new Exception("変換失敗");
			}
			samplesConsumed += retval;
			if(packet.isComplete()) {
				for(Tag tag : flvDepacketizer.getTag(encoder, packet)) {
					if(target != null) {
						synchronized (orderManager) {
//							orderManager.addTag(tag);
//							for(Tag t : orderManager.getCompleteTags()) {
								target.setMixedData(tag);
//							}
						}
					}
				}
			}
		}
	}
}
