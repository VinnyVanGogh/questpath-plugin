package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;

/**
 * One line of an overlay, as a description rather than as drawing code.
 *
 * <p>Five overlays each drawing their own rectangles would drift apart within
 * a week: a different row height here, a different icon gap there, and the
 * display-mode rule implemented five times and four of them subtly wrong. So
 * an overlay says what its rows <em>are</em> and this decides what that looks
 * like. The one place display mode is honoured is here, which is the only
 * reason "just images" can be trusted to mean the same thing on all five.
 *
 * <p>Rows that have no picture to show -- an xp figure, a cap, a sentence --
 * draw their text in every mode, ICONS_ONLY included. Blanking them would
 * hide the rows that carry information no sprite could.
 */
abstract class OverlayRow
{
	/** How tall this row will be once drawn into the given width. */
	abstract int height(Graphics2D g, OverlayContext ctx, int width);

	/** Draws the row with its top-left at (x, y). */
	abstract void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width);

	/**
	 * The narrowest this row can be drawn without losing meaning. The panel
	 * takes the widest of these, so a row that genuinely needs room gets it.
	 */
	int minimumWidth(Graphics2D g, OverlayContext ctx)
	{
		return 0;
	}

	// ------------------------------------------------------------------
	// Factories. These are the whole vocabulary an overlay writes in.
	// ------------------------------------------------------------------

	/** Blank space, for separating groups. */
	static OverlayRow spacer(int pixels)
	{
		return new Spacer(pixels);
	}

	/** A small accent sub-heading inside a panel. */
	static OverlayRow header(String text)
	{
		return new Header(text);
	}

	/** The one thing the overlay is really saying, drawn large. */
	static OverlayRow hero(String text, Color color)
	{
		return new Hero(text, color);
	}

	/** A left label and a right value on one line. */
	static OverlayRow line(String left, String right, Color leftColor, Color rightColor)
	{
		return new Line(left, right, leftColor, rightColor);
	}

	/** A wrapped sentence, for the states that need explaining rather than counting. */
	static OverlayRow note(String text, Color color)
	{
		return new Note(text, color);
	}

	/** A labelled progress bar with a caption on the right of its label. */
	static OverlayRow bar(String label, String caption, double fraction, Color color)
	{
		return new Bar(label, caption, fraction, color);
	}

	/**
	 * An item: its sprite, its name, and a value on the right.
	 *
	 * @param itemId questpath's id for it, or 0 when it has none -- which
	 *     draws an empty slot rather than a wrong picture.
	 * @param held whether the account already has it, which dims the art and
	 *     is the only signal that survives icon-only mode.
	 */
	static OverlayRow item(int itemId, String name, String right, boolean held,
		Color nameColor, Color rightColor)
	{
		return new Item(itemId, name, right, held, nameColor, rightColor);
	}

	/** A skill's icon beside a label and a value. */
	static OverlayRow skill(Skill skill, String left, String right, Color leftColor, Color rightColor)
	{
		return new SkillLine(skill, left, right, leftColor, rightColor);
	}

	/**
	 * A wrapping grid of item slots -- the densest form, and what icon-only
	 * mode exists for.
	 */
	static OverlayRow grid(List<Cell> cells)
	{
		return new Grid(cells);
	}

	/** One slot in a {@link #grid}. */
	static final class Cell
	{
		private final int itemId;
		private final String count;
		private final boolean held;

		Cell(int itemId, String count, boolean held)
		{
			this.itemId = itemId;
			this.count = count;
			this.held = held;
		}
	}

	// ------------------------------------------------------------------

	private static final class Spacer extends OverlayRow
	{
		private final int pixels;

		private Spacer(int pixels)
		{
			this.pixels = pixels;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			return pixels;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
		}
	}

	private static final class Header extends OverlayRow
	{
		private final String text;

		private Header(String text)
		{
			this.text = text;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			return g.getFontMetrics().getHeight() + 3;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			FontMetrics metrics = g.getFontMetrics();
			String shown = OverlayTheme.truncate(metrics, text, width);
			OverlayTheme.shadowed(g, shown, x, y + metrics.getAscent(), OverlayTheme.ACCENT);
			int ruleY = y + metrics.getAscent() + 2;
			g.setColor(new Color(93, 82, 58, 120));
			g.drawLine(x + metrics.stringWidth(shown) + 4, ruleY, x + width, ruleY);
		}

		@Override
		int minimumWidth(Graphics2D g, OverlayContext ctx)
		{
			g.setFont(OverlayTheme.smallFont());
			return g.getFontMetrics().stringWidth(text) + 8;
		}
	}

	private static final class Hero extends OverlayRow
	{
		private final String text;
		private final Color color;

		private Hero(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.boldFont());
			return OverlayTheme.wrap(g.getFontMetrics(), text, width).size()
				* g.getFontMetrics().getHeight();
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(OverlayTheme.boldFont());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = y + metrics.getAscent();
			for (String piece : OverlayTheme.wrap(metrics, text, width))
			{
				OverlayTheme.shadowed(g, piece, x, baseline, color);
				baseline += metrics.getHeight();
			}
		}
	}

	private static final class Line extends OverlayRow
	{
		private final String left;
		private final String right;
		private final Color leftColor;
		private final Color rightColor;

		private Line(String left, String right, Color leftColor, Color rightColor)
		{
			this.left = left;
			this.right = right;
			this.leftColor = leftColor;
			this.rightColor = rightColor;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.font());
			return g.getFontMetrics().getHeight();
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(OverlayTheme.font());
			OverlayTheme.keyValue(g, x, y, width, left, leftColor, right, rightColor);
		}

		@Override
		int minimumWidth(Graphics2D g, OverlayContext ctx)
		{
			g.setFont(OverlayTheme.font());
			FontMetrics metrics = g.getFontMetrics();
			return Math.min(190, metrics.stringWidth(left)
				+ (right == null ? 0 : metrics.stringWidth(right) + 8));
		}
	}

	private static final class Note extends OverlayRow
	{
		private final String text;
		private final Color color;

		private Note(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			return OverlayTheme.wrap(g.getFontMetrics(), text, width).size()
				* g.getFontMetrics().getHeight();
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = y + metrics.getAscent();
			for (String piece : OverlayTheme.wrap(metrics, text, width))
			{
				OverlayTheme.shadowed(g, piece, x, baseline, color);
				baseline += metrics.getHeight();
			}
		}
	}

	private static final class Bar extends OverlayRow
	{
		private static final int BAR_HEIGHT = 7;

		private final String label;
		private final String caption;
		private final double fraction;
		private final Color color;

		private Bar(String label, String caption, double fraction, Color color)
		{
			this.label = label;
			this.caption = caption;
			this.fraction = fraction;
			this.color = color;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			return g.getFontMetrics().getHeight() + BAR_HEIGHT + 2;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(OverlayTheme.smallFont());
			FontMetrics metrics = g.getFontMetrics();
			OverlayTheme.keyValue(g, x, y, width, label, OverlayTheme.TEXT,
				caption, OverlayTheme.MUTED);
			OverlayTheme.bar(g, x, y + metrics.getHeight() + 1, width, BAR_HEIGHT, fraction, color);
		}
	}

	private static final class Item extends OverlayRow
	{
		private final int itemId;
		private final String name;
		private final String right;
		private final boolean held;
		private final Color nameColor;
		private final Color rightColor;

		private Item(int itemId, String name, String right, boolean held,
			Color nameColor, Color rightColor)
		{
			this.itemId = itemId;
			this.name = name;
			this.right = right;
			this.held = held;
			this.nameColor = nameColor;
			this.rightColor = rightColor;
		}

		/**
		 * Icon rows shrink to a text line when pictures are off, and to a
		 * single slot when words are -- except that an item questpath has no
		 * id for keeps its text in every mode, because the alternative is a
		 * blank row where the unidentifiable item used to be.
		 */
		private boolean drawsIcon(OverlayContext ctx)
		{
			return ctx.mode().showsIcons() && itemId > 0;
		}

		private boolean drawsText(OverlayContext ctx)
		{
			return ctx.mode().showsText() || !drawsIcon(ctx);
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.font());
			int textHeight = g.getFontMetrics().getHeight();
			if (!drawsIcon(ctx))
			{
				return textHeight;
			}
			return Math.max(textHeight, OverlayIcons.ITEM_HEIGHT - 6);
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			int rowHeight = height(g, ctx, width);
			int textX = x;

			if (drawsIcon(ctx))
			{
				int slotWidth = OverlayIcons.ITEM_WIDTH - 6;
				OverlayTheme.slot(g, x, y, slotWidth, rowHeight, held);
				BufferedImage image = ctx.icons().item(itemId);
				OverlayTheme.icon(g, image, x, y, slotWidth, rowHeight, held);
				if (!ctx.mode().showsText() && right != null && !right.isEmpty())
				{
					// Icon-only still has to say "three of these", or a list
					// of five items reads as five singles.
					OverlayTheme.stackCount(g, x, y, right, rightColor);
				}
				textX = x + slotWidth + 5;
			}

			if (!drawsText(ctx))
			{
				return;
			}

			g.setFont(OverlayTheme.font());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = y + (rowHeight - metrics.getHeight()) / 2 + metrics.getAscent();
			int room = width - (textX - x);
			int rightWidth = right == null || right.isEmpty() ? 0 : metrics.stringWidth(right);
			OverlayTheme.shadowed(g,
				OverlayTheme.truncate(metrics, name, room - (rightWidth == 0 ? 0 : rightWidth + 6)),
				textX, baseline, nameColor);
			if (rightWidth > 0)
			{
				OverlayTheme.shadowed(g, right, x + width - rightWidth, baseline, rightColor);
			}
		}
	}

	private static final class SkillLine extends OverlayRow
	{
		private final Skill skill;
		private final String left;
		private final String right;
		private final Color leftColor;
		private final Color rightColor;

		private SkillLine(Skill skill, String left, String right, Color leftColor, Color rightColor)
		{
			this.skill = skill;
			this.left = left;
			this.right = right;
			this.leftColor = leftColor;
			this.rightColor = rightColor;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(OverlayTheme.font());
			int textHeight = g.getFontMetrics().getHeight();
			return ctx.mode().showsIcons()
				? Math.max(textHeight, OverlayIcons.SKILL_SIZE + 2)
				: textHeight;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			int rowHeight = height(g, ctx, width);
			int textX = x;
			if (ctx.mode().showsIcons())
			{
				BufferedImage image = ctx.icons().skill(skill);
				if (image != null)
				{
					g.drawImage(image, x, y + (rowHeight - OverlayIcons.SKILL_SIZE) / 2, null);
				}
				textX = x + OverlayIcons.SKILL_SIZE + 4;
			}

			// The value is never dropped: a skill icon says which skill, and
			// nothing at all about the level, the cap or the cost -- which is
			// the entire content of these rows.
			g.setFont(OverlayTheme.font());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = y + (rowHeight - metrics.getHeight()) / 2 + metrics.getAscent();
			int room = width - (textX - x);
			int rightWidth = right == null || right.isEmpty() ? 0 : metrics.stringWidth(right);
			String shownLeft = ctx.mode().showsText() ? left : "";
			OverlayTheme.shadowed(g,
				OverlayTheme.truncate(metrics, shownLeft, room - (rightWidth == 0 ? 0 : rightWidth + 6)),
				textX, baseline, leftColor);
			if (rightWidth > 0)
			{
				OverlayTheme.shadowed(g, right, x + width - rightWidth, baseline, rightColor);
			}
		}
	}

	private static final class Grid extends OverlayRow
	{
		private static final int SLOT_W = OverlayIcons.ITEM_WIDTH - 4;
		private static final int SLOT_H = OverlayIcons.ITEM_HEIGHT - 4;
		private static final int GAP = 2;

		private final List<Cell> cells;

		private Grid(List<Cell> cells)
		{
			this.cells = cells == null ? new ArrayList<>() : cells;
		}

		private int columns(int width)
		{
			return Math.max(1, (width + GAP) / (SLOT_W + GAP));
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			if (cells.isEmpty())
			{
				return 0;
			}
			int columns = columns(width);
			int rows = (cells.size() + columns - 1) / columns;
			return rows * (SLOT_H + GAP) - GAP;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			int columns = columns(width);
			for (int index = 0; index < cells.size(); index++)
			{
				Cell cell = cells.get(index);
				int slotX = x + (index % columns) * (SLOT_W + GAP);
				int slotY = y + (index / columns) * (SLOT_H + GAP);
				OverlayTheme.slot(g, slotX, slotY, SLOT_W, SLOT_H, cell.held);
				OverlayTheme.icon(g, ctx.icons().item(cell.itemId),
					slotX, slotY, SLOT_W, SLOT_H, cell.held);
				OverlayTheme.stackCount(g, slotX, slotY, cell.count,
					cell.held ? OverlayTheme.GOOD : OverlayTheme.TEXT);
			}
		}

		@Override
		int minimumWidth(Graphics2D g, OverlayContext ctx)
		{
			return cells.isEmpty() ? 0 : SLOT_W;
		}
	}
}
