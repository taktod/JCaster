package com.ttProject.jcaster.flazr.core;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import com.flazr.rtmp.RtmpMessage;
import com.flazr.rtmp.RtmpPublisher;
import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.message.Command;

/**
 * 独自の配信制御
 * @author taktod
 * rtmpPublisherも自作して、内部timerを１つにしてしまえばもっと動作は良くなると思われます。
 */
public class MyRtmpPublisher extends RtmpPublisher {
	/** netty用のtimer */
	private Timer timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS);
	/** 動作時のstreamId */
	private int streamId;
	/** thread解放のタイミングコントロール */
	private int counter = 0;
	/**
	 * コンストラクタ
	 * @param reader
	 * @param streamId
	 * @param bufferDuration
	 * @param useSharedTimer
	 * @param aggregateModeEnabled
	 */
	public MyRtmpPublisher(RtmpReader reader, int streamId, int bufferDuration,
			boolean useSharedTimer, boolean aggregateModeEnabled) {
		super(reader, streamId, bufferDuration, useSharedTimer, aggregateModeEnabled);
		this.streamId = streamId;
	}
	/**
	 * 停止時の転送メッセージ
	 */
	@Override
	protected RtmpMessage[] getStopMessages(long paramLong) {
		// とりあえず閉じるときにunpublishの命令をなげておく。
		return new RtmpMessage[]{Command.unpublish(streamId)};
	}
	/**
	 * 次の処理に進める(hookしてthreadを解放してやることで転送が止まる問題に対処したい。)
	 */
	public void fireNext(final Channel channel, final long delay) {
		if(counter > 2) {
			timer.newTimeout(new TimerTask() {
				/**
				 * 実行処理
				 */
				@Override
				public void run(Timeout timeout) throws Exception {
					fireNext2(channel, delay);
				}
			}, 10, TimeUnit.MILLISECONDS);
		}
		else {
			counter ++;
			super.fireNext(channel, delay);
		}
	}
	/**
	 * 次の処理にすすめるための中間処理(timeTaskでそのままsuper.fireNextが呼び出せないため)
	 * @param channel
	 * @param delay
	 */
	private void fireNext2(final Channel channel, final long delay) {
		super.fireNext(channel, delay);
	}
}
