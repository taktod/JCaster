package com.ttProject.jcaster.module;

import java.util.HashSet;
import java.util.Set;

import com.ttProject.jcaster.model.MixedMediaOrderModel;
import com.ttProject.jcaster.plugin.module.IMixerModule;
import com.ttProject.jcaster.plugin.module.IOutputModule;
import com.ttProject.media.flv.Tag;

/**
 * mixerモジュールを管理するモジュール
 * @author taktod
 * TODO 入力モジュールからのデータを受け取る前にデータのソートとmsh対策はやっておきたい。
 * msh対策とは・・・
 * aacとavcの場合はmshという解析に必要なタグがはじめだけ流れてくるという仕様がある。
 * そのデータをトラップしておいて、次にきたモジュールにきちんと流してやること。
 * 
 * flip対策
 * 映像と音声のデータがflipした場合きちんとしたオーダーに戻して動作させてやる必要がある。
 * 
 * この２点はここでもOutputModuleでも実行する必要があるので、適当なモジュールをつくって対策しておきたいところ。
 */
public class MixerModule implements IMixerModule {
	/** 通常のmixerModule */
	private IMixerModule mixerModule;
	/** viewerでうけとりたい場合のmixerModule */
	private Set<IMixerModule> viewerModules = new HashSet<IMixerModule>();

	/**
	 * mediaデータのソートを実行するモデル
	 */
	private final MixedMediaOrderModel mixedMediaOrderModel = new MixedMediaOrderModel();
	/**
	 * ゼロにリセットするための動作フラグ
	 */
	private boolean zeroReset = true;
	/**
	 * 変換モジュールをセットする
	 * @param module
	 */
	public void setMixerModule(IMixerModule module) {
		mixerModule = module;
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			mixerModule.setData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			mixerModule.setData(tag);
		}
	}
	/**
	 * 変換モジュールを撤去する
	 * @param module
	 */
	public void removeMixerModule(IMixerModule module) {
		if(mixerModule == module) {
			mixerModule = null;
		}
	}
	/**
	 * 表示モジュールを設定する
	 * @param module
	 */
	public void setViewerModule(IMixerModule module) {
		viewerModules.add(module);
		Tag tag = mixedMediaOrderModel.getAudioMshTag();
		if(tag != null) {
			module.setData(tag);
		}
		tag = mixedMediaOrderModel.getVideoMshTag();
		if(tag != null) {
			module.setData(tag);
		}
	}
	/**
	 * 表示モジュールを撤去する
	 * @param module
	 */
	public void removeViewerModule(IMixerModule module) {
		viewerModules.remove(module);
	}
	/**
	 * タイマーイベント
	 */
	@Override
	public void onTimerEvent() {
		// タイマーイベントは本家
		if(mixerModule != null) {
			mixerModule.onTimerEvent();
		}
	}
	/**
	 * 出力モジュールを設定する
	 */
	@Override
	public void registerOutputModule(IOutputModule outputModule) {
		// 出力モジュールへの受け渡しは本家のmixerModuleのみ
		if(mixerModule != null) {
			// TODO ここで設定しているmixerモジュールはあたらしく変換モジュールを設定したときに付け替える必要がいちおうある。
			mixerModule.registerOutputModule(outputModule);
		}
	}
	/**
	 * データを設置する。
	 */
	@Override
	public void setData(Object mediaData) {
		// timestampが0の場合はリセットする
		if(MixedMediaOrderModel.getTimestamp(mediaData) == 0) {
			if(zeroReset) { // timestamp = 0がきたらリセットする。
				mixedMediaOrderModel.reset();
			}
			zeroReset = false;
		}
		else {
			zeroReset = true;
		}
		// 中途でregisterしたらmediaSequenceHeaderを送るという処理がありえるので、ここで管理しておいて、あたらしく登録した場合には送り直す必要がでてくる。面倒だね。
		// データのhookは本家もviewerも実行
		mixedMediaOrderModel.addData(mediaData);
		for(Object data : mixedMediaOrderModel.getCompleteData()) {
			if(mixerModule != null) {
				mixerModule.setData(data);
				// viewerModuleへの転送も追加する必要あり。(とりあえずあとでやる。)
				// TODO オブジェクトのコピーが必要ですね。
			}
		}
	}
}
