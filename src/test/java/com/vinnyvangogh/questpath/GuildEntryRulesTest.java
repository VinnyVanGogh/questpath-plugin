package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guild entry over the real bundle: the Warriors' Guild bronze med helm report. */
public class GuildEntryRulesTest
{
	private static final Gson GSON = new Gson();
	private static final String WARRIORS = "Warrior Guild Armoury";

	private static PlayerState account(int attack, int strength, Set<String> completed)
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Attack", attack);
		levels.put("Strength", strength);
		return new PlayerState(levels, new HashMap<>(), completed, Collections.emptyList());
	}

	private static ShopReachability.Result judge(String name, PlayerState player) throws Exception
	{
		QuestGraph graph = QuestGraph.load(GSON);
		QuestGraph.Shop shop = graph.shop(name);
		assertTrue("the bundle no longer carries " + name, shop != null);
		return ShopReachability.of(shop, player, graph, null);
	}

	@Test
	public void warriorsGuildClosedBelow130Combined() throws Exception
	{
		ShopReachability.Result result = judge(WARRIORS, account(65, 64, new HashSet<>()));
		assertEquals(ShopReachability.Verdict.BLOCKED, result.verdict());
		assertFalse(result.usable());
		assertTrue(result.reason(), result.reason().contains("130"));
	}

	@Test
	public void warriorsGuildOpensAt130Combined() throws Exception
	{
		assertTrue(judge(WARRIORS, account(65, 65, new HashSet<>())).usable());
		assertTrue(judge("Warrior Guild Food Shop", account(100, 30, new HashSet<>())).usable());
	}

	@Test
	public void warriorsGuildOpensAt99InEither() throws Exception
	{
		assertTrue(judge(WARRIORS, account(99, 1, new HashSet<>())).usable());
		assertTrue(judge(WARRIORS, account(1, 99, new HashSet<>())).usable());
		assertFalse(judge(WARRIORS, account(98, 1, new HashSet<>())).usable());
	}

	@Test
	public void championsGuildNeeds32QuestPointsAndUnknownIsClosed() throws Exception
	{
		String name = "Valaine's Shop of Champions.";
		PlayerState base = account(1, 1, new HashSet<>());
		assertFalse("unread quest points must not open it", judge(name, base).usable());
		assertFalse(judge(name, base.withQuestPoints(31)).usable());
		assertTrue(judge(name, base.withQuestPoints(32)).usable());
	}

	@Test
	public void mythsGuildNeedsDragonSlayerII() throws Exception
	{
		String name = "Myths' Guild Herbalist";
		assertFalse(judge(name, account(1, 1, new HashSet<>())).usable());
		Set<String> done = new HashSet<>();
		done.add("Dragon Slayer II");
		assertTrue(judge(name, account(1, 1, done)).usable());
	}

	@Test
	public void bundleGuildGatesHoldAtTheirBoundaries() throws Exception
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Magic", 65);
		assertFalse(judge("Magic Guild Store (Runes and Staves)",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
		levels.put("Magic", 66);
		assertTrue(judge("Magic Guild Store (Runes and Staves)",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
		levels.clear();
		levels.put("Farming", 44);
		assertFalse(judge("Alan (Farming Guild)",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
		levels.put("Farming", 45);
		assertTrue(judge("Alan (Farming Guild)",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
	}

	/**
	 * The reported bug: a bronze med helm account under 130 combined has
	 * reachable shops for it, and the guild is not among them. At 130 the guild
	 * is allowed.
	 */
	@Test
	public void bronzeMedHelmRoutesToAReachableShopNeverTheGuildBelow130() throws Exception
	{
		String[] sellers = {"Blair's Armour", "Helmet Shop.", WARRIORS};
		PlayerState under = account(65, 64, new HashSet<>());
		int reachable = 0;
		for (String name : sellers)
		{
			boolean usable = judge(name, under).usable();
			assertEquals(name, !WARRIORS.equals(name), usable);
			reachable += usable ? 1 : 0;
		}
		assertTrue("a reachable med helm shop must remain", reachable > 0);
		assertTrue(judge(WARRIORS, account(65, 65, new HashSet<>())).usable());
	}

	/** Ranging Guild shop: 40 Ranged, boundary values. */
	@Test
	public void rangingGuildNeeds40Ranged() throws Exception
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Ranged", 39);
		assertFalse(judge("Dargaud's Bow and Arrows.",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
		levels.put("Ranged", 40);
		assertTrue(judge("Dargaud's Bow and Arrows.",
			new PlayerState(levels, new HashMap<>(), new HashSet<>(), Collections.emptyList()))
			.usable());
	}

	/** A shop named for a guild must be gated, ruled or unknown: never silently open. */
	@Test
	public void noGuildNamedShopIsUngated() throws Exception
	{
		QuestGraph graph = QuestGraph.load(GSON);
		for (QuestGraph.Shop shop : graph.shops())
		{
			if (shop.name().toLowerCase().contains("guild") && shop.reachability() == null)
			{
				assertTrue(shop.name() + " is guild-named with no gate",
					GuildEntryRules.judge(shop.name(), account(1, 1, new HashSet<>())) != null);
			}
		}
	}

	/** Every shop the bundle marks unknown is unreachable unless a guild rule opens it. */
	@Test
	public void everyUnknownShopIsUnreachableForAFreshAccount() throws Exception
	{
		QuestGraph graph = QuestGraph.load(GSON);
		PlayerState fresh = account(1, 1, new HashSet<>());
		int unknowns = 0;
		for (QuestGraph.Shop shop : graph.shops())
		{
			if (shop.reachability() != null && shop.reachability().unknown())
			{
				unknowns++;
				assertFalse(shop.name() + " was treated as open",
					ShopReachability.of(shop, fresh, graph, null).usable());
			}
		}
		assertTrue(unknowns > 0);
	}
}
