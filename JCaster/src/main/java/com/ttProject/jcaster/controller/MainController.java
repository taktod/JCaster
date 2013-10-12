package com.ttProject.jcaster.controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.apache.log4j.Logger;

import com.ttProject.jcaster.SignalEvent;
import com.ttProject.jcaster.model.PluginModel;
import com.ttProject.jcaster.module.InputModule;
import com.ttProject.jcaster.module.MixerModule;
import com.ttProject.jcaster.module.OutputModule;
import com.ttProject.jcaster.plugin.IPlugin;
import com.ttProject.jcaster.plugin.IViewerPlugin;
import com.ttProject.jcaster.plugin.IPlugin.Type;
import com.ttProject.jcaster.plugin.base.Base;
import com.ttProject.jcaster.plugin.base.ISwingMainBase;
import com.ttProject.jcaster.plugin.module.IInputModule;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.jcaster.plugin.module.IViewerModule;
import com.ttProject.jcaster.swing.MainFrame;

/**
 * 中央でコントロールするコントローラー
 * @author taktod
 * TODO viewer用のモジュール(出力モジュール等と同じ動作？)が追加されたときに、hook用のプログラムを作る必要がありそう。
 */
public class MainController extends Base implements ISwingMainBase, ActionListener {
	/** ロガー */
	private final Logger logger = Logger.getLogger(MainController.class);
	/** 中央フレーム保持 */
	private MainFrame frame;
	/** plugin管理用のモデル保持 */
	private PluginModel pluginModel;

	// 各モジュール保持
	private InputModule inputModule = new InputModule();
	private MixerModule mixerModule = new MixerModule();
	private OutputModule outputModule = new OutputModule();
	private Set<IViewerModule> viewerModules = new HashSet<IViewerModule>();
	
	// 選択中のプラグイン保持
	private IPlugin inputPlugin;
	private IPlugin outputPlugin;
	private IPlugin mixerPlugin;
	
