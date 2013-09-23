JCaster

内容:
linuxやmacでxsplitみたいなのがないし、rtmpに対する理解が進んできたのでjavaで配信クライアント書いてみたい。

作者:taktod

コンタクト
twitter: http://twitter.com/taktod
blog: http://poepoemix.blogspot.jp/

ライセンス：
LGPL
 ConsoleViewer:log4jの出力を表示する
 FlvSavePlugin:flvのデータを保存する
 JCaster:本体
 JCaster.Plugin:プラグインのベース
 Mp4ReaderPlugin:mp4のデータを読み取る
 NoMixerPlugin:なにもmixしないmixer
 RtmpPublishPlugin:rtmpでデータを配信する
 FlvReaderPlugin:flvのデータを読み取る(シーク動作がまだ遅い)

GPL
 playViewer:再生中のデータ確認 xuggle利用 GPL(作成中)
 EncodeMixerPlugin:データを適当なコーデックに変換するプラグイン(作成中)

メモ：
とりあえずLGPLで出します。
xuggleのライブラリを使うと基本GPLになるのでxuggleが絡んでいるプラグインはGPLになります。

一応flvのコーデック変換や映像や音声を混ぜる動作はすでにmyLib.xuggleで実験済みだし。
カメラの取り込み(vlcjやxuggleにやらせる？)やデスクトップキャプチャ(javaのrobotで可能)でできる予定。

xsplitみたいに高機能になったら面白いですね。
いまのところできるのは、mp4(h264 + aac)のデータを元にして
mp4→flv変換。と任意のmp4をrtmpに配信すること。

メモ２：
現状では、出力プラグイン(と再生プラグイン)で、データのソート動作をやっているが、これをやめて、全部mixerPluginにやらせた方が一元管理できていいと思う。
mixerPluginではデータの出力調整を実施します。
具体的には・・・
・出力データはどんな入力であっても一定のtimestampの上昇で動作する(巻き戻りは基本発生させない。もどる場合は０に戻す)
・音声と映像でtimestampの入れ替わりがないようにしておく。
この２点。

各出力プラグインでは、データの巻き戻りのみ注意すればよいことにしておく。
mshデータはmixerPluginで調整・・・したとしても中途でmixerPluginがいれかわったらダメか・・・

今後やること予定：
・RtmpReaderPlugin:rtmpのデータを読み取る LGPL
・DesktopReaderPlugin:デスクトップのデータを読み取る LGPL
・CameraReaderPlugin:カメラのデータを読み取る ?
・AudioReaderPlugin:音声データを読み取る LGPL?
・MultiReaderPlugin:読み込みソースを混ぜたものを読み取る GPL
というのをできるところからやっていきたいところ。
すでにつくったプラグインについてもまだ突貫レベルなので、いろいろとバグがありそうだ。

とりあえずは、playViewerが強制再生になっているので、これをやめたいところ。あと家のwinXPで動作させるとrtmp通信が追いつかなくなっていくので(thread使い過ぎか？)そこを改善したいところ。

なにかありましたら、twitterあたりでコンタクトとってみてください。

うまくいくといいけど・・・