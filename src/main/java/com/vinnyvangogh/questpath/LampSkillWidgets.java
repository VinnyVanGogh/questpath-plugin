package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;

/**
 * The skill-choice interface's own components, one per skill.
 *
 * <p>Every id here is a named constant from {@code
 * net.runelite.api.gameval.InterfaceID}, never a number. That is
 * {@code AGENTS.md}'s rule — "use {@code net.runelite.api.gameval} package
 * constants ... never hardcode magic numbers when gameval constants can be
 * used instead", and "pass the component ID from gamevals ... do not manually
 * combine interface + component child IDs" — and it is also the only way to
 * be sure: these were read out of the shipped jar with
 *
 * <pre>
 * javap -cp runelite-api-1.12.39.jar -constants 'net.runelite.api.gameval.InterfaceID$Xpreward'
 * </pre>
 *
 * rather than recalled, and they are recorded in questpath's
 * {@code RESEARCH.md} §24.6 with that command beside them.
 *
 * <p>All twenty-four skills are present, Sailing among them. Whether a given
 * lamp will actually accept Sailing is a separate and currently unsettled
 * question — see RESEARCH.md §24's "What this source does NOT settle" — and
 * is the lamp's business, not this map's: this only says where the square is.
 */
final class LampSkillWidgets
{
	/**
	 * Skill name to component id, in questpath's own hiscores order — which
	 * is the order the shared golden vectors emit and the order the advisor
	 * is handed, so nothing has to re-sort to line the two up.
	 */
	private static final Map<String, Integer> COMPONENTS = build();

	private LampSkillWidgets()
	{
	}

	private static Map<String, Integer> build()
	{
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("attack", InterfaceID.Xpreward.ATTACK);
		map.put("defence", InterfaceID.Xpreward.DEFENCE);
		map.put("strength", InterfaceID.Xpreward.STRENGTH);
		map.put("hitpoints", InterfaceID.Xpreward.HITPOINTS);
		map.put("ranged", InterfaceID.Xpreward.RANGED);
		map.put("prayer", InterfaceID.Xpreward.PRAYER);
		map.put("magic", InterfaceID.Xpreward.MAGIC);
		map.put("cooking", InterfaceID.Xpreward.COOKING);
		map.put("woodcutting", InterfaceID.Xpreward.WOODCUTTING);
		map.put("fletching", InterfaceID.Xpreward.FLETCHING);
		map.put("fishing", InterfaceID.Xpreward.FISHING);
		map.put("firemaking", InterfaceID.Xpreward.FIREMAKING);
		map.put("crafting", InterfaceID.Xpreward.CRAFTING);
		map.put("smithing", InterfaceID.Xpreward.SMITHING);
		map.put("mining", InterfaceID.Xpreward.MINING);
		map.put("herblore", InterfaceID.Xpreward.HERBLORE);
		map.put("agility", InterfaceID.Xpreward.AGILITY);
		map.put("thieving", InterfaceID.Xpreward.THIEVING);
		map.put("slayer", InterfaceID.Xpreward.SLAYER);
		map.put("farming", InterfaceID.Xpreward.FARMING);
		map.put("runecraft", InterfaceID.Xpreward.RUNECRAFT);
		map.put("hunter", InterfaceID.Xpreward.HUNTER);
		map.put("construction", InterfaceID.Xpreward.CONSTRUCTION);
		map.put("sailing", InterfaceID.Xpreward.SAILING);
		return Collections.unmodifiableMap(map);
	}

	/** Every skill this interface has a square for, in advisor order. */
	static java.util.List<String> skills()
	{
		return new java.util.ArrayList<>(COMPONENTS.keySet());
	}

	/** The component id for a skill, or -1 for a name the interface lacks. */
	static int componentFor(String skill)
	{
		Integer id = COMPONENTS.get(Lamp.normalise(skill));
		return id == null ? -1 : id;
	}

	/** The interface group, for testing whether it is the one that opened. */
	static int group()
	{
		return InterfaceID.XPREWARD;
	}
}
