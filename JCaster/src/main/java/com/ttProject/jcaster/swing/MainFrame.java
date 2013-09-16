package com.ttProject.jcaster.swing;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ttProject.jcaster.controller.MainController;
import com.ttProject.jcaster.plugin.module.IInputModule;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;

/**
 * 中心のGUI部分
 * @author taktod
 */
public class MainFrame extends JFrame {
	private static final long serialVersionUID = -1233747404470340015L;
	/** 動作ロガー */
//	private static final Logger logger = Logger.getLogger(MainFrame.class);
	/** 中央コントローラー */
	private final MainController mainController;
	/** 入力コントロール用のパネル */
	private JPanel inputPanel;
	/** 入力コントロールのコンボボックス */
	private JComboBox inputComboBox;
	/** 出力コントロール用のパネル */
	private JPanel outputPanel;
	/** 出力コントロールのコンボボックス */
	private JComboBox outputComboBox;
	/** ミキサー動作用のパネル */
	private JPanel mixerPanel;
	/** ミキサーのコンボボックス */
	private JComboBox mixerComboBox;
	/** データ表示用のタブ */
	private JTabbedPane viewerTabPane;
	/**
	 * コンストラクタ
	 */
	public MainFrame() {
		mainController = new MainController(this);
		Thread shutdown = new Thread(new Runnable() {
			@Override
			public void run() {
				// shutdownを実行していく。
				mainController.onShutdown();
			}
		});
		shutdown.setDaemon(false);
		Runtime.getRuntime().addShutdownHook(shutdown);
	}
	/**
	 * データのセットアップ
	 */
	public void setup() {
		setSize(new Dimension(800, 600));
		setMinimumSize(new Dimension(320, 240));
		setName("JCaster");
		setTitle("JCaster");
		// メニューをつくっておく。
		setupMenu();
		// GUIをつくっておく。
		setupPanel();
		
		// デフォルトデータをつくっておく。
		mainController.loadProfile();
	}
	private void setupMenu() {
		
	}
	private void setupPanel() {
		setLayout(new GridLayout(2, 1));
		viewerTabPane = new JTabbedPane();
		add(viewerTabPane);

		JPanel panel;

		// 存在しているプラグイン分だけ、出力のタブを追加しておく。
		mainController.setupViewerModule(viewerTabPane);

		// 下部のパネル(左が入力データ、右が出力データ用のパネル)
		panel = new JPanel();
		add(panel);
		panel.setLayout(new GridLayout(1, 3));
		// 入力用
		inputPanel = new JPanel();
		JScrollPane scroll = new JScrollPane(inputPanel);
		panel.add(scroll);
		inputComboBox = new JComboBox();
		inputComboBox.addActionListener(mainController);
		mainController.setupInputModule(inputComboBox);
		// mixer用
		mixerPanel = new JPanel();
		scroll = new JScrollPane(mixerPanel);
		panel.add(scroll);
		mixerComboBox = new JComboBox();
		mixerComboBox.addActionListener(mainController);
		mainController.setupMixerModule(mixerComboBox);

		outputPanel = new JPanel();
		scroll = new JScrollPane(outputPanel);
		panel.add(scroll);
		outputComboBox = new JComboBox();
		outputComboBox.addActionListener(mainController);
		mainController.setupOutputModule(outputComboBox);
	}
	/**
	 * moduleクラスに対応したパネルを応答します。
	 * @param targetClass
	 * @return
	 */
	public JPanel getComponentPanel(Class<?> targetClass) {
		if(IInputModule.class.isAssignableFrom(targetClass)) {
			return inputPanel;
		}
		else if(IOutputModule.class.isAssignableFrom(targetClass)) {
			return outputPanel;
		}
		else if(IMixerModule.class.isAssignableFrom(targetClass)) {
			return mixerPanel;
		}
		return null;
	}
	/**
	 * moduleクラスに対応したコンボボックスを応答します。
	 * @param targetClass
	 * @return
	 */
	public JComboBox getComboBox(Class<?> targetClass) {
		if(IInputModule.class.isAssignableFrom(targetClass)) {
			return inputComboBox;
		}
		else if(IOutputModule.class.isAssignableFrom(targetClass)) {
			return outputComboBox;
		}
		else if(IMixerModule.class.isAssignableFrom(targetClass)) {
			return mixerComboBox;
		}
		return null;
	}
}
