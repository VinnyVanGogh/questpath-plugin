package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

/**
 * One look, shared by every questpath overlay.
 *
 * <p>Five overlays drawn independently would be five slightly different
 * panels: a different amber, a different border, a different idea of padding.
 * Every colour, font and primitive an overlay draws with lives here instead,
 * so they read as one system on screen rather than five plugins that happen
 * to be installed together.
 *
 * <p>The idiom is the game's, not the web app's. questpath the site has nine
 * themes; the client has exactly one look and a player who has spent an hour
 * in it. So: the game's own fonts, a dark stone ground, a thin gold edge, and
 * inventory-style slots behind item art. The one liberty is the accent, an
 * amber close enough to RuneLite's own brand orange to sit beside the client's
 * chrome without arguing with it.
 */
final class OverlayTheme
{
	private OverlayTheme()
	{
	}

	/** The panel ground. Translucent, because an overlay sits over the game. */
	static final Color GROUND = new Color(18, 16, 13, 218);
	/** A slightly lighter wash across the top, so the panel has a light source. */
	static final Color GROUND_TOP = new Color(38, 34, 27, 218);
	/** The outer edge: the worn gold of an OSRS interface border. */
	static final Color EDGE = new Color(93, 82, 58);
	/** The inner edge, one pixel in, which is what gives the border depth. */
	static final Color EDGE_INNER = new Color(10, 9, 7, 180);

	/** questpath's accent. Headings, rules, and the head of the route. */
	static final Color ACCENT = new Color(224, 138, 30);
	/** Body text: parchment rather than white, which glares over dark scenery. */
	static final Color TEXT = new Color(230, 226, 214);
	/** Secondary text. Present, readable, clearly not the answer. */
	static final Color MUTED = new Color(154, 147, 132);

	/** Done, held, met. */
	static final Color GOOD = new Color(76, 195, 128);
	/** Needs work, but work is possible. */
	static final Color WARN = new Color(232, 177, 63);
	/** Off the table, or a cap reached. */
	static final Color BAD = new Color(224, 79, 79);

	/** The empty half of a progress bar. */
	static final Color BAR_GROUND = new Color(0, 0, 0, 140);

	/** An inventory slot's fill and edge, drawn behind item art. */
	static final Color SLOT = new Color(0, 0, 0, 110);
	static final Color SLOT_EDGE = new Color(70, 63, 47);
	/** A slot for something already held, which the eye should skip. */
	static final Color SLOT_HELD = new Color(30, 62, 42, 150);
	static final Color SLOT_HELD_EDGE = new Color(64, 122, 86);

	/** Padding inside the panel edge. */
	static final int PAD = 6;
	/** The gap between one row and the next. */
	static final int ROW_GAP = 2;

	static Font font()
	{
		return FontManager.getRunescapeFont();
	}

	static Font smallFont()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font boldFont()
	{
		return FontManager.getRunescapeBoldFont();
	}

	/**
	 * Turns on the rendering hints the rest of this class assumes.
	 *
	 * Text antialiasing on, image interpolation off: the fonts are vector and
	 * want smoothing, the item sprites are pixel art authored at the size they
	 * are drawn and smoothing them turns a crisp bucket into a smear.
	 */
	static void prepare(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
	}

	/**
	 * The panel behind everything: ground, gradient, and a two-tone edge.
	 *
	 * Drawn at (0,0) because an overlay's graphics are already translated to
	 * its own corner by the overlay renderer.
	 */
	static void panel(Graphics2D g, int width, int height)
	{
		g.setPaint(new GradientPaint(0, 0, GROUND_TOP, 0, Math.max(1, height), GROUND));
		g.fillRect(0, 0, width, height);
		g.setPaint(null);

		g.setColor(EDGE_INNER);
		g.drawRect(1, 1, width - 3, height - 3);
		g.setColor(EDGE);
		g.drawRect(0, 0, width - 1, height - 1);
	}

