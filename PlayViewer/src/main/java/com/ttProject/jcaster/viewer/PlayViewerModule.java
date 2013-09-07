package com.ttProject.jcaster.viewer;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.decode.FlvDecoder;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.jcaster.plugin.module.IViewerModule;
import com.ttProject.media.flv.Tag;

/**
 * 表示用のモジュール
 * @author taktod
 */
public class PlayViewerModule implements IViewerModule, IOutputModule {
	/** ロガー */
	private Logger logger = Logger.getLogger(PlayViewerModule.class);
	private FlvDecoder decoder = new FlvDecoder();
	
	public void setup(JPanel panel) {
		logger.info("転送中のデータを表示するviewをセットアップします。");
		ISwingMainBase mainbase = BaseHandler.getISwingMainBase();
		if(mainbase == null) {
			// CUI動作はありえない。
			throw new RuntimeException("swingでのみ動作する部分です。");
		}
		else {
			setupSwingComponent(panel);
			mainbase.registerModule(this);
		}
	}
	private void setupSwingComponent(JPanel panel) {
		// とりあえずパネル上の配置をつくっておく。
		panel.setBackground(Color.PINK);
		JComponent component = decoder.getComponent();
		component.setSize(100, 100);
		panel.setLayout(new BorderLayout());
		panel.add(component, BorderLayout.CENTER);
	}
	@Override
	public void onTimerEvent() {
		// タイマーによる動作
//		logger.info("timeきたよ");
	}

	@Override
	public void setMixedData(Tag tag) {
		// データをうけとったら、xuggleをつかって、デコードを実行して表示するようにしておく。
		decoder.addTag(tag);
	}
}
