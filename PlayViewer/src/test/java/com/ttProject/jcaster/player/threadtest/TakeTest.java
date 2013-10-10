package com.ttProject.jcaster.player.threadtest;

import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

/**
 * 
 * @author taktod
 *
 */
public class TakeTest {
//	@Test
	public void test() throws Exception {
		System.out.println("test start");
		final LinkedBlockingQueue<String> data = new LinkedBlockingQueue<String>();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(data.take());
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});
		t.start();
		System.out.println("try clear");
		data.clear();
		t.interrupt();
		t.join();
		System.out.println("test end");
	}
}