	/**
	 * The title strip: the overlay's name in the accent, over a hairline rule.
	 *
	 * @return the height it occupied.
	 */
	static int title(Graphics2D g, int x, int y, int width, String text)
	{
		g.setFont(boldFont());
		FontMetrics metrics = g.getFontMetrics();
		int baseline = y + metrics.getAscent();
		shadowed(g, text, x, baseline, ACCENT);

		int ruleY = baseline + 3;
		g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 70));
		g.drawLine(x, ruleY, x + width, ruleY);
		return (ruleY - y) + 4;
	}

	/**
	 * Text with the game's own drop shadow.
	 *
	 * Not decoration: an overlay is drawn over whatever the player happens to
	 * be standing in, and light text on a snowfield is unreadable without it.
	 */
	static void shadowed(Graphics2D g, String text, int x, int baseline, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, baseline + 1);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	/**
	 * A left-aligned label and a right-aligned value on one line.
	 *
	 * The right half is dropped rather than overlapped when the two cannot
	 * both fit: a value printed over a name is two unreadable strings where
	 * one readable one would do.
	 */
	static void keyValue(Graphics2D g, int x, int y, int width,
		String left, Color leftColor, String right, Color rightColor)
	{
		FontMetrics metrics = g.getFontMetrics();
		int baseline = y + metrics.getAscent();
		int rightWidth = right == null || right.isEmpty() ? 0 : metrics.stringWidth(right);
		int room = width - (rightWidth == 0 ? 0 : rightWidth + 6);

		shadowed(g, truncate(metrics, left, room), x, baseline, leftColor);
		if (rightWidth > 0 && room > 12)
		{
			shadowed(g, right, x + width - rightWidth, baseline, rightColor);
		}
	}

	/**
	 * A filled bar with its own hairline frame.
	 *
	 * @param fraction clamped to 0..1, so a caller that computed 21 of 20 gets
	 *     a full bar rather than one drawn past its own frame.
	 */
	static void bar(Graphics2D g, int x, int y, int width, int height, double fraction, Color color)
	{
		double clamped = Math.max(0, Math.min(1, fraction));
		g.setColor(BAR_GROUND);
		g.fillRect(x, y, width, height);
		int filled = (int) Math.round((width - 2) * clamped);
		if (filled > 0)
		{
			g.setColor(color);
			g.fillRect(x + 1, y + 1, filled, height - 2);
		}
		g.setColor(new Color(0, 0, 0, 170));
		g.drawRect(x, y, width - 1, height - 1);
	}

	/**
	 * The inventory-slot frame item art sits in.
	 *
	 * Held items get a green slot rather than a green name: the slot is what
	 * the eye lands on first in icon mode, and in that mode there is no name.
	 */
	static void slot(Graphics2D g, int x, int y, int width, int height, boolean held)
	{
		g.setColor(held ? SLOT_HELD : SLOT);
		g.fillRect(x, y, width, height);
		g.setColor(held ? SLOT_HELD_EDGE : SLOT_EDGE);
		g.drawRect(x, y, width - 1, height - 1);
	}

	/**
	 * An item sprite centred in a slot, dimmed when the account does not hold
	 * it.
	 *
	 * Dimmed rather than hidden: what is missing is the point of every list
	 * this appears in, and the difference between "you have this" and "you do
	 * not" has to survive icon-only mode, where there is no text to carry it.
	 */
	static void icon(Graphics2D g, BufferedImage image, int x, int y, int width, int height, boolean dim)
	{
		if (image == null)
		{
			return;
		}
		int drawX = x + (width - image.getWidth()) / 2;
		int drawY = y + (height - image.getHeight()) / 2;
		if (!dim)
		{
			g.drawImage(image, drawX, drawY, null);
			return;
		}
		java.awt.Composite before = g.getComposite();
		g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.42f));
		g.drawImage(image, drawX, drawY, null);
		g.setComposite(before);
	}

	/**
	 * A small count in the corner of a slot, the way the game numbers a stack.
	 */
	static void stackCount(Graphics2D g, int slotX, int slotY, String text, Color color)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		Font before = g.getFont();
		g.setFont(smallFont());
		FontMetrics metrics = g.getFontMetrics();
		shadowed(g, text, slotX + 2, slotY + metrics.getAscent(), color);
		g.setFont(before);
	}

	/**
	 * Cuts a string to fit, with an ellipsis, or returns it untouched.
	 *
	 * Never returns something wider than {@code room}. A name that overflows
	 * its panel does not wrap, it draws over the border and off the edge.
	 */
	static String truncate(FontMetrics metrics, String text, int room)
	{
		if (text == null)
		{
			return "";
		}
		if (room <= 0)
		{
			return "";
		}
		if (metrics.stringWidth(text) <= room)
		{
			return text;
		}
		String ellipsis = "..";
		int allowance = room - metrics.stringWidth(ellipsis);
		if (allowance <= 0)
		{
			return "";
		}
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end)) > allowance)
		{
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	/**
	 * Splits text into lines that each fit the given width.
	 *
	 * Word-wrapping rather than truncation, for the few places that carry a
	 * sentence instead of a value -- "no route saved" has to be readable in
	 * full or it is not worth the pixels.
	 */
	static java.util.List<String> wrap(FontMetrics metrics, String text, int width)
	{
		java.util.List<String> lines = new java.util.ArrayList<>();
		if (text == null || text.isEmpty() || width <= 0)
		{
			return lines;
		}
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" "))
		{
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (metrics.stringWidth(candidate) <= width || current.length() == 0)
			{
				current.setLength(0);
				current.append(candidate);
			}
			else
			{
				lines.add(current.toString());
				current.setLength(0);
				current.append(word);
			}
		}
		if (current.length() > 0)
		{
			lines.add(current.toString());
		}
		return lines;
	}
}
