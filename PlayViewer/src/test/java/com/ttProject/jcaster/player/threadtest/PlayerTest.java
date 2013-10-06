package com.ttProject.jcaster.player.threadtest;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.junit.Test;

import com.ttProject.jcaster.player.decode.FlvDecoder;
import com.ttProject.jcaster.player.swing.TestFrame;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.ITagAnalyzer;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.TagAnalyzer;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;

/**
 * 実際に動画の再生を実施する動作テスト
 * @author taktod
 */
public class PlayerTest {
	private boolean workingFlg = true;
	/**
	 * テスト
	 */
	@Test
	public void test() throws Exception {
		workingFlg = true;
		TestFrame frame = new TestFrame();
		frame.setPreferredSize(new Dimension(640, 480));
		frame.setSize(new Dimension(640, 480));
		frame.setVisible(true);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// ここに閉じるときのflagを折る動作を追加
				workingFlg = false;
			};
		});
		// flvデータを読み込んで動作させる。
		/*
		 * 基本的にFlvDecoderにデータを流す
		 * そこから、AudioDecoderとVideoDecoderにデータが流れる。
		 * Audioの再生を基本としつつ音声のタイミングに合わせて映像を動作させるという動作でいけばよさそう。
		 * 映像のない再生は同じやり方でOK
		 * 音声のない再生は別のやり方を考えないとだめ。
		 * それぞれのthreadがそれぞれの動作をするようにする。
		 * 
		 * とりあえずいきなり本家でつくるとややこしいので、それぞれのクラスをつくってやっていこうと思う
		 */
		ITagAnalyzer analyzer = new TagAnalyzer();
		IFileReadChannel ch = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.flv");
		FlvHeader header = new FlvHeader();
		header.analyze(ch);
		FlvDecoder flvDecoder = new FlvDecoder();
		frame.add(flvDecoder.getComponent());
		Tag tag = null;
		long startTime = -1;
		// tagのデータを現在時刻から、1秒先のものに限定しておく。
		while(workingFlg && (tag = analyzer.analyze(ch)) != null) {
			if(startTime == -1) {
				// 現在時刻保持
				startTime = System.currentTimeMillis();
			}
			// 転送していいデータの範囲を計算しておく。
			long passedTime = System.currentTimeMillis() - startTime + 1000;
			if(tag.getTimestamp() > passedTime) {
				// 表示していい時間ではなければsleepでちょっと待たせる。
				Thread.sleep(tag.getTimestamp() - passedTime);
			}
			flvDecoder.addTag(tag);
		}
		Thread.sleep(2000);
		flvDecoder.onShutdown();
	}
}
