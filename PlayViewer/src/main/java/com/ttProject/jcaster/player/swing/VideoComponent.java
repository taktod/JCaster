package com.ttProject.jcaster.player.swing;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * 映像データを表示するコンポーネント
 * @author taktod
 */
public class VideoComponent extends JComponent {
	private static final long serialVersionUID = -4250506002325710194L;
	private Image image;
	public void setImage(Image image) {
		SwingUtilities.invokeLater(new ImageRunnable(image));
	}
	private class ImageRunnable implements Runnable {
		private final Image newImage;
		public ImageRunnable(Image newImage) {
			super();
			this.newImage = newImage;
		}
		@Override
		public void run() {
			VideoComponent.this.image = newImage;
			Dimension newSize = new Dimension(image.getWidth(null), image.getHeight(null));
			if(!newSize.equals(getSize())) {
				// サイズの書き換えを実行します。
				setSize(newSize);
				setPreferredSize(newSize);
			}
			repaint();
		}
	}
	public synchronized void paint(Graphics g) {
		if(image != null) {
			g.drawImage(image, 0, 0, this);
		}
	}
}
