package com.ttProject.jcaster.player.threadtest;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

import org.junit.Test;

import com.ttProject.jcaster.player.decode.FlvDecoder;
import com.ttProject.jcaster.player.swing.TestFrame;
import com.ttProject.media.extra.flv.FlvOrderModel;
import com.ttProject.media.extra.mp4.IndexFileCreator;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.ITagAnalyzer;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.TagAnalyzer;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.TmpFile;

/**
 * 実際に動画の再生を実施する動作テスト
 * @author taktod
 */
public class PlayerTest {
	private boolean workingFlg = true;
	/**
	 * テスト
	 */
//	@Test
	public void flvtest() throws Exception {
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
			long passedTime = System.currentTimeMillis() - startTime + 100;
			if(tag.getTimestamp() > passedTime) {
				// 表示していい時間ではなければsleepでちょっと待たせる。
				Thread.sleep(tag.getTimestamp() - passedTime);
			}
			flvDecoder.addTag(tag);
		}
		Thread.sleep(2000);
		flvDecoder.onShutdown();
	}
	/**
	 * テスト
	 */
//	@Test
	public void mp4test() throws Exception {
//		Thread.sleep(10000);
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
		IFileReadChannel ch = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.mp4");
		File tmpFile = new TmpFile("/test.tmp");
		System.out.println(tmpFile);
		IndexFileCreator analyzer = new IndexFileCreator(tmpFile);
		Atom atom = null;
		while((atom = analyzer.analyze(ch)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		analyzer.updatePrevTag();
		analyzer.checkDataSize();
		analyzer.close();
		IFileReadChannel tmp = FileReadChannel.openFileReadChannel(tmpFile.getAbsolutePath());
		FlvOrderModel orderModel = new FlvOrderModel(tmp, true, true, 0);
		FlvDecoder flvDecoder = new FlvDecoder();
		frame.add(flvDecoder.getComponent());
		List<Tag> tagList = null;
		long startTime = -1;
		// tagのデータを現在時刻から、1秒先のものに限定しておく。
		while(workingFlg && (tagList = orderModel.nextTagList(ch)) != null) {
			for(Tag tag : tagList) {
				if(!workingFlg) {
					break;
				}
				if(startTime == -1) {
					// 現在時刻保持
					startTime = System.currentTimeMillis() - tag.getTimestamp();
				}
				// 転送していいデータの範囲を計算しておく。
				long passedTime = System.currentTimeMillis() - startTime + 10;
				if(tag.getTimestamp() > passedTime) {
					// 表示していい時間ではなければsleepでちょっと待たせる。
					Thread.sleep(tag.getTimestamp() - passedTime);
				}
				flvDecoder.addTag(tag);
//				Thread.sleep(0);
			}
		}
		tmp.close();
		ch.close();
		Thread.sleep(2000);
		flvDecoder.onShutdown();
	}
	/**
	 * テスト
	 */
	@Test
	public void flvtest2() throws Exception {
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
		IFileReadChannel ch = FileReadChannel.openFileReadChannel("http://49.212.39.17/rtypeDelta.flv");
		File tmpFile = new TmpFile("test.tmp");
		com.ttProject.media.flv.model.IndexFileCreator indexFileCreator = new com.ttProject.media.flv.model.IndexFileCreator(tmpFile, ch);
		indexFileCreator.initSetup();
		IFileReadChannel tmp = FileReadChannel.openFileReadChannel(tmpFile.getAbsolutePath());
		com.ttProject.media.flv.model.FlvOrderModel orderModel = new com.ttProject.media.flv.model.FlvOrderModel(indexFileCreator, true, true, 460000);
		orderModel.initialize(ch);
		FlvDecoder flvDecoder = new FlvDecoder();
		frame.add(flvDecoder.getComponent());
		List<Tag> tagList = null;
		long startTime = -1;
		while(workingFlg && (tagList = orderModel.nextTagList(ch)) != null) {
			for(Tag tag : tagList) {
				if(!workingFlg) {
					break;
				}
				if(startTime == -1) {
					startTime = System.currentTimeMillis() - tag.getTimestamp();
				}
				long passedTime = System.currentTimeMillis() - startTime + 10;
				if(tag.getTimestamp() > passedTime) {
					Thread.sleep(tag.getTimestamp() - passedTime);
				}
				flvDecoder.addTag(tag);
			}
		}
		System.out.println("おわり。");
		indexFileCreator.close(); // これは最後でもよい。
		tmp.close();
		ch.close();
		Thread.sleep(2000);
		flvDecoder.onShutdown();
		/*
		ITagAnalyzer analyzer = new TagAnalyzer();
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
			long passedTime = System.currentTimeMillis() - startTime + 100;
			if(tag.getTimestamp() > passedTime) {
				// 表示していい時間ではなければsleepでちょっと待たせる。
				Thread.sleep(tag.getTimestamp() - passedTime);
			}
			flvDecoder.addTag(tag);
		}
		Thread.sleep(2000);
		flvDecoder.onShutdown();*/
	}
}
