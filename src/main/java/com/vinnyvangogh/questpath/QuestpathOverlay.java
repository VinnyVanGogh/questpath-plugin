package com.vinnyvangogh.questpath;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The chrome, the layout and the rules every questpath overlay shares.
 *
 * <p>A subclass says three things: whether it is switched on, what it is
 * called, and what rows it wants. Everything after that -- measuring, sizing,
 * the panel, the title, honouring the display mode, refusing to draw when
 * there is nothing true to say -- happens once, here. That is deliberate:
 * five overlays written independently would be five slightly different
 * panels, and the display-mode rule implemented five times would be wrong in
 * at least one of them.
 *
 * <p>Drawn on {@link OverlayLayer#ABOVE_SCENE} and positioned TOP_LEFT by
 * default, but every one is movable, so where they actually sit is the
 * player's business and RuneLite remembers it.
 */
abstract class QuestpathOverlay extends Overlay
{
	/**
	 * The narrowest a panel is ever drawn, whatever the width setting says.
	 *
	 * Below this a title truncates and the panel stops being readable at all,
	 * which is not a trade worth offering.
	 */
	private static final int MIN_WIDTH = 140;

	private final QuestpathPlugin plugin;

	/**
	 * Protected so a subclass can read its own options.
	 *
	 * Panels differ in what they offer -- which build rules, whether to list
	 * held items -- and threading each of those through rows() as a parameter
	 * would put every panel's options in every panel's signature.
	 */
	protected final QuestpathConfig config;
	private final OverlayIcons icons;
	private final String title;

	QuestpathOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons, String title)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.icons = icons;
		this.title = title;

		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
		setResizable(false);
		setMovable(true);
		setSnappable(true);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "questpath " + title);
	}

	/** Whether this overlay's own config toggle is on. */
	abstract boolean enabled(QuestpathConfig config);

	/**
	 * This panel's own width setting.
	 *
	 * Per panel because the panels are not alike: a route preview is happy
	 * narrow and a sixty-eight item shopping list is not, and forcing both to
	 * one number means picking which of the two to make worse. Defaults to
	 * MATCH_GLOBAL so the shared setting keeps working for anyone who does not
	 * care.
	 */
	abstract OverlayWidth width(QuestpathConfig config);

	/**
	 * What to draw, or an empty list for "nothing to say right now".
	 *
	 * An empty list hides the panel entirely rather than drawing an empty
	 * one. A subclass with a reason to say nothing should say the reason as a
	 * note row instead -- a blank panel and a panel that has not been told
	 * anything look the same, and only the second is worth screen space.
	 */
	abstract List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx);

	@Override
	public Dimension render(Graphics2D g)
	{
		if (config == null || !enabled(config))
		{
			return null;
		}
		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		if (snapshot == null)
		{
			// Logged out, or nothing read yet. Not an empty overlay: there is
			// no account to be empty about.
			return null;
		}

		OverlayContext ctx = new OverlayContext(icons, config.overlayDisplayMode(),
			config.overlayCompactText(), config.overlayShowOverflow(),
			config.overlayListLength());
		List<OverlayRow> rows = rows(snapshot, ctx);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}
		rows = arrange(rows, ctx);
		if (rows.isEmpty())
		{
			return null;
		}

		OverlayTheme.prepare(g);

		int width = widthFor();
		int contentWidth = width - OverlayTheme.PAD * 2;

		int height = OverlayTheme.PAD;
		g.setFont(ctx.boldFont());
		int titleHeight = g.getFontMetrics().getAscent() + 7;
		height += titleHeight;
		for (OverlayRow row : rows)
		{
			height += row.height(g, ctx, contentWidth) + OverlayTheme.ROW_GAP;
		}
		height += OverlayTheme.PAD - OverlayTheme.ROW_GAP;

		OverlayTheme.panel(g, width, height);

		int y = OverlayTheme.PAD;
		y += OverlayTheme.title(g, OverlayTheme.PAD, y, contentWidth, title);
		for (OverlayRow row : rows)
		{
			int rowHeight = row.height(g, ctx, contentWidth);
			row.draw(g, ctx, OverlayTheme.PAD, y, contentWidth);
			y += rowHeight + OverlayTheme.ROW_GAP;
		}

		return new Dimension(width, height);
	}

	/**
	 * Rewrites the row list for the mode and settings before anything is
	 * measured.
	 *
	 * Two passes, both deliberately here rather than in the five overlays.
	 * Dropping overflow notes in one place is what makes the setting mean the
	 * same thing everywhere; packing icon rows into grids in one place is what
	 * makes "just images" actually denser than "just text" rather than the
	 * same height with less in it.
	 */
	private static List<OverlayRow> arrange(List<OverlayRow> rows, OverlayContext ctx)
	{
		List<OverlayRow> kept = new ArrayList<>(rows.size());
		for (OverlayRow row : rows)
		{
			if (row.isOverflow() && !ctx.showOverflow())
			{
				continue;
			}
			kept.add(row);
		}

		if (!ctx.mode().showsText() && ctx.mode().showsIcons())
		{
			return packIcons(kept);
		}
		return packColumns(kept);
	}

	/**
	 * Icon-only mode: consecutive item rows become one wrapping grid.
	 *
	 * Left alone, an item row here is a single slot on its own line, so twenty
	 * of them cost the height of the text version while carrying less -- which
	 * is the opposite of what the mode is for.
	 */
	private static List<OverlayRow> packIcons(List<OverlayRow> rows)
	{
		List<OverlayRow> packed = new ArrayList<>(rows.size());
		List<OverlayRow.Cell> run = new ArrayList<>();
		for (OverlayRow row : rows)
		{
			OverlayRow.Cell cell = row.asCell();
			if (cell != null)
			{
				run.add(cell);
				continue;
			}
			flushCells(packed, run);
			packed.add(row);
		}
		flushCells(packed, run);
		return packed;
	}

	/**
	 * Text modes: a long run of one-line rows is laid side by side instead of
	 * stacked.
	 *
	 * Sixty-eight items in one column is a stripe down the screen whatever
	 * font it is set in. Short runs are left alone -- two columns of three
	 * items each is harder to read than six stacked, and the point is density
	 * where density is the problem.
	 */
	private static List<OverlayRow> packColumns(List<OverlayRow> rows)
	{
		List<OverlayRow> packed = new ArrayList<>(rows.size());
		List<OverlayRow> run = new ArrayList<>();
		for (OverlayRow row : rows)
		{
			if (row.isColumnisable())
			{
				run.add(row);
				continue;
			}
			flushColumns(packed, run);
			packed.add(row);
		}
		flushColumns(packed, run);
		return packed;
	}

	/** Below this many rows, columns cost more legibility than they buy. */
	private static final int MIN_ROWS_FOR_COLUMNS = 6;

	private static void flushCells(List<OverlayRow> into, List<OverlayRow.Cell> run)
	{
		if (run.isEmpty())
		{
			return;
		}
		into.add(OverlayRow.grid(new ArrayList<>(run)));
		run.clear();
	}

	private static void flushColumns(List<OverlayRow> into, List<OverlayRow> run)
	{
		if (run.isEmpty())
		{
			return;
		}
		if (run.size() < MIN_ROWS_FOR_COLUMNS)
		{
			into.addAll(run);
		}
		else
		{
			into.add(OverlayRow.columns(new ArrayList<>(run)));
		}
		run.clear();
	}

	/**
	 * The panel's width: exactly what the player asked for.
	 *
	 * Fixed rather than fitted to the content, and that is the point. Five
	 * panels that each sized themselves to their longest row would never line
	 * up with each other, and the widest one would be whichever happened to
	 * hold a long quest name that day -- a layout that moves under you is
	 * worse than one that truncates. Past this width rows truncate, which is
	 * what OverlayTheme.truncate is for, and the way to see more is to choose
	 * a wider setting.
	 */
	private int widthFor()
	{
		return Math.max(MIN_WIDTH, width(config).resolve(config.overlayWidth()));
	}
}
