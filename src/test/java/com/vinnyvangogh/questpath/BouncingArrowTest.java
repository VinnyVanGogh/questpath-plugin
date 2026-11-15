package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BouncingArrowTest
{
	@Test
	public void bobsWithinItsRangeAndRepeats()
	{
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (long t = 0; t < BouncingArrow.PERIOD_MILLIS; t += 10)
		{
			int lift = BouncingArrow.lift(t);
			lowest = Math.min(lowest, lift);
			highest = Math.max(highest, lift);
			assertEquals("periodic at " + t, lift, BouncingArrow.lift(t + 3 * BouncingArrow.PERIOD_MILLIS));
		}
		assertEquals(0, lowest);
		assertEquals(BouncingArrow.BOUNCE_PX, highest);
	}

	@Test
	public void pointsDownWithItsTipAtTheAnchor()
	{
		Polygon arrow = BouncingArrow.shape(100, 50, 0);
		assertEquals(100, arrow.xpoints[0]);
		assertEquals("tip at rest", 50, arrow.ypoints[0]);
		for (int i = 1; i < arrow.npoints; i++)
		{
			assertTrue("everything above the tip", arrow.ypoints[i] < arrow.ypoints[0]);
		}
		Polygon raised = BouncingArrow.shape(100, 50, BouncingArrow.PERIOD_MILLIS / 2);
		assertEquals("tip lifted mid-bob", 50 - BouncingArrow.BOUNCE_PX, raised.ypoints[0]);
	}

	/**
	 * Headless shot: a floor tile with its outline and label as {@link
	 * GroundItemOverlay} draws them, and the arrow over the label, at rest and
	 * mid-bob. Written to build/screenshots/floor-item-arrow.png for a person
	 * to look at; the assertion only checks the arrow's colour landed above the
	 * label.
	 */
	@Test
	public void headlessShotOfTheArrowOverAFloorItem() throws IOException
	{
		BufferedImage image = new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x4a5d23));
		g.fillRect(0, 0, 320, 160);
		Color mark = OverlayTheme.WARN;
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		int[] centres = {90, 230};
		long[] moments = {0, BouncingArrow.PERIOD_MILLIS / 2};
		int restTip = 0;
		for (int i = 0; i < centres.length; i++)
		{
			int cx = centres[i];
			Polygon tile = new Polygon(new int[]{cx - 40, cx + 40, cx + 30, cx - 30}, new int[]{140, 140, 110, 110}, 4);
			g.setColor(mark);
			g.draw(tile);
			String label = "Bucket (1)";
			int width = g.getFontMetrics().stringWidth(label);
			int baseline = 95;
			g.setColor(Color.BLACK);
			g.drawString(label, cx - width / 2 + 1, baseline + 1);
			g.setColor(mark);
			g.drawString(label, cx - width / 2, baseline);
			int tip = baseline - g.getFontMetrics().getAscent() - 3;
			restTip = i == 0 ? tip : restTip;
			BouncingArrow.draw(g, cx, tip, moments[i], mark);
		}
		g.dispose();

		File out = new File("build/screenshots/floor-item-arrow.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);

		assertEquals("arrow fill above the resting label", mark.getRGB() & 0xffffff,
			image.getRGB(90, restTip - BouncingArrow.HEAD_HEIGHT / 2) & 0xffffff);
	}
}
