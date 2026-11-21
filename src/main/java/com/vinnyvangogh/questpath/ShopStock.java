package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What each shop was last seen holding, per item, with the time it was seen.
 *
 * <p>The report this closes: the route named shops that stock an item "in
 * principle" even when the shelf was empty. A shop's own window is the only
 * live source this plugin has, so every time one is open its stock is
 * recorded here, and a shop whose record for an item says zero stops being a
 * source for that item.
 *
 * <p>Three answers, deliberately not two. A recorded zero is {@link
 * State#EMPTY} until {@link #EMPTY_TTL} has passed, because shops restock on
 * their own and a record that never aged out would exclude a shop forever.
 * After that the record is no longer evidence either way: {@link
 * State#MAY_BE_OUT} keeps the shop as a source but says so, rather than
 * silently promising stock nobody has seen. No record at all is {@link
 * State#UNKNOWN}, the bundled base-stock answer, and is routed to as before.
 *
 * <p>Pure: every {@link Instant} is a parameter. Session state, cleared on
 * logout -- stock seen on one world means nothing on another.
 */
final class ShopStock
{
	/** How long a seen-empty shelf is trusted to still be empty. */
	static final Duration EMPTY_TTL = Duration.ofMinutes(5);

	enum State
	{
		/** Never seen, or seen stocked: routed to as the bundle says. */
		UNKNOWN,
		/** Seen at zero, recently enough to believe it. Not a source. */
		EMPTY,
		/** Seen at zero, long enough ago that it may have restocked. Still a source, flagged. */
		MAY_BE_OUT
	}

	private static final class Seen
	{
		final long quantity;
		final Instant at;

		Seen(long quantity, Instant at)
		{
			this.quantity = quantity;
			this.at = at;
		}
	}

	private final Map<String, Seen> seen = new HashMap<>();

	/**
	 * Records one item's quantity as read from an open shop window. Later
	 * reads replace earlier ones, so a restock re-includes the shop on the
	 * next visit.
	 */
	void record(String shop, String item, long quantity, Instant now)
	{
		if (shop == null || item == null || now == null)
		{
			return;
		}
		seen.put(key(shop, item), new Seen(Math.max(0L, quantity), now));
	}

	/** Where the item stands at the shop, as of {@code now}. */
	State state(String shop, String item, Instant now)
	{
		if (shop == null || item == null)
		{
			return State.UNKNOWN;
		}
		Seen record = seen.get(key(shop, item));
		if (record == null || record.quantity > 0)
		{
			return State.UNKNOWN;
		}
		if (now == null || Duration.between(record.at, now).compareTo(EMPTY_TTL) < 0)
		{
			return State.EMPTY;
		}
		return State.MAY_BE_OUT;
	}

	/** True when the shop must not be offered as a source for the item. */
	boolean excludes(String shop, String item, Instant now)
	{
		return state(shop, item, now) == State.EMPTY;
	}

	/** Forgets everything -- on logout and when the plugin stops. */
	void clear()
	{
		seen.clear();
	}

	private static String key(String shop, String item)
	{
		return shop.toLowerCase(Locale.ROOT) + "|" + item.toLowerCase(Locale.ROOT);
	}
}
