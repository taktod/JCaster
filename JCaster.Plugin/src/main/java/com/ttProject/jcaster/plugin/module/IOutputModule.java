package com.ttProject.jcaster.plugin.module;

import com.ttProject.media.flv.Tag;

/**
 * 出力モジュール
 * 欲しい動作
 * ・メディアデータの受け入れ。
 * ・データ元の転送が終わった場合のイベント(開始した場合のイベントでもよい。)
 * @author taktod
 */
public interface IOutputModule extends IModule {
	/**
	 * 変換処理済みのtagを登録する
	 * @param tag
	 */
	public void setMixedData(Tag tag);
}
