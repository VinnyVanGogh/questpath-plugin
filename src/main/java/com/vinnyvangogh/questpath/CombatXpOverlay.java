package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * The single most consequential readout on a pure: what each combat stat
 * would cost, in real experience, to buy the next combat level.
 *
 * <p>At a pure's stats almost every stat reads "+1 level" -- {@link
 * CombatMath#levelsToNextCombat} discriminates nothing there, because one
 * level is one level whichever skill it is in. The experience behind that
 * level is what actually separates a Defence level that ends the build from
 * a Strength level that is a week of work, so this overlay orders by cost
 * rather than by skill, cheapest first, and prices each row exactly rather
 * than from a level threshold -- see {@link SkillXp#toLevel} for why an
 * upper bound is worse than no number at all.
 *
 * <p>{@link QuestpathPanel}'s cost block in the sidebar answers the same
 * question; this exists for the moment the player is not looking at it, mid
 * fight or mid grind, and wants the same warning at a glance.
 */
class CombatXpOverlay extends QuestpathOverlay
{
	CombatXpOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Combat XP to next level");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.overlayCombatXp();
	}

	@Override
	OverlayWidth width(QuestpathConfig config)
	{
		return config.overlayCombatXpWidth();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		CombatStats stats = snapshot.stats();
		SkillXp xp = snapshot.xp();
		if (stats == null || xp == null)
		{
			// A logged-out client reports every skill as level 1, which
			// floors to a real-looking combat level 3. Rendering that would
			// be a confident, wrong answer rather than an honest "not yet".
			return Collections.singletonList(OverlayRow.note(
				"Levels haven't been read yet.", OverlayTheme.MUTED));
		}

		List<OverlayRow> rows = new ArrayList<>();
		int level = CombatMath.combatLevel(stats);
		double progress = CombatMath.combatProgress(stats);
		rows.add(OverlayRow.hero("Combat level " + level, OverlayTheme.ACCENT));
		rows.add(OverlayRow.bar("Progress to " + (level + 1),
			Math.round(progress * 100) + "%", progress, OverlayTheme.ACCENT));

		Map<Skill, Integer> deltas = CombatMath.levelsToNextCombat(stats);
		if (deltas.isEmpty())
		{
			// The formula genuinely has nothing left to give below 99 in any
			// combat skill -- distinct from "not known", so it earns its own
			// sentence rather than an empty list under the bar.
			rows.add(OverlayRow.note("Nothing raises combat at or below 99.", OverlayTheme.MUTED));
			return rows;
		}

		List<Map.Entry<Skill, Integer>> ordered = new ArrayList<>(deltas.entrySet());
		ordered.sort(Comparator.comparingLong(entry ->
		{
			long cost = xp.toLevel(entry.getKey(), stats.get(entry.getKey()) + entry.getValue());
			// Unknown sorts last, not first: a skill the client hasn't
			// reported XP for yet is not the cheapest step just because
			// there is no number to compare it against.
			return cost < 0 ? Long.MAX_VALUE : cost;
		}));

		rows.add(OverlayRow.header("Cheapest first"));
		for (Map.Entry<Skill, Integer> entry : ordered)
		{
			Skill skill = entry.getKey();
			int delta = entry.getValue();
			int target = stats.get(skill) + delta;
			rows.add(OverlayRow.skill(skill, String.valueOf(target), costText(xp, skill, target, delta),
				OverlayTheme.TEXT, costColor(delta)));
		}

		return rows;
	}

	/** The middle dot separating a level delta from its cost -- escaped to keep source ASCII. */
	private static final String SEP = " \u00B7 ";

	/**
	 * "+1 (middle dot) 12.4k xp", or just "+1" when the client hasn't
	 * supplied this skill's XP yet.
	 *
	 * The figure is never estimated from a level threshold: that is an upper
	 * bound, not the cost, and printing it as fact is the exact bug the
	 * sidebar cost block already fixed once. An omitted figure is the honest
	 * answer where an exact one is not available.
	 */
	private static String costText(SkillXp xp, Skill skill, int targetLevel, int delta)
	{
		long cost = xp.toLevel(skill, targetLevel);
		if (cost < 0)
		{
			return "+" + delta;
		}
		return "+" + delta + SEP + XpTable.format(cost) + " xp";
	}

	/**
	 * Colour by how few levels the step takes -- inverted from every other
	 * overlay's GOOD/BAD, because on a pure a cheap step is the dangerous
	 * one. A single level away is the step that ends the build (BAD, the
	 * colour that means "off the table" elsewhere); several levels off is
	 * comfortably far away (GOOD).
	 */
	private static Color costColor(int delta)
	{
		if (delta <= 1)
		{
			return OverlayTheme.BAD;
		}
		if (delta <= 3)
		{
			return OverlayTheme.WARN;
		}
		return OverlayTheme.GOOD;
	}
}
