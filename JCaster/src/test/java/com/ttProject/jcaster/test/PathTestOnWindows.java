package com.ttProject.jcaster.test;

import org.apache.log4j.Logger;
import org.junit.Test;

public class PathTestOnWindows {
	private Logger logger = Logger.getLogger(PathTestOnWindows.class);
	private final String fileSeparator = System.getProperty("file.separator");
	@Test
	public void test() {
		String test = "a\\b";
		logger.info(test.split(fileSeparator+fileSeparator)[0]);
	}
}
