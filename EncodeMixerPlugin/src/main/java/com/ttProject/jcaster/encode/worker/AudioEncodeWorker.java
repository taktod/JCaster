package com.ttProject.jcaster.encode.worker;

import java.util.ArrayList;
import java.util.List;

import com.ttProject.media.flv.Tag;
import com.ttProject.xuggle.flv.FlvDepacketizer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IStreamCoder.Direction;

/**
 * エンコードを実施して、flvTagを作り上げる
 * @author taktod
 */
public class AudioEncodeWorker {
	private int channels = 2;
	private int sampleRate = 44100;
	private int bitRate = 96000;
	private IStreamCoder encoder;
	private FlvDepacketizer depacketizer = new FlvDepacketizer();
	/**
	 * コンストラクタ
	 */
	public AudioEncodeWorker() throws Exception {
		encoder = IStreamCoder.make(Direction.ENCODING, ICodec.ID.CODEC_ID_MP3);
		encoder.setSampleRate(sampleRate);
		encoder.setChannels(channels);
		encoder.setBitRate(bitRate);
		if(encoder.open(null, null) < 0) {
			throw new Exception("変換コーダーが開けませんでした。");
		}
	}
	public List<Tag> makeTags(IAudioSamples samples) throws Exception {
		List<Tag> tagList = new ArrayList<Tag>();
		IPacket packet = IPacket.make();
		int samplesConsumed = 0;
		while(samplesConsumed < samples.getNumSamples()) {
			int retval = encoder.encodeAudio(packet, samples, samplesConsumed);
			if(retval < 0) {
				throw new Exception("変換失敗");
			}
			samplesConsumed += retval;
			if(packet.isComplete()) {
				for(Tag tag : depacketizer.getTag(encoder, packet)) {
					tagList.add(tag);
				}
			}
		}
		return tagList;
	}
	public void onShutdown() {
		if(encoder != null) {
			encoder.close();
			encoder = null;
		}
	}
}
