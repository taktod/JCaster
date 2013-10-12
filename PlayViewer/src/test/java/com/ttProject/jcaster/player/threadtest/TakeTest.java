package com.ttProject.jcaster.player.threadtest;

import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * 
 * @author taktod
 *
 */
public class TakeTest {
	private Logger logger = Logger.getLogger(TakeTest.class);
//	@Test
	public void test() throws Exception {
		logger.info("test start");
		final LinkedBlockingQueue<String> data = new LinkedBlockingQueue<String>();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger.info(data.take());
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});
		t.start();
		logger.info("try clear");
		data.clear();
		t.interrupt();
		t.join();
		logger.info("test end");
	}
}
