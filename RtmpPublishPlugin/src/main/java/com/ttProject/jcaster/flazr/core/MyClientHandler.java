package com.ttProject.jcaster.flazr.core;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.client.ClientHandler;
import com.flazr.rtmp.client.ClientOptions;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.Control;
import com.ttProject.jcaster.flazr.core.RtmpSender.Event;

/**
 * クライアントハンドラー動作
 * @author taktod
 */
public class MyClientHandler extends ClientHandler {
	/** 動作ロガー */
	private static final Logger logger = Logger.getLogger(MyClientHandler.class);
	private final ClientOptions options;
	private int transactionId = 1;
	private Map<Integer, String> transactionToCommandMap;
	private int streamId = 0;
	public int getStreamId() {
		return streamId;
	}
	private Channel channel;
	private RtmpSender sender; // publisherのかわり。
	/**
	 * コンストラクタ
	 * @param options
	 */
	public MyClientHandler(ClientOptions options, RtmpSender sender) {
		super(options);
		this.options = options;
		transactionToCommandMap = new HashMap<Integer, String>();
		this.sender = sender;
	}
	/**
	 * 応答が期待される動作のコマンドを発行する。
	 * @param channel
	 * @param command
	 */
	private void writeCommandExpectingResult(Channel channel, Command command) {
		final int id = transactionId ++;
		command.setTransactionId(id);
		transactionToCommandMap.put(id, command.getName());
		channel.write(command);
	}
	/**
	 * 接続時実行
	 */
	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
		writeCommandExpectingResult(e.getChannel(), Command.connect(options));
	}
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) {
		channel = me.getChannel();
		if(sender != null && sender.handle(me)) {
			// senderがしょりすべきデータだったので、そちらで処理されました。
			return;
		}
		if(!(me.getMessage() instanceof RtmpMessage)) {
			logger.info("メッセージがrtmpMessageではないので、myClientHandlerで処理しません。");
			return;
		}
		final RtmpMessage message = (RtmpMessage) me.getMessage();
		logger.info("messageType:" + message.getHeader().getMessageType());
		switch(message.getHeader().getMessageType()) {
		case CONTROL:
			Control control = (Control) message;
			switch(control.getType()) {
			case STREAM_BEGIN:
				// 配信をまだ実行していない場合は開始させる必要あり。
				if(!sender.isStarted()) {
					// 開始させる必要あり。
					logger.info("ここにきたら開始させる必要あり。");
					return;
				}
				if(streamId != 0) { // こっちは再生時に飛んでくるっぽいですね。
					channel.write(Control.setBuffer(streamId, options.getBuffer()));
				}
				return;
			default:
				break;
			}
			break;
		case COMMAND_AMF0:
		case COMMAND_AMF3:
			Command command = (Command) message;
			String name = command.getName();
			logger.info("command name:" + name);
			if("_result".equals(name)) {
				String resultFor = transactionToCommandMap.get(command.getTransactionId());
				if("connect".equals(resultFor)) {
					// 接続時の応答
					writeCommandExpectingResult(channel, Command.createStream());
				}
				else if("createStream".equals(resultFor)) {
					// ストリーム生成時
					streamId = ((Double) command.getArg(0)).intValue();
					logger.info("streamId決定:" + streamId);
				}
				return;
			}
			else if("onStatus".equals(name)) {
				@SuppressWarnings("unchecked")
				final Map<String, Object> temp = (Map<String, Object>) command.getArg(0);
				final String code = (String) temp.get("code");
				logger.info("code:" + code);
				if("NetStream.Publish.Start".equals(code)) {
					logger.info("publish");
					// senderの動作をstartさせる必要あり。
					// こっちでもsenderを開始する必要あり。
					sender.onPublished(channel);
					return;
				}
				else if("NetStream.Unpublish.Success".equals(code)) {
					// unpublishがきたら接続しなおした方がよさそう。
					logger.info("unpublish");
					ChannelFuture future = channel.write(Command.closeStream(streamId));
					future.addListener(ChannelFutureListener.CLOSE);
					return;
				}
			}
			else if("close".equals(name)) {
				logger.info("connection closed.");
				channel.close();
			}
			break;
		default:
			break;
		}
		super.messageReceived(ctx, me);
	}
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		logger.info("closeします。");
		if(sender != null) {
			sender.stop(); // 停止したときのイベント発行
		}
		super.channelClosed(ctx, e);
	}
	public void close() {
		channel.close();
	}
	public void fireMessageReceived(Event data) {
		Channels.fireMessageReceived(channel, data);
	}
	public void publish() {
		// publishを実行する。これがうけいれられたら今後はメッセージの送信を実行することができる。
		// rtmpPublisher側でもやることはあるっぽい。
		channel.write(Command.publish(streamId, options));
	}
}
