package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * The pure decision behind {@link ShopRouteOverlay}: which shortfalls are
 * worth mentioning next, in what order, which one is "current," where each
 * one sends you, and what to say about it.
 *
 * <p>Kept apart from {@link ShopRouteOverlay} the same way {@link
 * ShopRouteGuard} is kept apart from the plugin class it advises: no {@code
 * Graphics2D}, no {@code Overlay}, no plugin to construct -- just {@link
 * QuestItems.Total} values, a {@link ShopItemSkips}, and a shop-name lookup
 * -- so this can be exercised directly in a test rather than only ever
 * through a rendered overlay nothing in this project currently constructs in
 * one. See {@link ShopRouteGuard}'s own class doc for the same argument made
 * about a clock instead of a canvas.
 *
 * <p><b>One place decides where a shop trip goes.</b> {@link #destinations}
 * is what Shortest Path is handed and {@link #destinationOf} is what the
 * overlay names, and they read the same shop list by the same rules. They
 * did not always: the panel gathered every placeable shop while the overlay
 * named whichever happened to be first in that list, so an item sold in
 * three places could be labelled with a shop the player was demonstrably not
 * walking to. That is the player's own report -- "it's not telling me what
 * store I'm going to" -- reappearing one layer down, as a label that answers
 * confidently and wrongly.
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
	 * A shop this plugin can actually route to: its name, its tile, and how
	 * many other placeable shops also sell the same item.
	 */
	static final class Destination
	{
		private final String shopName;
		private final WorldPoint point;
		private final int alternatives;

		Destination(String shopName, WorldPoint point, int alternatives)
		{
			this.shopName = shopName;
			this.point = point;
			this.alternatives = alternatives;
		}

		/** The shop's name, as the wiki spells it. */
		String shopName()
		{
			return shopName;
		}

		/** Where it is. */
		WorldPoint point()
		{
			return point;
		}

		/**
		 * How many other placeable shops sell this same item -- zero when
		 * this is the only one.
		 *
		 * <p>Not decoration. Every one of them is handed to Shortest Path,
		 * which picks between them by real path distance rather than the
		 * straight line this class measures, so with alternatives on the
		 * table the named shop is this plugin's best guess and not a
		 * certainty. Saying how many others exist is what keeps the label
		 * from overclaiming.
		 */
		int alternatives()
		{
			return alternatives;
		}
	}

	/**
	 * Every outstanding, unskipped total, in the order {@link
	 * QuestItems#totals} already sorted them (closest to done first, then
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
	 * Every tile worth walking to for the whole outstanding list -- what
	 * Shortest Path is handed.
	 *
	 * <p>Deliberately every placeable shop for every outstanding item, not
	 * one per item: Shortest Path chooses between destinations by real path
	 * distance, over a map where a straight line and a walk are very
	 * different things, and it is far better at that than this class is.
	 * {@link #destinationOf} is the one that has to commit to a single name
	 * for the label, and it says so when it is guessing.
	 */
	static Set<WorldPoint> destinations(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator)
	{
		Set<WorldPoint> out = new LinkedHashSet<>();
		for (Stop stop : next(totals, skips))
		{
			for (String shopName : stop.total().shopNames())
			{
				WorldPoint point = shopLocator.apply(shopName);
				if (point != null)
				{
					out.add(point);
				}
			}
		}
		return out;
	}

	/**
	 * The shop this stop should be labelled with: the placeable one nearest
	 * {@code from}, or the first placeable one when there is no position to
	 * measure from yet.
	 *
	 * <p>Nearest is measured as a straight line on x and y, ignoring plane.
	 * That is a weaker measure than the one Shortest Path applies to the
	 * same set, and the closest of several shops by straight line can differ
	 * from the one a real path reaches first. Hence {@link
	 * Destination#alternatives()}: the label built from this admits when
	 * others were in the running rather than presenting a guess as the
	 * answer.
	 *
	 * @return null when no shop selling this item has a tile in the bundle
	 */
	static Destination destinationOf(QuestItems.Total total,
		Function<String, WorldPoint> shopLocator, WorldPoint from)
	{
		String bestName = null;
		WorldPoint bestPoint = null;
		long bestDistance = Long.MAX_VALUE;
		int placeable = 0;

		for (String shopName : total.shopNames())
		{
			WorldPoint point = shopLocator.apply(shopName);
			if (point == null)
			{
				continue;
			}
			placeable++;
			// With no position to measure from, every distance is zero and
			// the strict less-than below leaves the first placeable shop
			// standing -- the behaviour this had before a position was
			// available, kept deliberately rather than drifting to whichever
			// shop the loop happens to end on.
			long distance = from == null ? 0L : squaredDistance(from, point);
			if (bestPoint == null || distance < bestDistance)
			{
				bestName = shopName;
				bestPoint = point;
				bestDistance = distance;
			}
		}

		return bestPoint == null ? null : new Destination(bestName, bestPoint, placeable - 1);
	}

	private static long squaredDistance(WorldPoint a, WorldPoint b)
	{
		long dx = (long) a.getX() - b.getX();
		long dy = (long) a.getY() - b.getY();
		return dx * dx + dy * dy;
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
	 * graph carries no coordinate for it. Neither is collapsed into a silent
	 * gap: a stop this method cannot place is still worth knowing about, and
	 * guessing a destination for it would be worse than saying nothing.
	 *
	 * @param total the shortfall to describe
	 * @param shopLocator resolves a shop name to a tile, or null when the
	 *     bundle carries none for it -- e.g. {@code QuestpathPlugin::shopLocation}
	 * @param from where the player is, for choosing between shops that all
	 *     sell it; null before the client has a position
	 */
	static String destinationLine(QuestItems.Total total,
		Function<String, WorldPoint> shopLocator, WorldPoint from)
	{
		String quest = total.questSummary();
		String forQuest = quest.isEmpty() ? "" : " -- for " + quest;

		Destination destination = destinationOf(total, shopLocator, from);
		if (destination != null)
		{
			int others = destination.alternatives();
			String orOthers = others == 0
				? ""
				: " or " + others + " other" + (others == 1 ? "" : "s");
			return "-> " + destination.shopName() + orOthers + forQuest;
		}
		if (!total.shopNames().isEmpty())
		{
			return "Known shop, but no location to route to" + forQuest;
		}
		return "No known shop sells this yet" + forQuest;
	}
}
