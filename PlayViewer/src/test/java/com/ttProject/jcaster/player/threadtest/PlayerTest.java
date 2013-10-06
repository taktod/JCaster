package com.ttProject.jcaster.player.threadtest;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

import com.ttProject.jcaster.player.swing.TestFrame;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.ITagAnalyzer;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.TagAnalyzer;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.xuggle.flv.FlvPacketizer;
import com.xuggle.xuggler.IPacket;

/**
 * 実際に動画の再生を実施する動作テスト
 * @author taktod
 */
public class PlayerTest {
	/**
	 * テスト
	 */
	@Test
	public void test() throws Exception {
		TestFrame frame = new TestFrame();
		frame.setPreferredSize(new Dimension(640, 480));
		frame.setSize(new Dimension(640, 480));
		frame.setVisible(true);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// ここに閉じるときのflagを折る動作を追加
			};
		});
		File fileTarget = new File("C:\\Users\\taktod\\Downloads\\mario.flv");
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
		IFileReadChannel ch = FileReadChannel.openFileReadChannel(fileTarget.getAbsolutePath());
		FlvHeader header = new FlvHeader();
		header.analyze(ch);

		// データの受け渡し用
		final LinkedBlockingQueue<Tag> videoQueue = new LinkedBlockingQueue<Tag>();
		final LinkedBlockingQueue<Tag> audioQueue = new LinkedBlockingQueue<Tag>();
		// ここで一度packetをつくっておかないと各threadで詰まることがあるっぽい。
//		IPacket packet = IPacket.make();
		// Threadの準備
		Thread videoThread = new Thread(new Runnable() {
			IPacket packet = IPacket.make();
			FlvPacketizer packetizer = new FlvPacketizer();
			@Override
			public void run() {
				try {
					while(true) {
						Tag tag = videoQueue.take();
						System.out.println(tag);
						IPacket packet = packetizer.getPacket(tag, this.packet);
						System.out.println("packet化おわり");
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});
		videoThread.start();
		Thread audioThread = new Thread(new Runnable() {
			IPacket packet = IPacket.make();
			FlvPacketizer packetizer = new FlvPacketizer();
			@Override
			public void run() {
				try {
					while(true) {
						Tag tag = audioQueue.take();
						System.out.println(tag);
						IPacket packet = packetizer.getPacket(tag, this.packet);
						System.out.println("packet化おわり");
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});
		audioThread.start();
		Tag tag = null;
		while((tag = analyzer.analyze(ch)) != null) {
			if(tag instanceof AudioTag) {
				audioQueue.add(tag);
			}
			else if(tag instanceof VideoTag) {
				videoQueue.add(tag);
			}
//			System.out.println(tag);
		}
		videoThread.interrupt();
		audioThread.interrupt();
	}
}
