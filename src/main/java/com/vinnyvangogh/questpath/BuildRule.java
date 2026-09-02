package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/**
 * One skill's declared limit, as questpath reports it on a push response.
 *
 * <p>questpath owns these — the plugin displays them and never writes them
 * back. There is exactly one editor, and it is the web app.
 *
 * <p>The distinction that matters is {@link #blocking}: NO_XP and HARD_CAP end
 * a build if crossed, while SOFT_CAP and GOAL only inform. Drawing them the
 * same would turn "you are approaching a goal" into "you are about to ruin
 * your account", and the second warning stops meaning anything if it fires for
 * the first.
 */
class BuildRule
{
	/** Rule types questpath sends, verbatim. */
	static final String NO_XP = "NO_XP";
	static final String HARD_CAP = "HARD_CAP";
	static final String SOFT_CAP = "SOFT_CAP";
	static final String GOAL = "GOAL";

	@SerializedName("skill")
	private String skill;

	@SerializedName("type")
	private String type;

	/** The cap or target level. Absent — and so zero — when the rule carries none. */
	@SerializedName("value")
	private int value;

	@SerializedName("blocking")
	private boolean blocking;

	@SerializedName("note")
	private String note;

	String skillName()
	{
		return skill == null ? "" : skill;
	}

	String type()
	{
		return type == null ? "" : type;
	}

	int value()
	{
		return value;
	}

	/** Whether the rule carries a level at all. SOFT_CAP never does. */
	boolean hasValue()
	{
		return value > 0;
	}

	boolean blocking()
	{
		return blocking;
	}

	String note()
	{
		return note == null ? "" : note;
	}

	/**
	 * The client Skill this rule is about, or null when questpath names one
	 * this client does not know.
	 *
	 * Null rather than a guess: a skill questpath knows and the client does
	 * not is a real disagreement, and the panel says so instead of silently
	 * dropping a rule that might be the one ending the build.
	 */
	Skill skill()
	{
		for (Skill candidate : Skill.values())
		{
			if (candidate.getName().equalsIgnoreCase(skillName()))
			{
				return candidate;
			}
		}
		return null;
	}

	/** A short description of the limit, for a row that already names the skill. */
	String describe()
	{
		switch (type())
		{
			case NO_XP:
				return "no xp at all";
			case HARD_CAP:
				return hasValue() ? "capped at " + value : "capped";
			case SOFT_CAP:
				return "warns, never blocks";
			case GOAL:
				return hasValue() ? "goal " + value : "goal";
			default:
				// A type this build does not know. Shown rather than hidden:
				// questpath added something, and a rule silently missing from
				// this list is worse than one rendered plainly.
				return type();
		}
	}

	/**
	 * Orders rules the way they matter: what ends a build first, then caps,
	 * then goals, and alphabetically within each.
	 */
	static List<BuildRule> ordered(List<BuildRule> rules)
	{
		List<BuildRule> out = new ArrayList<>(rules);
		Collections.sort(out, (a, b) ->
		{
			int byWeight = Integer.compare(weight(a), weight(b));
			return byWeight != 0 ? byWeight : a.skillName().compareToIgnoreCase(b.skillName());
		});
		return out;
	}

	private static int weight(BuildRule rule)
	{
		switch (rule.type())
		{
			case NO_XP:
				return 0;
			case HARD_CAP:
				return 1;
			case SOFT_CAP:
				return 2;
			case GOAL:
				return 3;
			default:
				return 4;
		}
	}
}
