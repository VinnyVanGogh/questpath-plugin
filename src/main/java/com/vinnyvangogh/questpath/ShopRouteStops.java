package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * The pure decision behind {@link ShopRouteOverlay}: which shortfalls are
 * worth mentioning next, in what order, which one is "current," and what to
 * say about where each one sends you.
 *
 * <p>Kept apart from {@link ShopRouteOverlay} the same way {@link
 * ShopRouteGuard} is kept apart from the plugin class it advises: no {@code
 * Graphics2D}, no {@code Overlay}, no plugin to construct -- just {@link
 * QuestItems.Total} values, a {@link ShopItemSkips}, and a shop-name lookup
 * -- so this can be exercised directly in a test rather than only ever
 * through a rendered overlay nothing in this project currently constructs in
 * one. See {@link ShopRouteGuard}'s own class doc for the same argument made
 * about a clock instead of a canvas.
 */
final class ShopRouteStops
{
	private ShopRouteStops()
	{
	}

	/**
	 * One shortfall worth mentioning: the total behind it, and whether it is
	 * the one the player is walking to right now rather than merely one of
	 * the next few.
	 */
	static final class Stop
	{
		private final QuestItems.Total total;
		private final boolean current;

		Stop(QuestItems.Total total, boolean current)
		{
			this.total = total;
			this.current = current;
		}

		/** The shortfall this stop is about. */
		QuestItems.Total total()
		{
			return total;
		}

		/**
		 * Whether this is the very next stop -- the first unsatisfied,
		 * unskipped total in shopping-priority order -- rather than one of
		 * the ones after it.
		 */
		boolean current()
		{
			return current;
		}
	}

	/**
	 * Every outstanding, unskipped total, in the order {@link
	 * QuestItems#totalsOf} already sorted them (closest to done first, then
	 * everything else), with the first one flagged {@link Stop#current()}.
	 *
	 * <p>A skipped total ({@link ShopItemSkips#isSkipped}) is left out
	 * entirely -- moved to the back of what this method reports, which for a
	 * caller only asking "what's next" is the same as not being in the
	 * answer at all. It is still counted elsewhere: see {@link
	 * ShopRouteOverlay}'s own covered/total bar, which sums every total
	 * regardless of skip.
	 *
	 * @param totals a shopping list's totals, in their own priority order --
	 *     typically {@link QuestItems#totals()}
	 * @param skips which items to leave out of the answer
	 */
	static List<Stop> next(List<QuestItems.Total> totals, ShopItemSkips skips)
	{
		List<Stop> out = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.satisfied() || skips.isSkipped(total.name()))
			{
				continue;
			}
			out.add(new Stop(total, out.isEmpty()));
		}
		return out;
	}

	/**
	 * "-> shop -- for quest", or one of two honest admissions instead of a
	 * shop name.
	 *
	 * <p>Mirrors the distinction the web side's shopping page already draws
	 * between an unlocated shop and a no-shop item (see {@code
	 * internal/ui/shopping}'s own doc in the {@code osrs} repo): {@link
	 * QuestItems.Total#shopNames()} empty means no shop is known to sell this
	 * at all; non-empty but every name failing to resolve through {@code
	 * shopLocator} means a shop is known by name but this plugin's bundled
	 * graph carries no coordinate for it (see {@link QuestGraph.Shop}'s own
	 * doc -- only shops the bundle could place are ever included in it at
	 * all, so a name that does not resolve here is a name the bundle chose
	 * not to carry a tile for). Neither is collapsed into a silent gap: a
	 * stop this method cannot place is still worth knowing about, and
	 * guessing a destination for it would be worse than saying nothing.
	 *
	 * @param total the shortfall to describe
	 * @param shopLocator resolves a shop name to a tile, or null when the
	 *     bundle carries none for it -- e.g. {@code QuestpathPlugin::shopLocation}
	 */
	static String destinationLine(QuestItems.Total total, Function<String, WorldPoint> shopLocator)
	{
		String quest = total.questSummary();
		String forQuest = quest.isEmpty() ? "" : " -- for " + quest;

		List<String> shopNames = total.shopNames();
		for (String shopName : shopNames)
		{
			if (shopLocator.apply(shopName) != null)
			{
				return "-> " + shopName + forQuest;
			}
		}
		if (!shopNames.isEmpty())
		{
			return "Known shop, but no location to route to" + forQuest;
		}
		return "No known shop sells this yet" + forQuest;
	}
}
