package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notices the moment an outstanding item stops being outstanding.
 *
 * <p>Every other surface in this plugin has to be looked at. The moment worth
 * knowing about -- the eleventh log going into your bags -- is precisely the
 * moment you are looking at a tree instead, and a panel that quietly turns
 * green behind you is a panel nobody reads at the only time it mattered.
 *
 * <p>Pure state machine, no client and no chat: it is handed successive
 * {@link QuestItems} and answers what has just been completed since the last
 * one. That keeps the interesting part -- which transitions count, and more
 * importantly which do not -- testable without a running game.
 */
class ItemCompletionWatcher
{
	/**
	 * Items that were short at the previous observation.
	 *
	 * Null until the first one, which is the difference between "these ninety
	 * items are outstanding" and "these ninety items just became outstanding".
	 * Without it, logging in would announce every satisfied item on the plan
	 * at once.
	 */
	private Set<String> shortBefore;
	private boolean bankSeenBefore;

	/** One item that has just gone from short to fully held. */
	static class Completion
	{
		private final String itemName;
		private final int required;
		private final String quests;

		Completion(String itemName, int required, String quests)
		{
			this.itemName = itemName;
			this.required = required;
			this.quests = quests;
		}

		String itemName()
		{
			return itemName;
		}

		int required()
		{
			return required;
		}

		/** The quests wanting it, already phrased -- see QuestItems.phrase. */
		String quests()
		{
			return quests;
		}

		/**
		 * The words before the highlighted item: "you now have all " when a
		 * pile was wanted, "you now have the " when a single one was. "All 1
		 * clay" is the line this method exists to stop.
		 */
		String leadIn()
		{
			return required == 1 ? "you now have the " : "you now have all ";
		}

		/** The highlighted part: "3 clay", or just "clay" when one was enough. */
		String subject()
		{
			return required == 1 ? itemName : required + " " + itemName;
		}
	}

	/** Forgets everything. Called on logout: the next login may be another account. */
	void reset()
	{
		shortBefore = null;
		bankSeenBefore = false;
	}

	/**
	 * What has been completed since the last call.
	 *
	 * Three conditions, and the two negative ones are the ones doing the work:
	 *
	 * <ul>
	 * <li>It has to have been short last time. Otherwise the first observation
	 *     announces the whole plan.
	 * <li>Some of it has to be <em>on you</em>. An item that became satisfied
	 *     without any of it in your bags was not collected, it was found --
	 *     questpath learning about a bank it had not seen, or a sync arriving.
	 *     "You now have all 11 oak logs" is a lie about the last few seconds
	 *     even when it is true about the account.
	 * <li>The bank must not have been opened on this same pass. Opening a bank
	 *     turns one carried log into eleven owned ones for a hundred items at
	 *     once, and every one of them would otherwise pass the test above.
	 * </ul>
	 */
	List<Completion> observe(QuestItems items)
	{
		List<Completion> out = new ArrayList<>();
		if (items == null)
		{
			return out;
		}

		Set<String> shortNow = new HashSet<>();
		boolean bankJustOpened = items.bankSeen() && !bankSeenBefore;

		for (QuestItems.Total total : items.totals())
		{
			String key = total.name().toLowerCase();
			if (!total.satisfied())
			{
				shortNow.add(key);
				continue;
			}
			if (shortBefore == null || bankJustOpened)
			{
				continue;
			}
			if (!shortBefore.contains(key) || total.carried() <= 0)
			{
				continue;
			}
			out.add(new Completion(total.name(), total.required(), total.questSummary()));
		}

		shortBefore = shortNow;
		bankSeenBefore = items.bankSeen();
		return out;
	}
}
