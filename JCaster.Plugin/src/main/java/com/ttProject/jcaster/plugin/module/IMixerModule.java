package com.ttProject.jcaster.plugin.module;

import com.ttProject.jcaster.plugin.base.IMainBase.Media;

/**
 * コーデックの変更を実行したりするモジュール
 * 欲しい動作
 * ・メディアデータのうけいれ。
 * ・データ元の転送が終わった場合のイベントがほしい。(初期化する必要があるため)
 * (一応メタデータがあるかでもよい。)
 * メタデータでやりとりしようとすると、flvでない場合に困るので、やっぱりイベントがほしいところ。
 * @author taktod
 */
public interface IMixerModule extends IModule {
	/**
	 * 出力モジュールを設定する。
	 * @param outputModule
	 */
	public void registerOutputModule(IOutputModule outputModule);
	/**
	 * メディアデータの受け入れ
	 * @param media
	 * @param mediaData
	 */
	public void setData(Media media, Object mediaData);
}
