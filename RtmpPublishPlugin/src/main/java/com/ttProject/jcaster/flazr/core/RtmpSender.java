package com.ttProject.jcaster.flazr.core;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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

import com.flazr.rtmp.RtmpDecoder;
import com.flazr.rtmp.RtmpEncoder;
import com.flazr.rtmp.client.ClientHandshakeHandler;
import com.flazr.rtmp.client.ClientOptions;

/**
 * rtmpのpublishを実行する動作
 * とりあえずrtmpPublisherの後継ということでつくっていく。
 * @author taktod
 *
 */
public class RtmpSender {
	/** ロガー */
	private static final Logger logger = Logger.getLogger(RtmpSender.class);
	/** 現在のやり取りID */
	private int currentConversationId = 0;
	
	private final String streamName;
	private final String rtmpAddress;

	private ClientBootstrap bootstrap = null;
	private ChannelFuture future = null;
	private MyClientHandler clientHandler = null;
	
	private boolean closePressed = false;

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
	public void publish() {
		clientHandler.publish();
	}
	public void unpublish() {
		clientHandler.unpublish();
	}
}
