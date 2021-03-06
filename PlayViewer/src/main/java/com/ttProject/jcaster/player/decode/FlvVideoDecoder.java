package com.ttProject.jcaster.player.decode;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.swing.VideoComponent;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.VideoData;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * 映像をデコードして表示させる処理
 * @author taktod
 *
 */
public class FlvVideoDecoder implements Runnable {
	/** ロガー */
	private final Logger logger = Logger.getLogger(FlvVideoDecoder.class);
	/** 表示コンポーネント */
	private boolean workingFlg = true;
	private final VideoComponent component;
	private final Thread worker;
	private final LinkedBlockingQueue<VideoTag> dataQueue;
	private final FlvPacketizer packetizer;
	private VideoTag lastVideoTag = null;
	private IStreamCoder videoDecoder = null;
	private FlvAudioDecoder audioDecoder;
	private final LinkedList<VideoData> videoDataQueue;
	private IVideoPicture picture;
//	private long nextAcceptVideoTimestamp = -1;
	private boolean waitingFlg = false;
	private final IPacket packet = IPacket.make();
	/**
	 * コンストラクタ
	 * @param audioDecoder
	 */
	public FlvVideoDecoder(FlvAudioDecoder audioDecoder) {
		this(audioDecoder, new VideoComponent());
	}
	/**
	 * コンストラクタ
	 * @param audioDecoder
	 */
	public FlvVideoDecoder(FlvAudioDecoder audioDecoder, VideoComponent component) {
		this.audioDecoder = audioDecoder;
		this.component = component;
		dataQueue = new LinkedBlockingQueue<VideoTag>();
		packetizer = new FlvPacketizer();
		videoDataQueue = new LinkedList<VideoData>();
		worker = new Thread(this);
		worker.setDaemon(true);
		worker.setName("VideoPlayViewerWorker");
		worker.setPriority(1);
		worker.start();
	}
	/**
	 * audioDecoderを更新する
	 * @param audioDecoder
	 */
	public void setAudioDecoder(FlvAudioDecoder audioDecoder) {
		this.audioDecoder = audioDecoder;
	}
	/**
	 * コンポーネント参照
	 * @return
	 */
	public VideoComponent getComponent() {
		return component;
	}
	/**
	 * 処理すべきタグ追加
	 * @param tag
	 */
	public void addTag(Tag tag) {
		if(tag instanceof VideoTag) {
			synchronized(dataQueue) {
				dataQueue.add((VideoTag)tag);
			}
		}
	}
	/**
	 * 内部処理
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				VideoTag tag = null;
				synchronized(dataQueue) {
					if(dataQueue.size() != 0) {
						tag = dataQueue.poll();
					}
				}
				if(tag == null) {
					waitingFlg = true;
					Thread.sleep(10);
					waitingFlg = false;
					continue;
				}
				IPacket packet = packetizer.getPacket(tag, this.packet);
				if(packet == null) {
					continue;
				}
				if(lastVideoTag == null || lastVideoTag.getCodec() != tag.getCodec()) {
					// コーデックが違う場合は取り直す必要あり
					videoDecoder = packetizer.createVideoDecoder();
				}
				lastVideoTag = tag;
				int offset = 0;
				while(offset < packet.getSize()) {
					if(picture == null) {
						picture = IVideoPicture.make(videoDecoder.getPixelType(), videoDecoder.getWidth(), videoDecoder.getHeight());
					}
					int bytesDecoded = 0;
					bytesDecoded = videoDecoder.decodeVideo(picture, packet, offset);
					if(bytesDecoded < 0) {
						logger.info("デコード中に問題発生。");
//						throw new Exception("映像のデコード中に問題が発生しました。");
						break;
					}
					offset += bytesDecoded;
					if(picture.isComplete()) {
						IVideoPicture newPic = picture;
						// フレームのスキップを実装しておかないと、処理の重いフレームにあたるとアウトになる。
						long timestamp = audioDecoder.getTimestamp();
						if(tag.getTimestamp() < timestamp) {
							logger.info("時間がかかりすぎているので、スキップする。");
							continue;
						}
//						long startTime = System.currentTimeMillis();
						if(picture.getPixelType() != IPixelFormat.Type.BGR24) {
							IVideoResampler resampler = IVideoResampler.make(videoDecoder.getWidth(), videoDecoder.getHeight(), IPixelFormat.Type.BGR24, picture.getWidth(), picture.getHeight(), picture.getPixelType());
							newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), resampler.getOutputWidth(), resampler.getOutputHeight());
							if(resampler.resample(newPic, picture) < 0) {
								throw new Exception("映像リサンプル失敗");
							}
						}
						IConverter converter = ConverterFactory.createConverter("XUGGLER-BGR-24", newPic);
//						component.setImage(converter.toImage(newPic));
						videoDataQueue.add(new VideoData(converter.toImage(newPic), tag.getTimestamp()));
//						picture = null;
					}
//					Thread.sleep(10);
				}
				updatePicture();
			}
		}
		catch (Exception e) {
			logger.error("映像デコード処理で失敗しました。", e);
			e.printStackTrace();
		}
		logger.info("停止処理を実行します。");
		if(videoDecoder != null) {
			logger.info("videoDecoder止める");
			videoDecoder.close();
			videoDecoder = null;
		}
		// xuggleのオブジェクトはreleaseしておいた方がよさそうですね。メモリーの参照カウントが解放されないのかも・・・
		// TODO むしろ勝手に解放しちゃだめ、xuggleが内部的にreleaseするっぽいです。
		// 解放するとfinalyzerでエラーでる。
//		if(packet != null) {
//			packet.release();
//		}
//		if(picture != null) {
//			picture.release();
//		}
		videoDataQueue.clear();
	}
	/**
	 * 停止処理
	 */
	public void close() {
		dataQueue.clear();
		workingFlg = false;
		if(waitingFlg) {
			worker.interrupt();
		}
	}
	/**
	 * 映像を更新する。
	 */
	private void updatePicture() {
		// 最終音声の位置から、timestampが移動していなかった場合音声データが途絶えたと考えていいと思う。
		long timestamp = audioDecoder.getTimestamp();
		VideoData videoData = null;
		while(timestamp != -1 && videoDataQueue.size() != 0) {
			VideoData vData = videoDataQueue.getFirst();
			// 現在のtimestampとvDataのずれをまず知る必要がある。
			if(timestamp != -1 && vData.getTimestamp() > timestamp) {
				if(dataQueue.size() == 0) {
					if(videoData != null) {
//						logger.info("target Timestamp:" + timestamp);
//						logger.info(" avideo timestamp:" + videoData.getTimestamp());
						component.setImage(videoData.getImage());
						videoData = null;
					}
					// データqueueが存在していない場合は、次のtimestampまで待ってデータを吐いてもいいと思う。
					try {
//						logger.info("try sleep");
						long sleepLength = vData.getTimestamp() - audioDecoder.getTimestamp();
//						logger.info("sleepLength:" + sleepLength);
						Thread.sleep(sleepLength);
						if(vData.getTimestamp() < audioDecoder.getTimestamp()) {
							videoData = videoDataQueue.removeFirst();
							timestamp = audioDecoder.getTimestamp();
						}
					}
					catch (Exception e) {
					}
				}
				break;
			}
			videoData = videoDataQueue.removeFirst();
		}
		if(videoData != null) {
//			logger.info(" bvideo timestamp:" + videoData.getTimestamp());
			component.setImage(videoData.getImage());
		}
		else {
//			logger.info(" none");
		}
	}
	public void onShutdown() {
		close();
	}
}
