package com.ttProject.jcaster.player.decode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.apache.log4j.Logger;

import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.util.BufferUtil;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;

/**
 * 音声をデコードして表示させる処理
 * @author taktod
 * TODO coderの停止動作がどうやらおかしいみたいです。
 * releaseすべきなのだろうか？
 * 一度こわれるとwindows8の場合再起動しないと復帰できなくなるので致命的
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
	/** はじめのaudioTagの位置設定 */
	private long startTimestamp = -1; // はじめのaudioTagの位置を設定
	private int vuLevel = 0;
	private IAudioSamples samples = null;
	private boolean waitingFlg = false;
	private final IPacket packet = IPacket.make();
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
		return audioLine.getMicrosecondPosition() / 1000 + startTimestamp;
	}
	/**
	 * 音声レベルを応答する
	 * @return
	 */
	public int getVolumeLevel() {
		if(audioLine == null) {
			return 0;
		}
		return vuLevel; // コレじゃない感がすごいw
	}
	/**
	 * 音量を設定する0 〜 100
	 * @param volume
	 */
	public void setVolume(int volume) {
		this.volume = volume;
	}
	/**
	 * 処理すべきtagを追加する
	 * @param tag
	 */
	public void addTag(Tag tag) {
		if(tag instanceof AudioTag) {
			if(startTimestamp == -1) {
				startTimestamp = tag.getTimestamp();
			}
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
		if(waitingFlg) {
			worker.interrupt();
		}
	}
	/**
	 * 処理
	 */
	@Override
	public void run() {
		try {
			while(workingFlg) {
				waitingFlg = true;
				AudioTag tag = dataQueue.take();
				waitingFlg = false;
				IPacket packet = packetizer.getPacket(tag, this.packet);
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
					DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
					audioLine = (SourceDataLine)AudioSystem.getLine(info);
					audioLine.open(audioFormat);
					audioLine.start();
				}
				lastAudioTag = tag;
				int offset = 0;
//				if(samples == null) {
					samples = IAudioSamples.make(1024, audioDecoder.getChannels());
//				}
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
						// まずはサンプリング数を計算しておくことにする。
						ByteBuffer buffer = samples.getByteBuffer();
						buffer.order(ByteOrder.LITTLE_ENDIAN);
						ByteBuffer volumedBuffer = ByteBuffer.allocate(samples.getSize());
						volumedBuffer.order(ByteOrder.LITTLE_ENDIAN);
						while(buffer.remaining() > 1) {
							volumedBuffer.putShort((short)(buffer.getShort() * volume / 100));
						}
						volumedBuffer.flip();
						if(audioLine != null) {
							audioLine.write(BufferUtil.toByteArray(volumedBuffer), 0, samples.getSize());
//							audioLine.write(volumedBuffer.array(), 0, samples.getSize());
						}
//						samples = null;
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
		samples = null;
		vuLevel = 0;
	}
	public void onShutdown() {
//		if(audioDecoder != null) {
//			audioDecoder.close();
//			audioDecoder = null;
//		}
		close();
	}
}
