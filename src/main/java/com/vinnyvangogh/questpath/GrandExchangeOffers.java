package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Which of the account's eight Grand Exchange slots currently hold a buy
 * offer, and for what -- the difference between "still to buy" and "already
 * asked for".
 *
 * <p><b>Why this exists.</b> Without it the shopping list only moved on once
 * an item was <em>collected</em> into the inventory or bank, because that is
 * the only moment {@link QuestItems} can see anything change: it counts what
 * the client says you hold, and an offer sitting in a slot is not held. So
 * the list kept naming an item that was already bought and waiting, and the
 * player had to remember which of the eight slots they had already filled --
 * which is exactly the bookkeeping the list is supposed to do for them.
 *
 * <p><b>Only buys, and only live ones.</b> {@link
 * GrandExchangeOfferState#BUYING} and {@link GrandExchangeOfferState#BOUGHT}
 * count. Everything else clears the slot, and each case is deliberate rather
 * than a default:
 *
 * <ul>
 * <li>{@code SELLING} and {@code SOLD} are the other direction entirely.
 *     Selling five bronze bars must never tick a shopping list that wants
 *     five bronze bars.</li>
 * <li>{@code CANCELLED_BUY} is an offer the player took back. This is the
 *     case that has to reverse: an aborted offer means the item is wanted
 *     again, and it must reappear at once rather than after the collection
 *     box is emptied. Whatever part of it did complete before the abort is
 *     still in the collection box and still counts once collected -- by
 *     then {@link QuestItems} owns the question again, which is the right
 *     hand-off.</li>
 * <li>{@code EMPTY} is a collected or never-used slot. Collected means the
 *     items are now held, so {@link QuestItems} sees them and the line is
 *     satisfied for real -- the permanent version of what this class does
 *     temporarily.</li>
 * </ul>
 *
 * <p><b>Keyed by slot, not by item.</b> The client reports a slot's whole
 * state on every change, and two slots can legitimately buy the same item.
 * Keying by item would make the second offer overwrite the first and the
 * first's later {@code EMPTY} erase both.
 *
 * <p><b>Matched by id when there is one, by name when there is not.</b>
 * {@link QuestItems.Total#itemId()} is 0 for anything questpath holds no id
 * for, and those lines are exactly as worth ticking as the rest. Names are
 * compared case-insensitively and trimmed because the two sides come from
 * different places -- the wiki's name for a thing and the game's own item
 * definition -- and casing has already caused one bug in this feature (see
 * {@link GrandExchangeSearchBridge#searchText}).
 *
 * <p>Pure state with no client calls of its own: the caller resolves the
 * item's name, which needs the client thread, and hands the result in.
 */
final class GrandExchangeOffers
{
	/** One slot's live buy offer. */
	static final class Order
	{
		private final int slot;
		private final int itemId;
		private final String name;
		private final int quantity;
		private final int bought;
		private final boolean complete;

		Order(int slot, int itemId, String name, int quantity, int bought, boolean complete)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.bought = bought;
			this.complete = complete;
		}

		/** Which of the eight slots this is in. */
		int slot()
		{
			return slot;
		}

		/** The game's own item id for what is on offer. */
		int itemId()
		{
			return itemId;
		}

		/** The game's own name for it, or null when the caller could not resolve one. */
		String name()
		{
			return name;
		}

		/** How many the offer asks for. */
		int quantity()
		{
			return quantity;
		}

		/** How many have arrived so far. */
		int bought()
		{
			return bought;
		}

		/** Whether the offer has finished buying and is only waiting to be collected. */
		boolean complete()
		{
			return complete;
		}

		/** "3 of 5", or "5 of 5 -- collect" once finished. */
		String describe()
		{
			return bought + " of " + quantity + (complete ? " -- collect" : "");
		}
	}

	private final Map<Integer, Order> bySlot = new HashMap<>();

	/**
	 * Records what a slot now holds. Safe to call for every {@code
	 * GrandExchangeOfferChanged}, including the burst of them the client
	 * fires for all eight slots at login -- that burst is what seeds this.
	 *
	 * @param name the game's own name for the item, or null when the caller
	 *     could not resolve one. Null narrows matching to the item id
	 *     rather than failing: an id match is the better one anyway.
	 */
	void update(int slot, GrandExchangeOfferState state, int itemId, String name, int quantity, int bought)
	{
		if (state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.BOUGHT)
		{
			// Sold, cancelled, or emptied. See this class's doc for why each
			// of those is a removal rather than something to remember.
			bySlot.remove(slot);
			return;
		}
		bySlot.put(slot, new Order(slot, itemId, name, quantity, bought,
			state == GrandExchangeOfferState.BOUGHT));
	}

	/** Forgets every slot -- for a logout, where the next login may be a different account's offers. */
	void clear()
	{
		bySlot.clear();
	}

	/** Whether any slot holds a live buy offer at all. */
	boolean isEmpty()
	{
		return bySlot.isEmpty();
	}

	/**
	 * The live buy offer for an item, or null when none of the eight slots
	 * holds one -- see this class's doc for how the two sides are matched.
	 *
	 * <p>When more than one slot buys the same thing, the one with fewest
	 * still to arrive wins, so a nearly-finished offer is what the player is
	 * told about rather than an arbitrary one.
	 */
	Order forItem(int itemId, String name)
	{
		Order best = null;
		for (Order order : bySlot.values())
		{
			if (!matches(order, itemId, name))
			{
				continue;
			}
			if (best == null || remaining(order) < remaining(best))
			{
				best = order;
			}
		}
		return best;
	}

	/** Every live buy offer, item name first and then slot, so the list does not reshuffle on its own. */
	List<Order> all()
	{
		List<Order> out = new ArrayList<>(bySlot.values());
		out.sort(Comparator
			.comparing((Order o) -> o.name() == null ? "" : o.name(), String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(Order::slot));
		return out;
	}

	private static int remaining(Order order)
	{
		return Math.max(0, order.quantity() - order.bought());
	}

	private static boolean matches(Order order, int itemId, String name)
	{
		if (itemId > 0 && order.itemId() == itemId)
		{
			return true;
		}
		return normalise(order.name()) != null && normalise(order.name()).equals(normalise(name));
	}

	private static String normalise(String name)
	{
		if (name == null)
		{
			return null;
		}
		String trimmed = name.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}
}
