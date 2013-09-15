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
	
	private long nextAcceptVideoTimestamp = -1;
	/**
	 * コンストラクタ
	 * @param audioDecoder
	 */
	public FlvVideoDecoder(FlvAudioDecoder audioDecoder) {
		this.audioDecoder = audioDecoder;
		component = new VideoComponent();
		dataQueue = new LinkedBlockingQueue<VideoTag>();
		packetizer = new FlvPacketizer();
		videoDataQueue = new LinkedList<VideoData>();
		worker = new Thread(this);
		worker.setDaemon(true);
		worker.setName("VideoPlayViewerWorker");
		worker.setPriority(1);
		worker.start();
	}
	public VideoComponent getComponent() {
		return component;
	}
	/**
	 * 処理すべきタグ追加
	 * @param tag
	 */
	public void addTag(Tag tag) {
		if(tag instanceof VideoTag) {
			dataQueue.add((VideoTag)tag);
		}
	}
	/**
	 * 内部処理
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				VideoTag tag = dataQueue.take();
				IPacket packet = packetizer.getPacket(tag);
				if(packet == null) {
					continue;
				}
				if(lastVideoTag == null || lastVideoTag.getCodec() != tag.getCodec()) {
					// コーデックが違う場合は取り直す必要あり
					videoDecoder = packetizer.createVideoDecoder();
				}
				lastVideoTag = tag;
				int offset = 0;
				IVideoPicture picture = IVideoPicture.make(videoDecoder.getPixelType(), videoDecoder.getWidth(), videoDecoder.getHeight());
				while(offset < packet.getSize()) {
					int bytesDecoded = videoDecoder.decodeVideo(picture, packet, offset);
					if(bytesDecoded < 0) {
						throw new Exception("映像のデコード中に問題が発生しました。");
					}
					offset += bytesDecoded;
					if(picture.isComplete()) {
						IVideoPicture newPic = picture;
						if(tag.getTimestamp() < nextAcceptVideoTimestamp) {
//							System.out.println("時間がかかりすぎているので、スキップする。");
							continue;
						}
						long startTime = System.currentTimeMillis();
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
						nextAcceptVideoTimestamp = System.currentTimeMillis() - startTime + tag.getTimestamp();
						if(nextAcceptVideoTimestamp < 40 + tag.getTimestamp()) {
							nextAcceptVideoTimestamp = 40 + tag.getTimestamp();
						}
					}
				}
				updatePicture();
			}
		}
		catch (Exception e) {
			logger.error("映像デコード処理で失敗しました。", e);
		}
	}
	/**
	 * 映像を更新する。
	 */
	private void updatePicture() {
		long timestamp = audioDecoder.getTimestamp();
		if(timestamp == -1) {
			return;
		}
//		System.out.println("al:" + timestamp);
		VideoData videoData = null;
//		System.out.println("vdq:" + videoDataQueue);
		while(videoDataQueue.size() != 0) {
			VideoData vData = videoDataQueue.getFirst();
			if(vData.getTimestamp() > timestamp) {
//				System.out.println("vd:" + vData.getTimestamp());
				break;
			}
			videoData = videoDataQueue.removeFirst();
		}
		if(videoData != null) {
			component.setImage(videoData.getImage());
		}
	}
}
