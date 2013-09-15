package com.ttProject.jcaster.flazr.core;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import com.flazr.io.flv.FlvAtom;
import com.flazr.rtmp.RtmpDecoder;
import com.flazr.rtmp.RtmpEncoder;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.client.ClientHandshakeHandler;
import com.flazr.rtmp.client.ClientOptions;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.MetadataAmf0;
import com.ttProject.flazr.TagManager;
import com.ttProject.jcaster.outputplugin.RtmpPublishModule;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * rtmpを送信する動作
 * @author taktod
 */
public class RtmpSender implements RtmpReader {
	/** ロガー */
	private final Logger logger = Logger.getLogger(RtmpSender.class);
	/** 親モジュール参照 */
	private final RtmpPublishModule module;
	/** 配信用のスレッドに渡すデータ保持 */
	private final LinkedBlockingQueue<FlvAtom> dataQueue = new LinkedBlockingQueue<FlvAtom>();
	/** デフォルトのメタデータ */
	private Metadata metadata = new MetadataAmf0("onMetaData");
	/** 集合メッセージ用の設定保持(常に0期待) */
	private int aggregateDuration = 0;

	/** 接続サーバーアドレス */
	private final String rtmpAddress;
	/** アドレス解析用 */
	private static final Pattern pattern = Pattern.compile("^rtmp://([^/:]+)(:[0-9]+)?/(.*)(.*?)$");

	/** 接続処理用 */
	private ClientBootstrap bootstrap = null;
	private ChannelFuture future = null;
	private ClientOptions options = null;
	private MyClientHandler clientHandler = null;
	/** 接続中フラグ */
	private boolean isWorking = true;
	/** 放送中フラグ */
	private boolean isPublishing = false;
	
	/** msh用のタグ */
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	/** 入力データの処理位置 */
	private int processPos = -1;
	/** 現在の処理位置 */
	private int savePos = 0;