	private List<IPlugin> pluginList = new ArrayList<IPlugin>();
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
		for(final IPlugin plugin : pluginModel.getPlugins()) {
			pluginList.add(plugin);
			logger.info("処理plugin:" + plugin.toString());
			if(plugin.getType() == Type.Viewer || plugin instanceof IViewerPlugin) {
				IViewerPlugin vPlugin = (IViewerPlugin)plugin;
				JPanel panel = new JPanel();
				vPlugin.setViewerPanel(panel);
				pane.addTab(vPlugin.toString(), panel);
				panel.addAncestorListener(new AncestorListener() {
					@Override
					public void ancestorRemoved(AncestorEvent paramAncestorEvent) {
						// deactivateを実装したい場合はここにいれればよさそう。
						plugin.onDeactivated();
					}
					@Override
					public void ancestorMoved(AncestorEvent paramAncestorEvent) {
					}
					@Override
					public void ancestorAdded(AncestorEvent paramAncestorEvent) {
						plugin.onActivated();
					}
				});
			}
		}
	}
	/**
	 * 入力モジュール用の初期化処理
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void setupInputModule(JComboBox comboBox) {
		logger.info("inputModule用のcomboboxを準備しておきます。");
		for(IPlugin plugin : pluginModel.getPlugins()) {
			pluginList.add(plugin);
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
			pluginList.add(plugin);
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
			pluginList.add(plugin);
			if(plugin.getType() == Type.Mixer) {
				comboBox.addItem(plugin);
			}
		}
	}
	/**
	 * モジュールを登録する
	 */
	@Override
	public void registerModule(IModule module) {
		// viewerModuleの場合はextraModuleとして追加しなければいけない。
		if(module instanceof IViewerModule) {
			// viewerモジュール
			if(module instanceof IInputModule) {
				throw new RuntimeException("viewer用の入力モジュールは設定できません。");
			}
			if(module instanceof IMixerModule) {
				mixerModule.setViewerModule((IMixerModule) module);
			}
			if(module instanceof IOutputModule) {
				outputModule.setViewerModule((IOutputModule) module);
			}
			viewerModules.add((IViewerModule)module);
		}
		else if(module instanceof IInputModule) {
			inputModule.setInputModule((IInputModule)module);
			inputModule.registerMixerModule(mixerModule);
		}
		else if(module instanceof IOutputModule) {
			outputModule.setOutputModule((IOutputModule)module);
//			if(mixerModule != null) {
//				mixerModule.registerOutputModule(outputModule);
//			}
		}
		else if(module instanceof IMixerModule) {
			mixerModule.setMixerModule((IMixerModule)module);
			mixerModule.registerOutputModule(outputModule);
//			inputModule.registerMixerModule(mixerModule);
			mixerModule.registerOutputModule(outputModule);
//			if(outputModule != null) {
//				mixerModule.registerOutputModule(outputModule);
//			}
		}
	}
	/**
	 * モジュールをはずす。
	 */
	@Override
	public void unregisterModule(IModule module) {
		if(module instanceof IViewerModule) {
			if(module instanceof IInputModule) {
				throw new RuntimeException("viewer用の入力モジュールは設定できません。");
			}
			if(module instanceof IMixerModule) {
				mixerModule.removeViewerModule((IMixerModule) module);
			}
			if(module instanceof IOutputModule) {
				outputModule.removeViewerModule((IOutputModule) module);
			}
			viewerModules.remove((IViewerModule)module);
			return;
		}
		else if(module instanceof IInputModule) {
			inputModule.removeInputModule((IInputModule)module);
		}
		else if(module instanceof IOutputModule) {
			outputModule.removeOutputModule((IOutputModule)module);
		}
		else if(mixerModule == module) {
			mixerModule.removeMixerModule((IMixerModule)module);
		}
	}
	/**
	 * タイマーによる処理を実行
	 */
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
		for(IViewerModule module : viewerModules) {
			module.onTimerEvent();
		}
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
		// とりあえず決まっていないので現状一番上にあるプラグインを有効にすることにします。(これはとりあえずいらないかもね。)
		JComboBox comboBox = getComboBox(IInputModule.class);
		Object obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			// 選択されたものとして動作させる。
			plugin.onActivated();
			inputPlugin = plugin;
		}
		comboBox = getComboBox(IMixerModule.class);
		obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			plugin.onActivated();
			mixerPlugin = plugin;
		}
		comboBox = getComboBox(IOutputModule.class);
		obj = comboBox.getItemAt(0);
		if(obj != null) {
			IPlugin plugin = (IPlugin) obj;
			plugin.onActivated();
			outputPlugin = plugin;
		}
	}
	/**
	 * それぞれのコンポーネントのコンボボックスが変更されたときの処理
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() instanceof JComboBox) {
			JComboBox comboBox = (JComboBox) e.getSource();
			Object item = comboBox.getSelectedItem();
			if(item instanceof IPlugin) {
				IPlugin plugin = (IPlugin) item;
				switch(plugin.getType()) {
				case Input:
					if(inputPlugin == plugin) {
						return;
					}
					if(inputPlugin != null) {
						inputPlugin.onDeactivated();
					}
					inputPlugin = plugin;
					break;
				case Mixer:
					if(mixerPlugin == plugin) {
						return;
					}
					if(mixerPlugin != null) {
						mixerPlugin.onDeactivated();
					}
					mixerPlugin = plugin;
					break;
				case Output:
					if(outputPlugin == plugin) {
						// 同じアイテムを選択しただけなので、なにもしない。
						return;
					}
					if(outputPlugin != null) {
						outputPlugin.onDeactivated();
					}
					outputPlugin = plugin;
					break;
				case Viewer:
					return;
				}
				plugin.onActivated();
			}
		}
	}
	public void onShutdown() {
		for(IPlugin plugin : pluginList) {
			plugin.onShutdown();
		}
	}
}
