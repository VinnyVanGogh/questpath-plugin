package com.vinnyvangogh.questpath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Marks, in the bank's own grid, the stock the account's plan still needs.
 *
 * <p>The same idea as {@link ShopItemOverlay} pointed at the place the items
 * actually live. A bank is several hundred slots across a dozen tabs and the
 * shopping list is a hundred and thirty rows on another screen; reading one
 * against the other by eye is exactly the bookkeeping this plugin exists to
 * remove, and it is worse here than at a shop counter because the bank is
 * where the answer is acted on.
 *
 * <p>Deliberately marks the real bank slots rather than replacing the grid
 * with a list of its own. A drawn list cannot be withdrawn from, and
 * withdrawing is the entire reason to be standing here -- so the marks go on
 * the items themselves, which stay clickable.
 *
 * <p>Matching is by item id, never by name, for the reason ShopItemOverlay
 * matches by id: questpath's catalogue already resolved which "Bucket" a
 * quest wants.
 */
class BankNeedsOverlay extends WidgetItemOverlay
{
	/** Thicker than a hairline, or it is lost against the bank's slot border. */
	private static final Stroke OUTLINE_STROKE = new BasicStroke(2f);

	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;
	private final TooltipManager tooltips;

	/**
	 * The needed-items lookup keyed by item id, rebuilt only when the list
	 * changes.
	 *
	 * renderItemOverlay runs once per visible slot every frame for as long as
	 * the bank is open, and a bank shows far more slots than a shop. Building
	 * a map per slot per frame for a list that rarely changes between frames
	 * is the cost QuestItems.signature() exists to avoid.
	 */
	private Map<Integer, QuestItems.Total> lookup = java.util.Collections.emptyMap();
	private int lookupSignature;
	private boolean haveLookup;

	BankNeedsOverlay(QuestpathPlugin plugin, QuestpathConfig config, Client client,
		TooltipManager tooltips)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.tooltips = tooltips;
		showOnInterfaces(InterfaceID.BANKMAIN, InterfaceID.BANKSIDE);
	}

	/**
	 * Draws over one bank slot.
	 *
	 * Nothing here may throw. This runs inside RuneLite's render loop once per
	 * slot every frame, and an uncaught exception does not fail one frame
	 * quietly -- it can take the overlay down for the session.
	 */
	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		try
		{
			paint(g, itemId, widgetItem);
		}
		catch (RuntimeException e)
		{
			// Draw nothing this frame; the next one gets a fresh attempt.
		}
	}

	private void paint(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		if (config == null || !config.bankHighlight() || !plugin.bankNeedsShown())
		{
			return;
		}

		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		if (snapshot == null || snapshot.items() == null)
		{
			// Logged out, or nothing read yet. Silence, not a false "you need
			// none of this".
			return;
		}

		QuestItems.Total total = lookupFor(snapshot.items()).get(itemId);
		if (total == null)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return;
		}

		// Two states, and the difference is the whole point of marking a bank
		// rather than a shop: amber is "take some of this out", green is
		// "you already have enough, leave it". A shop only ever needed the
		// first, because nothing in a shop is already yours.
		boolean enough = total.satisfied();
		if (enough && config.bankHighlightShortOnly())
		{
			return;
		}
		Color outline = enough ? OverlayTheme.GOOD : config.bankHighlightColour();

		OverlayTheme.prepare(g);
		Stroke previous = g.getStroke();
		g.setStroke(OUTLINE_STROKE);
		g.setColor(outline);
		g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		g.setStroke(previous);

		if (config.bankHighlightCounts() && !enough)
		{
			drawCount(g, bounds, Long.toString(total.outstanding()));
		}

		describeOnHover(bounds, total);
	}

	/**
	 * How many still to find, above the slot rather than inside it.
	 *
	 * The corner is taken: the bank prints its own stack size there in yellow
	 * or green, and a second unrelated number in the same place is worse than
	 * either alone. Above the slot, in a colour the bank does not use.
	 */
	private static void drawCount(Graphics2D g, Rectangle bounds, String text)
	{
		g.setFont(OverlayTheme.smallFont());
		FontMetrics metrics = g.getFontMetrics();
		int width = metrics.stringWidth(text);

		int x = bounds.x + (bounds.width - width) / 2;
		int baseline = bounds.y - 2;
		if (baseline - metrics.getAscent() < 0)
		{
			baseline = bounds.y + metrics.getAscent();
		}
		OverlayTheme.shadowed(g, text, x, baseline, OverlayTheme.GOOD);
	}

	private void describeOnHover(Rectangle bounds, QuestItems.Total total)
	{
		if (client == null || tooltips == null)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		String quests = total.questSummary();
		if (quests.isEmpty())
		{
			return;
		}
		tooltips.add(new Tooltip(total.name() + " " + total.describe()
			+ "</br>For " + quests));
	}

	/**
	 * The needed-items lookup as an id map, or the cached one.
	 *
	 * Duplicated from ShopItemOverlay rather than shared: the two overlays
	 * cache against their own signatures and have independent lifetimes, and
	 * a shared cache would be invalidated by whichever interface opened last.
	 */
	private Map<Integer, QuestItems.Total> lookupFor(QuestItems items)
	{
		int signature = items.signature();
		if (haveLookup && signature == lookupSignature)
		{
			return lookup;
		}

		Map<Integer, QuestItems.Total> built = new HashMap<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.itemId() <= 0)
			{
				// questpath never resolved a game id for this line, so there
				// is nothing to match a slot against. Marking by name instead
				// would undo the resolution the id exists to settle.
				continue;
			}
			built.putIfAbsent(total.itemId(), total);
		}

		lookup = built;
		lookupSignature = signature;
		haveLookup = true;
		return lookup;
	}
}
