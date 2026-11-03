package com.vinnyvangogh.questpath;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every way one quest breaks one account's blocking build rules, with the
 * reason attached.
 *
 * <p>This is {@link QuestClassifier}'s build guard with its working shown:
 * the classifier only needs "does it break", and asks {@link #of} whether the
 * list is empty, so the two can never disagree. The Group tab needs the
 * reasons -- "Bob would get 18,650 Defence xp, capped at 1" -- because a
 * group deciding what to do next has to know who it would hurt and how.
 *
 * <p>Both sides, which is the correction the classifier exists for: a reward
 * that pushes a capped skill past its cap, and a requirement that demands a
 * level above one. Boostable is deliberately not consulted on the
 * requirement side -- a boost is relative to the base level, so a capped skill
 * cannot reach a boostable requirement above the cap.
 */
final class BuildBreaches
{
	enum Kind
	{
		/** The quest grants experience the rule forbids. */
		REWARD,
		/** The quest demands a level the rule forbids reaching. */
		REQUIREMENT
	}

	/** One breach: the skill, what the quest does to it, and the rule it breaks. */
	static final class Breach
	{
		private final String skill;
		private final Kind kind;
		private final long amount;
		private final BuildRule rule;

		Breach(String skill, Kind kind, long amount, BuildRule rule)
		{
			this.skill = skill;
			this.kind = kind;
			this.amount = amount;
			this.rule = rule;
		}

		/** The skill as the graph names it. */
		String skill()
		{
			return skill;
		}

		Kind kind()
		{
			return kind;
		}

		/** Experience granted for a reward, the level demanded for a requirement. */
		long amount()
		{
			return amount;
		}

		BuildRule rule()
		{
			return rule;
		}

		/** "18,650 Defence xp (capped at 1)" or "needs 30 Defence (capped at 1)". */
		String describe()
		{
			String skillName = skill.isEmpty() ? skill
				: skill.substring(0, 1).toUpperCase(Locale.ROOT) + skill.substring(1).toLowerCase(Locale.ROOT);
			String what = kind == Kind.REWARD
				? NumberFormat.getIntegerInstance(Locale.ROOT).format(amount) + " " + skillName + " xp"
				: "needs " + amount + " " + skillName;
			return what + " (" + rule.describe() + ")";
		}
	}

	private BuildBreaches()
	{
	}

	static List<Breach> of(QuestGraph.Quest quest, PlayerState account)
	{
		List<Breach> out = new ArrayList<>();
		for (QuestGraph.Reward reward : quest.rewards())
		{
			BuildRule rule = account.ruleFor(reward.skill());
			if (rule == null || !rule.blocking())
			{
				continue;
			}
			long xp = (long) Math.floor(reward.xp());
			if (BuildRule.NO_XP.equals(rule.type()))
			{
				// Any experience at all. XP that does not raise the level is
				// still XP, and a NO_XP account has said no to all of it.
				out.add(new Breach(reward.skill(), Kind.REWARD, xp, rule));
				continue;
			}
			if (BuildRule.HARD_CAP.equals(rule.type()) && rule.hasValue())
			{
				long after = account.experienceIn(reward.skill()) + xp;
				if (XpTable.levelFor(after) > rule.value())
				{
					out.add(new Breach(reward.skill(), Kind.REWARD, xp, rule));
				}
			}
		}

		for (QuestGraph.Requirement requirement : quest.requirements())
		{
			BuildRule rule = account.ruleFor(requirement.skill());
			if (rule == null || !rule.blocking())
			{
				continue;
			}
			int cap = BuildRule.NO_XP.equals(rule.type()) ? startLevelFor(requirement.skill()) : rule.value();
			if (BuildRule.HARD_CAP.equals(rule.type()) && !rule.hasValue())
			{
				continue;
			}
			if (requirement.level() > cap)
			{
				out.add(new Breach(requirement.skill(), Kind.REQUIREMENT, requirement.level(), rule));
			}
		}
		return out;
	}

	/**
	 * The level a NO_XP skill can still legitimately be at.
	 *
	 * Hitpoints starts at 10 and every other skill at 1, so a NO_XP Hitpoints
	 * account still meets a requirement of 10. Treating the floor as 1 would
	 * block quests that ask for nothing the account has not already got.
	 */
	private static int startLevelFor(String skill)
	{
		return "hitpoints".equalsIgnoreCase(skill) ? 10 : 1;
	}
}
