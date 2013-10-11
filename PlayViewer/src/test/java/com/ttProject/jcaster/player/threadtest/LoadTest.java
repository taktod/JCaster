package com.ttProject.jcaster.player.threadtest;

import com.xuggle.ferry.JNIEnv;

/**
 * 読み込み動作確認
 * @author taktod
 * archの偽装はできるけど、違うライブラリの読み込みはできないっぽい。
 * パスがうまく取得できずに、ライブラリ読み込みエラーになりました。
 */
public class LoadTest {
	
//	@Test
	public void test() {
		System.setProperty("os.arch", "x86");
		System.out.println(System.getProperty("os.arch"));
		JNIEnv env = JNIEnv.getEnv();
		System.out.println(env.getCPUArch());
		System.out.println(env.getOSFamily());
	}
}
