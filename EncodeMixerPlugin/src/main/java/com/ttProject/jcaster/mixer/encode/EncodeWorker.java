package com.ttProject.jcaster.mixer.encode;

import java.util.concurrent.LinkedBlockingQueue;

import com.ttProject.jcaster.plugin.base.IMainBase.Media;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.AudioData;
import com.ttProject.media.raw.VideoData;
import com.ttProject.xuggle.flv.FlvDepacketizer;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IStreamCoder.Direction;
import com.xuggle.xuggler.IVideoResampler;

/**
 * エンコードを実行する処理
 * @author taktod
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
	/**
	 * コンストラクタ
	 */
	public EncodeWorker() {
		workingThread = new Thread(this);
		workingThread.setName("EncoderWorker");
		workingThread.setDaemon(true);
		// もう初期化時に起動しておく。
		workingThread.start();

		// 変換用のコーダーをつくっておく。
		encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_FLV1);
		IRational frameRate = IRational.make(15, 1); // 15fps
		encoder.setNumPicturesInGroupOfPictures(30); // gopを30にしておく。keyframeが30枚ごとになる。
		encoder.setBitRate(250000); // 250kbps
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
	}
	public void setOutputTarget(IOutputModule target) {
		this.target = target;
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
					// videoTagなのでVideoPictureに変換したいところ
					videoTagToVideoPicture((VideoTag) mediaData);
				}
				else if(mediaData instanceof AudioTag) {
					
				}
				else if(mediaData instanceof VideoData) {
					
				}
				else if(mediaData instanceof AudioData) {
					
				}
				else if(mediaData instanceof IVideoPicture) {
					
				}
				else if(mediaData instanceof IAudioSamples) {
					
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
		}
	}
	private void videoTagToVideoPicture(VideoTag tag) throws Exception {
		System.out.print("in");
		System.out.println(tag);
		IPacket packet = flvPacketizer.getPacket(tag);
		if(packet == null) {
			return;
		}
		if(decoder == null || !FlvPacketizer.isSameCodec(tag, decoder)) {
			if(decoder != null) {
				decoder.close();
				decoder = null;
			}
			System.out.println("decoderつくります。");
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
	private void audioTagToAudioSamples(AudioTag tag) {
		
	}
	private void audioDataToAudioSamples(AudioData data) {
		
	}
	private void videoDataToVideoPicture(VideoData data) {
		
	}
	private void videoPictureToFlvTag(IVideoPicture picture) throws Exception {
		System.out.println(picture);
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
			System.out.println(packet);
			for(Tag tag : flvDepacketizer.getTag(encoder, packet)) {
				if(target != null) {
					synchronized(target) {
						target.setMixedData(tag);
					}
				}
			}
		}
	}
	private void audioSampleToFlvTag(IAudioSamples samples) {
		
	}
}
