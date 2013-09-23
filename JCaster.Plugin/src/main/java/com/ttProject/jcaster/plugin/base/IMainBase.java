package com.ttProject.jcaster.plugin.base;

import com.ttProject.jcaster.plugin.module.IModule;

/**
 * 中心となるコントローラのベース
 * もともとプラグインをいれるにしていたんだが、それだと大変なので、各部分にlistenerをいれるようにすることで対処できるようにしておこうと思う。
 * pluginの登録制にすると各プラグインはその動作に専念できる。
 * listenerの登録性にすると各プラグインはいろいろといじることができる。
 * というわけでややこしいので両方いれておくか・・・
 * @author taktod
 */
public interface IMainBase {
	// 各プラグインの設定
	// プラグインを設定することで、各イベントを勝手にmainControllerが実行してくれるようになる。
	// つまりlistenerを登録するようなもの。
	// プラグインは登録しなくても勝手に読み込まれてmenuの保持動作したがる。
	// 内部動作として、プラグインはあとから登録する必要があることにする。
	/**
	 * 動作モジュールを登録する
	 */
	public void registerModule(IModule module);
	/**
	 * 動作モジュールを破棄する。
	 * @param module
	 */
	public void unregisterModule(IModule module);
	/**
	 * メディアタイプの定義
	 * @author taktod
	 * /
	public enum Media {
		AudioData, // myLib.media.rawのAudioData
		VideoData, // myLib.media.rawのVideoData
		FlvTag, // flvTag
		VideoPicture, // xuggleのpicture
		AudioSamples // xuggleのsamples
	}//*/
}
