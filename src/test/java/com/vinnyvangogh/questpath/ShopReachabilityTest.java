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

/**
 * Whether a shop is worth walking to, over the real bundled graph.
 *
 * <p>The two shops asserted here are the two that prompted the whole feature,
 * and they fail in opposite directions: the Fishing Guild Shop is behind a
 * skill the account does not have, and Uglug's is behind a quest the account
 * cannot start. Both were being offered as ordinary destinations.
 */
public class ShopReachabilityTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	private static PlayerState account(Map<String, Integer> levels, Set<String> completed)
	{
		return new PlayerState(levels, new HashMap<>(), completed, Collections.emptyList());
	}

	private static QuestGraph.Shop shop(QuestGraph graph, String name)
	{
		QuestGraph.Shop found = graph.shop(name);
		assertTrue("the bundle no longer carries " + name, found != null);
		return found;
	}

	/** A shop behind nothing is open to anybody, including an empty account. */
	@Test
	public void anUngatedShopIsOpen() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Shop open = null;
		for (QuestGraph.Shop candidate : graph.shops())
		{
			if (candidate.reachability() == null)
			{
				open = candidate;
				break;
			}
		}
		assertTrue("the bundle carries no ungated shop at all", open != null);

		ShopReachability.Result result = ShopReachability.of(open,
			account(new HashMap<>(), new HashSet<>()), graph, null);

		assertEquals(ShopReachability.Verdict.OPEN, result.verdict());
		assertTrue(result.usable());
		assertEquals("", result.reason());
	}

	/**
	 * 59 Fishing does not get you into the Fishing Guild, and the reason says
	 * what is missing rather than only that something is.
	 */
	@Test
	public void theFishingGuildIsClosedBelowItsLevel() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Fishing", 59);

		ShopReachability.Result result = ShopReachability.of(
			shop(graph, "Fishing Guild Shop."), account(levels, new HashSet<>()), graph, null);

		assertEquals(ShopReachability.Verdict.BLOCKED, result.verdict());
		assertFalse("this is the trip that was wasted", result.usable());
		assertTrue(result.reason(), result.reason().contains("68"));
		assertTrue(result.reason(), result.reason().toLowerCase().contains("fishing"));
	}

	/** And 68 does. */
	@Test
	public void theFishingGuildOpensAtItsLevel() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Fishing", 68);

		assertEquals(ShopReachability.Verdict.OPEN, ShopReachability.of(
			shop(graph, "Fishing Guild Shop."), account(levels, new HashSet<>()), graph, null)
			.verdict());
	}

	/**
	 * A boost that closes the gap makes the trip worth making, and says so
	 * rather than reporting plain "open" -- you still have to bring the
	 * potion.
	 */
	@Test
	public void aBoostableGapIsItsOwnVerdictAndStillWorthWalkingTo() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Fishing", 65);

		ShopReachability.Result result = ShopReachability.of(shop(graph, "Fishing Guild Shop."),
			account(levels, new HashSet<>()), graph, new Boosts(graph.boosts()));

		assertEquals(ShopReachability.Verdict.BOOSTABLE, result.verdict());
		assertTrue("a shop a potion opens is not a wasted trip", result.usable());
	}

	/**
	 * Without a boost table the same account is blocked rather than waved
	 * through. Absent information is not permission.
	 */
	@Test
	public void aBoostableGapWithNoBoostTableIsNotAssumedPassable() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Fishing", 65);

		assertEquals(ShopReachability.Verdict.BLOCKED, ShopReachability.of(
			shop(graph, "Fishing Guild Shop."), account(levels, new HashSet<>()), graph, null)
			.verdict());
	}

	/**
	 * Uglug Nar will not trade until Zogre Flesh Eaters is under way, and
	 * having started it is enough.
	 *
	 * <p>His real gate is being handed a Relicym's balm partway through that
	 * quest -- a moment inside it, not a state of it. "You must be able to
	 * start it" is a sound lower bound on every moment inside, which is the
	 * whole reason this is representable at all.
	 */
	@Test
	public void uglugNarNeedsHisQuestOnlyStarted() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Shop uglug = shop(graph, "~ Uglug's stuffsies ~");

		ShopReachability.Result untouched = ShopReachability.of(uglug,
			account(new HashMap<>(), new HashSet<>()), graph, null);
		assertEquals(ShopReachability.Verdict.BLOCKED, untouched.verdict());
		assertTrue(untouched.reason(), untouched.reason().contains("Zogre"));

		Set<String> started = new HashSet<>();
		started.add("Zogre Flesh Eaters");
		assertEquals("started is enough; completed is not required",
			ShopReachability.Verdict.OPEN,
			ShopReachability.of(uglug, new PlayerState(new HashMap<>(), new HashMap<>(),
				new HashSet<>(), started, Collections.emptyList()), graph, null).verdict());
	}

	/** Every gated shop in the bundle can be judged without throwing. */
	@Test
	public void everyGatedShopInTheBundleIsJudgeable() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState fresh = account(new HashMap<>(), new HashSet<>());
		int gated = 0;
		for (QuestGraph.Shop candidate : graph.shops())
		{
			if (candidate.reachability() == null)
			{
				continue;
			}
			gated++;
			ShopReachability.Result result = ShopReachability.of(candidate, fresh, graph, null);
			assertTrue(candidate.name() + " was blocked with no stated reason",
				result.verdict() == ShopReachability.Verdict.OPEN || !result.reason().isEmpty());
		}
		assertTrue("the bundle carries no gated shops -- did the export regress?", gated > 0);
	}
}
