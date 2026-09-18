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

	/**
	 * A rule the player wrote in the plugin's own editor, rather than one
	 * questpath pushed.
	 *
	 * <p>Deliberately the same type as a pushed rule. Nothing downstream —
	 * not the verdict engine, not the lamp advisor — should be able to tell
	 * the two apart: a rule is a rule, and giving locally-authored ones a
	 * type of their own would mean two code paths through every check, which
	 * is how one of them comes to be missing a case.
	 *
	 * <p>The class doc above says questpath owns these and the plugin never
	 * writes them back. That is still true of <em>pushed</em> rules and is
	 * the reason {@link LocalBuildRules} keeps its own set separately rather
	 * than editing them: there is one editor per origin, and neither
	 * overwrites the other.
	 */
	static BuildRule local(String skill, String type, int value, String note)
	{
		BuildRule rule = new BuildRule();
		rule.skill = skill;
		rule.type = type;
		rule.value = value;
		rule.note = note;
		// blocking() derives this from the type regardless; it is set here
		// for anything that serialises the rule back out.
		rule.blocking = NO_XP.equals(type) || HARD_CAP.equals(type);
		return rule;
	}

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

	/**
	 * Whether crossing this rule ends the build.
	 *
	 * Derived from the type as well as read from the wire, and the two are
	 * OR-ed rather than the field being trusted alone. questpath sends the
	 * flag on a push response but not in the generated planner vectors, and
	 * trusting the field alone made every rule in the vectors non-blocking --
	 * 94 of 218 verdicts silently came back ELIGIBLE for quests that end a
	 * pure. The type is the fact; the flag is questpath's chance to tell a
	 * future build that something new also blocks.
	 */
	boolean blocking()
	{
		return blocking || NO_XP.equals(type()) || HARD_CAP.equals(type());
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
