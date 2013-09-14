package com.ttProject.jcaster.player.decode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.swing.VideoComponent;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.raw.VideoData;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * flvのデータをデコードして、再生する部分
 * @author taktod
 * TODO ループスレッド処理ってまとめることができそうな気がする
 */
public class FlvDecoder implements Runnable {
	/** ロガー */
	private final Logger logger = Logger.getLogger(FlvDecoder.class);
	/** ボリューム 0 - 100 */
	private int volume = 100;
	/** データ表示コンポーネント */
	private final VideoComponent component;
	/** 音声出力コンポーネント */
	private SourceDataLine audioLine = null;
	/** 音声出力フォーマット */
	private AudioFormat audioFormat = null;
	/** 処理スレッド(処理に時間がかかるので、別スレッドで実行) */
	private final Thread decodeWorker;
	/** 送信データを一時的にためるqueue */
	private final LinkedBlockingQueue<Tag> dataQueue;
//	private final LinkedBlockingDeque<Tag> dataQueue;
	/** threadの動作フラグ */
	private boolean workingFlg = true;
	/** パケット復元用オブジェクト */
	private final FlvPacketizer audioPacketizer;
	private final FlvPacketizer videoPacketizer;
	private long pictureFirstTimestamp = -1;
	private AudioTag lastAudioTag = null;
	private VideoTag lastVideoTag = null;
	private IStreamCoder audioDecoder = null;
	private IStreamCoder videoDecoder = null;
	private long nextAcceptVideoTimestamp = -1;
	// もっとくのは映像のみでOK
//	private final List<VideoData> videoDataList = new ArrayList<VideoData>();
	private final LinkedList<VideoData> videoDataQueue = new LinkedList<VideoData>();
	/**
	 * コンストラクタ
	 */
	public FlvDecoder() {
		// 映像出力先の設定
		component =new VideoComponent();
		// audio出力の設定
		dataQueue = new LinkedBlockingQueue<Tag>();
//		dataQueue = new LinkedBlockingDeque<Tag>();
		audioPacketizer = new FlvPacketizer();
		videoPacketizer = new FlvPacketizer();
		decodeWorker = new Thread(this);
		decodeWorker.setDaemon(true);
		decodeWorker.setName("PlayViewerWorker");
		decodeWorker.setPriority(1);
		decodeWorker.start();
	}
	/**
	 * videoコンポーネント
	 * @return
	 */
	public VideoComponent getComponent() {
		return component;
	}
	/**
	 * 処理すべきtagを追加する。
	 * @param tag
	 */
	public void addTag(Tag tag) {
		dataQueue.add(tag);
	}
	/**
	 * 処理動作
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				List<Tag> tagList = new ArrayList<Tag>();
				tagList.add(dataQueue.take());
				while(true) {
					Tag tag = dataQueue.poll();
					if(tag == null) {
						break;
					}
					tagList.add(tag);
				}
				for(Tag tag : tagList) {
					if(tag instanceof AudioTag) {
						processAudio((AudioTag)tag);
					}
				}
				for(Tag tag : tagList) {
					if(tag instanceof VideoTag) {
						processVideo((VideoTag)tag);
					}
				}
/*				if(tag != null) {
					if(tag instanceof VideoTag) {
						processVideo((VideoTag)tag);
					}
					else if(tag instanceof AudioTag) {
						processAudio((AudioTag)tag);
					}
				}*/
				updatePicture();
			}
		}
		catch (Exception e) {
			logger.error("処理に失敗しました。", e);
		}
	}
	/**
	 * 映像を更新する。
	 * 音声はaudioLineが自動的に処理するので、そちらにまかせる。
	 */
	private void updatePicture() {
		if(audioLine == null || audioFormat == null) {
			return;
		}
		long timestamp = (long)(audioLine.getLongFramePosition() / audioFormat.getSampleRate() * 1000);
		VideoData videoData = null;
		while(videoDataQueue.size() != 0) {
			VideoData vData = videoDataQueue.getFirst();
			if(vData.getTimestamp() > timestamp) {
				break;
			}
			videoData = videoDataQueue.removeFirst();
		}
		if(videoData != null) {
			component.setImage(videoData.getImage());
		}
	}
	/**
	 * 音声データの処理
	 * @throws Exception
	 */
	private void processAudio(AudioTag tag) throws Exception {
		IPacket packet = audioPacketizer.getPacket(tag);
		if(packet == null) {
			return;
		}
		if(lastAudioTag == null || 
				lastAudioTag.getCodec() != tag.getCodec() || 
				lastAudioTag.getChannels() != tag.getChannels() ||
				lastAudioTag.getSampleRate() != tag.getSampleRate()) {
			audioDecoder = audioPacketizer.createAudioDecoder();
			if(audioLine != null) {
				audioLine.close();
				audioLine = null;
			}
			// デコーダーが更新されているのでaudioLineも更新する。
			audioFormat = new AudioFormat(audioDecoder.getSampleRate(),
					(int)IAudioSamples.findSampleBitDepth(audioDecoder.getSampleFormat()),
					audioDecoder.getChannels(), true, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
			audioLine = (SourceDataLine)AudioSystem.getLine(info);
			audioLine.open(audioFormat);
			audioLine.start();
		}
		lastAudioTag = tag;
		int offset = 0;
		IAudioSamples samples = IAudioSamples.make(1024, audioDecoder.getChannels());
		while(offset < packet.getSize()) {
			int bytesDecoded = audioDecoder.decodeAudio(samples, packet, offset);
			if(bytesDecoded < 0) {
				throw new Exception("デコード中にエラーが発生");
			}
			offset += bytesDecoded;
			if(samples.isComplete()) {
				byte[] rawBytes = samples.getData().getByteArray(0, samples.getSize());
				if(audioLine != null) {
					audioLine.write(rawBytes, 0, samples.getSize());
				}
			}
		}
	}
	/**
	 * 映像データの処理
	 * @throws Exception
	 */
	private void processVideo(VideoTag tag) throws Exception {
		IPacket packet = videoPacketizer.getPacket(tag);
		if(packet == null) {
			return;
		}
		if(lastVideoTag == null || lastVideoTag.getCodec() != tag.getCodec()) {
			// コーデックが違う場合は取り直す必要あり
			videoDecoder = videoPacketizer.createVideoDecoder();
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
				// 変換はリアルタイムに実行可能っぽい。
				// よってあまりにも密度が濃い場合はフレームをスキップするようにしてやればとりあえずなんとかできそう。
				// 30fpsくらいは表示してやりたいところだが・・・どうするかな・・・
				if(tag.getTimestamp() < nextAcceptVideoTimestamp) {
					// 前のvideoデータの処理に時間がかかりすぎている場合は次のデータをスキップする。
					System.out.println("時間かかりすぎを検知したので、スキップします。");
					System.out.println(tag);
					return;
				}
				// 最終のデータ変換に時間がかかった場合、次のデータ変換はパスするようにすればいい感じか？
				long startTime = System.currentTimeMillis();
				IVideoPicture newPic = picture;
				if(picture.getPixelType() != IPixelFormat.Type.BGR24) {
					IVideoResampler resampler = IVideoResampler.make(videoDecoder.getWidth(), videoDecoder.getHeight(), IPixelFormat.Type.BGR24, picture.getWidth(), picture.getHeight(), picture.getPixelType());
					newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), resampler.getOutputWidth(), resampler.getOutputHeight());
					if(resampler.resample(newPic, picture) < 0) {
						throw new Exception("映像リサンプル失敗");
					}
				}
//				if(pictureFirstTimestamp == -1) {
//					pictureFirstTimestamp = picture.getTimeStamp();
//				}
				IConverter converter = ConverterFactory.createConverter("XUGGLER-BGR-24", newPic);
//				videoDataList.add(new VideoData(converter.toImage(newPic), (long)(newPic.getTimeStamp() * newPic.getTimeBase().getDouble() * 1000)));
//				videoDataQueue.add(new VideoData(converter.toImage(newPic), (long)((picture.getTimeStamp() - pictureFirstTimestamp) / 10000)));
				videoDataQueue.add(new VideoData(converter.toImage(newPic), tag.getTimestamp()));
				nextAcceptVideoTimestamp = System.currentTimeMillis() - startTime + tag.getTimestamp() + 5;
				if(nextAcceptVideoTimestamp < 100 + tag.getTimestamp()) {
					nextAcceptVideoTimestamp = 100 + tag.getTimestamp();
				}
			}
		}
	}
}
