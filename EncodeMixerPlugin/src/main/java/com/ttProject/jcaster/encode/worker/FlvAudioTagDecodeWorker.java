package com.ttProject.jcaster.encode.worker;

import java.util.ArrayList;
import java.util.List;

import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;

/**
 * flvTagのaudioデータをaudioSamplesに戻す動作
 * @author taktod
 */
public class FlvAudioTagDecodeWorker {
	/** パケット化動作 */
	private FlvPacketizer packetizer = new FlvPacketizer();
	private IStreamCoder decoder = null;
	private IAudioSamples samples = null;
	private AudioTag lastAudioTag = null;
	/**
	 * audioTagからIAudioSamplesを取り出す処理
	 * @param tag
	 * @return
	 */
	public List<IAudioSamples> getSamples(AudioTag tag) throws Exception {
		List<IAudioSamples> result = new ArrayList<IAudioSamples>();
		IPacket packet = packetizer.getPacket(tag);
		if(packet == null) {
			return result;
		}
		// 中途でコーデックがかわっている場合も作り直す必要あり。
		if(decoder == null || // デコーダーが未定義
				lastAudioTag == null || // audioTagが未定義
				lastAudioTag.getCodec() != tag.getCodec() || // コーデックが一致せず
				lastAudioTag.getChannels() != tag.getChannels() || // チャンネル数が一致せず
				lastAudioTag.getSampleRate() != tag.getSampleRate()) { // サンプリングレートが一致せず
			decoder = packetizer.createAudioDecoder();
		}
		lastAudioTag = tag;
		int offset = 0;
		while(offset < packet.getSize()) {
			if(samples == null) {
				samples = IAudioSamples.make(1024, decoder.getChannels());
			}
			int bytesDecoded = decoder.decodeAudio(samples, packet, offset);
			if(bytesDecoded < 0) {
				throw new Exception("デコード中にエラーが発生しました。");
			}
			offset += bytesDecoded;
			if(samples.isComplete()) {
				result.add(samples);
				samples = null;
			}
		}
		return result;
	}
	public void onShutdown() {
		if(decoder != null) {
			decoder.close();
			decoder = null;
		}
	}
}
