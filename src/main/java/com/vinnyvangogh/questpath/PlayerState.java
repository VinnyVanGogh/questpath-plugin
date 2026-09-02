package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the verdict engine needs to know about one account.
 *
 * <p>Assembled from three places, all of which the plugin already has: levels
 * and experience from the client, completed quests from the client's own quest
 * varbits, and build rules from questpath's push response. That last one is
 * why the Quests tab needs a sync — the client knows what you have done, but
 * only questpath knows what you have decided not to do.
 *
 * <p>Skill names are matched case-insensitively against whatever the graph and
 * the rules use, because three sources spell them three ways and none of them
 * is wrong.
 */
class PlayerState
{
	private final Map<String, Integer> levels;
	private final Map<String, Long> experience;
	private final Set<Long> completed;
	private final Map<String, BuildRule> rules;

	PlayerState(Map<String, Integer> levels, Map<String, Long> experience,
		Set<Long> completed, List<BuildRule> rules)
	{
		this.levels = lowerKeys(levels);
		this.experience = lowerKeys(experience);
		this.completed = completed == null ? Collections.emptySet() : new HashSet<>(completed);

		Map<String, BuildRule> bySkill = new HashMap<>();
		if (rules != null)
		{
			for (BuildRule rule : rules)
			{
				bySkill.put(rule.skillName().toLowerCase(), rule);
			}
		}
		this.rules = bySkill;
	}

	private static <T> Map<String, T> lowerKeys(Map<String, T> source)
	{
		Map<String, T> out = new HashMap<>();
		if (source != null)
		{
			for (Map.Entry<String, T> entry : source.entrySet())
			{
				out.put(entry.getKey().toLowerCase(), entry.getValue());
			}
		}
		return out;
	}

	/**
	 * The account's level in a skill.
	 *
	 * A skill the account has said nothing about is at its start level, not
	 * zero: zero is not a level any skill can hold, and treating it as one
	 * would make every requirement look unmet.
	 */
	int levelIn(String skill)
	{
		Integer level = levels.get(skill.toLowerCase());
		if (level != null)
		{
			return level;
		}
		return "hitpoints".equalsIgnoreCase(skill) ? 10 : 1;
	}

	long experienceIn(String skill)
	{
		Long xp = experience.get(skill.toLowerCase());
		return xp == null ? 0L : xp;
	}

	boolean isComplete(long questId)
	{
		return completed.contains(questId);
	}

	/** The account's rule for a skill, or null when it has declared none. */
	BuildRule ruleFor(String skill)
	{
		return rules.get(skill.toLowerCase());
	}

	/**
	 * Whether any build rules are known.
	 *
	 * The Quests tab needs this: with no rules, the engine cannot report
	 * BUILD_BLOCKED for anything, so every verdict it gives is "as far as I
	 * know" rather than an answer. Saying so beats quietly rendering a pure's
	 * quest list as if it were a main's.
	 */
	boolean hasRules()
	{
		return !rules.isEmpty();
	}
}
