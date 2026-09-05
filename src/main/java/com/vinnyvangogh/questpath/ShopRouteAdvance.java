package com.vinnyvangogh.questpath;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * What should happen the moment a shop trip's current item stops being
 * outstanding.
 *
 * <p>Before this existed a shop route was sent once and never revisited.
 * Shortest Path was handed every shop worth visiting and walked you to the
 * nearest -- and then you bought the thing, and it kept walking you to the
 * shop you were already standing in, because nothing ever told it the list
 * had got shorter. The trip worked exactly once and then quietly stopped
 * being a trip.
 *
 * <p>So: when {@link ItemCompletionWatcher} reports something finished and a
 * shop route is still live, the remaining destinations are recomputed and
 * sent again. The list only ever shrinks, so this cannot loop -- every
 * re-route follows an item leaving the outstanding set, and there are
 * finitely many items.
 *
 * <p><b>Pure decision, like its neighbours.</b> No client, no chat, no
 * Shortest Path: handed the facts, it answers what should happen, the same
 * shape {@link ItemCompletionWatcher} and {@link ShopArrivalWatcher} already
 * have and for the same reason -- the interesting part is which transitions
 * count, and that is worth testing without a running game.
 */
final class ShopRouteAdvance
{
	private ShopRouteAdvance()
	{
	}

	/** What the plugin should do about a completion during a shop trip. */
	enum Action
	{
		/**
		 * Nothing at all: no trip is running, or nothing finished, so there
		 * is no reason to disturb whatever route the player is following.
		 */
		NOTHING,

		/** Something finished and stops remain -- send the shorter list. */
		REROUTE,

		/**
		 * Something finished and it was the last one. The trip is over, and
		 * saying so is the point: a route that simply stops updating looks
		 * identical to a broken one.
		 */
		FINISHED
	}

	/** The answer, plus everything the caller needs to act on it. */
	static final class Decision
	{
		private final Action action;
		private final Set<WorldPoint> destinations;
		private final String nextItem;
		private final String nextShop;
		private final int remaining;

		Decision(Action action, Set<WorldPoint> destinations, String nextItem, String nextShop,
			int remaining)
		{
			this.action = action;
			this.destinations = destinations;
			this.nextItem = nextItem;
			this.nextShop = nextShop;
			this.remaining = remaining;
		}

		static Decision nothing()
		{
			return new Decision(Action.NOTHING, Set.of(), "", "", 0);
		}

		/** What to do. */
		Action action()
		{
			return action;
		}

		/** The shorter destination set, for {@link Action#REROUTE}. */
		Set<WorldPoint> destinations()
		{
			return destinations;
		}

		/** The item the trip now heads for, or empty when there is none. */
		String nextItem()
		{
			return nextItem;
		}

		/**
		 * The shop the next item is expected at, or empty when this plugin
		 * cannot place one -- which is not the same as there being no next
		 * stop, and is never filled in with a guess. See {@link
		 * ShopRouteStops#destinationOf}.
		 */
		String nextShop()
		{
			return nextShop;
		}

		/** How many stops are left, including the next one. */
		int remaining()
		{
			return remaining;
		}
	}

	/**
	 * Decides what a completion means for a running shop trip.
	 *
	 * @param routeActive whether a shop route sent to Shortest Path is still
	 *     live -- {@code QuestpathPlugin#shopRouteActive()}. False means the
	 *     player is not on a shop trip and nothing here applies
	 * @param completed what {@link ItemCompletionWatcher} just reported
	 * @param totals the current shopping list
	 * @param skips items the player has set aside for this trip
	 * @param shopLocator resolves a shop name to a tile
	 * @param from where the player is, for choosing between shops selling
	 *     the same item; null before the client has a position
	 */
	static Decision observe(boolean routeActive,
		List<ItemCompletionWatcher.Completion> completed,
		List<QuestItems.Total> totals, ShopItemSkips skips,
		Function<String, WorldPoint> shopLocator, WorldPoint from)
	{
		if (!routeActive || completed == null || completed.isEmpty() || totals == null)
		{
			return Decision.nothing();
		}

		List<ShopRouteStops.Stop> stops = ShopRouteStops.next(totals, skips, shopLocator);
		if (stops.isEmpty())
		{
			return new Decision(Action.FINISHED, Set.of(), "", "", 0);
		}

		Set<WorldPoint> destinations = ShopRouteStops.destinations(totals, skips, shopLocator);
		if (destinations.isEmpty())
		{
			// Stops remain, but none of them is anywhere this plugin can
			// route to -- every one is an item with no known shop, or a shop
			// the bundle carries no tile for. Re-sending an empty set would
			// clear the player's path and leave them standing there with no
			// explanation, so the trip is declared over instead. What is
			// still outstanding is on the Items tab, which is the honest
			// place for a stop nobody can be walked to.
			return new Decision(Action.FINISHED, Set.of(), "", "", stops.size());
		}

		QuestItems.Total nextTotal = stops.get(0).total();
		for (ShopRouteStops.Stop stop : stops)
		{
			if (stop.current())
			{
				// The stop being routed to, which is the first one that can be
				// walked to rather than the first one listed.
				nextTotal = stop.total();
				break;
			}
		}
		ShopRouteStops.Destination destination =
			ShopRouteStops.destinationOf(nextTotal, shopLocator, from);
		return new Decision(Action.REROUTE, destinations, nextTotal.name(),
			destination == null ? "" : destination.shopName(), stops.size());
	}
}
