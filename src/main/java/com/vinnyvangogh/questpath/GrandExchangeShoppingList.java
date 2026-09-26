package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The flat, ordered buy list for an account that has a Grand Exchange, built
 * from the same {@link QuestItems.Total} rows the walking shop tour uses.
 *
 * <p>Everything built for shopping before this assumed an ironman: a
 * geographic tour, shop reachability, drop routing -- "which of eleven shops
 * sells this and can I get in". None of that matters once one building sells
 * everything, so this class asks a much smaller question than {@link
 * ShopRouteStops} does: not where to walk, just what to buy and how many,
 * outstanding items only.
 *
 * <p><b>Order: plain alphabetical, not {@link QuestItems#totals()}'s own
 * order.</b> That order -- unsatisfied first, then in-progress items
 * (something already carried) before untouched ones, then bank placeholders,
 * alphabetical only as the final tie-break -- exists to sequence a walking
 * trip: what to finish first, what you are already mid-gathering, what the
 * bank says you ran out of. None of that is about travel exactly, but all of
 * it is about *when* to act on a shortfall relative to *where you already are
 * standing* -- and standing at a Grand Exchange counter, that question has
 * one answer for every item: right now, in the same click. There is nothing
 * left to sequence, so this class sorts what is left over once
 * {@link QuestItems.Total#satisfied()} items are dropped -- purely by name,
 * case-insensitively, which is exactly the tie-break {@code totalsOf} already
 * falls back to when it has nothing sharper to sort by. Alphabetical was
 * defensible there only as a last resort; here it is the whole rule, because
 * a name is the one thing worth scanning a flat list by.
 */
final class GrandExchangeShoppingList
{
	private GrandExchangeShoppingList()
	{
	}

	/**
	 * Whether an item can be bought at a Grand Exchange counter at all.
	 *
	 * <p>Three values rather than two, because "questpath does not know" is a
	 * different answer from "no" and this project does not let those collapse
	 * into each other.
	 */
	enum Availability
	{
		/** On the Grand Exchange. Safe to suggest. */
		BUYABLE,
		/**
		 * A real item the Grand Exchange does not trade -- Karamjan rum, and
		 * every other quest item that only exists where it is found.
		 * Suggesting one is the failure this project rules out everywhere:
		 * never offer an account something it cannot act on.
		 */
		NOT_ON_GE,
		/**
		 * questpath holds no item id for this row, so nothing can be asked
		 * about it.
		 *
		 * <p>Not a rare edge: 275 of the 645 distinct item rows in the
		 * shipped bundle have no id, and reading them shows why -- they are
		 * requirements written as prose ("A light source", "A way to enter
		 * Port Phasmatys", "A pickaxe and 35 Mining to mine coal") rather
		 * than named items. None of those is something the Grand Exchange
		 * could sell you under that name, so they are kept off the buy list
		 * -- but they are kept apart from {@link #NOT_ON_GE}, because
		 * "cannot look this up" is a statement about questpath and "this is
		 * not traded" is a statement about the game.
		 */
		UNKNOWN,
		/**
		 * A members-only item, on a free-to-play world.
		 *
		 * <p>Its own value rather than folded into {@link #NOT_ON_GE},
		 * because the two are different claims and the difference is
		 * actionable. An item the Grand Exchange does not trade will never be
		 * buyable there; a members item becomes buyable the moment you hop to
		 * a members world. Printing "not traded" for one of these would tell
		 * a player to stop looking for something they could have in thirty
		 * seconds.
		 *
		 * <p>It is also the only value here that depends on where the player
		 * is standing rather than on the item alone, so it must be recomputed
		 * with the list and never cached across a world hop.
		 */
		MEMBERS_ONLY,
	}

	/**
	 * Answers {@link Availability} for an item -- production wiring asks
	 * {@code ItemComposition#isGeTradeable()}, the same flag RuneLite's own
	 * bundled Grand Exchange plugin filters its search results on.
	 *
	 * <p>An interface rather than a direct call so this class stays testable
	 * without a client: the real implementation needs {@code ItemManager} and
	 * the client thread, and neither belongs in a list-builder.
	 */
	interface Tradeability
	{
		Availability of(int itemId, String name);
	}

	/** One line of the list: an item, how many more to buy, whether it is on offer, and whether it can be bought here at all. */
	static final class Line
	{
		private final String name;
		private final int itemId;
		private final long outstanding;
		private final GrandExchangeOffers.Order order;
		private final Availability availability;
		private final java.util.List<String> notes;
		private final boolean recommended;

		Line(String name, int itemId, long outstanding)
		{
			this(name, itemId, outstanding, null, Availability.BUYABLE);
		}

		Line(String name, int itemId, long outstanding, GrandExchangeOffers.Order order)
		{
			this(name, itemId, outstanding, order, Availability.BUYABLE);
		}

		Line(String name, int itemId, long outstanding, GrandExchangeOffers.Order order,
			Availability availability)
		{
			this(name, itemId, outstanding, order, availability, null);
		}

		Line(String name, int itemId, long outstanding, GrandExchangeOffers.Order order,
			Availability availability, java.util.List<String> notes)
		{
			this(name, itemId, outstanding, order, availability, notes, false);
		}

		Line(String name, int itemId, long outstanding, GrandExchangeOffers.Order order,
			Availability availability, java.util.List<String> notes, boolean recommended)
		{
			this.notes = notes;
			this.name = name;
			this.itemId = itemId;
			this.outstanding = outstanding;
			this.order = order;
			this.availability = availability;
			this.recommended = recommended;
		}

		boolean recommended()
		{
			return recommended;
		}

		String name()
		{
			return name;
		}

		/** The game's own item id, or 0 when questpath holds none -- see {@link QuestItems.Total#itemId()}. */
		int itemId()
		{
			return itemId;
		}

		/** How many more to buy. Always positive -- a satisfied total never becomes a line. */
		long outstanding()
		{
			return outstanding;
		}

		/**
		 * The live buy offer already covering this item, or null when there
		 * is none -- see {@link GrandExchangeOffers} for what counts as live,
		 * and why a cancelled offer stops counting immediately rather than
		 * once its collection box is emptied.
		 */
		GrandExchangeOffers.Order order()
		{
			return order;
		}

		/**
		 * The wiki's own caveats for this item, verbatim.
		 *
		 * <p>This is the surface they were missing from. The Knight's Sword
		 * asks for "2 or 4 iron bars (4 if you want your own blurite sword)";
		 * the list said two and the player, an ironman who wanted the second
		 * sword, found out only after buying.
		 */
		java.util.List<String> notes()
		{
			return notes == null ? java.util.Collections.emptyList() : notes;
		}

		/** Whether one of the eight slots is already buying this. */
		boolean queued()
		{
			return order != null;
		}

		/** Whether this can be bought at a Grand Exchange counter at all -- see {@link Availability}. */
		Availability availability()
		{
			return availability;
		}

		/** Shorthand for the only case a buy list may suggest. */
		boolean buyable()
		{
			return availability == Availability.BUYABLE;
		}

		/**
		 * How far a queued item has got, counted against what the account
		 * needs -- "1 of 1", "10 of 1", "0 of 1 -- collect".
		 *
		 * <p><b>The denominator is the need, not the size of the offer.</b>
		 * It used to be the offer's own quantity, which made buying ten of
		 * something a quest wants one of read "10 of 10" -- a true sentence
		 * about the offer and a useless one about the trip. Only a line knows
		 * both halves, which is why the wording lives here rather than on
		 * {@link GrandExchangeOffers.Order}: an offer has no idea what it was
		 * placed for.
		 *
		 * <p>Overshooting is shown rather than clamped. "10 of 1" is the
		 * honest reading of ten bought against one wanted, and rounding it
		 * down to "1 of 1" would hide nine items sitting in the collection
		 * box.
		 */
		String progress()
		{
			if (order == null)
			{
				return "";
			}
			return order.bought() + " of " + outstanding + (collectable() ? " -- collect" : "");
		}

		/**
		 * Whether there is something in the collection box worth walking over
		 * for: enough has arrived to cover the need, or the offer has
		 * finished and there is nothing more coming.
		 */
		boolean collectable()
		{
			return order != null && (order.covers(outstanding) || order.complete());
		}
	}

	/**
	 * Every outstanding total, alphabetically by name -- see this class's own
	 * doc for why that replaces {@link QuestItems#totals()}'s walking-tour
	 * order rather than reusing it.
	 *
	 * @param totals the account's merged shopping list, e.g. {@link
	 *     QuestItems#totals()}. Null is treated as empty, matching how every
	 *     other caller of a possibly-absent {@link QuestItems} in this
	 *     plugin already renders "nothing to show" rather than throwing.
	 */
	static List<Line> of(List<QuestItems.Total> totals)
	{
		return of(totals, null);
	}

	/**
	 * The same list, with every item one of the eight Grand Exchange slots is
	 * already buying marked as such.
	 *
	 * <p><b>Marked, not dropped.</b> A queued item is not finished with: the
	 * offer can be cancelled, it can be sitting at three of five, and it
	 * still has to be collected. Dropping it would make the list shorter and
	 * the player's job harder, because the thing they most need to know at a
	 * Grand Exchange counter is which slots they have already spent. So it
	 * stays on the list and moves to its own section -- see {@link
	 * GrandExchangeOverlay} for where that lands on screen.
	 *
	 * <p>{@link #next} is what actually skips them, and it is the only place
	 * that does.
	 *
	 * @param offers the account's live buy offers, or null when nothing is
	 *     tracking them -- which yields exactly the unmarked list {@link
	 *     #of(List)} always returned
	 */
	static List<Line> of(List<QuestItems.Total> totals, GrandExchangeOffers offers)
	{
		return of(totals, offers, null);
	}

	/**
	 * The same list again, with each line also told whether the Grand
	 * Exchange trades it at all.
	 *
	 * <p><b>Karamjan rum is why this parameter exists.</b> The list is built
	 * from every outstanding quest item, and plenty of those are never sold
	 * at a Grand Exchange counter -- they are found, stolen, or handed over
	 * during the quest itself. Offering one as the next thing to buy is the
	 * failure this project rules out everywhere else: never suggest
	 * something the account cannot act on. {@link #next} and {@link #left}
	 * now skip them and {@link #unbuyable} collects them, so they are moved
	 * rather than hidden -- the player still needs them, just not from here.
	 *
	 * @param tradeability how to ask, or null for "assume everything is
	 *     buyable", which is what the two-argument form does and what every
	 *     caller with no client to ask gets
	 */
	static List<Line> of(List<QuestItems.Total> totals, GrandExchangeOffers offers,
		Tradeability tradeability)
	{
		if (totals == null || totals.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.covered())
			{
				// Nothing to buy. A satisfied total on a buy list would be a
				// line telling you to purchase zero of something you already
				// have -- noise, not information.
				continue;
			}
			GrandExchangeOffers.Order order = offers == null
				? null
				: offers.forItem(total.itemId(), total.name());
			Availability availability = tradeability == null
				? Availability.BUYABLE
				: tradeability.of(total.itemId(), total.name());
			out.add(new Line(total.name(), total.itemId(), total.outstanding(), order,
				availability == null ? Availability.UNKNOWN : availability, total.notes(), total.recommended()));
		}
		out.sort(Comparator.comparing(Line::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	/**
	 * The one item to buy right now: the first line no slot is already
	 * buying, or null when every outstanding item is already on offer.
	 * Required items are always prioritized ahead of recommended items.
	 *
	 * <p>Null is a real answer and a good one -- "you have asked for
	 * everything you still need" -- and it is why the caller must not fall
	 * back to the head of the list when this returns nothing. Suggesting an
	 * item the player has already bought is the bug this whole pass exists
	 * to fix.
	 */
	static Line next(List<Line> lines)
	{
		List<Line> left = left(lines);
		if (left.isEmpty())
		{
			return null;
		}
		for (Line line : left)
		{
			if (!line.recommended())
			{
				return line;
			}
		}
		return left.get(0);
	}

	/** Every line that is a recommended item rather than required. */
	static List<Line> recommended(List<Line> lines)
	{
		if (lines == null)
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (line.recommended())
			{
				out.add(line);
			}
		}
		return out;
	}

	/** Every line that is required. */
	static List<Line> required(List<Line> lines)
	{
		if (lines == null)
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (!line.recommended())
			{
				out.add(line);
			}
		}
		return out;
	}

	/** Every line already on offer, in the list's own order. */
	static List<Line> queued(List<Line> lines)
	{
		if (lines == null)
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (line.queued())
			{
				out.add(line);
			}
		}
		return out;
	}

	/**
	 * Every line still to buy here, in the list's own order -- {@link #next}
	 * included, as its head.
	 *
	 * <p>Not on offer, and actually sold at a Grand Exchange. A line failing
	 * either test is on {@link #unbuyable} instead.
	 */
	static List<Line> left(List<Line> lines)
	{
		if (lines == null)
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (!line.queued() && line.buyable())
			{
				out.add(line);
			}
		}
		return out;
	}

	/**
	 * Everything outstanding that a Grand Exchange cannot supply to this
	 * account right now -- see {@link Availability} for the three ways a line
	 * lands here and why none of them is merged into another.
	 *
	 * <p>Listed rather than dropped. These are still things the account
	 * needs; they just come from somewhere else, and a buy list that silently
	 * shortened itself would leave the player believing the trip was over.
	 */
	static List<Line> unbuyable(List<Line> lines)
	{
		if (lines == null)
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (!line.queued() && !line.buyable())
			{
				out.add(line);
			}
		}
		return out;
	}
}
