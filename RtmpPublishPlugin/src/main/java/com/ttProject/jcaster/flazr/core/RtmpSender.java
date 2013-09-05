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
	
	private final LinkedBlockingQueue<FlvAtom> dataQueue = new LinkedBlockingQueue<FlvAtom>();
	private Metadata metadata = new MetadataAmf0("onMetaData");
	private int aggregateDuration = 0;

	private final String rtmpAddress; // こっちは一度つくったら変更不可(接続先の変更になるため、やり直す必要あり。)
	private String streamName; // あとで変更が効くようにしておく。
	private static final Pattern pattern = Pattern.compile("^rtmp://([^/:]+)(:[0-9]+)?/(.*)(.*?)$");

	private ClientBootstrap bootstrap = null;
	private ChannelFuture future = null;
	private ClientOptions options = null;
	private MyClientHandler clientHandler = null;
	private boolean isWorking = true;
	
	// mediaSequenceHeaderをとっておくのも重要だが、timestampの調整も実施する必要あり。
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private int processPos = -1;
	private int savePos = 0;
	
	private final TagManager manager = new TagManager();
	/**
	 * コンストラクタ
	 * @param rtmpAdderss
	 * @param streamName
	 */
	public RtmpSender(String rtmpAdderss, String streamName) {
		this.rtmpAddress = rtmpAdderss;
		this.streamName = streamName;
	}
	/**
	 * 接続を開きます。
	 * @throws Exception
	 */
	public void open() throws Exception {
		options = new ClientOptions();
		parseAddress(options);
		options.setStreamName(streamName);
		options.publishLive();
		options.setFileToPublish(null);
		options.setReaderToPublish(this);
		
		connect(options);
	}
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
	}
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
	public void setStreamName(String name) {
//		streamName = name;
	}
	public void publish() {
		clientHandler.publish();
	}
	public void unpublish() {
		// TODO 一度unpublishしてから再度publishさせたかったら、dataQueueの内容をクリアしたり、前のmshデータを復帰させたりという動作が必要になる。
		clientHandler.unpublish();
	}
	/**
	 * 外部から手を加えてとめる処理
	 */
	public void stop() {
		isWorking = false;
		future.getChannel().close();
	}
	@Override
	public void close() {
		if(isWorking) {
			// これじゃなくて、別のイベントで実行すべきといっている。
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					connect(options);
				}
			});
			t.setDaemon(true);
			t.start();
			return;
		}
		// 切断がおわってから呼ばれる停止処理
		// 切断が外部からよばれている場合は止める。
		// そうでないなら、再接続しておく。
		logger.info("真・停止");
	}
	@Override
	public Metadata getMetadata() {
		return metadata;
	}
	@Override
	public RtmpMessage[] getStartMessages() {
		return new RtmpMessage[]{metadata};
	}
	@Override
	@Deprecated
	public long getTimePosition() {
		throw new RuntimeException("ライブだからseekは禁止だって");
	}
	@Override
	public boolean hasNext() {
		return true;
	}
	@Override
	public RtmpMessage next() {
		if(aggregateDuration <= 0) {
			try {
				return dataQueue.take();
			}
			catch (Exception e) {
				return null;
			}
		}
		else {
			throw new RuntimeException("aggregateによるchunk転送は未実装です。");
		}
	}
	@Override
	@Deprecated
	public long seek(long paramLong) {
		throw new RuntimeException("seekは禁止");
	}
	@Override
	public void setAggregateDuration(int targetDuration) {
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
		// データを登録する
		if(processPos == -1) {
			// 始めのデータである場合
			// 現在の位置を開始位置に設定して、動作をさせる。
			processPos = tag.getTimestamp();
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
				audioMshTag.setTimestamp(tag.getTimestamp());
				dataQueue.add(manager.getAtom(audioMshTag));
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			if(videoMshTag != null) {
				videoMshTag.setTimestamp(tag.getTimestamp());
				dataQueue.add(manager.getAtom(videoMshTag));
				videoMshTag = null;
			}
		}
		dataQueue.add(manager.getAtom(tag));
	}
}
