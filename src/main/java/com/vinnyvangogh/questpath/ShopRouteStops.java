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
		private final boolean monster;

		Destination(String shopName, WorldPoint point, int alternatives)
		{
			this(shopName, point, alternatives, false);
		}

		Destination(String shopName, WorldPoint point, int alternatives, boolean monster)
		{
			this.shopName = shopName;
			this.point = point;
			this.alternatives = alternatives;
			this.monster = monster;
		}

		/**
		 * Whether this is somewhere to kill something rather than somewhere
		 * to buy something.
		 *
		 * <p>Worth distinguishing in the label and nowhere else: the trip is
		 * the same walk either way, but "-> General store" and "-> kill Giant
		 * bat" want very different things once you arrive, and a player
		 * reading the first while facing the second has been told the wrong
		 * thing.
		 */
		boolean monster()
		{
			return monster;
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
	static List<Stop> next(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator)
	{
		return next(totals, skips, shopLocator, name -> null);
	}

	/** The same, counting a monster you can kill for it as somewhere to go. */
	static List<Stop> next(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator)
	{
		List<QuestItems.Total> outstanding = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			// covered(), not satisfied(): an item questpath says to make from
			// what you already hold is not a stop -- see QuestItems.Total#covered.
			if (!total.covered() && !skips.isSkipped(total.name()))
			{
				outstanding.add(total);
			}
		}

		// The current stop is the first one that can actually be walked to,
		// not simply the first one listed. Bat bones sit at the top of this
		// list because the list's last tiebreak is alphabetical, and no shop
		// sells them -- calling that the current stop names a destination
		// that does not exist and leaves the trip pointing at nothing.
		int current = -1;
		for (int i = 0; i < outstanding.size(); i++)
		{
			if (destinationOf(outstanding.get(i), shopLocator, dropLocator, null) != null)
			{
				current = i;
				break;
			}
		}
		if (current < 0 && !outstanding.isEmpty())
		{
			// Nothing on the list can be routed to at all. The first is still
			// named, so the overlay can say what it is and why there is no
			// route, rather than showing an empty panel.
			current = 0;
		}

		List<Stop> out = new ArrayList<>();
		for (int i = 0; i < outstanding.size(); i++)
		{
			out.add(new Stop(outstanding.get(i), i == current));
		}
		return out;
	}

	/**
	 * The stops in the order you would actually walk them, starting from
	 * where you are standing.
	 *
	 * <p><b>This replaces alphabetical order, which is what the list had and
	 * which is no order at all.</b> {@link QuestItems#totals} sorts by how
	 * close each item is to being finished and falls through to the item's
	 * name -- and until the bank has been read nothing distinguishes any of
	 * them, so every item ties and the whole list is alphabetical. Following
	 * it means a big fishing net in Catherby, then barley malt, then imps
	 * somewhere for a black bead, then back to Catherby for a black candle,
	 * then Al Kharid for black dye. The player's own words: "it wouldn't be
	 * efficient or helpful at all."
	 *
	 * <p>So: nearest first, then nearest from there, and so on -- a greedy
	 * tour from the player's own position rather than from the top of an
	 * alphabet. Items sold at the same shop fall together for free, because
	 * the distance between them is zero, which is what makes "get the other
	 * three things while you are here" happen without special-casing it.
	 *
	 * <p><b>Straight lines, not real paths, and that is a real limitation.</b>
	 * Shortest Path can only be asked about the route it is currently
	 * drawing, not about arbitrary pairs of tiles, so this cannot consult it
	 * while ordering. Straight-line distance ignores water, walls and
	 * teleports, so the tour is a good ordering rather than a proven optimal
	 * one -- and it is still enormously better than sorting by first letter.
	 * Greedy nearest-neighbour is also not the optimal tour even in straight
	 * lines; that is the travelling salesman, and this list runs to eighty
	 * stops.
	 *
	 * <p>Items nothing can route to keep their place at the end rather than
	 * being dropped. They are still outstanding and still worth seeing; they
	 * simply cannot be walked to, so they cannot be part of a walk.
	 *
	 * @param from where the player is. Null falls back to the list's own
	 *     order, because a tour with no starting point is not a tour
	 */
	static List<Stop> tour(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator,
		WorldPoint from)
	{
		List<Stop> stops = next(totals, skips, shopLocator, dropLocator);
		if (from == null || stops.size() < 2)
		{
			return stops;
		}

		List<QuestItems.Total> routable = new ArrayList<>();
		List<QuestItems.Total> unreachable = new ArrayList<>();
		for (Stop stop : stops)
		{
			if (destinationOf(stop.total(), shopLocator, dropLocator, from) != null)
			{
				routable.add(stop.total());
			}
			else
			{
				unreachable.add(stop.total());
			}
		}

		List<QuestItems.Total> ordered = new ArrayList<>(stops.size());
		WorldPoint at = from;
		while (!routable.isEmpty())
		{
			int best = 0;
			long bestDistance = Long.MAX_VALUE;
			for (int i = 0; i < routable.size(); i++)
			{
				// Measured from where this leg starts, not from the player,
				// which is the whole difference between a tour and a sorted
				// list of distances.
				Destination destination = destinationOf(routable.get(i), shopLocator, dropLocator, at);
				if (destination == null)
				{
					continue;
				}
				long distance = squaredDistance(at, destination.point());
				if (distance < bestDistance)
				{
					bestDistance = distance;
					best = i;
				}
			}
			QuestItems.Total chosen = routable.remove(best);
			Destination leg = destinationOf(chosen, shopLocator, dropLocator, at);
			if (leg != null)
			{
				at = leg.point();
			}
			ordered.add(chosen);
		}
		ordered.addAll(unreachable);

		List<Stop> out = new ArrayList<>(ordered.size());
		for (int i = 0; i < ordered.size(); i++)
		{
			// Index 0 is the first leg of the walk, and it is routable whenever
			// anything is -- unreachable items were appended after everything
			// else. With nothing routable at all the first is still named, which
			// is next()'s own contract.
			out.add(new Stop(ordered.get(i), i == 0));
		}
		return out;
	}

	/**
	 * The tiles for the current stop, and only the current stop -- what
	 * Shortest Path is handed.
	 *
	 * <p><b>This used to be every shop for every outstanding item, and that
	 * was the bug.</b> Measured from a real session: the button sent 31
	 * destinations, and three consecutive skips sent 29, 29 and 29. Skipping
	 * an item almost never removes a destination, because the shop selling it
	 * also sells a dozen other things still on the list -- so the path on the
	 * ground never moved and the feature looked broken. It was: with 31
	 * scattered destinations, Shortest Path walks to whichever is nearest,
	 * which has nothing to do with the item the panel names.
	 *
	 * <p>One stop at a time is what makes the rest of this coherent. The
	 * overlay names a shop and that is the shop the line goes to; a skip
	 * moves the line because the stop changed; arriving and buying advances
	 * to the next one. Every placeable shop for that one item is still sent,
	 * because Shortest Path chooses between those by real path distance far
	 * better than {@link #destinationOf}'s straight line can.
	 */
	static Set<WorldPoint> destinations(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator)
	{
		return destinations(totals, skips, shopLocator, name -> null);
	}

	/** The same, falling back to somewhere the item can be killed for. */
	static Set<WorldPoint> destinations(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator)
	{
		return destinations(totals, skips, shopLocator, dropLocator, null);
	}

	/**
	 * The same, walked from where the player is standing.
	 *
	 * <p>The first leg of {@link #tour}, so the line on the ground and the
	 * order in the panel are the same decision rather than two that happen to
	 * agree. Without a position this is the list's own first routable stop,
	 * which is what it always was.
	 */
	static Set<WorldPoint> destinations(List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator,
		WorldPoint from)
	{
		Set<WorldPoint> out = new LinkedHashSet<>();
		for (Stop stop : tour(totals, skips, shopLocator, dropLocator, from))
		{
			if (!stop.current())
			{
				continue;
			}
			for (String shopName : stop.total().shopNames())
			{
				addApproaches(out, shopLocator.apply(shopName));
			}
			if (out.isEmpty())
			{
				// Nothing sells it, so every place it drops is a destination
				// and Shortest Path picks between them -- the same treatment
				// the shops above get, for the same reason.
				for (String dropper : stop.total().dropSourceNames())
				{
					addApproaches(out, dropLocator.apply(dropper));
				}
			}
			break;
		}
		return out;
	}

	/**
	 * How far around a destination tile to offer as alternatives.
	 *
	 * <p>One ring. A shop's recorded tile is where the shopkeeper stands,
	 * which is usually behind a counter and therefore not somewhere a player
	 * can be -- and Shortest Path answers "destination could not be reached"
	 * for a tile nobody can stand on, even one you are already next to.
	 */
	private static final int APPROACH_RING = 1;

	/**
	 * Adds a destination and the tiles around it.
	 *
	 * <p>This exists because of a real regression. While the trip sent every
	 * shop for every outstanding item -- thirty-one destinations -- an
	 * unwalkable one cost nothing: Shortest Path simply routed to one of the
	 * thirty that were fine, and nobody noticed that shop tiles are the
	 * shopkeeper's square. Narrowing the trip to one stop made that the only
	 * destination, so a counter tile became a dead trip, and nine stops
	 * skipped in a row all failed the same way.
	 *
	 * <p>Offering the ring around it is the fix rather than moving the tile:
	 * the recorded position is correct and useful for saying where a shop is,
	 * and which side of a counter a player can stand on is a fact about the
	 * map that this plugin has no business guessing. Shortest Path already
	 * chooses the nearest reachable destination it is given, so handing it
	 * the neighbours lets it answer that question with the map in front of
	 * it.
	 */
	private static void addApproaches(Set<WorldPoint> out, WorldPoint at)
	{
		if (at == null)
		{
			return;
		}
		out.add(at);
		for (int dx = -APPROACH_RING; dx <= APPROACH_RING; dx++)
		{
			for (int dy = -APPROACH_RING; dy <= APPROACH_RING; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					out.add(new WorldPoint(at.getX() + dx, at.getY() + dy, at.getPlane()));
				}
			}
		}
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
		return destinationOf(total, shopLocator, name -> null, from);
	}

	/**
	 * The same, with a fallback to somewhere the item can be killed for.
	 *
	 * <p>Reported from play: "for something like Bat bones, if there is no
	 * shop, it should route me to the nearest bats." Nothing stocks bat
	 * bones, so the trip used to step over the item and say nothing at all
	 * about where to get it -- which is the shape of unhelpfulness this whole
	 * feature exists to remove.
	 *
	 * <p>Shops are still tried first and that is not arbitrary: a shop is
	 * repeatable and predictable, a drop is a grind, so for an ironman a shop
	 * is the answer whenever there is one. The same ordering item_sources
	 * already applies on the server.
	 *
	 * @param dropLocator resolves a monster name to a tile, or null when the
	 *     bundle carries none for it
	 */
	static Destination destinationOf(QuestItems.Total total,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator,
		WorldPoint from)
	{
		Destination shop = shopDestinationOf(total, shopLocator, from);
		if (shop != null)
		{
			return shop;
		}
		return nearestOf(total.dropSourceNames(), dropLocator, from, true);
	}

	private static Destination shopDestinationOf(QuestItems.Total total,
		Function<String, WorldPoint> shopLocator, WorldPoint from)
	{
		return nearestOf(total.shopNames(), shopLocator, from, false);
	}

	private static Destination nearestOf(java.util.List<String> names,
		Function<String, WorldPoint> locator, WorldPoint from, boolean monster)
	{
		String bestName = null;
		WorldPoint bestPoint = null;
		long bestDistance = Long.MAX_VALUE;
		int placeable = 0;

		for (String shopName : names)
		{
			WorldPoint point = locator.apply(shopName);
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

		return bestPoint == null
			? null
			: new Destination(bestName, bestPoint, placeable - 1, monster);
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
		return destinationLine(total, shopLocator, name -> null, from);
	}

	/** The same, able to name a monster when no shop sells it. */
	static String destinationLine(QuestItems.Total total,
		Function<String, WorldPoint> shopLocator, Function<String, WorldPoint> dropLocator,
		WorldPoint from)
	{
		String quest = total.questSummary();
		String forQuest = quest.isEmpty() ? "" : " -- for " + quest;

		Destination destination = destinationOf(total, shopLocator, dropLocator, from);
		if (destination != null)
		{
			int others = destination.alternatives();
			String orOthers = others == 0
				? ""
				: " or " + others + " other" + (others == 1 ? "" : "s");
			String lead = destination.monster() ? "-> kill " : "-> ";
			return lead + destination.shopName() + orOthers + forQuest;
		}
		if (!total.shopNames().isEmpty())
		{
			// Two causes, deliberately said as one. A shop name resolves to
			// nothing either because the bundle carries no tile for it or
			// because this account cannot be served there (see
			// ShopReachability). Naming only the first would be wrong
			// whenever the second applies, and this line's whole job is not
			// to claim more than is known.
			return "A shop sells this, but none you can get to" + forQuest;
		}
		return "No known shop sells this yet" + forQuest;
	}
}
