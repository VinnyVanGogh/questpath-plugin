package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a monster is somewhere a route may send this account for a drop.
 *
 * <p>Reported from play, 2026-11-14: "Quest Route: next up Molten glass at
 * Thermonuclear smoke devil -- 42 stops left" on a low-level Group Ironman.
 * The route treated every dropper as equally good, so a boss behind 93
 * Slayer stood in for a furnace and two shop items.
 *
 * <p>Two rules, and only two. A boss is never a route stop, whatever the
 * account's levels: farming a boss for a quest item is a decision a player
 * makes, not one a shopping list makes for them. A Slayer or combat level the
 * account lacks blocks the same way. Anything else is practical. These sit on
 * top of {@link DropRegionPolicy}, never instead of it: a practical monster in
 * a declared-off-limits region is still off limits.
 *
 * <p>An impractical dropper is still worth naming. It stays in the item's
 * source list with the reason beside it ("boss, needs 93 Slayer"), because
 * "nothing drops this" and "only a boss drops this" are different facts.
 */
final class DropPracticality
{
	private DropPracticality()
	{
	}

	/**
	 * Why this dropper is not a route stop, or empty when it is one.
	 *
	 * @param source what gates the monster; null when the export carries
	 *     nothing for it, which is read as "nothing known to gate it" --
	 *     the behaviour every graph older than 14 already had
	 * @param slayerLevel the account's real Slayer level
	 * @param combatLevel the account's combat level
	 */
	static String blockReason(QuestGraph.DropSource source, int slayerLevel, int combatLevel)
	{
		if (source == null)
		{
			return "";
		}
		List<String> reasons = new ArrayList<>();
		if (source.boss())
		{
			reasons.add("boss");
		}
		if (source.slayerLevel() > slayerLevel)
		{
			reasons.add("needs " + source.slayerLevel() + " Slayer");
		}
		if (source.combatLevel() > combatLevel)
		{
			reasons.add("needs combat " + source.combatLevel());
		}
		return String.join(", ", reasons);
	}

	/** Whether a route may send the account here. */
	static boolean practical(QuestGraph.DropSource source, int slayerLevel, int combatLevel)
	{
		return blockReason(source, slayerLevel, combatLevel).isEmpty();
	}
}
