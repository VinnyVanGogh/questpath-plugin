package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which "Take" entries in a freshly-built game menu belong at the
 * top -- RuneLite's left-click default -- because questpath's own item list
 * still counts them as short, and in what order when more than one such
 * item shares a tile.
 *
 * <p>Kept free of every RuneLite type on purpose. The event handler that
 * calls this (see {@code QuestpathPlugin#onPostMenuSort}) reduces the live
 * {@code MenuEntry[]} down to {@link Candidate}s and translates the answer
 * back into an array permutation; nothing here needs a {@code MenuEntry},
 * a {@code Client}, or a mock of either to be exercised, the same reasoning
 * {@code BankTabLayout.byClosestToDone} follows for the bank row order.
 *
 * <p>The RuneLite side of this: {@code PostMenuSort} fires once per menu
 * build, after the client's own native sort (which is what already applied
 * any {@code MenuEntry#setDeprioritized(true)} from a plugin such as core's
 * Ground Items) and before the menu is shown, so it is the last point a
 * plugin can still move an entry -- the same hook RuneLite's own
 * {@code MenuEntrySwapperPlugin} uses for its own swaps. Entries in a
 * RuneLite menu array run bottom-to-top: index {@code length - 1} is what a
 * left click actually runs.
 */
final class GroundItemTakePriority
{
	private GroundItemTakePriority()
	{
	}

	/**
	 * One "Take" entry, in the menu as it was built, for a ground item
	 * questpath's plan still counts as short.
	 */
	static final class Candidate
	{
		private final int index;
		private final int itemId;
		private final long shortfall;

		Candidate(int index, int itemId, long shortfall)
		{
			this.index = index;
			this.itemId = itemId;
			this.shortfall = shortfall;
		}

		/** This entry's position in the menu array that was scanned. */
		int index()
		{
			return index;
		}

		int itemId()
		{
			return itemId;
		}

		long shortfall()
		{
			return shortfall;
		}
	}

	/**
	 * Deterministic order among several wanted items sharing a tile: most
	 * outstanding first, ties broken by the lower item id so the same tile
	 * always resolves the same way frame to frame, independent of whatever
	 * order the client happened to add the entries in.
	 */
	private static final Comparator<Candidate> MOST_WANTED_FIRST =
		Comparator.comparingLong(Candidate::shortfall).reversed()
			.thenComparingInt(Candidate::itemId);

	/**
	 * Returns the menu, as a permutation of its original indexes, with
	 * every candidate's "Take" entry moved above everything that is not a
	 * candidate -- including "Walk here" and any other, unwanted ground
	 * item's own "Take" -- and ordered among themselves by
	 * {@link #MOST_WANTED_FIRST}, most outstanding ending up last: the
	 * left-click default.
	 *
	 * <p>Every entry not named by a candidate keeps its original relative
	 * order; only the block of candidates moves. Returns {@code null} when
	 * there is nothing to promote, so a caller can skip rebuilding and
	 * re-submitting the array on the (overwhelmingly common) frame where no
	 * wanted item is on screen.
	 *
	 * @param entryCount the length of the menu array the candidates were
	 *                   read from
	 * @param candidates every candidate found in that array; may be empty
	 */
	static int[] reorder(int entryCount, List<Candidate> candidates)
	{
		if (candidates.isEmpty())
		{
			return null;
		}

		Set<Integer> candidateIndexes = new HashSet<>();
		for (Candidate candidate : candidates)
		{
			candidateIndexes.add(candidate.index());
		}

		List<Candidate> sorted = new ArrayList<>(candidates);
		sorted.sort(MOST_WANTED_FIRST);

		int[] order = new int[entryCount];
		int pos = 0;
		for (int i = 0; i < entryCount; i++)
		{
			if (!candidateIndexes.contains(i))
			{
				order[pos++] = i;
			}
		}
		// Appended least-wanted first, most-wanted last, so the single most
		// outstanding candidate lands at order[entryCount - 1] -- the
		// left-click default -- with the rest of the group directly below
		// it in MOST_WANTED_FIRST order read top to bottom.
		for (int i = sorted.size() - 1; i >= 0; i--)
		{
			order[pos++] = sorted.get(i).index();
		}
		return order;
	}
}
