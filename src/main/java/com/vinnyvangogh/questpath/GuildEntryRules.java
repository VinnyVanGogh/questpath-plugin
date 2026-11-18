package com.vinnyvangogh.questpath;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry rules for guild shops the bundle carries no usable gate for.
 *
 * <p>Checked before the bundle's own reachability, so a guild the export
 * marked "unknown" (the Warriors' Guild) gets its real rule. Sources, OSRS
 * Wiki: "Warriors' Guild" (130 combined Attack + Strength, or 99 in either),
 * "Champions' Guild" (32 quest points), "Myths' Guild" (Dragon Slayer II).
 * None of these is boostable here: under-claiming costs a walk, the opposite
 * wastes a trip. Guilds already gated in the bundle (Fishing 68, Magic 66,
 * Farming 45, Woodcutting 60, Legends' Quest) are left to it.
 */
final class GuildEntryRules
{
	private interface Rule
	{
		ShopReachability.Result judge(PlayerState player);
	}

	private static final Map<String, Rule> RULES = new HashMap<>();

	static
	{
		Rule warriors = GuildEntryRules::warriorsGuild;
		RULES.put("warrior guild armoury", warriors);
		RULES.put("warrior guild food shop", warriors);
		RULES.put("valaine's shop of champions.", GuildEntryRules::championsGuild);
		RULES.put("myths' guild herbalist", GuildEntryRules::mythsGuild);
	}

	private GuildEntryRules()
	{
	}

	/** The verdict, or null when this shop has no rule here. */
	static ShopReachability.Result judge(String shopName, PlayerState player)
	{
		Rule rule = shopName == null ? null : RULES.get(shopName.toLowerCase());
		return rule == null ? null : rule.judge(player);
	}

	private static ShopReachability.Result open()
	{
		return new ShopReachability.Result(ShopReachability.Verdict.OPEN, "");
	}

	private static ShopReachability.Result blocked(String reason)
	{
		return new ShopReachability.Result(ShopReachability.Verdict.BLOCKED, reason);
	}

	private static ShopReachability.Result warriorsGuild(PlayerState player)
	{
		int attack = player.levelIn("attack");
		int strength = player.levelIn("strength");
		if (attack + strength >= 130 || attack >= 99 || strength >= 99)
		{
			return open();
		}
		return blocked("Warriors' Guild: needs 130 Attack + Strength (or 99 in either)");
	}

	private static ShopReachability.Result championsGuild(PlayerState player)
	{
		return player.questPoints() >= 32
			? open() : blocked("Champions' Guild: needs 32 quest points");
	}

	private static ShopReachability.Result mythsGuild(PlayerState player)
	{
		return player.isComplete("Dragon Slayer II")
			? open() : blocked("Myths' Guild: needs Dragon Slayer II");
	}
}
