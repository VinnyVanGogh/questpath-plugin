package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which member of a category requirement to name, and whether the account
 * already satisfies it.
 *
 * <p>A great many quest requirements are a category rather than an item -- "A
 * light source", "Any pickaxe", "nails". questpath now ships those as a set
 * with each member's own level requirements (graph version 10); this is where
 * a set becomes one answer.
 *
 * <p><b>The three rules are the player's own, not inferred:</b>
 *
 * <ol>
 * <li>Already held wins. Check the bank, the inventory and worn equipment
 *     first; if the account holds any member it can use, the requirement is
 *     satisfied and the best held member is the one named. Buying a second
 *     pickaxe because the list did not look in the bank is exactly the errand
 *     this whole feature exists to stop sending people on.</li>
 * <li>Otherwise name the best usable member, not the cheapest.</li>
 * <li>And name a second, cheaper one beside it. The best member can be an
 *     absurd purchase -- a dragon pickaxe against a rune pickaxe -- and
 *     questpath does not know what anything costs. Showing the tier below
 *     hands that judgement to the player instead of guessing at it.</li>
 * </ol>
 *
 * <p><b>Which requirements bind is the caller's decision, and that is the
 * point of keeping each with its own skill.</b> A pickaxe states Mining and
 * Attack: Mining gates using it, Attack only gates wielding it, and a pickaxe
 * mines perfectly well from the inventory. An account hard-capping Attack --
 * the entire reason questpath exists -- can therefore use a rune pickaxe it
 * could never wield. A caller that knows a skill does not bind leaves it out
 * of the map it passes; see {@link #usable}.
 *
 * <p>Pure logic, no client and no graph lookups, so every rule above is
 * testable without a running game.
 */
final class ItemGroupChoice
{
	private ItemGroupChoice()
	{
	}

	/** What to say about one category requirement. */
	static final class Choice
	{
		private final QuestGraph.Alternative best;
		private final QuestGraph.Alternative fallback;
		private final boolean held;
		private final int usableCount;

		Choice(QuestGraph.Alternative best, QuestGraph.Alternative fallback,
			boolean held, int usableCount)
		{
			this.best = best;
			this.fallback = fallback;
			this.held = held;
			this.usableCount = usableCount;
		}

		/**
		 * The member to name: the best one already held when the account
		 * holds any, otherwise the best one it could buy. Null when nothing
		 * in the set is usable at the account's levels at all.
		 */
		QuestGraph.Alternative best()
		{
			return best;
		}

		/**
		 * A cheaper second option, or null when there is not one worth
		 * naming.
		 *
		 * <p>Null rather than a repeat of {@link #best()} when only one
		 * member is usable: drawing the same item twice would imply a choice
		 * that is not there. Also null when the best is already held --
		 * nothing needs buying, so there is no purchase to offer an
		 * alternative to.
		 */
		QuestGraph.Alternative fallback()
		{
			return fallback;
		}

		/** Whether {@link #best()} is something the account already holds. */
		boolean held()
		{
			return held;
		}

		/** How many members the account's levels allow at all. */
		int usableCount()
		{
			return usableCount;
		}

		/** Whether there is anything at all to say. */
		boolean any()
		{
			return best != null;
		}
	}

	/**
	 * Chooses what to name for one set.
	 *
	 * @param alternatives the set, in the wiki's own tier order -- <b>never
	 *     re-sorted here</b>, because that order is the wiki's ranking and
	 *     re-deriving it would replace a real ranking with an invented one
	 * @param levels the account's levels by skill name. A skill absent from
	 *     this map is treated as level 1, not as unlimited: reading silence
	 *     as "no limit" would offer a dragon pickaxe to an account with 1
	 *     Mining, and over-claiming is the direction this project refuses.
	 *     A caller that means to ignore a requirement it knows does not bind
	 *     passes {@code ignoredSkills} instead of lying about the level.
	 * @param ignoredSkills skills whose requirements do not actually gate
	 *     use. "Attack" for a tool held in the inventory is the case this
	 *     exists for.
	 * @param heldByName how many of each item the account holds, keyed by
	 *     lower-case item name, across bank, inventory and equipment
	 */
	static Choice choose(List<QuestGraph.Alternative> alternatives,
		Map<String, Integer> levels, java.util.Set<String> ignoredSkills,
		Map<String, Long> heldByName)
	{
		List<QuestGraph.Alternative> usable = usable(alternatives, levels, ignoredSkills);
		if (usable.isEmpty())
		{
			return new Choice(null, null, false, 0);
		}

		// Rule 1: the best thing already held beats anything to buy, and it is
		// checked against the whole usable set including the variants the
		// ladder excludes -- an infernal pickaxe in the bank is still a
		// pickaxe, even though nobody should be told to go and buy one.
		for (int i = usable.size() - 1; i >= 0; i--)
		{
			QuestGraph.Alternative candidate = usable.get(i);
			if (heldCount(candidate, heldByName) > 0)
			{
				return new Choice(candidate, null, true, usable.size());
			}
		}

		// Rules 2 and 3: the best it can buy, and the tier below as a hedge
		// against that being an absurd purchase. Chosen from the ladder only.
		List<QuestGraph.Alternative> ladder = usable(
			ladderOf(alternatives), levels, ignoredSkills);
		if (ladder.isEmpty())
		{
			// Everything usable is a variant. Nothing sensible to recommend
			// buying, but the set is still real -- say so rather than
			// pretending it is empty.
			return new Choice(usable.get(usable.size() - 1), null, false, usable.size());
		}
		QuestGraph.Alternative best = ladder.get(ladder.size() - 1);
		QuestGraph.Alternative fallback = ladder.size() > 1 ? ladder.get(ladder.size() - 2) : null;
		return new Choice(best, fallback, false, usable.size());
	}

	/**
	 * The tier ladder: the leading run of the table whose stated levels never
	 * go backwards.
	 *
	 * <p><b>A wiki table is a ladder followed by a junk drawer, and treating
	 * the whole thing as a ladder recommended a rare cosmetic.</b> The Pickaxe
	 * table reads bronze, iron, steel, black, mithril, adamant, rune, dragon,
	 * crystal -- ascending, exactly as it looks -- and then keeps going with
	 * gilded, 3rd Age, dragon (or), infernal. Those are ornamental and special
	 * variants appended after the ladder, and the gilded pickaxe restates the
	 * rune pickaxe's own 41 Mining. Taking the last usable row therefore told
	 * anyone who could use a rune pickaxe to buy a gilded one instead: not
	 * better, just rarer.
	 *
	 * <p>The break is visible in the data rather than needing a list of known
	 * variants: the ladder is monotonic and the appended rows are the point
	 * where the level drops back. So the ladder is the monotonic prefix, and
	 * within it later really does mean better -- an iron pickaxe and a bronze
	 * one both need level 1, and iron is the later row because it is the
	 * better tool.
	 *
	 * <p>Variants are excluded from what to <em>buy</em> only. They stay in
	 * the usable set for the held check, because owning one genuinely
	 * satisfies the requirement.
	 */
	static List<QuestGraph.Alternative> ladderOf(List<QuestGraph.Alternative> alternatives)
	{
		List<QuestGraph.Alternative> out = new ArrayList<>();
		if (alternatives == null)
		{
			return out;
		}
		int highestSoFar = 0;
		for (QuestGraph.Alternative alt : alternatives)
		{
			int level = highestLevel(alt);
			if (level < highestSoFar)
			{
				break;
			}
			highestSoFar = level;
			out.add(alt);
		}
		return out;
	}

	/** The highest level any of a member's requirements states, or 1 for none. */
	private static int highestLevel(QuestGraph.Alternative alt)
	{
		int highest = 1;
		for (QuestGraph.Requirement req : alt.requirements())
		{
			highest = Math.max(highest, req.level());
		}
		return highest;
	}

	/**
	 * The members the account's levels allow, in the wiki's own order.
	 *
	 * <p>Every stated requirement must be met, except those named in
	 * ignoredSkills. An absent skill counts as level 1 -- see {@link #choose}
	 * for why that direction and not the other.
	 */
	static List<QuestGraph.Alternative> usable(List<QuestGraph.Alternative> alternatives,
		Map<String, Integer> levels, java.util.Set<String> ignoredSkills)
	{
		List<QuestGraph.Alternative> out = new ArrayList<>();
		if (alternatives == null)
		{
			return out;
		}
		for (QuestGraph.Alternative alt : alternatives)
		{
			if (meetsAll(alt, levels, ignoredSkills))
			{
				out.add(alt);
			}
		}
		return out;
	}

	/**
	 * How many of a set the account holds in total.
	 *
	 * <p>Summed across members rather than taken as the largest single stack,
	 * because a count can genuinely be spread: six of "any nails" is
	 * satisfied by three bronze and four iron. For a tool wanted once the two
	 * readings agree anyway -- holding any member at all clears a count of
	 * one -- so the summing rule is the one that is right in both cases.
	 */
	static long heldAcross(List<QuestGraph.Alternative> alternatives, Map<String, Long> heldByName)
	{
		long total = 0;
		if (alternatives == null)
		{
			return 0;
		}
		for (QuestGraph.Alternative alt : alternatives)
		{
			total += heldCount(alt, heldByName);
		}
		return total;
	}

	private static long heldCount(QuestGraph.Alternative alt, Map<String, Long> heldByName)
	{
		if (heldByName == null)
		{
			return 0;
		}
		return heldByName.getOrDefault(alt.name().toLowerCase(Locale.ROOT), 0L);
	}

	private static boolean meetsAll(QuestGraph.Alternative alt,
		Map<String, Integer> levels, java.util.Set<String> ignoredSkills)
	{
		for (QuestGraph.Requirement req : alt.requirements())
		{
			if (ignoredSkills != null && ignoredSkills.contains(req.skill()))
			{
				continue;
			}
			int have = 1;
			if (levels != null && levels.containsKey(req.skill()))
			{
				have = levels.get(req.skill());
			}
			if (have < req.level())
			{
				return false;
			}
		}
		return true;
	}
}
