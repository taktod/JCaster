package com.ttProject.jcaster.flvreader;

import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.FlvManager;
import com.ttProject.media.flv.ITagAnalyzer;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.nio.channels.IReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * flvのタグを探す解析動作
 * 特定の値までは、tagの内部データのanalyzeを実行しません。
 * @author taktod
 */
public class FlvTagAnalyzer implements ITagAnalyzer {
	private final FlvManager manager = new FlvManager();
	private final int position;
	public FlvTagAnalyzer(int position) {
		this.position = position;
	}
	@Override
	public Tag analyze(IReadChannel ch) throws Exception {
		Tag tag = null;
		do {
			if(tag != null) {
				ch.position(tag.getPosition() + tag.getInitSize());
			}
			tag = manager.getUnit(ch);
			if(tag == null) {
				return null;
			}
		} while((tag instanceof VideoTag || tag instanceof AudioTag) && tag.getSize() <= 15); // メディアデータなのに、内容がない場合は合っても仕方ないので捨てます。
		if(tag.getTimestamp() >= position) {
			// 時間が満了しているデータならそのまま読み込む
			tag.analyze(ch, false);
		}
		else {
			// 時間が満了していないデータなら、msh以外はスルーする。
			if(tag instanceof VideoTag) {
				// 映像タグの場合
				VideoTag vTag = (VideoTag) tag;
				ch.position(vTag.getPosition() + 11); // これで移動は多分おきない
				vTag.analyzeTagByte(BufferUtil.safeRead(ch, 1).get());
				if(vTag.getCodec() == CodecType.AVC) {
					// h264の場合はmshであるかの判定が必要
					if(BufferUtil.safeRead(ch, 1).get() == 0) {
						// mshである。
						// よって読み込む必要あり。
						vTag.setMSHFlg(true);
						int size = vTag.getSize() - 17;
						vTag.setRawData(BufferUtil.safeRead(ch, size));
						// tailの確認はしない。
					}
				}
			}
			else if(tag instanceof AudioTag) {
				// 音声タグの場合
				AudioTag aTag = (AudioTag) tag;
				ch.position(aTag.getPosition() + 11);
				aTag.analyzeTagByte(BufferUtil.safeRead(ch, 1).get());
				if(aTag.getCodec() == CodecType.AAC) {
					// aacの場合はmshであるかの判定が必要
					if(BufferUtil.safeRead(ch, 1).get() == 0) {
						// mshである。よって読み込む必要あり。
						aTag.setMSHFlg(true);
						int size = aTag.getSize() - 17;
						aTag.setData(ch, size);
						// tailの確認はやっぱなし。
					}
				}
			}
		}
		ch.position(tag.getPosition() + tag.getInitSize());
		return tag;
	}
}
