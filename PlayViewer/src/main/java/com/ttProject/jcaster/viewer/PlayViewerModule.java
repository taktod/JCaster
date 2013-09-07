package com.ttProject.jcaster.viewer;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.player.decode.FlvDecoder;
import com.ttProject.jcaster.plugin.base.BaseHandler;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.jcaster.plugin.module.IViewerModule;
import com.ttProject.media.flv.Tag;
import com.ttProject.swing.component.GroupLayoutEx;

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
		JComponent component = decoder.getComponent();
		JScrollPane scrollPane = new JScrollPane(component);
		panel.setLayout(new BorderLayout());
		panel.add(scrollPane, BorderLayout.CENTER);
		// 右にコントロール部をあつめておく。
		JPanel control = new JPanel();
		panel.add(control, BorderLayout.LINE_END);
		GroupLayoutEx layout = new GroupLayoutEx(control);
		control.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		JSlider slider = new JSlider(JSlider.VERTICAL);
		Object[][] components = {
				{new JButton("▶")},
				{new JCheckBox("video")},
				{new JCheckBox("sound")},
				{new JLabel("volume")},
				{slider}
		};
		layout.addComponents(components);
		control.validate();
		control.repaint();
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
