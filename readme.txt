JCaster

作者:taktod

コンタクト
twitter: http://twitter.com/taktod
blog: http://poepoemix.blogspot.jp/

ライセンス：
LGPL
 ConsoleViewer
 FlvSavePlugin
 JCaster
 JCaster.Plugin
 Mp4ReaderPlugin
 NoMixerPlugin
 RtmpPublishPlugin(作りかけ)

GPL

メモ：
とりあえずLGPLで出します。
今後xuggleをつかった変換処理も取り込む予定ですが、そのときには、対象プラグインはGPLになる予定。

一応flvのコーデック変換や映像や音声を混ぜる動作はすでにmyLib.xuggleで実験済みだし。
カメラの取り込み(vlcjやxuggleにやらせる？)やデスクトップキャプチャ(javaのrobotで可能)でできる予定。

xsplitみたいに高機能になったら面白いですね。
いまのところできるのは、mp4→flv変換。

今後やること予定：
・rtmpPublishPlugin flazr利用 LGPL
・playViewer(再生中の動画視聴部) xuggle利用 GPL
・MixerPlugin(コーデック変換) xuggle利用 GPL
・FlvReaderPlugin(flvを流す動作) LGPL
・RtmpReaderPlugin(他のサーバーに流れておるデータを流す動作) LGPL
・multiReaderPlugin(いくつかのソースを組み合わせる動作) GPL
というのをできるところからやっていきたいところ。
とりあえずRtmpPublishPluginは早めに終わらせたいですね。

うまくいくといいけど・・・