package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether this account can walk into a shop and be served.
 *
 * <p>The report this closes, from play: the shopping list sent the player to
 * the Fishing Guild Shop for a big fishing net. The guild wants 68 Fishing
 * and the account had 59. A shop in Catherby sells the same net and they
 * could have walked straight in. questpath named the one they could not use
 * and never mentioned the one they could -- not by choosing badly, but
 * because the bundle carried no way to know a shop was behind anything at
 * all. Graph 9 carries it; this reads it.
 *
 * <p>It is the project's oldest rule reaching a fourth surface: never
 * suggest something the account cannot act on. Diary tiers, then towns
 * behind quests, then quest items, then shops.
 *
 * <p><b>Boosts are consulted, not assumed.</b> A gate the wiki marks
 * boostable is answered with {@link Boosts}, the same sizing the quest
 * classifier already uses, so the two cannot disagree about whether a potion
 * closes a gap. A shop reachable only with a boost is its own verdict rather
 * than being folded into either "open" or "blocked": walking there with a
 * fishing potion is a real plan, and walking there without one is a wasted
 * trip.
 */
final class ShopReachability
{
	private ShopReachability()
	{
	}

	/** How reachable a shop is, worst case first. */
	enum Verdict
	{
		/** Nothing stands in the way. */
		OPEN,

		/** Reachable, but only after drinking something. */
		BOOSTABLE,

		/** Not reachable at all right now. */
		BLOCKED
	}

	/** The verdict, and something a person can read about why. */
	static final class Result
	{
		private final Verdict verdict;
		private final String reason;

		Result(Verdict verdict, String reason)
		{
			this.verdict = verdict;
			this.reason = reason;
		}

		/** How reachable. */
		Verdict verdict()
		{
			return verdict;
		}

		/**
		 * Why, in the shop's own terms ("68 Fishing", "Zogre Flesh Eaters"),
		 * or empty when the shop is simply open. Never a guess: a gate this
		 * class could not evaluate is reported as blocked with the raw
		 * requirement rather than silently passed.
		 */
		String reason()
		{
			return reason;
		}

		/** Whether a trip here is worth making at all. */
		boolean usable()
		{
			return verdict != Verdict.BLOCKED;
		}
	}

	private static final Result OPEN = new Result(Verdict.OPEN, "");

	/**
	 * Judges one shop.
	 *
	 * @param shop the shop, whose {@code reachability} is null for most
	 * @param player the account's levels and quest progress
	 * @param graph the bundle, for resolving quest ids to names
	 * @param boosts the same boost table the classifier uses; may be null,
	 *     in which case a boostable gate is judged on the base level alone
	 *     rather than assumed to be passable
	 */
	static Result of(QuestGraph.Shop shop, PlayerState player, QuestGraph graph, Boosts boosts)
	{
		if (shop == null || player == null)
		{
			return OPEN;
		}
		QuestGraph.Reachability gates = shop.reachability();
		if (gates == null)
		{
			return OPEN;
		}

		List<String> blocked = new ArrayList<>();
		List<String> boostable = new ArrayList<>();

		for (QuestGraph.SkillGate gate : gates.requiredSkills())
		{
			int have = player.levelIn(gate.skill());
			if (have >= gate.level())
			{
				continue;
			}
			String need = gate.level() + " " + gate.skill();
			if (gate.boostable() && boosts != null
				&& have + boosts.bestFor(gate.skill(), have).levels() >= gate.level())
			{
				boostable.add(need);
			}
			else
			{
				blocked.add(need);
			}
		}

		for (Integer questId : gates.requiredQuestIds())
		{
			if (questId == null)
			{
				continue;
			}
			QuestGraph.Quest quest = graph == null ? null : graph.get(questId);
			if (quest == null)
			{
				// The bundle names a quest it does not carry. Blocked rather
				// than waved through: an unresolvable gate is still a gate,
				// and the failure this whole class exists to prevent is
				// treating an unknown as passable.
				blocked.add("an unknown quest");
				continue;
			}
			boolean startedIsEnough = gates.startedOnlyQuestIds().contains(questId);
			boolean satisfied = startedIsEnough
				? player.hasStarted(quest.name())
				: player.isComplete(quest.name());
			if (!satisfied)
			{
				blocked.add(quest.name());
			}
		}

		if (!blocked.isEmpty())
		{
			return new Result(Verdict.BLOCKED, String.join(", ", blocked));
		}
		if (!boostable.isEmpty())
		{
			return new Result(Verdict.BOOSTABLE, String.join(", ", boostable));
		}
		return OPEN;
	}
}
