package com.ttProject.jcaster.player.threadtest;

import org.apache.log4j.Logger;

import com.xuggle.ferry.JNIEnv;

/**
 * 読み込み動作確認
 * @author taktod
 * archの偽装はできるけど、違うライブラリの読み込みはできないっぽい。
 * パスがうまく取得できずに、ライブラリ読み込みエラーになりました。
 */
public class LoadTest {
	private Logger logger = Logger.getLogger(LoadTest.class);
//	@Test
	public void test() {
		System.setProperty("os.arch", "x86");
		logger.info(System.getProperty("os.arch"));
		JNIEnv env = JNIEnv.getEnv();
		logger.info(env.getCPUArch());
		logger.info(env.getOSFamily());
	}
}
