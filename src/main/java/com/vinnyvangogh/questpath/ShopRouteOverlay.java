package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;

/**
 * Where a shop route is taking you, and why -- drawn for as long as the
 * route sent to Shortest Path stays active.
 *
 * <p>The report this closes, twice, in the player's own words: "it's giving
 * me the path, but it's not telling me what store I'm going to or why I'm
 * going there." Shortest Path draws the line on the ground; nothing else on
 * screen says which of the plan's items is at the other end of it, how many
 * more are needed, or which quest is waiting on it. This overlay is that
 * answer, kept to roughly three stops so it reads as "what to do next," not
 * as a second copy of the shopping list.
 *
 * <p><b>When it is showing, and the swap with {@link NeededItemsOverlay}.</b>
 * {@link #isShowing} is on exactly while {@link QuestpathConfig#overlayShopRoute()}
 * is switched on and {@link QuestpathPlugin#shopRouteActive()} says a route
 * sent to Shortest Path has not yet expired or been arrived at -- see that
 * method's own doc for the window. {@link NeededItemsOverlay} asks the same
 * question and stands down while this is showing, the identical shared-predicate
 * swap it already has with {@link BankListOverlay}, so the two can never
 * disagree about which of them is on screen: "everything you need" replaced
 * by "where you're going right now" for exactly the span of an active shop
 * trip, and back again the moment it ends.
 *
 * <p><b>Content, not just a destination.</b> Each stop shown is one of
 * {@link QuestItems.Total}'s still-outstanding items: its name, how many
 * more are needed, the shop it resolves to, and which quest is asking for it
 * ({@link QuestItems.Total#questSummary()}). The first stop shown is
 * coloured differently from the rest -- the same accent {@link
 * NeededItemsOverlay} uses for "already started" rows -- because it is the
 * one the player is walking to right now, not merely one of the next few.
 *
 * <p><b>No fabricated destinations.</b> An item can be short a shop in two
 * different, genuinely different ways, mirrored here from the same
 * distinction the web side's shopping page draws between an unlocated shop
 * and a no-shop item (see {@code internal/ui/shopping}'s own doc in the
 * {@code osrs} repo): {@link QuestItems.Total#shopNames()} empty means no
 * shop is known to sell this at all; non-empty but every name failing to
 * resolve through {@link #shopLocator} means a shop is known by name but
 * this plugin's bundled graph carries no coordinate for it. Both are said in
 * as many words rather than collapsed into a silent gap in the route -- a
 * stop this overlay cannot place is still worth knowing about, and guessing
 * a destination for it would be worse than saying nothing.
 *
 * <p><b>Skips.</b> An item {@link ShopItemSkips#isSkipped} is excluded from
 * the stops shown and from the covered/total bar's denominator not at all --
 * the bar still counts it, only the "what's next" list moves past it. See
 * {@link ShopItemSkips}'s own doc and {@link QuestpathPanel}'s skip button,
 * which sits beside "Send shop route to Shortest Path."
 */
class ShopRouteOverlay extends QuestpathOverlay
{
	/**
	 * The baseline number of stops shown at the Normal list-length setting --
	 * "roughly three," per the brief this overlay answers. Scaled by {@link
	 * OverlayContext#itemListCap}, the same mechanism every other overlay's
	 * list uses, so Short/Long/Everything still mean something here rather
	 * than being silently ignored by the one panel that asked for "roughly."
	 */
	private static final int BASE_STOPS = 3;

	private final Function<String, WorldPoint> shopLocator;
	private final Function<String, WorldPoint> dropLocator;
	private final Supplier<WorldPoint> playerLocation;
	private final ShopItemSkips skips;

	ShopRouteOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator,
		Supplier<WorldPoint> playerLocation, ShopItemSkips skips)
	{
		super(plugin, config, icons, "Shop route");
		this.shopLocator = shopLocator;
		this.dropLocator = dropLocator;
		this.playerLocation = playerLocation;
		this.skips = skips;
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return isShowing(plugin(), config);
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayShopRouteWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayShopRouteLength();
	}

	/**
	 * Whether this panel actually draws right now: the config toggle is on
	 * and a shop route sent to Shortest Path is still active.
	 *
	 * <p>Shared with {@link NeededItemsOverlay}, the same reason {@link
	 * BankListOverlay#isShowing} is: a second, independently written copy of
	 * this test would be free to disagree with this one, and the one way that
	 * could go unnoticed here is worse than the bank pair's -- "everything you
	 * need" and "where you're going" both standing down believing the other
	 * has it covered, leaving nothing on screen during an active shop trip.
	 */
	static boolean isShowing(QuestpathPlugin plugin, QuestpathConfig config)
	{
		return config != null && config.overlayShopRoute() && plugin != null && plugin.shopRouteActive();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		QuestItems items = snapshot.items();
		if (items == null)
		{
			return List.of(OverlayRow.note(
				"questpath cannot answer this yet -- no item list loaded.",
				OverlayTheme.MUTED));
		}

		if (items.isEmpty())
		{
			return List.of(OverlayRow.note(
				"None of the quests you can currently start need any items.",
				OverlayTheme.MUTED));
		}

		boolean includeRecommended = config != null && config.recommendedItems();
		List<QuestItems.Total> totals = items.totals(includeRecommended);
		int satisfiedCount = 0;
		List<QuestItems.Total> outstanding = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.covered())
			{
				satisfiedCount++;
			}
			else
			{
				outstanding.add(total);
			}
		}

		List<OverlayRow> rows = new ArrayList<>();
		// Same lead-in the other two item panels use: the shopping trip's
		// size before anything else, so a player glancing at three overlays
		// gets the same first number from all of them.
		rows.add(OverlayRow.bar("Covered", satisfiedCount + " / " + totals.size(),
			totals.isEmpty() ? 1.0 : (double) satisfiedCount / totals.size(),
			outstanding.isEmpty() ? OverlayTheme.GOOD : OverlayTheme.WARN));

		if (outstanding.isEmpty())
		{
			rows.add(OverlayRow.note("Everything your doable quests need is already in hand.",
				OverlayTheme.GOOD));
			return rows;
		}

		List<ShopRouteStops.Stop> next =
			ShopRouteStops.tour(totals, skips, shopLocator, dropLocator, playerLocation.get());
		int skippedCount = outstanding.size() - next.size();

		if (next.isEmpty())
		{
			// Every real shortfall is skipped. Not the same picture as
			// "nothing needed" above -- the bar already said as much -- so
			// this says why the stop list below it is empty rather than
			// leaving that unexplained.
			rows.add(OverlayRow.note(
				"Every remaining item is skipped for this trip (" + skippedCount
					+ "). Undo a skip on the Items tab to route to it.",
				OverlayTheme.MUTED));
			return rows;
		}

		rows.add(OverlayRow.header("Next stops"));
		int shown = Math.min(next.size(), ctx.itemListCap(BASE_STOPS));
		for (int i = 0; i < shown; i++)
		{
			ShopRouteStops.Stop stop = next.get(i);
			QuestItems.Total total = stop.total();
			// The list leads with the stop being walked to (see
			// ShopRouteStops#nextCurrentFirst), and that one is named in the
			// accent colour for the same reason NeededItemsOverlay's
			// "already started" rows are -- on a panel where every row is
			// otherwise the same colour, the name is the only place left to
			// say "this one is live". The rows under it are what comes
			// after, including anything nothing can route to yet.
			rows.add(OverlayRow.item(total.itemId(), total.name(),
				total.outstanding() + " short", false,
				stop.current() ? OverlayTheme.ACCENT : OverlayTheme.TEXT, OverlayTheme.WARN));
			rows.add(OverlayRow.note(ShopRouteStops.destinationLine(total, shopLocator, dropLocator, playerLocation.get()), OverlayTheme.MUTED));
		}
		int remaining = next.size() - shown;
		if (remaining > 0)
		{
			rows.add(OverlayRow.overflow(
				"...and " + remaining + " more stop" + (remaining == 1 ? "" : "s") + " after this."));
		}
		if (skippedCount > 0)
		{
			rows.add(OverlayRow.note(
				skippedCount + " skipped for this trip -- see the Items tab to undo.",
				OverlayTheme.MUTED));
		}
		return rows;
	}
}
