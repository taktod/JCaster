package com.ttProject.jcaster.mp4plugin.test;

import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.junit.Test;

/**
 * リストの動作テストを実行
 * @author taktod
 */
public class ListTest {
	private Logger logger = Logger.getLogger(ListTest.class);
	@Test
	public void test() {
		LinkedList<String> list = new LinkedList<String>();
		logger.info(list);
		list.addLast("a");
		logger.info(list);
		list.addLast("b");
		logger.info(list);
		list.addLast("c");
		logger.info(list);
		logger.info(list.pop());
		logger.info(list.pop());
		logger.info(list);
	}
}