	/** flvTag→flvAtomの変換補助 */
	private final TagManager manager = new TagManager();
	/**
	 * コンストラクタ
	 * @param rtmpAdderss
	 * @param streamName
	 */
	public RtmpSender(String rtmpAdderss, RtmpPublishModule module) {
		this.rtmpAddress = rtmpAdderss;
		this.module = module;
	}
	/**
	 * 接続を開きます。
	 * @throws Exception
	 */
	public void open() throws Exception {
		options = new ClientOptions();
		parseAddress(options);
		options.publishLive();
		options.setFileToPublish(null);
		options.setReaderToPublish(this);

		// 接続を開始します。blockしてしまうので別threadにやらせます。
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				// フラグがついている場合はとまっても再接続します。
				while(isWorking) {
					try {
						// 保持queueのデータをいったんクリアします。
						dataQueue.clear();
						audioMshTag = null;
						videoMshTag = null;
						processPos = -1;
						savePos = 0;
						module.requestMshData();
					}
					catch (Exception e) {
					}
					connect(options); // ここでブロックが発生
					logger.info("接続おわった。");
				}
				logger.info("真・停止");
				// おわったときになにか解放処理をしたい場合はここで何ぞすればよい。
			}
		});
		t.setDaemon(true);
		t.start();
	}
	/**
	 * 接続処理
	 * @param options
	 */
	private void connect(final ClientOptions options) {
		bootstrap = getBootstrap(Executors.newCachedThreadPool(), options);
		future = bootstrap.connect(new InetSocketAddress(options.getHost(), options.getPort()));
		future.awaitUninterruptibly();
		if(!future.isSuccess()) {
			logger.warn("処理失敗しました。");
		}
		else {
			logger.info("接続しました。");
		}
		// これやっちゃうと・・・他の処理がしにそうな気がするけど・・・
		future.getChannel().getCloseFuture().awaitUninterruptibly(); 
		bootstrap.getFactory().releaseExternalResources();
	}
	/**
	 * bootstrapの作成処理
	 * @param executor
	 * @param options
	 * @return
	 */
	private ClientBootstrap getBootstrap(final Executor executor, final ClientOptions options) {
		final ChannelFactory factory = new NioClientSocketChannelFactory(executor, executor);
		final ClientBootstrap bootstrap = new ClientBootstrap(factory);
		clientHandler = new MyClientHandler(options);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				pipeline.addLast("handshaker", new ClientHandshakeHandler(options));
				pipeline.addLast("decoder", new RtmpDecoder());
				pipeline.addLast("encoder", new RtmpEncoder());
				// 通常のclientHandlerを利用すると、接続だけして、publishする前という動作ができない。
				pipeline.addLast("handler", clientHandler);
				return pipeline;
			}
		});
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);
		return bootstrap;
	}
	/**
	 * アドレスの解析処理
	 * @param options
	 * @throws Exception
	 */
	private void parseAddress(ClientOptions options) throws Exception {
		Matcher matcher = pattern.matcher(rtmpAddress);
		if(!matcher.matches()) {
			throw new Exception("rtmpアドレスがおかしいです。");
		}
		if(matcher.groupCount() != 4) {
			throw new Exception("アドレス解析に失敗しました。");
		}
		options.setHost(matcher.group(1));
		if(matcher.group(2) == null) {
			options.setPort(1935);
		}
		else {
			options.setPort(Integer.parseInt(matcher.group(2).substring(1)));
		}
		options.setAppName(matcher.group(3));
	}
	/**
	 * 放送を開始
	 */
	public void publish() {
		options.setStreamName(module.getStreamName());
		isPublishing = true;
		clientHandler.publish();
	}
	/**
	 * 放送を中断
	 */
	public void unpublish() {
		isPublishing = false;
		clientHandler.unpublish();
	}
	/**
	 * 外部から手を加えてとめる処理
	 */
	public void stop() {
		isWorking = false;
		future.getChannel().close();
	}
	/**
	 * 停止処理
	 * いまのところ特にすることなし。
	 * なお、publishしていない場合に停止したら呼ばれないのでここで処理はせず。connectのおわりの部分で処理をすべき
	 */
	@Override
	public void close() {
	}
	/**
	 * metaデータ取得
	 */
	@Override
	public Metadata getMetadata() {
		return metadata;
	}
	/**
	 * 開始時に送信するメッセージ
	 */
	@Override
	public RtmpMessage[] getStartMessages() {
		return new RtmpMessage[]{metadata};
	}
	/**
	 * 再生位置を取得
	 * seekがらみなので禁止
	 */
	@Override
	@Deprecated
	public long getTimePosition() {
		throw new RuntimeException("ライブだからseekは禁止だって");
	}
	/**
	 * 次のメッセージが存在しているか判定
	 * liveで常にあることにします。(なお転送がなくなってもqueueのtake処理でblockかかります。)
	 */
	@Override
	public boolean hasNext() {
		return true;
	}
	/**
	 * publisherから次のデータが要求されたときの動作
	 * nullを応答するとunpublishになるようになっています。
	 */
	@Override
	public RtmpMessage next() {
		if(aggregateDuration <= 0) {
			if(!isPublishing || !isWorking) {
				return null;
			}
			try {
				FlvAtom atom = dataQueue.take();
				return atom;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		else {
			throw new RuntimeException("aggregateによるchunk転送は未実装です。");
		}
	}
	/**
	 * シーク動作
	 * 禁止(っていうか入力モジュールでの送信時にコントロールすればよい。)
	 */
	@Override
	@Deprecated
	public long seek(long paramLong) {
		throw new RuntimeException("seekは禁止");
	}
	/**
	 * 集合メッセージ用のデータ設置処理(0がくることを想定しています。)
	 */
	@Override
	public void setAggregateDuration(int targetDuration) {
//		logger.info("aggregateDurationをセットします。" + targetDuration);
		aggregateDuration = targetDuration;
	}
	/**
	 * flvTagの情報をうけとったときの動作
	 * @param tag
	 */
	public void send(Tag tag) throws Exception {
		if(tag == null) {
			return;
		}
		// mshデータは保持しておく。
		if(tag instanceof AudioTag) {
			AudioTag aTag = (AudioTag) tag;
			if(aTag.getCodec() == CodecType.AAC && aTag.isMediaSequenceHeader()) {
				audioMshTag = aTag;
				return;
			}
			else if(aTag.getCodec() != CodecType.AAC) {
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			VideoTag vTag = (VideoTag) tag;
			if(vTag.getCodec() == CodecType.AVC && vTag.isMediaSequenceHeader()) {
				videoMshTag = vTag;
				return;
			}
			else if(vTag.getCodec() != CodecType.AVC) {
				videoMshTag = null;
			}
		}
		if(!isPublishing) {
			return;
		}
		// データを登録する
		if(processPos == -1) {
			// 始めのデータである場合
			// 現在の位置を開始位置に設定して、動作をさせる。
			processPos = tag.getTimestamp();
			savePos = 0;
		}
		else if(Math.abs((processPos - tag.getTimestamp())) > 200){
			// 前後のticが200以上離れている場合は、なんらかの理由でやり直しになった。
			processPos = tag.getTimestamp();
			savePos += 100; // 100 ticだけすすめて、続きを保存させる。
		}
		else {
			// 前からのずれ分を加える必要あり。
			savePos += tag.getTimestamp() - processPos;
			processPos = tag.getTimestamp();
		}
		// 時間の調整を実施する。
		tag.setTimestamp(savePos);
		if(tag instanceof AudioTag) {
			if(audioMshTag != null) {
				logger.info("mshをおくります。audio");
				audioMshTag.setTimestamp(tag.getTimestamp());
				dataQueue.add(manager.getAtom(audioMshTag));
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			if(videoMshTag != null) {
				logger.info("mshをおくります。video");
				videoMshTag.setTimestamp(tag.getTimestamp());
				dataQueue.add(manager.getAtom(videoMshTag));
				videoMshTag = null;
			}
		}
		dataQueue.add(manager.getAtom(tag));
	}
}
