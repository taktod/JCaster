package com.ttProject.jcaster.player.decode;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.swing.VideoComponent;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.h264.ConfigData;
import com.ttProject.media.h264.DataNalAnalyzer;
import com.ttProject.media.h264.Frame;
import com.ttProject.media.h264.frame.PictureParameterSet;
import com.ttProject.media.h264.frame.SequenceParameterSet;
import com.ttProject.media.h264.frame.SliceIDR;
import com.ttProject.nio.channels.ByteReadChannel;
import com.ttProject.nio.channels.IReadChannel;
import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.IStreamCoder.Direction;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * flvTagデータをデコードして、表示します。
 * とりあえず映像を表示するだけでもやっておきたいところ。
 * 音声の再生を実施するとたぶん、音声再生中にCPUが占領される現象があるので、そうしないようにしておくことにします。
 * 映像データの再生が安定しないのは、100ミリ秒ごとにデータの塊がとんでくるため、時間で制御をきちんとすれば、安定した再生になるはず。
 * @author taktod
 */
public class FlvDecoder implements Runnable {
	/** ロガー */
	private Logger logger = Logger.getLogger(FlvDecoder.class);
	// 再生時のボリューム値
	private int volume;
	/** データ表示コンポーネント */
	private VideoComponent component;
	/** 処理スレッド */
	private Thread decodeWorker = null;
	// ここにデータをためていって、再生させる。
	private LinkedBlockingQueue<Tag> dataQueue = new LinkedBlockingQueue<Tag>();
	private boolean workingFlg = true;
	private IStreamCoder videoDecoder = null;
	private IStreamCoder audioDecoder = null;
	
	// 処理用のパケットを作成しておく。
	private IPacket packet = IPacket.make();
	
