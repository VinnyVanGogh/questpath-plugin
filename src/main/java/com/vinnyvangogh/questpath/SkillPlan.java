package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;

/**
 * What it would take to reach a level in one skill, and how much of it quests
 * can do for you.
 *
 * <p>The question the sidebar could not answer until now: "I want 50
 * Woodcutting -- what does that actually cost me?" A skill guide answers it in
 * raw experience and pretends quests do not exist. The quest list answers it
 * per quest and never adds anything up. Neither one tells you the number that
 * decides your evening, which is how much you have to chop after the quests
 * have paid out.
 *
 * <p>Four bands, and the fourth is the one only this project can produce:
 *
 * <ul>
 * <li><b>Ready</b> -- quests you can start right now that grant this skill.
 * <li><b>Later</b> -- quests that grant it but are behind levels or other
 *     quests. Real experience, not yet reachable.
 * <li><b>Off the table</b> -- quests a build rule or an unreachable
 *     prerequisite has closed permanently. This experience does not exist for
 *     this account and never will, and a plan that quietly counts it is a plan
 *     that ends short.
 * <li><b>Train</b> -- whatever is left when the first two are subtracted. The
 *     honest number.
 * </ul>
 *
 * <p>Pure: a graph, an account and a target in, a plan out. No client, no
 * Swing, no I/O.
 */
class SkillPlan
{
	/** One quest's contribution to a skill. */
	static class Entry
	{
		private final String questName;
		private final double xp;
		private final QuestStatus status;

		Entry(String questName, double xp, QuestStatus status)
		{
			this.questName = questName;
			this.xp = xp;
			this.status = status;
		}

		String questName()
		{
			return questName;
		}

		double xp()
		{
			return xp;
		}

		QuestStatus status()
		{
			return status;
		}
	}

	private final String skill;
	private final int currentLevel;
	private final long currentXp;
	private final int targetLevel;

	private final List<Entry> ready = new ArrayList<>();
	private final List<Entry> later = new ArrayList<>();
	private final List<Entry> lost = new ArrayList<>();

	private int cappedAt = -1;

	private SkillPlan(String skill, int currentLevel, long currentXp, int targetLevel)
	{
		this.skill = skill;
		this.currentLevel = currentLevel;
		this.currentXp = currentXp;
		this.targetLevel = targetLevel;
	}

	/**
	 * Builds the plan.
	 *
	 * <p>Completed quests are excluded from every band on purpose: their
	 * experience is already inside {@code currentXp}, and listing it again as
	 * something quests can still give you would double-count the whole of your
	 * questing history.
	 */
	static SkillPlan of(QuestGraph graph, PlayerState account, QuestClassifier classifier,
		String skill, int targetLevel)
	{
		long currentXp = account.experienceIn(skill);
		SkillPlan plan = new SkillPlan(skill, account.levelIn(skill), currentXp, targetLevel);
		plan.cappedAt = capFor(account, skill);

		for (QuestGraph.Quest quest : graph.quests())
		{
			double xp = 0;
			for (QuestGraph.Reward reward : quest.rewards())
			{
				if (reward.skill().equalsIgnoreCase(skill))
				{
					xp += reward.xp();
				}
			}
			if (xp <= 0)
			{
				continue;
			}

			QuestStatus status = classifier.classify(quest);
			if (status == QuestStatus.COMPLETE)
			{
				// Already counted in currentXp.
				continue;
			}

			Entry entry = new Entry(quest.name(), xp, status);
			if (status.permanent())
			{
				plan.lost.add(entry);
			}
			else if (status == QuestStatus.ELIGIBLE || status == QuestStatus.BOOSTABLE)
			{
				plan.ready.add(entry);
			}
			else
			{
				plan.later.add(entry);
			}
		}

		// Biggest payout first in every band. A list of forty quests ordered
		// by name buries the two that would move the needle.
		plan.ready.sort((a, b) -> Double.compare(b.xp, a.xp));
		plan.later.sort((a, b) -> Double.compare(b.xp, a.xp));
		plan.lost.sort((a, b) -> Double.compare(b.xp, a.xp));
		return plan;
	}

	/**
	 * The level a build rule pins this skill to, or -1 for none.
	 *
	 * Only blocking rules with a level count, and both halves matter. A
	 * non-blocking rule is an intention rather than a wall -- reporting it as
	 * a ceiling would call a target impossible when it is merely against the
	 * plan. And SOFT_CAP carries no level at all, so treating its zero as a
	 * cap would pin every soft-capped skill to level zero and declare every
	 * target a build break.
	 */
	private static int capFor(PlayerState account, String skill)
	{
		BuildRule rule = account.ruleFor(skill);
		if (rule == null || !rule.blocking() || !rule.hasValue())
		{
			return -1;
		}
		return rule.value();
	}

	String skill()
	{
		return skill;
	}

	int currentLevel()
	{
		return currentLevel;
	}

	int targetLevel()
	{
		return targetLevel;
	}

	List<Entry> ready()
	{
		return ready;
	}

	List<Entry> later()
	{
		return later;
	}

	List<Entry> lost()
	{
		return lost;
	}

	/** Whether the target is already met. */
	boolean reached()
	{
		return currentLevel >= targetLevel;
	}

	/**
	 * The build cap on this skill, or -1 when nothing caps it.
	 *
	 * @see #targetBreaksBuild()
	 */
	int cappedAt()
	{
		return cappedAt;
	}

	/** Whether the chosen target would break the account's own rules. */
	boolean targetBreaksBuild()
	{
		return cappedAt >= 0 && targetLevel > cappedAt;
	}

	/** Experience between here and the target. Zero once reached. */
	long xpNeeded()
	{
		return reached() ? 0 : XpTable.xpBetween(currentLevel, targetLevel) - intoLevel();
	}

	/**
	 * How far into the current level the account already is.
	 *
	 * Without this the plan asks for a full level's experience the moment you
	 * are one point into it, which overstates the remaining work by up to a
	 * whole level -- most visibly at the high end, where one level is more
	 * experience than the first forty put together.
	 */
	private long intoLevel()
	{
		return currentXp - XpTable.xpFor(currentLevel);
	}

	private static long sum(List<Entry> entries)
	{
		double total = 0;
		for (Entry entry : entries)
		{
			total += entry.xp;
		}
		return Math.round(total);
	}

	long readyXp()
	{
		return sum(ready);
	}

	long laterXp()
	{
		return sum(later);
	}

	/** Experience closed off permanently by this account's own rules. */
	long lostXp()
	{
		return sum(lost);
	}

	/**
	 * What is left to train after every reachable quest has paid out.
	 *
	 * Counts ready and later together, and deliberately not the lost band: a
	 * number that assumed experience the account can never earn would be
	 * exactly the confidently wrong headline this project exists to avoid.
	 */
	long xpToTrain()
	{
		return Math.max(0L, xpNeeded() - readyXp() - laterXp());
	}

	/** Whether quests alone would carry the account to the target. */
	boolean questsAreEnough()
	{
		return !reached() && xpToTrain() == 0;
	}
}
