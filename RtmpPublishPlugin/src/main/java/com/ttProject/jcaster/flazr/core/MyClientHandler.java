package com.ttProject.jcaster.flazr.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpPublisher;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.client.ClientHandler;
import com.flazr.rtmp.client.ClientOptions;
import com.flazr.rtmp.message.ChunkSize;
import com.flazr.rtmp.message.Command;
import com.flazr.rtmp.message.Control;

/**
 * 独自の接続制御
 * @author taktod
 */
public class MyClientHandler extends ClientHandler {
	/** ロガー */
	private final Logger logger = Logger.getLogger(MyClientHandler.class);
	private final ClientOptions options;
	private int transactionId = 1;
	private Map<Integer, String> transactionToCommandMap;
	private RtmpPublisher publisher = null;
	private int streamId = 0;

	private Channel channel = null;
	private Thread thread = null;
	private Timer timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS);
	/**
	 * コンストラクタ
	 * @param options
	 */
	public MyClientHandler(ClientOptions options) {
		super(options);
		this.options = options;
		transactionToCommandMap = new HashMap<Integer, String>();
	}
	public void publish() {
		if(options.getPublishType() != null) {
			RtmpReader reader = options.getReaderToPublish();
			publisher = new RtmpPublisher(reader, streamId, options.getBuffer(), false, false) {
				private int counter = 0;
				@Override
				protected RtmpMessage[] getStopMessages(long paramLong) {
					return new RtmpMessage[]{Command.unpublish(streamId)};
				}
				/**
				 * 次の処理にすすめる。
				 * TODO timerをはさんで動作をさせないと、固まることがあるみたいです。
				 * よって10ミリ秒だけ強制的にはさむようにしました。
				 * ただし、superのfireNextが呼べないので、適当な関数を挟むようにしました。かっこわるいね。
				 */
				public void fireNext(final Channel channel, final long delay) {
					counter ++;
					if(counter > 10) {
						timer.newTimeout(new TimerTask() {
							@Override
							public void run(Timeout timeout) throws Exception {
								fireNext2(channel, delay);
							}
						}, 10, TimeUnit.MILLISECONDS);
					}
					else {
						super.fireNext(channel, delay);
					}
				};
				private void fireNext2(final Channel channel, final long delay) {
					super.fireNext(channel, delay);
				}
			};
			channel.write(Command.publish(streamId, options));
		}
	}
	public void unpublish() {
		if(thread != null) {
			thread.interrupt();
		}
	}
	private void writeCommandExpectingResult(Channel channel, Command command) {
		final int id = transactionId ++;
		command.setTransactionId(id);
		transactionToCommandMap.put(id, command.getName());
		channel.write(command);
	}
	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
		writeCommandExpectingResult(e.getChannel(), Command.connect(options));
	}
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		super.channelClosed(ctx, e);
		if(publisher != null) {
			publisher.close();
		}
		else {
			RtmpReader reader = options.getReaderToPublish();
			reader.close();
		}
		publisher = null;
		thread = null;
		channel = null;
	}
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent me) {
		channel = me.getChannel();
		thread = Thread.currentThread();
		if(publisher != null && publisher.handle(me)) {
			return;
		}
		if(!(me.getMessage() instanceof RtmpMessage)) {
			System.out.println("messageがrtmpMessageではありませんでした。なんだこれ？");
			System.out.println(me.getMessage().getClass());
			return;
		}
		final RtmpMessage message = (RtmpMessage) me.getMessage();
		switch(message.getHeader().getMessageType()) {
		case CONTROL:
			Control control = (Control)message;
			switch(control.getType()) {
			case STREAM_BEGIN:
				if(publisher != null && !publisher.isStarted()) {
					publisher.start(channel, options.getStart(), options.getLength(), new ChunkSize(4096));
					return;
				}
				if(streamId != 0) {
					channel.write(Control.setBuffer(streamId, options.getBuffer()));
				}
				break;
			default:
				break;
			}
			break;
		case COMMAND_AMF0:
		case COMMAND_AMF3:
			Command command = (Command)message;
			String name = command.getName();
			logger.info("command name:" + name);
			if("_result".equals(name)) {
				String resultFor = transactionToCommandMap.get(command.getTransactionId());
				if("connect".equals(resultFor)) {
					writeCommandExpectingResult(channel, Command.createStream());
				}
				else if("createStream".equals(resultFor)) {
					streamId = ((Double) command.getArg(0)).intValue();
					logger.info("streamId is confirmed:" + streamId);
				}
				return;
			}
			else if("onStatus".equals(name)) {
				@SuppressWarnings("unchecked")
				final Map<String, Object> temp = (Map<String, Object>)command.getArg(0);
				final String code = (String)temp.get("code");
				System.out.println(code);
				if("NetStream.Publish.Start".equals(code)) {
					logger.info("publish");
					if(publisher != null && !publisher.isStarted()) {
						publisher.start(channel, options.getStart(), options.getLength(), new ChunkSize(4096));
						return;
					}
				}
				else if("NetStream.Unpublish.Success".equals(code)) {
					logger.info("unpublish");
					ChannelFuture future = channel.write(Command.closeStream(streamId));
					future.addListener(ChannelFutureListener.CLOSE);
					return;
				}
			}
			break;
		}
		
		// COMMAND_AMF0をhookしてresultがcreateStreamのときに、次の処理をさせない。
		super.messageReceived(ctx, me);
		if(publisher != null && publisher.isStarted()) {
			publisher.fireNext(channel, 0);
		}
	}
}
