package com.vinnyvangogh.questpath;

import java.awt.Dimension;
import java.awt.Graphics2D;
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
	 * The width a panel is drawn at unless its own rows demand more.
	 *
	 * Chosen to sit comfortably beside a fixed-mode viewport without covering
	 * the minimap or the chat. Rows that need more say so through
	 * minimumWidth, and the panel grows rather than truncating them.
	 */
	private static final int BASE_WIDTH = 168;
	private static final int MAX_WIDTH = 260;

	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
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

		OverlayContext ctx = new OverlayContext(icons, config.overlayDisplayMode());
		List<OverlayRow> rows = rows(snapshot, ctx);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}

		OverlayTheme.prepare(g);

		int width = widthFor(g, ctx, rows);
		int contentWidth = width - OverlayTheme.PAD * 2;

		int height = OverlayTheme.PAD;
		g.setFont(OverlayTheme.boldFont());
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
	 * Wide enough for the widest row that asked, within a ceiling.
	 *
	 * The ceiling matters more than the fit: an overlay that grows to hold a
	 * long quest name would cover a third of the screen the one time you are
	 * doing that quest. Past it, rows truncate, which is what
	 * OverlayTheme.truncate is for.
	 */
	private int widthFor(Graphics2D g, OverlayContext ctx, List<OverlayRow> rows)
	{
		int widest = BASE_WIDTH - OverlayTheme.PAD * 2;
		g.setFont(OverlayTheme.boldFont());
		widest = Math.max(widest, g.getFontMetrics().stringWidth(title) + 4);
		for (OverlayRow row : rows)
		{
			widest = Math.max(widest, row.minimumWidth(g, ctx));
		}
		return Math.min(MAX_WIDTH, widest + OverlayTheme.PAD * 2);
	}
}
