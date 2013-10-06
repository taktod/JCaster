package com.ttProject.jcaster.test;

import org.junit.Test;

public class pathTestOnWindows {
	private final String fileSeparator = System.getProperty("file.separator");
	@Test
	public void test() {
		String test = "a\\b";
		System.out.println(test.split(fileSeparator+fileSeparator)[0]);
	}
}
