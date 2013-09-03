package com.ttProject.jcaster.controller;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.SignalEvent;
import com.ttProject.jcaster.model.PluginModel;
import com.ttProject.jcaster.plugin.IPlugin;
import com.ttProject.jcaster.plugin.IViewerPlugin;
import com.ttProject.jcaster.plugin.IPlugin.Type;
import com.ttProject.jcaster.plugin.base.Base;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IInputModule;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.jcaster.swing.MainFrame;

/**
 * 中央でコントロールするコントローラー
 * @author taktod
 */
public class MainController extends Base implements ISwingMainBase {
	/** ロガー */
	private final Logger logger = Logger.getLogger(MainController.class);
	/** 中央フレーム保持 */
	private MainFrame frame;
	/** plugin管理用のモデル保持 */
	private PluginModel pluginModel;
	
	// 各モジュール保持
	private IInputModule inputModule;
	private IOutputModule outputModule;
	private IMixerModule mixerModule;
	/**
	 * コンストラクタ
	 * @param frame
	 */
	public MainController(MainFrame frame) {
		super();
		this.frame = frame;
	}
	/**
	 * 初期化処理
	 */
	@Override
	protected void initialize() {
		pluginModel = new PluginModel();
		pluginModel.checkPlugins(); // プラグインをチェックしておく。
		
		SignalEvent event = SignalEvent.getInstance();
		event.setController(this);
		// イベントを開始しておく。
		event.start();
	}
	/**
	 * viewer用のモジュールの初期化処理
	 * @param pane
	 */
	public void setupViewerModule(JTabbedPane pane) {
		logger.info("viewer用のtabPaneの構築を実施します。");
		// viewerのPluginの設定をつくっていく。
		for(IPlugin plugin : pluginModel.getPlugins()) {
			logger.info("処理plugin:" + plugin.toString());
			if(plugin.getType() == Type.Viewer || plugin instanceof IViewerPlugin) {
				IViewerPlugin vPlugin = (IViewerPlugin)plugin;
				JPanel panel = new JPanel();
				vPlugin.setViewerPanel(panel);
				pane.addTab(vPlugin.toString(), panel);
			}
		}
	}
	/**
	 * 入力モジュール用の初期化処理
	 */
	public void setupInputModule(JComboBox comboBox) {
		logger.info("inputModule用のcomboboxを準備しておきます。");
		for(IPlugin plugin : pluginModel.getPlugins()) {
			if(plugin.getType() == Type.Input) {
				comboBox.addItem(plugin);
			}
		}
	}
	/**
	 * 出力モジュール用の初期化処理
	 */
	public void setupOutputModule(JComboBox comboBox) {
		logger.info("outputModule用のcomboboxを準備しておきます。");
		for(IPlugin plugin : pluginModel.getPlugins()) {
			if(plugin.getType() == Type.Output) {
				comboBox.addItem(plugin);
			}
		}
	}
	/**
	 * 変換モジュール用の初期化処理
	 */
	public void setupMixerModule(JComboBox comboBox) {
		logger.info("mixerModule用のcomboboxを準備しておきます。");
		for(IPlugin plugin : pluginModel.getPlugins()) {
			if(plugin.getType() == Type.Mixer) {
				comboBox.addItem(plugin);
			}
		}
	}
	@Override
	public void registerModule(IModule module) {
		if(module instanceof IInputModule) {
			inputModule = (IInputModule) module;
			if(mixerModule != null) {
				inputModule.registerMixerModule(mixerModule);
			}
		}
		else if(module instanceof IOutputModule) {
			outputModule = (IOutputModule) module;
			if(mixerModule != null) {
				mixerModule.registerOutputModule(outputModule);
			}
		}
		else if(module instanceof IMixerModule) {
			mixerModule = (IMixerModule) module;
			if(inputModule != null) {
				inputModule.registerMixerModule(mixerModule);
			}
			if(outputModule != null) {
				mixerModule.registerOutputModule(outputModule);
			}
		}
	}
	@Override
	public void unregisterModule(IModule module) {
		if(inputModule == module) {
			inputModule = null;
		}
		else if(outputModule == module)
		{
			if(mixerModule != null) {
				mixerModule.registerOutputModule(null);
			}
			outputModule = null;
		}
		else if(mixerModule == module) {
			if(inputModule != null) {
				inputModule.registerMixerModule(null);
			}
			mixerModule = null;
		}
	}
	public void fireTimerEvent() {
		if(inputModule != null) {
			inputModule.onTimerEvent();
		}
		if(mixerModule != null) {
			mixerModule.onTimerEvent();
		}
		if(outputModule != null) {
			outputModule.onTimerEvent();
		}
//		logger.info("timeEvent");
	}
	@Override
	public JFrame getMainFrame() {
		return frame;
	}
	/**
	 * コンボボックスを応答する。
	 */
	@Override
	public JComboBox getComboBox(Class<?> targetClass) {
		return frame.getComboBox(targetClass);
	}
	/**
	 * パネルを応答する。
	 */
	@Override
	public JPanel getComponentPanel(Class<?> targetClass) {
		return frame.getComponentPanel(targetClass);
	}
	/**
	 * プロファイル設定を読み込み、そのデータを開きます。
	 */
	public void loadProfile() {
		// とりあえず決まっていないので現状一番上にあるプラグインを有効にすることにします。
		JComboBox comboBox = getComboBox(IInputModule.class);
		Object obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			// 選択されたものとして動作させる。
			plugin.onActivated();
		}
		comboBox = getComboBox(IMixerModule.class);
		obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			plugin.onActivated();
		}
		comboBox = getComboBox(IOutputModule.class);
		obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			plugin.onActivated();
		}
	}
}
