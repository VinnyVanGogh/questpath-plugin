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
	/** The vertical gap between stacked rows, matching the panel's own. */
	private static final int ROW_GAP = OverlayTheme.ROW_GAP;

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

	/**
	 * The "...and 58 more" line at the end of a capped list.
	 *
	 * A distinct kind rather than a note, so it can be switched off in one
	 * place. Truncating a list without saying so would leave it claiming to be
	 * complete, which is why this exists at all -- but on a panel deliberately
	 * capped short it is also a line of chrome per section, and a player who
	 * already knows the lists are capped should be able to reclaim those rows.
	 * The cap does not move when it is hidden; only the explanation goes.
	 */
	static OverlayRow overflow(String text)
	{
		return new Overflow(text);
	}

	/** Whether the panel may drop this row when overflow notes are off. */
	boolean isOverflow()
	{
		return false;
	}

	/**
	 * The grid cell this row collapses to when only pictures are being drawn,
	 * or null when it has no picture to collapse to.
	 *
	 * This is what makes "just images" actually dense. Left alone, an item row
	 * in icon-only mode is a single 30px slot on its own line and twenty of
	 * them is a stripe down the screen -- the same height as the text version
	 * for a fraction of the information. The panel packs consecutive rows that
	 * answer this into one wrapping grid instead, which is the whole point of
	 * the mode.
	 */
	Cell asCell()
	{
		return null;
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

	/**
	 * A run of one-line rows laid side by side instead of stacked.
	 *
	 * Sixty-eight items in one column is a stripe down the screen whatever
	 * font it is set in; the same sixty-eight in three columns is a block you
	 * can actually read at a glance. The panel decides how many columns fit
	 * from its own width, so this is one row as far as everything above is
	 * concerned.
	 */
	static OverlayRow columns(List<OverlayRow> rows)
	{
		return new Columns(rows);
	}

	/**
	 * Whether this row is a single line of text that could sit in a column
	 * beside its neighbours.
	 *
	 * Only simple rows qualify. A heading, a bar or a wrapped note is a
	 * full-width thing whose meaning depends on spanning the panel, and
	 * columnising one would break the grouping it exists to provide.
	 */
	boolean isColumnisable()
	{
		return false;
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
			g.setFont(ctx.smallFont());
			return g.getFontMetrics().getHeight() + 3;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(ctx.smallFont());
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
			g.setFont(ctx.smallFont());
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
		@Override
		boolean isColumnisable()
		{
			return true;
		}

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
			g.setFont(ctx.font());
			return g.getFontMetrics().getHeight();
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(ctx.font());
			OverlayTheme.keyValue(g, x, y, width, left, leftColor, right, rightColor);
		}

		@Override
		int minimumWidth(Graphics2D g, OverlayContext ctx)
		{
			g.setFont(ctx.font());
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
			g.setFont(ctx.smallFont());
			return OverlayTheme.wrap(g.getFontMetrics(), text, width).size()
				* g.getFontMetrics().getHeight();
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(ctx.smallFont());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = y + metrics.getAscent();
			for (String piece : OverlayTheme.wrap(metrics, text, width))
			{
				OverlayTheme.shadowed(g, piece, x, baseline, color);
				baseline += metrics.getHeight();
			}
		}
	}

	/**
	 * A note that says a list was cut short. See {@link #overflow(String)}.
	 */
	private static final class Overflow extends OverlayRow
	{
		private final Note inner;

		private Overflow(String text)
		{
			this.inner = new Note(text, OverlayTheme.MUTED);
		}

		@Override
		boolean isOverflow()
		{
			return true;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			return inner.height(g, ctx, width);
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			inner.draw(g, ctx, x, y, width);
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
			g.setFont(ctx.smallFont());
			return g.getFontMetrics().getHeight() + BAR_HEIGHT + 2;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			g.setFont(ctx.smallFont());
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

		/**
		 * An item row is one line tall whether or not it carries a sprite, so
		 * it sits in a column happily -- the icon simply rides along at the
		 * head of its own column.
		 */
		@Override
		boolean isColumnisable()
		{
			return true;
		}

		/**
		 * Collapsible only when there is a sprite to collapse to. An item
		 * questpath could not identify keeps its own row and its text, for
		 * the same reason it keeps its text in every mode.
		 */
		@Override
		Cell asCell()
		{
			return itemId > 0 ? new Cell(itemId, right, held) : null;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			g.setFont(ctx.font());
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

			g.setFont(ctx.font());
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
			g.setFont(ctx.font());
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
			g.setFont(ctx.font());
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

	/**
	 * Lays a run of one-line rows out in as many columns as the panel's width
	 * allows, filling down each column before starting the next.
	 *
	 * Column-major rather than row-major on purpose: these runs are sorted
	 * lists -- what you are short of first, cheapest step first -- and reading
	 * order has to preserve that. Filling across would scatter a sorted list
	 * over three columns and make the ordering meaningless.
	 */
	private static final class Columns extends OverlayRow
	{
		/**
		 * Three at most. A fourth column at any width this panel can be
		 * leaves each one too narrow to hold a quest name, and a grid of
		 * truncations carries less than one readable column.
		 */
		private static final int MAX_COLUMNS = 3;

		/** Below this a column would be all ellipsis. */
		private static final int MIN_COLUMN_WIDTH = 96;

		private static final int GUTTER = 8;

		private final List<OverlayRow> rows;

		private Columns(List<OverlayRow> rows)
		{
			this.rows = rows == null ? new ArrayList<>() : rows;
		}

		private int columnCount(int width)
		{
			int fits = (width + GUTTER) / (MIN_COLUMN_WIDTH + GUTTER);
			return Math.max(1, Math.min(Math.min(MAX_COLUMNS, fits), rows.size()));
		}

		private int perColumn(int columns)
		{
			return (rows.size() + columns - 1) / columns;
		}

		/**
		 * Every row in a run is one line of the same font, so one row's height
		 * is every row's height. Measured rather than assumed so a mixed run
		 * still lays out sanely.
		 */
		private int rowHeight(Graphics2D g, OverlayContext ctx, int columnWidth)
		{
			int tallest = 0;
			for (OverlayRow row : rows)
			{
				tallest = Math.max(tallest, row.height(g, ctx, columnWidth));
			}
			return tallest;
		}

		@Override
		int height(Graphics2D g, OverlayContext ctx, int width)
		{
			if (rows.isEmpty())
			{
				return 0;
			}
			int columns = columnCount(width);
			int columnWidth = (width - GUTTER * (columns - 1)) / columns;
			int perColumn = perColumn(columns);
			return perColumn * (rowHeight(g, ctx, columnWidth) + ROW_GAP) - ROW_GAP;
		}

		@Override
		void draw(Graphics2D g, OverlayContext ctx, int x, int y, int width)
		{
			if (rows.isEmpty())
			{
				return;
			}
			int columns = columnCount(width);
			int columnWidth = (width - GUTTER * (columns - 1)) / columns;
			int perColumn = perColumn(columns);
			int rowHeight = rowHeight(g, ctx, columnWidth);

			for (int index = 0; index < rows.size(); index++)
			{
				int column = index / perColumn;
				int position = index % perColumn;
				rows.get(index).draw(g, ctx,
					x + column * (columnWidth + GUTTER),
					y + position * (rowHeight + ROW_GAP),
					columnWidth);
			}
		}

		@Override
		int minimumWidth(Graphics2D g, OverlayContext ctx)
		{
			return rows.isEmpty() ? 0 : MIN_COLUMN_WIDTH;
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
