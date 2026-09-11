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

		/**
		 * Whether enough has arrived to satisfy a need of this size.
		 *
		 * <p>Partial fills reach the collection box as they arrive, so an
		 * offer for ten can already cover a need for one long before it
		 * finishes. Waiting for {@link #complete} to say so would leave the
		 * panel telling the player to keep waiting for something they could
		 * collect right now.
		 */
		boolean covers(long needed)
		{
			return bought > 0 && bought >= needed;
		}
	}

	private final Map<Integer, Order> bySlot = new HashMap<>();
	private final Map<String, Long> collected = new HashMap<>();

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
			//
			// Whatever the slot had already bought has now left it, and where
			// it went may be somewhere the plugin cannot see -- see
			// collected() for the bank case this exists for. Credited before
			// the slot is dropped, because the slot is the only record of how
			// many there were.
			Order leaving = bySlot.get(slot);
			if (leaving != null && leaving.bought() > 0)
			{
				String key = normalise(leaving.name());
				if (key != null)
				{
					collected.merge(key, (long) leaving.bought(), Long::sum);
				}
			}
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
		collected.clear();
	}

	/**
	 * What has been collected out of the slots, by lower-case item name.
	 *
	 * <p><b>Why this has to be remembered at all.</b> While an offer sits in a
	 * slot, the plugin knows the items are bought because it can see the
	 * offer. Collect them and the slot empties -- and if they went to the
	 * bank, that is the end of the evidence, because the bank's contents are
	 * only visible once the bank has been opened this session. So an item
	 * moved out of "Queued" and straight back into "Left", as though it had
	 * never been bought. Reported from play, doing exactly that.
	 *
	 * <p>This is the memory of it: whatever a slot had bought when it went
	 * empty, credited to the item.
	 *
	 * <p><b>Merged into what the account holds with max, never by adding.</b>
	 * Collecting to the inventory is visible to the plugin, so the same items
	 * would be counted once by the container and once by this tally; taking
	 * the larger of the two is right in both directions and is exactly what
	 * QuestItems.effectiveHeld already does with the last sync's item counts,
	 * for the same reason.
	 *
	 * <p><b>Where max under-reports, and why that is the right way to be
	 * wrong.</b> If the bank was already seen this session holding eight, and
	 * two more are collected into it without the bank being reopened, this
	 * reports eight rather than ten. That is the behaviour as it is today --
	 * the collected pair are invisible either way -- so nothing regresses,
	 * and the failure is "you are told to buy something you already have"
	 * rather than "an item you still need vanishes off the list". Only the
	 * second one loses a quest.
	 */
	java.util.Map<String, Long> collected()
	{
		return java.util.Collections.unmodifiableMap(collected);
	}

	/**
	 * Forgets the collected tally, for when the bank itself has been seen and
	 * is now the authority on what the account holds.
	 */
	void forgetCollected()
	{
		collected.clear();
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
