package com.vinnyvangogh.questpath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Marks, in a shop's own item grid, exactly the stock the account's doable
 * quests still need.
 *
 * <p>This is the highest-value surface the plugin draws. Every other overlay
 * answers a question the player has to go looking for; this one answers it
 * while they are already standing at the one screen where the answer is
 * actionable. The alternative is reading a shopping list off a separate
 * panel and cross-checking it against forty shop slots by eye -- exactly the
 * bank-vs-inventory kind of bookkeeping this plugin exists to remove.
 *
 * <p>Matching is by item id only, never by name. questpath's own catalogue
 * already resolved "Bucket" the quest item to a specific id and not the
 * Meiyerditch one or the animation-only one; redoing that guess here by
 * string-comparing shop slot names would undo the one thing the id exists to
 * settle.
 */
class ShopItemOverlay extends WidgetItemOverlay
{
	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;

	/**
	 * Unused, kept only so this constructor's shape matches what the plugin
	 * wires it up with. This overlay draws an outline and a digit, never a
	 * sprite of its own -- the item art in the slot is the shop's, already
	 * on screen -- so it has no call for the item image cache ItemManager
	 * would otherwise supply.
	 */
	private final ItemManager itemManager;

	/** The stroke the outline is drawn with -- thicker than a hairline, or it is lost against the shop's own slot border. */
	private static final Stroke OUTLINE_STROKE = new BasicStroke(2f);

	/**
	 * The merged shopping list, keyed by item id, rebuilt only when the list
	 * itself changes.
	 *
	 * renderItemOverlay runs once per slot, every frame, for as long as a
	 * shop is open -- rebuilding a HashMap out of QuestItems.totals() on
	 * every one of those calls would be a map build per slot per frame for
	 * nothing, since the list underneath rarely changes between frames.
	 * QuestItems.signature() is the cheap check that says whether it did.
	 */
	private Map<Integer, QuestItems.Total> lookup = java.util.Collections.emptyMap();
	private int lookupSignature;
	private boolean haveLookup;

	ShopItemOverlay(QuestpathPlugin plugin, QuestpathConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		// SHOPMAIN, not the deprecated widgets.InterfaceID.SHOP -- same id
		// (300), current constant. SHOPSIDE (301) is the inventory beside the
		// shop, which the player can already read.
		showOnInterfaces(InterfaceID.SHOPMAIN);
	}

	/**
	 * Draws over one shop slot.
	 *
	 * Nothing here may throw: this runs inside RuneLite's render loop, once
	 * per slot every frame, and an uncaught exception here does not fail one
	 * frame quietly, it can take the overlay down for the rest of the
	 * session. Every early return below is a deliberate "draw nothing", not
	 * a shortcut around a case that was not considered.
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
			// Whatever went wrong, the safe answer is to draw nothing this
			// frame. The next frame gets a fresh attempt.
		}
	}

	private void paint(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		if (config == null || !config.shopHighlight())
		{
			return;
		}

		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		if (snapshot == null)
		{
			// Logged out, or nothing read yet. Nothing to check the shop
			// against, so nothing is marked -- not the same claim as "you
			// need none of this".
			return;
		}

		QuestItems items = snapshot.items();
		if (items == null)
		{
			// No bundle loaded, or no container seen this session. Same
			// reasoning: silence here, not a false "nothing needed".
			return;
		}

		QuestItems.Total total = lookupFor(items).get(itemId);
		if (total == null || total.satisfied())
		{
			// Either this slot is not on the list at all, or it is and the
			// account already has enough of it. A shop full of outlines for
			// things already owned is noise, not help.
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return;
		}

		// bankSeen() false means every owned() count on the snapshot is
		// inventory and equipment only -- the bank has not been opened this
		// session, so an item sitting in the bank reads as "needed" here
		// when it may not be. Still worth marking (silently dropping the
		// slot would be the "we don't know" -> "there is nothing" failure
		// this project explicitly forbids), but MUTED rather than WARN so
		// the outline itself carries the caveat: a visibly weaker claim
		// instead of a confident one that might be wrong.
		Color outline = items.bankSeen() ? OverlayTheme.WARN : OverlayTheme.MUTED;

		OverlayTheme.prepare(g);
		Stroke previousStroke = g.getStroke();
		g.setStroke(OUTLINE_STROKE);
		g.setColor(outline);
		g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		g.setStroke(previousStroke);

		if (config.shopHighlightCounts())
		{
			long outstanding = Math.max(0L, total.required() - total.owned());
			// The slot corner is where the game itself prints a stack count,
			// so a second number there reads as more of the same rather than
			// as a new kind of overlay -- there is no room for more than the
			// number anyway.
			OverlayTheme.stackCount(g, bounds.x, bounds.y, Long.toString(outstanding), outline);
		}
	}

	/**
	 * The shopping list rebuilt as an id lookup, or the cached one from a
	 * prior frame when nothing has changed.
	 *
	 * A Total with itemId() 0 -- something questpath's catalogue could not
	 * identify -- is excluded rather than indexed under key 0. Shop slots
	 * never carry item id 0, so it could never match by accident, but
	 * leaving it out is the honest statement of the rule: an unresolved
	 * item can never match a shop slot, full stop, not "almost never".
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
				continue;
			}
			// Totals are already merged one-per-name; two different names
			// resolving to the same item id is possible but rare, and the
			// first one seen is as good a choice as any -- the count it
			// carries is what the shop slot will show either way.
			built.putIfAbsent(total.itemId(), total);
		}

		lookup = built;
		lookupSignature = signature;
		haveLookup = true;
		return lookup;
	}
}
