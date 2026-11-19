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

	/*
	 * Thieving levels of the stalls a shop entry names, OSRS Wiki "Thieving"
	 * stall table. Bundled, never fetched. Only stalls whose kind is a known
	 * stealable stall are listed: the Elder / Carefree / Vannah stalls are
	 * ordinary shops that merely carry the word.
	 */
	private static final String[][] STALL_LEVELS = {
		{"veg", "2"}, {"baker", "5"}, {"bake stall", "5"}, {"craft", "5"},
		{"silk", "20"}, {"fur", "35"}, {"fish", "42"}, {"silver", "50"},
		{"spice", "65"}, {"gem", "75"}
	};

	private static final String[] NOT_THIEVING = {"elder ", "carefree", "vannah", "grud", "solihib", "vermundi"};

	/** The Thieving level a stall shop needs, or 0 when it is not a stall. */
	static int stallLevel(String shopName)
	{
		if (shopName == null)
		{
			return 0;
		}
		String name = shopName.toLowerCase();
		if (!name.contains("stall"))
		{
			return 0;
		}
		for (String not : NOT_THIEVING)
		{
			if (name.contains(not))
			{
				return 0;
			}
		}
		for (String[] kind : STALL_LEVELS)
		{
			if (name.contains(kind[0]))
			{
				return Integer.parseInt(kind[1]);
			}
		}
		return 0;
	}

	/**
	 * Blocked result when this is a stall the account lacks the Thieving
	 * level for, else null. Unlike guild rules, a null here means "keep
	 * checking the bundle's own gates" (quests, Sailing), never "open".
	 */
	static ShopReachability.Result stallBlock(String shopName, PlayerState player)
	{
		int need = stallLevel(shopName);
		if (need == 0)
		{
			return null;
		}
		int have = player.levelIn("thieving");
		return have >= need ? null : blocked("needs " + need + " Thieving, you have " + have);
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
