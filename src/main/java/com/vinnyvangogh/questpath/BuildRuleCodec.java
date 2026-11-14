package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads, writes and edits the player's own build rules as a JSON string.
 *
 * <p>Pure: {@code String} in, {@code String} out, with no {@code
 * ConfigManager} and no client. {@link LocalBuildRules} is the thin adapter
 * that puts the result somewhere — exactly the split {@link LoadoutCodec} and
 * {@link LoadoutStore} already use, and for the same reason: {@code
 * ConfigManager} has only a private constructor and cannot be faked in a test
 * without Mockito, which this project does not depend on. All the logic worth
 * testing therefore lives here, where {@link BuildRuleCodecTest} can reach it.
 *
 * <p><b>One rule per skill.</b> Two rules on one skill is not a richer
 * expression of intent, it is an ambiguity — "no XP at all" and "cap at 40"
 * cannot both be obeyed — so {@link #set} replaces rather than appends, and
 * decoding collapses a duplicate to the last one seen rather than letting the
 * advisor pick whichever it happened to iterate first.
 */
final class BuildRuleCodec
{
	private BuildRuleCodec()
	{
	}

	/**
	 * Parses stored rules, or an empty list.
	 *
	 * <p>Unparseable stored text returns empty rather than throwing. A
	 * corrupt config value is not worth taking the plugin down for, and the
	 * consequence of returning empty is well-defined and visible: the editor
	 * shows no rules, which the player can see and fix. The alternative —
	 * throwing from a config read on startup — is a plugin that will not
	 * load and says nothing about why.
	 */
	static List<BuildRule> decode(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		// An array class rather than a Gson type token for List<BuildRule>:
		// the token is a reflection type, and this plugin keeps reflection out
		// of src/main entirely -- see NoReflectionTest.
		BuildRule[] array;
		try
		{
			array = gson.fromJson(json, BuildRule[].class);
		}
		catch (JsonSyntaxException e)
		{
			return Collections.emptyList();
		}
		if (array == null)
		{
			return Collections.emptyList();
		}
		List<BuildRule> parsed = Arrays.asList(array);

		// Collapse duplicates and drop the unusable, keeping insertion order
		// so the editor's list does not reshuffle itself on every save.
		Map<String, BuildRule> bySkill = new LinkedHashMap<>();
		for (BuildRule rule : parsed)
		{
			if (rule == null || rule.skillName().isEmpty() || rule.type().isEmpty())
			{
				continue;
			}
			if (!known(rule.type()))
			{
				// A type this build does not understand. Dropped rather than
				// kept, because an unknown type is silently non-blocking
				// everywhere downstream -- which turns a rule the player
				// believes is protecting them into one that does nothing.
				continue;
			}
			bySkill.put(key(rule.skillName()), rule);
		}
		return new ArrayList<>(bySkill.values());
	}

	/** Serialises rules for storage. */
	static String encode(Gson gson, List<BuildRule> rules)
	{
		// An array, the same shape decode reads: a JSON array of rules, with
		// no type token needed on either side.
		BuildRule[] array = rules == null ? new BuildRule[0] : rules.toArray(new BuildRule[0]);
		return gson.toJson(array, BuildRule[].class);
	}

	/**
	 * Sets or replaces the rule on one skill.
	 *
	 * <p>A value is stored only for the types that carry one. NO_XP is
	 * self-describing and SOFT_CAP deliberately carries no number, so a level
	 * typed against either is dropped rather than stored — a stored-but-
	 * ignored number is a setting the player believes they have made.
	 */
	static List<BuildRule> set(List<BuildRule> rules, String skill, String type, int value, String note)
	{
		List<BuildRule> out = new ArrayList<>();
		boolean replaced = false;
		BuildRule fresh = BuildRule.local(skill, type, takesValue(type) ? value : 0, note);

		for (BuildRule rule : rules)
		{
			if (key(rule.skillName()).equals(key(skill)))
			{
				out.add(fresh);
				replaced = true;
			}
			else
			{
				out.add(rule);
			}
		}
		if (!replaced)
		{
			out.add(fresh);
		}
		return out;
	}

	/** Removes the rule on one skill, if there is one. */
	static List<BuildRule> clear(List<BuildRule> rules, String skill)
	{
		List<BuildRule> out = new ArrayList<>();
		for (BuildRule rule : rules)
		{
			if (!key(rule.skillName()).equals(key(skill)))
			{
				out.add(rule);
			}
		}
		return out;
	}

	/**
	 * Merges the player's own rules over the ones questpath pushed.
	 *
	 * <p><b>Local wins on a conflict, and that direction is the whole
	 * decision.</b> A pushed rule can be several minutes stale — questpath
	 * treats a push as live for five — and it arrives without the player
	 * having done anything. A local rule is something they typed. When the
	 * two disagree about one skill, the one they typed is the more recent
	 * statement of intent, and it is the one they will be surprised to find
	 * ignored.
	 *
	 * <p>Both sets survive otherwise: a skill only questpath has a rule for
	 * keeps it, which is what lets the plugin be useful immediately after a
	 * sync without the player re-entering twenty-four rules by hand.
	 */
	static List<BuildRule> merge(List<BuildRule> pushed, List<BuildRule> local)
	{
		Map<String, BuildRule> bySkill = new LinkedHashMap<>();
		for (BuildRule rule : pushed == null ? Collections.<BuildRule>emptyList() : pushed)
		{
			if (rule != null && !rule.skillName().isEmpty())
			{
				bySkill.put(key(rule.skillName()), rule);
			}
		}
		for (BuildRule rule : local == null ? Collections.<BuildRule>emptyList() : local)
		{
			if (rule != null && !rule.skillName().isEmpty())
			{
				bySkill.put(key(rule.skillName()), rule);
			}
		}
		return new ArrayList<>(bySkill.values());
	}

	/** Whether this rule type carries a level alongside it. */
	static boolean takesValue(String type)
	{
		return BuildRule.HARD_CAP.equals(type) || BuildRule.GOAL.equals(type);
	}

	private static boolean known(String type)
	{
		return BuildRule.NO_XP.equals(type) || BuildRule.HARD_CAP.equals(type)
			|| BuildRule.SOFT_CAP.equals(type) || BuildRule.GOAL.equals(type);
	}

	private static String key(String skill)
	{
		return skill == null ? "" : skill.toLowerCase(Locale.ROOT);
	}
}
