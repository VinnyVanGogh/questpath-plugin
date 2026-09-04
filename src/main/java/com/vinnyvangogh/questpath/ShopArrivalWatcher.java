package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Notices the moment a shop opens selling something the plan still needs.
 *
 * <p><b>This is not walking-distance proximity.</b> The brief this class was
 * built against asked for a prompt when the player is <i>near</i> a shop --
 * true world-coordinate proximity, computed before the player ever opens
 * anything. That needs a shop's location, and it did not exist anywhere
 * this plugin could reach when this class was written: the bundled quest
 * bundle's item {@code sources} field was a display string ("Shop: Tynan's
 * Fishing Supplies (2 Coins)"), never a coordinate, and nothing questpath
 * pushed back over HTTP carried one either.
 *
 * <p>Graph version 7 has since added exactly that -- {@link QuestGraph#shops()}
 * and {@link QuestGraph.Item#shopNames()}, wired into {@link
 * ShortestPathBridge}'s target set -- so a genuine walking-distance version
 * of this feature is now buildable. It was not built here: this class still
 * only answers "at a shop", which is a real, live fact and a different,
 * smaller claim than "near a shop", and rewriting it into a proximity
 * checker is a separate feature nobody has asked for yet, not a bug fix.
 *
 * <p>What this class answers instead is the nearest honest question the
 * client can actually see the answer to: <i>is a shop open right now, and
 * does it sell something outstanding</i>. That is "at a shop", not "near a
 * shop" -- a real, live fact rather than an estimate, just a narrower one
 * than distance would give. A player still has to have walked up and opened
 * the shop for this to fire at all.
 *
 * <p>Pure state machine, like {@link ItemCompletionWatcher}: handed
 * successive shop-open observations, it answers what is worth saying since
 * the last one. Kept testable without a running game for the same reason
 * that class is -- the interesting part is which transitions count (the
 * moment of opening) and which do not (every frame afterward, while the
 * shop stays open).
 */
class ShopArrivalWatcher
{
	/**
	 * Whether a shop was open at the previous observation.
	 *
	 * The reason anything is announced only once per opening rather than
	 * once per frame: a shop interface, once open, is re-observed every
	 * single game tick for as long as it stays open, and without this the
	 * same line would print dozens of times over one shopping trip.
	 */
	private boolean openBefore;

	/** Forgets everything. Called on logout: the next login may be another account. */
	void reset()
	{
		openBefore = false;
	}

	/**
	 * What is worth announcing since the last observation.
	 *
	 * @param shopOpenNow whether a shop interface is open right now
	 * @param shopItemIds the game item ids currently in that shop's stock,
	 *     ignored (and may be empty) when {@code shopOpenNow} is false
	 * @param items the current shopping list, or null when none is loaded
	 *     yet -- treated the same as "nothing to say", never guessed at
	 * @return outstanding items this shop sells, non-empty only on the tick
	 *     a shop transitions from closed to open, and only ever returned
	 *     once per opening -- the very next call for the same still-open
	 *     shop returns empty, exactly as {@link ItemCompletionWatcher}
	 *     never repeats a completion it has already reported
	 */
	List<QuestItems.Total> observe(boolean shopOpenNow, Set<Integer> shopItemIds, QuestItems items)
	{
		boolean justOpened = shopOpenNow && !openBefore;
		openBefore = shopOpenNow;

		if (!justOpened || items == null || shopItemIds == null || shopItemIds.isEmpty())
		{
			return Collections.emptyList();
		}

		List<QuestItems.Total> matches = new ArrayList<>();
		for (QuestItems.Total total : items.totals())
		{
			// itemId() <= 0 is questpath's own catalogue failing to resolve
			// the item -- never equal to a real shop slot's id, so it could
			// never match by accident, but excluded explicitly all the same;
			// see QuestItems.Total's own doc for why zero is never a real id.
			if (total.itemId() > 0 && !total.satisfied() && shopItemIds.contains(total.itemId()))
			{
				matches.add(total);
			}
		}
		return matches;
	}
}
