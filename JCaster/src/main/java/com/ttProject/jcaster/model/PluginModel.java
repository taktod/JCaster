package com.ttProject.jcaster.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.plugin.IPlugin;

/**
 * 利用プラグインをコントロールするモデル
 * @author taktod
 */
public class PluginModel {
	private static final Logger logger = Logger.getLogger(PluginModel.class);
	private final String fileSeparator = System.getProperty("file.separator");
	private static List<IPlugin> plugins = new ArrayList<IPlugin>();
	/**
	 * 利用可能なクラスについて調査しておく。
	 */
	public void checkPlugins() {
		String[] pathes = System.getProperty("java.class.path").split(";");
		String splitter = ("/".equals(fileSeparator) ? fileSeparator : fileSeparator + fileSeparator);
		if(pathes.length == 1) {
			pathes = System.getProperty("java.class.path").split(":");
		}
		for(String path : pathes) {
			if(path.endsWith(".jar")) {
				// とりあえず-versionの部分は撤去することにしておく。
				File file = new File(path);
				try {
					Class<?> pluginClass = getJarPluginClass(file, file.getName().split("-")[0]);
					if(pluginClass != null && IPlugin.class.isAssignableFrom(pluginClass)) {
						logger.info("登録plugin:" + pluginClass.toString());
						plugins.add((IPlugin)pluginClass.newInstance());
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			else if(path.endsWith(fileSeparator + "target" + fileSeparator + "classes")) {
				File file = new File(path.split(splitter + "target" + splitter + "classes")[0]);
				try {
					Class<?> pluginClass = getEclipsePluginClass(new File(path), file.getName(), null);
					if(pluginClass != null && IPlugin.class.isAssignableFrom(pluginClass)) {
						logger.info("登録plugin:" + pluginClass.toString());
						plugins.add((IPlugin)pluginClass.newInstance());
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		Collections.sort(plugins, new StringComparator());
		Collections.sort(plugins, new OrderComparator());
	}
	/**
	 * みつけたプラグインについて調べておく。
	 * @return
	 */
	public List<IPlugin> getPlugins() {
		return plugins;
	}
	/**
	 * jarファイルから読み込むべきプラグインパスをみつける。
	 * @return
	 */
	private Class<?> getJarPluginClass(File targetJarFile, String targetName) throws Exception {
		JarFile jarFile = new JarFile(targetJarFile);
		try {
			for(Enumeration<JarEntry> e = jarFile.entries();e.hasMoreElements();) {
				JarEntry entry = e.nextElement();
				if(entry.getName().endsWith(".class")) {
					// クラスデータ
					File f = new File(entry.getName());
					if(targetName.equals(f.getName().split(".class")[0])) {
						// みつけたデータ
						return Class.forName(entry.getName().replaceAll("/", ".").split(".class")[0]);
					}
				}
			}
			return null;
		}
		finally{
			jarFile.close();
		}
	}
	/**
	 * eclipse用のパスから、読み込むべきプラグインパスをみつける。
	 * @param targetDirectory
	 * @param targetName
	 * @param pathName
	 * @return
	 * @throws Exception
	 */
	private Class<?> getEclipsePluginClass(File targetDirectory, String targetName, String pathName) throws Exception {
		if(!targetDirectory.isDirectory()) {
			throw new Exception("directoryではないデータでした。");
		}
		else {
			for(File f : targetDirectory.listFiles()) {
				if(f.isDirectory()) {
					// ここreturnじゃだめ
					Class<?> result = getEclipsePluginClass(f, targetName, pathName != null ? pathName + "." + f.getName() : f.getName());
					if(result != null) {
						return result;
					}
				}
				else if(f.getName().endsWith(".class")) {
					String name = f.getName().split(".class")[0];
					if(targetName.equals(name)) {
						String classPath = (pathName != null ? pathName + "." : "") + f.getName().split(".class")[0];
						return Class.forName(classPath);
					}
				}
			}
		}
		return null;
	}
	private class StringComparator implements Comparator<IPlugin> {
		@Override
		public int compare(IPlugin o1, IPlugin o2) {
			return o1.toString().compareTo(o2.toString());
		}
	}
	private class OrderComparator implements Comparator<IPlugin> {
		@Override
		public int compare(IPlugin o1, IPlugin o2) {
			return o1.getOrder() > o2.getOrder() ? -1 : (o1.getOrder() == o2.getOrder() ? 0 : 1);
		}
	}
}
