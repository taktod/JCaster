package com.ttProject.jcaster.flazr.core;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import com.flazr.io.flv.FlvAtom;
import com.flazr.rtmp.RtmpDecoder;
import com.flazr.rtmp.RtmpEncoder;
import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.client.ClientHandshakeHandler;
import com.flazr.rtmp.client.ClientOptions;
import com.flazr.rtmp.message.ChunkSize;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.MetadataAmf0;
import com.ttProject.flazr.TagManager;
import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.Tag;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.VideoTag;

/**
 * rtmpのpublishを実行する動作
 * とりあえずrtmpPublisherの後継ということでつくっていく。
 * @author taktod
 * もしかしたらこのsenderは別のクラスにしておいた方が無難だったかも・・・
 */
public class RtmpSender {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(RtmpSender.class);
	private final TagManager tagManager = new TagManager();
	/** 現在のやり取りID */
	private int currentConversationId = 0;
	private int timePosition = 0; // 現在の処理time位置

	private final String streamName;
	private final String rtmpAddress;

	private ClientBootstrap bootstrap = null;
	private ChannelFuture future = null;
	private MyClientHandler clientHandler = null;
	
	private boolean closePressed = false;

	/**
	 * やりとり動作イベント
	 * @author taktod
	 *
	 */
	public static class Event {
		private final int conversationId;
		private final Object data; // 送信命令
		
		public Event(final int conversationId, final Object data) {
			this.conversationId = conversationId;
			this.data = data;
		}
		public int getConversationId() {
			return conversationId;
		}
		public Object getData() {
			return data;
		}
	}
	private static final Pattern pattern = Pattern.compile("^rtmp://([^/:]+)(:[0-9]+)?/(.*)(.*?)$");
	/**
	 * コンストラクタ
	 * @param rtmpAddress
	 * @param streamName
	 */
	public RtmpSender(String rtmpAddress, String streamName) {
		this.rtmpAddress = rtmpAddress;
		this.streamName = streamName;
	}
	/**
	 * 接続を開きます。
	 */
	public void open() throws Exception {
		final ClientOptions options = new ClientOptions();
		parseAddress(options);
		options.setStreamName(streamName);
		options.publishLive();
		options.setFileToPublish(null);
		logger.info(options.toString());
		
		connect(options);
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
	 * 接続処理
	 * @param options
	 */
	private void connect(final ClientOptions options) {
		bootstrap = getBootstrap(Executors.newCachedThreadPool(), options);
		future = bootstrap.connect(new InetSocketAddress(options.getHost(), options.getPort()));
		future.awaitUninterruptibly();
		if(!future.isSuccess()) {
			logger.error("接続に失敗しました。");
		}
		logger.info("接続処理おわり");
	}
	/**
	 * bootstrap取得
	 * @param executor
	 * @param options
	 * @return
	 */
	private ClientBootstrap getBootstrap(final Executor executor, final ClientOptions options) {
		final ChannelFactory factory = new NioClientSocketChannelFactory(executor, executor);
		final ClientBootstrap bootstrap = new ClientBootstrap(factory);
		clientHandler = new MyClientHandler(options, this);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				pipeline.addLast("handshaker", new ClientHandshakeHandler(options));
				pipeline.addLast("decoder", new RtmpDecoder());
				pipeline.addLast("encoder", new RtmpEncoder());
				pipeline.addLast("handler", clientHandler);
				return pipeline;
			}
		});
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);
		return bootstrap;
	}
	/**
	 * 停止要求
	 */
	public void close() {
		// 停止要求がきた場合
		closePressed = true;
		clientHandler.close();
	}
	/**
	 * 閉じた場合の処理
	 */
	public void stop() {
		// とまった場合の処理
		if(!closePressed) {
			try {
				// とまっただけで閉じられたわけでない場合は再度接続しておく。
				open();
			}
			catch (Exception e) {
				logger.error(e);
			}
		}
	}
	public boolean isStarted() {
		return currentConversationId > 0;
	}
	/**
	 * publishボタンがおされたときの動作
	 */
	public void publish() {
		clientHandler.publish();
	}
	/**
	 * publishがサーバー側でうけいれられたときの動作
	 */
	public void onPublished(Channel channel) {
		// chunksizeの命令とmetaデータを送る必要がある。(metaデータはまぁおくれたらでいいと思う。本来はmixerから変換データとしてうけとっているはずなので、おくらなくても勝手におくられると思う。)
		logger.info("publishが完了したので、命令をいくつか送信しておきます。");
		currentConversationId ++;
		writeToStream(channel, new ChunkSize(4096));
		// 仮のmetadataを送っておく。
		writeToStream(channel, new MetadataAmf0("onMetaData"));
		// ここまできたら、もう問題ないので、flvデータを転送してやればよい。
	}
	/**
	 * unpublishボタンがおされたときの動作
	 */
	public void unpublish() {
		clientHandler.fireMessageReceived(new Event(currentConversationId, Command.unpublish(clientHandler.getStreamId())));
	}
	private void writeToStream(final Channel channel, final RtmpMessage message) {
		if(message.getHeader().getChannelId() > 2) {
			message.getHeader().setStreamId(clientHandler.getStreamId());
			message.getHeader().setTime((int) timePosition);
		}
		channel.write(message);
	}
	/**
	 * senderで処理すべきメッセージなら処理する。
	 * @param me
	 * @return
	 */
	public boolean handle(MessageEvent me) {
		if(me.getMessage() instanceof Event) {
			Event pe = (Event)me.getMessage();
			if(pe.conversationId != currentConversationId) {
				// 現在処理中のデータではなかったので、すててなにもしない。
				return true;
			}
			// メッセージを処理する。
			Object data = pe.getData();
			if(data instanceof RtmpMessage) {
				writeToStream(me.getChannel(), (RtmpMessage)data);
			}
			if(data instanceof Tag) {
				// tagの場合はデータをそのまま送ればOKか？
				try {
					FlvAtom atom = tagManager.getAtom((Tag) data);
					me.getChannel().write(atom);
				}
				catch (Exception e) {
					logger.error("例外発生", e);
				}
			}
			return true;
		}
		return false;
	}
	private AudioTag audioMshTag = null;
	private VideoTag videoMshTag = null;
	private int processPos = -1; // 処理中のデータの位置
	private int savePos = 0; // 保存しているデータの位置(ファイルの長さでもある。)
	/**
	 * データを転送する。
	 * @param tag
	 */
	public void send(Tag tag) {
		if(tag == null) {
			return;
		}
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
		// ここから先は、動作が確定していないと実行してはいけない。
		if(!isStarted()) {
			return;
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
		// データを調整して入力させる。
		if(tag instanceof AudioTag) {
			if(audioMshTag != null) {
				audioMshTag.setTimestamp(tag.getTimestamp());
//				channel.write(audioMshTag.getBuffer());
				clientHandler.fireMessageReceived(new Event(currentConversationId, audioMshTag));
				audioMshTag = null;
			}
		}
		if(tag instanceof VideoTag) {
			if(videoMshTag != null) {
				videoMshTag.setTimestamp(tag.getTimestamp());
//				channel.write(videoMshTag.getBuffer());
				clientHandler.fireMessageReceived(new Event(currentConversationId, videoMshTag));
				videoMshTag = null;
			}
		}
		clientHandler.fireMessageReceived(new Event(currentConversationId, tag));
	}
}
