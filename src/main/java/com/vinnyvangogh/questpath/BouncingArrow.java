package com.vinnyvangogh.questpath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;

/**
 * The arrow drawn over a needed item on the floor: a downward-pointing arrow
 * that bobs a few pixels, so the eye finds it among a screen of loot.
 *
 * <p>Drawn by {@link GroundItemOverlay} rather than set with
 * {@code client.setHintArrow}: the client has one hint arrow, and other
 * plugins (Quest Helper among them) and the game itself take it.
 */
final class BouncingArrow
{
	/** How far the arrow travels up and down, in pixels. */
	static final int BOUNCE_PX = 6;
	/** One full bob, in milliseconds. */
	static final long PERIOD_MILLIS = 900;
	static final int HEAD_WIDTH = 14;
	static final int HEAD_HEIGHT = 10;
	static final int SHAFT_WIDTH = 6;
	static final int SHAFT_HEIGHT = 8;

	private BouncingArrow()
	{
	}

	/** How high the arrow sits above its rest at this moment, 0 to {@link #BOUNCE_PX}. */
	static int lift(long millis)
	{
		final double phase = (Math.floorMod(millis, PERIOD_MILLIS)) / (double) PERIOD_MILLIS;
		return (int) Math.round(BOUNCE_PX * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase)));
	}

	/**
	 * The arrow's outline with its tip at ({@code x}, {@code tipY}) less the
	 * bounce: head and shaft as one polygon.
	 */
	static Polygon shape(int x, int tipY, long millis)
	{
		final int tip = tipY - lift(millis);
		final int headTop = tip - HEAD_HEIGHT;
		final int shaftTop = headTop - SHAFT_HEIGHT;
		final int hw = HEAD_WIDTH / 2;
		final int sw = SHAFT_WIDTH / 2;
		return new Polygon(
			new int[]{x, x + hw, x + sw, x + sw, x - sw, x - sw, x - hw},
			new int[]{tip, headTop, headTop, shaftTop, shaftTop, headTop, headTop},
			7);
	}

	/** Fills the arrow in {@code colour} with a dark outline, readable on grass and sand alike. */
	static void draw(Graphics2D g, int x, int tipY, long millis, Color colour)
	{
		final Polygon arrow = shape(x, tipY, millis);
		final Stroke before = g.getStroke();
		g.setColor(colour);
		g.fillPolygon(arrow);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(Color.BLACK);
		g.drawPolygon(arrow);
		g.setStroke(before);
	}
}
