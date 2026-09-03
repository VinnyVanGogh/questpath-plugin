package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything standing between the account and one quest, all the way down.
 *
 * <p>"After more work" is an honest label and a useless one on its own. The
 * work is a specific list -- these four quests, in this order, and 41
 * Thieving along the way -- and that list is exactly what the account already
 * has the data to produce and has never been asked for.
 *
 * <p>Transitive on purpose. A quest blocked by one prerequisite that is
 * itself blocked by three more is not "one quest away", and reporting only
 * the immediate blocker is the kind of true-but-misleading answer that makes
 * someone start something they cannot finish. The skill gaps are gathered the
 * same way: a level a prerequisite needs is a level this quest needs, whatever
 * the quest's own page says.
 *
 * <p>Pure. Graph, account and classifier in; two lists out.
 */
class QuestBlockers
{
	/** One skill the account is short on, and by how much. */
	static class Gap
	{
		private final String skill;
		private final int have;
		private final int need;
		private final boolean boostable;

		Gap(String skill, int have, int need, boolean boostable)
		{
			this.skill = skill;
			this.have = have;
			this.need = need;
			this.boostable = boostable;
		}

		String skill()
		{
			return skill;
		}

		int have()
		{
			return have;
		}

		int need()
		{
			return need;
		}

		/** Whether a boost covers it, so it is not really training at all. */
		boolean boostable()
		{
			return boostable;
		}

		int shortfall()
		{
			return Math.max(0, need - have);
		}
	}

	private final List<String> quests = new ArrayList<>();
	private final List<Gap> skills = new ArrayList<>();

	private QuestBlockers()
	{
	}

	/**
	 * Walks the quest and everything it depends on.
	 *
	 * <p>Post-order, so the returned quest list is in an order that can
	 * actually be followed: a prerequisite always appears before the thing
	 * that needs it. Alphabetical or discovery order would both name the same
	 * quests and give no help with the only decision the list informs, which
	 * is what to start.
	 */
	static QuestBlockers of(QuestGraph graph, PlayerState account, QuestGraph.Quest quest)
	{
		QuestBlockers blockers = new QuestBlockers();
		Map<String, Gap> worst = new LinkedHashMap<>();
		blockers.walk(graph, account, quest, new LinkedHashSet<>(), worst, true);

		blockers.skills.addAll(worst.values());
		// Biggest shortfall first: the level furthest away is the one that
		// decides whether this is an evening or a fortnight.
		blockers.skills.sort((a, b) -> Integer.compare(b.shortfall(), a.shortfall()));
		return blockers;
	}

	private void walk(QuestGraph graph, PlayerState account, QuestGraph.Quest quest,
		Set<Long> visiting, Map<String, Gap> worst, boolean isRoot)
	{
		if (!visiting.add(quest.id()))
		{
			// A prerequisite cycle. The classifier treats one as unreachable;
			// here it is simply somewhere already being walked, and walking it
			// again would not add a requirement that is not already collected.
			return;
		}

		for (long prerequisiteId : quest.prerequisites())
		{
			QuestGraph.Quest prerequisite = graph.get(prerequisiteId);
			if (prerequisite == null || account.isComplete(prerequisite.name()))
			{
				continue;
			}
			walk(graph, account, prerequisite, visiting, worst, false);
			if (!quests.contains(prerequisite.name()))
			{
				quests.add(prerequisite.name());
			}
		}

		for (QuestGraph.Requirement requirement : quest.requirements())
		{
			int have = account.levelIn(requirement.skill());
			if (have >= requirement.level())
			{
				continue;
			}
			// One entry per skill, keeping the highest level anything in the
			// chain asks for. Listing 30 Thieving and 41 Thieving as two rows
			// would double-count work that is done once.
			Gap existing = worst.get(requirement.skill().toLowerCase());
			if (existing == null || requirement.level() > existing.need())
			{
				worst.put(requirement.skill().toLowerCase(),
					new Gap(requirement.skill(), have, requirement.level(),
						requirement.boostable()));
			}
		}
	}

	/**
	 * Prerequisite quests still to do, deepest first.
	 *
	 * Excludes the quest itself: it is the thing being asked about, not part
	 * of its own cost.
	 */
	List<String> quests()
	{
		return quests;
	}

	/** Levels still to reach, across the quest and everything under it. */
	List<Gap> skills()
	{
		return skills;
	}

	boolean isEmpty()
	{
		return quests.isEmpty() && skills.isEmpty();
	}
}
