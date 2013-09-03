package com.ttProject.jcaster.model;

import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
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
	private static Set<IPlugin> plugins = new HashSet<IPlugin>();
	/**
	 * 利用可能なクラスについて調査しておく。
	 */
	public void checkPlugins() {
		String[] pathes = System.getProperty("java.class.path").split(":");
		if(pathes.length == 1) {
			pathes = System.getProperty("java.class.path").split(";");
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
			else if(path.endsWith(fileSeparator + "target" + fileSeparator + "classes")){
				File file = new File(path.split(fileSeparator + "target" + fileSeparator + "classes")[0]);
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
	}
	/**
	 * みつけたプラグインについて調べておく。
	 * @return
	 */
	public Set<IPlugin> getPlugins() {
		return plugins;
	}
	/**
	 * jarファイルから読み込むべきプラグインパスをみつける。
	 * @return
	 */
	private Class<?> getJarPluginClass(File targetJarFile, String targetName) throws Exception {
		JarFile jarFile = new JarFile(targetJarFile);
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
}
