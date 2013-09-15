package com.ttProject.jcaster.player.decode;

import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.apache.log4j.Logger;

import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;

/**
 * 音声をデコードして表示させる処理
 * @author taktod
 *
 */
public class FlvAudioDecoder implements Runnable {
	/** ロガー */
	private final Logger logger = Logger.getLogger(FlvAudioDecoder.class);
	/** ボリューム */
	private int volume = 100;
	/** 音声出力 */
	private SourceDataLine audioLine = null;
	/** 出力オーディオフォーマット */
	private AudioFormat audioFormat = null;
	/** 処理スレッド */
	private final Thread worker;
	/** 送信データqueue保持 */
	private final LinkedBlockingQueue<AudioTag> dataQueue;
	/** threadの動作フラグ */
	private boolean workingFlg = true;
	/** パケット復元用オブジェクト */
	private final FlvPacketizer packetizer;
	/** 最終アクセス音声タグ保持 */
	private AudioTag lastAudioTag = null;
	/** 音声デコーダー */
	private IStreamCoder audioDecoder = null;
	/**
	 * コンストラクタ
	 */
	public FlvAudioDecoder() {
		dataQueue = new LinkedBlockingQueue<AudioTag>();
		packetizer = new FlvPacketizer();
		worker = new Thread(this);
		worker.setDaemon(true);
		worker.setName("audioPlayViewerWorker");
		worker.start();
	}
	/**
	 * 1/1000の状態で再生がどの位置にいるか応答します。
	 * @return
	 */
	public long getTimestamp() {
		if(audioLine == null) {
			return -1;
		}
		return audioLine.getMicrosecondPosition() / 1000;
	}
	/**
	 * 処理すべきtagを追加する
	 * @param tag
	 */
	public void addTag(Tag tag) {
		if(tag instanceof AudioTag) {
			dataQueue.add((AudioTag)tag);
		}
	}
	/**
	 * 停止処理
	 */
	public void close() {
		// queueの中身をクリアします。
		dataQueue.clear();
		// threadの停止を促します。
		workingFlg = false;
		// ロックされている部分は解放しておく。
		worker.interrupt();
	}
	/**
	 * 処理
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				AudioTag tag = dataQueue.take();
				IPacket packet = packetizer.getPacket(tag);
				if(packet == null) {
					continue;
				}
				if(lastAudioTag == null || 
						lastAudioTag.getCodec() != tag.getCodec() || 
						lastAudioTag.getChannels() != tag.getChannels() ||
						lastAudioTag.getSampleRate() != tag.getSampleRate()) {
					audioDecoder = packetizer.createAudioDecoder();
					if(audioLine != null) {
						audioLine.close();
						audioLine = null;
					}
					// デコーダーが更新されているのでaudioLineも更新する。
					audioFormat = new AudioFormat(audioDecoder.getSampleRate(),
							(int)IAudioSamples.findSampleBitDepth(audioDecoder.getSampleFormat()),
							audioDecoder.getChannels(), true, false);
					System.out.println(audioFormat);
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
						// TODO サンプリングデータ量と無音空間を計算して、無音部がある場合は挿入する必要あり。
						/*
						 * すでに完了した経過時間を計算しておいて、経過時間とtagのtimestampが一致しない場合は無音用のデータを挿入する必要あり。
						 * 下記の計算では、毎回0.032ほどずれたままになっていた。よって一致しない。
						 */
//						System.out.println(passedSamples / 44100.0 * 1000);
//						System.out.println(tag.getTimestamp());
//						System.out.println(tag.getTimestamp() - (passedSamples / 44.1f));
						// まずはサンプリング数を計算しておくことにする。
						// TODO byteBufferでつくってvolumeを調整する必要あり。
						byte[] rawBytes = samples.getData().getByteArray(0, samples.getSize());
						if(audioLine != null) {
							audioLine.write(rawBytes, 0, samples.getSize());
						}
//						passedSamples += rawBytes.length / 4;
					}
				}
			}
		}
		catch (Exception e) {
			logger.error("処理に失敗しました。", e);
		}
		if(audioLine != null) {
			audioLine.close();
			audioLine = null;
		}
		if(audioDecoder != null) {
			audioDecoder.close();
			audioDecoder = null;
		}
	}
}
