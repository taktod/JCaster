package com.ttProject.jcaster.mp4plugin.test;

import java.util.LinkedList;

import org.junit.Test;

/**
 * リストの動作テストを実行
 * @author taktod
 */
public class ListTest {
	@Test
	public void test() {
		LinkedList<String> list = new LinkedList<String>();
		System.out.println(list);
		list.addLast("a");
		System.out.println(list);
		list.addLast("b");
		System.out.println(list);
		list.addLast("c");
		System.out.println(list);
		System.out.println(list.pop());
		System.out.println(list.pop());
		System.out.println(list);
	}
}
