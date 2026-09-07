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

	/** One line of the list: an item, and how many more to buy. */
	static final class Line
	{
		private final String name;
		private final int itemId;
		private final long outstanding;

		Line(String name, int itemId, long outstanding)
		{
			this.name = name;
			this.itemId = itemId;
			this.outstanding = outstanding;
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
		if (totals == null || totals.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Line> out = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.satisfied())
			{
				// Nothing to buy. A satisfied total on a buy list would be a
				// line telling you to purchase zero of something you already
				// have -- noise, not information.
				continue;
			}
			out.add(new Line(total.name(), total.itemId(), total.outstanding()));
		}
		out.sort(Comparator.comparing(Line::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}
}
