package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/**
 * The whole reason questpath exists, on screen: how much room is left before
 * a declared build rule ends the account.
 *
 * <p>The sidebar already renders this correctly -- see
 * {@code QuestpathPanel.buildRulesBlock} and {@code ruleColor} -- and this
 * overlay carries the same reasoning rather than reinventing it: blocking
 * rules (NO_XP, HARD_CAP) are the ones that ruin a pure and are drawn as
 * warnings that escalate as the account closes in; informational ones
 * (SOFT_CAP, GOAL) never turn that colour, because a goal drawn like a cap
 * turns "approaching a target" into "about to ruin your account", and the
 * real warning stops meaning anything the first time it cries wolf.
 */
class BuildRulesOverlay extends QuestpathOverlay
{
	/** Rows shown per group before the rest collapse into a "+N more" note. */
	private static final int MAX_ROWS = 10;

	BuildRulesOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Build rules");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.overlayBuildRules();
	}

	@Override
	OverlayWidth width(QuestpathConfig config)
	{
		return config.overlayBuildRulesWidth();
	}

	@Override
	OverlayListLength length(QuestpathConfig config)
	{
		return config.overlayBuildRulesLength();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		List<BuildRule> rules = snapshot.buildRules();
		if (rules.isEmpty())
		{
			// Never "unconstrained" -- the plugin learns rules only from an
			// accepted questpath push, so empty is silence, not a clean bill
			// of health. Telling a pure their build has no limits when
			// nobody has said so yet is the one wrong answer this panel
			// could give.
			return Collections.singletonList(OverlayRow.note(
				"No build rules yet. questpath sends these when a push is "
					+ "accepted, so turn sync on.",
				OverlayTheme.MUTED));
		}

		// Which halves the player asked for. Read once here rather than
		// checked at each section, so "blocking only" also drops the headline
		// bar's counterpart rather than leaving a lone bar over an empty
		// panel.
		BuildRuleSections sections = config.overlayBuildRulesSections();

		CombatStats stats = snapshot.stats();
		List<BuildRule> ordered = BuildRule.ordered(rules);

		List<BuildRule> blocking = new ArrayList<>();
		List<BuildRule> informational = new ArrayList<>();
		for (BuildRule rule : ordered)
		{
			(rule.blocking() ? blocking : informational).add(rule);
		}

		List<OverlayRow> rows = new ArrayList<>();

		// Lead with whichever blocking rule is closest to firing -- the one
		// fact worth a glance without opening the sidebar. Only possible
		// once levels are known, and only for a rule with a level to close
		// in on; NO_XP and non-combat-seven skills have no such distance.
		BuildRule closest = sections.showsBlocking() ? closestToBreaking(blocking, stats) : null;
		if (closest != null)
		{
			rows.add(closestBar(closest, stats));
			rows.add(OverlayRow.spacer(2));
		}

		if (sections.showsBlocking() && !blocking.isEmpty())
		{
			rows.add(OverlayRow.header("Blocking"));
			addRuleRows(rows, blocking, stats, ctx);
		}

		if (sections.showsInformational() && !informational.isEmpty())
		{
			rows.add(OverlayRow.header("Informational"));
			addRuleRows(rows, informational, stats, ctx);
		}

		return rows;
	}

	/**
	 * The blocking rule nearest to being crossed, or null when none can be
	 * judged.
	 *
	 * "Nearest" is signed rather than clamped at zero: a rule already
	 * reached (a negative or zero distance) is more urgent than one a level
	 * away, and this must surface that one first, not treat both as equally
	 * "close".
	 */
	private static BuildRule closestToBreaking(List<BuildRule> blocking, CombatStats stats)
	{
		if (stats == null)
		{
			return null;
		}
		BuildRule best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (BuildRule rule : blocking)
		{
			Skill skill = rule.skill();
			if (skill == null || !rule.hasValue())
			{
				continue;
			}
			int level = stats.get(skill);
			if (level < 0)
			{
				continue;
			}
			int distance = rule.value() - level;
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = rule;
			}
		}
		return best;
	}

	/** The headline bar for the rule closest to breaking. */
	private static OverlayRow closestBar(BuildRule rule, CombatStats stats)
	{
		Skill skill = rule.skill();
		int level = stats.get(skill);
		double fraction = rule.value() == 0 ? 1.0 : level / (double) rule.value();
		Color color = ruleColor(rule, level);
		String label = skillLabel(rule, skill, level);
		String caption = level >= rule.value() ? rule.describe()
			: (rule.value() - level) + " to " + rule.value();
		return OverlayRow.bar(label, caption, fraction, color);
	}

	/**
	 * Appends one row per rule in the section, capped so a build with many
	 * declared rules stays a readout rather than a wall of text.
	 */
	private static void addRuleRows(List<OverlayRow> rows, List<BuildRule> section, CombatStats stats,
		OverlayContext ctx)
	{
		int shown = Math.min(ctx.listCap(MAX_ROWS), section.size());
		for (int i = 0; i < shown; i++)
		{
			rows.add(ruleRow(section.get(i), stats));
		}
		int more = section.size() - shown;
		if (more > 0)
		{
			rows.add(OverlayRow.overflow("+ " + more + " more"));
		}
	}

	/**
	 * One rule as a row. Uses the skill tab icon when the client knows the
	 * skill; falls back to a plain line under questpath's own name for it
	 * when it does not, because a skill this client cannot resolve is a real
	 * disagreement worth showing, not a row worth dropping.
	 */
	private static OverlayRow ruleRow(BuildRule rule, CombatStats stats)
	{
		Skill skill = rule.skill();
		int level = (stats == null || skill == null) ? -1 : stats.get(skill);
		String label = skillLabel(rule, skill, level);
		String right = headroom(rule, level);
		Color color = ruleColor(rule, level);

		if (skill != null)
		{
			return OverlayRow.skill(skill, label, right, OverlayTheme.TEXT, color);
		}
		return OverlayRow.line(label, right, OverlayTheme.TEXT, color);
	}

	/** "Defence 82", or just the skill's name when there is no level to show. */
	private static String skillLabel(BuildRule rule, Skill skill, int level)
	{
		String name = skill == null ? rule.skillName() : skillName(skill);
		return level >= 0 ? name + " " + level : name;
	}

	/**
	 * The distance to the rule's value while there is room left, or
	 * {@link BuildRule#describe()} once that distance runs out or was never
	 * computable. Reaching for a number only while it is still good news
	 * keeps "3 to 85" from turning into a countdown that reads as "0 to 85"
	 * forever after the cap is already blown.
	 */
	private static String headroom(BuildRule rule, int level)
	{
		if (rule.hasValue() && level >= 0 && level < rule.value())
		{
			return (rule.value() - level) + " to " + rule.value();
		}
		return rule.describe();
	}

	/**
	 * Amber once a blocking rule is one level away, red once it is reached,
	 * green while there is room -- and never green for an informational rule
	 * or one with no distance to render, because both would read as a
	 * warning that never actually fires.
	 *
	 * <p>Carried over from {@code QuestpathPanel.ruleColor} unchanged: that
	 * colour reasoning is correct and there is no version of it that should
	 * differ between the sidebar and the overlay.
	 */
	private static Color ruleColor(BuildRule rule, int level)
	{
		if (!rule.blocking())
		{
			return OverlayTheme.MUTED;
		}
		if (level < 0 || !rule.hasValue())
		{
			// NO_XP, or a skill outside the combat seven. It blocks, but
			// there is no distance to render -- neutral, not green.
			return OverlayTheme.WARN;
		}
		if (level >= rule.value())
		{
			return OverlayTheme.BAD;
		}
		if (level >= rule.value() - 1)
		{
			return OverlayTheme.WARN;
		}
		return OverlayTheme.GOOD;
	}

	/** Title-cased skill name, matching how the rest of the plugin shows one. */
	private static String skillName(Skill skill)
	{
		String name = skill.getName();
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}
}