	// h264用の動作補助
	private SequenceParameterSet sps;
	private PictureParameterSet pps;
	/**
	 * コンストラクタ
	 */
	public FlvDecoder() {
		component = new VideoComponent();
		decodeWorker = new Thread(this);
		decodeWorker.setDaemon(true);
		decodeWorker.setName("FlvDecoderThread");
		decodeWorker.start();
	}
	public VideoComponent getComponent() {
		return component;
	}
	/**
	 * 処理すべきtagを設定する。
	 * @param tag
	 */
	public void addTag(Tag tag) {
		// とりあえずデコードしてvideoTagだった場合に映像を出すことにします。
		dataQueue.add(tag);
	}
	@Override
	public void run() {
		try {
			while(workingFlg) {
				Tag tag = dataQueue.take();
				if(tag == null) {
					break;
				}
				// デコーダーについて調査する。
				checkDecoder(tag);
				if(tag instanceof VideoTag) {
					// 映像データの場合
					VideoTag vTag = (VideoTag) tag;
					if(vTag.isMediaSequenceHeader()) {
						// mshの場合はspsとppsを取得する。
						ConfigData configData = new ConfigData();
						IReadChannel rawData = new ByteReadChannel(vTag.getRawData());
						rawData.position(3);
						List<Frame> nals = configData.getNals(rawData);
						for(Frame nal : nals) {
							if(nal instanceof SequenceParameterSet) {
								sps = (SequenceParameterSet) nal;
							}
							else if(nal instanceof PictureParameterSet) {
								pps = (PictureParameterSet) nal;
							}
						}
					}
					else if(vTag.getCodec() == CodecType.AVC) {
						// avcの場合
						ByteBuffer rawData = vTag.getRawData();
						rawData.position(7);
						int size = rawData.remaining();
						IBuffer bufData = null;
						if(vTag.isKeyFrame()) {
							DataNalAnalyzer dataAnalyzer = new DataNalAnalyzer();
							rawData.position(3);
							IReadChannel rawDataChannel = new ByteReadChannel(rawData);
							Frame h264Frame = null;
							while((h264Frame = dataAnalyzer.analyze(rawDataChannel)) != null) {
								if(h264Frame instanceof SliceIDR) {
									break;
								}
							}
							packet.setKeyPacket(true);
							ByteBuffer spsData = sps.getData();
							ByteBuffer ppsData = pps.getData();
							ByteBuffer sliceIDRData = h264Frame.getData();
							ByteBuffer buffer = ByteBuffer.allocate(sliceIDRData.remaining() + 4 + spsData.remaining() + 4 + ppsData.remaining() + 4);
							buffer.putInt(1);
							buffer.put(spsData);
							buffer.putInt(1);
							buffer.put(ppsData);
							buffer.putInt(1);
							buffer.put(sliceIDRData);
							buffer.flip();
							size = buffer.remaining();
							bufData = IBuffer.make(null, buffer.array(), 0, size);
						}
						else {
							packet.setKeyPacket(false);
							ByteBuffer buffer = ByteBuffer.allocate(rawData.remaining() + 4);
							buffer.putInt(1);
							buffer.put(rawData.array(), 7, size);
							buffer.flip();
							size = buffer.remaining();
							bufData = IBuffer.make(null, buffer.array(), 0, size);
						}
						packet.setData(bufData);
						packet.setFlags(1);
						packet.setDts(vTag.getTimestamp());
						packet.setPts(vTag.getTimestamp());
						packet.setTimeBase(IRational.make(1, 1000));
						packet.setComplete(true, size);
						
						// データができたので、pictureに変換します。
						IVideoPicture picture = IVideoPicture.make(videoDecoder.getPixelType(), videoDecoder.getWidth(), videoDecoder.getHeight());
						int offset = 0;
						while(offset < packet.getSize()) {
							int bytesDecoded = videoDecoder.decodeVideo(picture, packet, offset);
							if(bytesDecoded < 0) {
								throw new Exception("デコードに失敗しました。");
							}
							offset += bytesDecoded;
							if(picture.isComplete()) {
								IVideoPicture newPic = picture;
								if(picture.getPixelType() != IPixelFormat.Type.BGR24) {
									IVideoResampler resampler = IVideoResampler.make(videoDecoder.getWidth(), videoDecoder.getHeight(), IPixelFormat.Type.BGR24, videoDecoder.getWidth(), videoDecoder.getHeight(), videoDecoder.getPixelType());
									newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
									if(resampler.resample(newPic, picture) < 0) {
										throw new Exception("リサンプル失敗しました。");
									}
								}
								IConverter converter = ConverterFactory.createConverter("XUGGLER-BGR-24", newPic);
								component.setImage(converter.toImage(newPic));
							}
						}
					}
					else {
						// その他のデータの場合
					}
				}
				else if(tag instanceof AudioTag) {
					
				}
			}
		}
		catch (Exception e) {
			logger.error("デコード処理で失敗しました。");
		}
	}
	private void checkDecoder(Tag tag) throws Exception {
		if(tag instanceof AudioTag) {
			// オーディオはとりあえず放置
		}
		else if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			// デコーダーが未設定もしくはコーデックが一致していない場合
			if(videoDecoder == null
					|| (vTag.getCodec() == CodecType.H263 && videoDecoder.getCodecID() != ICodec.ID.CODEC_ID_FLV1)
					|| (vTag.getCodec() == CodecType.ON2VP6 && videoDecoder.getCodecID() != ICodec.ID.CODEC_ID_VP6)
					|| (vTag.getCodec() == CodecType.ON2VP6_ALPHA && videoDecoder.getCodecID() != ICodec.ID.CODEC_ID_VP6A)
					|| (vTag.getCodec() == CodecType.AVC && videoDecoder.getCodecID() != ICodec.ID.CODEC_ID_H264)) {
				if(vTag.getCodec() == CodecType.H263) {
//					videoDecoder = IStreamCoder.make(Direction.DECODING, ICodec.ID.CODEC_ID_H264);
					throw new RuntimeException("h263の変換は未実装です");
				}
				else if(vTag.getCodec() == CodecType.AVC) {
					videoDecoder = IStreamCoder.make(Direction.DECODING, ICodec.ID.CODEC_ID_H264);
				}
				else {
					throw new RuntimeException("処理不能なコーデックを検知しました。" + vTag.getCodec());
				}
				if(videoDecoder.open(null, null) < 0) {
					throw new Exception("デコーダーが開けませんでした。");
				}
			}
		}
	}
}
