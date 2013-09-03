package com.ttProject.jcast.viewer.log4j;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

import com.ttProject.jcast.viewer.ConsoleViewer;

public class ConsoleViewerAppender extends AppenderSkeleton {
	public ConsoleViewerAppender() {
		ConsoleViewer.setAppender(this);
	}
	public ConsoleViewerAppender(final Layout layout) {
		this();
		setLayout(layout);
	}
	@Override
	public void close() {
		
	}
	@Override
	public boolean requiresLayout() {
		// layout必須にしてみた。
		return true;
	}
	@Override
	protected void append(LoggingEvent event) {
		if(layout == null) {
			throw new RuntimeException("layoutが未設定です。");
		}
		else {
			ConsoleViewer.append(layout.format(event));
		}
	}
}
