package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Consuming questpath's per-account item plan ({@code item_plan} on the push
 * response) over the real bundled graph: the plugin uses questpath's choice
 * and make steps for counting and routing, and changes nothing when no plan
 * has arrived.
 */
public class ItemPlanTest
{
	private static final Gson GSON = new Gson();

	private static final String FAIRYTALE = "Fairytale II - Cure a Queen";
	private static final String RATCATCHERS = "Ratcatchers";

	/** Fairytale II's line, decided for an account holding 21 Dramen staff. */
	private static final String DRAMEN_HELD = "{\"items\":[{\"requirement\":\"Dramen staff\","
		+ "\"members\":[\"Dramen staff\",\"Lunar staff\"],\"target\":\"Dramen staff\",\"target_item_id\":772,"
		+ "\"choice\":\"held\",\"needed\":1,\"owned\":21,\"shortfall\":0,\"make\":[],\"make_blocked\":[]}]}";

	/** Ratcatchers' dust, made from a banked horn with a pestle and mortar. */
	private static final String DUST_MADE = "{\"items\":[{\"requirement\":\"Unicorn horn dust\","
		+ "\"members\":[\"Unicorn horn dust\"],\"target\":\"Unicorn horn dust\",\"target_item_id\":235,"
		+ "\"choice\":\"only\",\"needed\":1,\"owned\":0,\"shortfall\":0,"
		+ "\"make\":[{\"quantity\":1,\"instruction\":\"use Pestle and mortar on Unicorn horn (in bank)\","
		+ "\"tools\":[\"Pestle and mortar\"],\"materials\":[{\"name\":\"Unicorn horn\",\"quantity\":1}],"
		+ "\"recipe\":\"Unicorn horn dust\"}],\"make_blocked\":[]}]}";

	/** Neither staff reachable. */
	private static final String NEITHER = "{\"items\":[{\"requirement\":\"Dramen staff\","
		+ "\"members\":[\"Dramen staff\",\"Lunar staff\"],\"target\":\"Dramen staff\",\"target_item_id\":772,"
		+ "\"choice\":\"unobtainable\",\"needed\":1,\"owned\":0,\"shortfall\":1,\"make\":[],\"make_blocked\":[]}]}";

	private static QuestItems itemsFor(String quest, Map<String, Long> held, ItemPlan plan) throws Exception
	{
		PlayerState account = new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
		Set<String> scope = new HashSet<>(Collections.singletonList(quest));
		return QuestItems.of(QuestGraph.load(GSON), account, held, true, Collections.<String>emptySet(), scope,
			Collections.<String, Long>emptyMap(), held, Collections.<String>emptySet(), plan);
	}

	private static QuestItems.Total total(QuestItems items, String name)
	{
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equalsIgnoreCase(name))
			{
				return total;
			}
		}
		return null;
	}

	private static ItemPlan plan(String json)
	{
		return GSON.fromJson(json, ItemPlan.class);
	}

	@Test
	public void anAbsentOrNullPlanIsNoDecision()
	{
		assertNull(GSON.fromJson("{\"player\":\"ship wrek\"}", PushResponse.class).itemPlan());
		assertNull(GSON.fromJson("{\"player\":\"ship wrek\",\"item_plan\":null}", PushResponse.class).itemPlan());
		PushResponse empty = GSON.fromJson("{\"player\":\"ship wrek\",\"item_plan\":{\"items\":[]}}", PushResponse.class);
		assertNotNull("an object is a decision, even with nothing in it", empty.itemPlan());
		assertTrue(empty.itemPlan().items().isEmpty());
	}

	/**
	 * With no plan the graph's own line stands, exactly as before -- and
	 * nothing claims a choice was made.
	 */
	@Test
	public void withoutAPlanTheGraphLineIsUnchanged() throws Exception
	{
		Map<String, Long> held = new HashMap<>();
		held.put("dramen staff", 21L);

		QuestItems items = itemsFor(FAIRYTALE, held, null);
		QuestItems.Total dramen = total(items, "Dramen staff");
		assertNotNull(dramen);
		assertFalse(dramen.plan().decided());
		assertNull(dramen.plan().describe());
		assertEquals("Dramen staff", dramen.displayName());
	}

	/**
	 * The reported bug: ship wrek holds 21 Dramen staff and the shop route
	 * sent it to Oneiromancer for a Lunar staff. With questpath's plan the
	 * line becomes the held Dramen staff and there is no stop at all.
	 */
	@Test
	public void aHeldAlternativeReplacesTheGraphMemberAndLeavesNothingToBuy() throws Exception
	{
		Map<String, Long> held = new HashMap<>();
		held.put("dramen staff", 21L);

		QuestItems items = itemsFor(FAIRYTALE, held, plan(DRAMEN_HELD));
		assertNull("the graph member is not listed once questpath chose another", total(items, "Lunar staff"));
		QuestItems.Total dramen = total(items, "Dramen staff");
		assertNotNull(dramen);
		assertTrue(dramen.satisfied());
		assertEquals(772, dramen.itemId());
		assertTrue(dramen.plan().describe().contains("you hold Dramen staff"));
		for (ShopRouteStops.Stop stop : ShopRouteStops.next(items.totals(), new ShopItemSkips(), name -> null))
		{
			assertFalse("no stop for a staff already in the bank",
				stop.total().name().toLowerCase().contains("staff"));
		}
	}

	/**
	 * One horn and a pestle and mortar make the dust: it is not a stop (the
	 * graph would otherwise send the player to kill a cave bug), and the row
	 * says how to make it.
	 */
	@Test
	public void anItemMadeFromWhatIsHeldIsCoveredAndNotRoutedTo() throws Exception
	{
		Map<String, Long> held = new HashMap<>();
		held.put("unicorn horn", 1L);
		held.put("pestle and mortar", 1L);

		QuestItems items = itemsFor(RATCATCHERS, held, plan(DUST_MADE));
		QuestItems.Total dust = total(items, "Unicorn horn dust");
		assertNotNull(dust);
		assertFalse("not held yet", dust.satisfied());
		assertTrue(dust.covered());
		assertTrue(dust.makeFromOwned());
		assertEquals(0L, dust.outstanding());
		assertEquals("Unicorn horn dust  (make)", dust.displayName());
		assertEquals(Arrays.asList("use Pestle and mortar on Unicorn horn (in bank)"),
			dust.plan().makeInstructions());
		for (ShopRouteStops.Stop stop : ShopRouteStops.next(items.totals(), new ShopItemSkips(), name -> null,
			name -> null))
		{
			assertFalse("a made item is never a stop", stop.total().name().equalsIgnoreCase("Unicorn horn dust"));
		}
	}

	/**
	 * The plan is as old as the last push. If the horn has gone since, the
	 * step is no longer possible and the dust is short again -- routed like
	 * any other item rather than kept off the trip by a stale promise.
	 */
	@Test
	public void aMakeStepWhoseMaterialsAreGoneNoLongerCovers() throws Exception
	{
		Map<String, Long> held = new HashMap<>();
		held.put("pestle and mortar", 1L);

		QuestItems items = itemsFor(RATCATCHERS, held, plan(DUST_MADE));
		QuestItems.Total dust = total(items, "Unicorn horn dust");
		assertNotNull(dust);
		assertFalse(dust.covered());
		assertEquals(1L, dust.outstanding());
		assertEquals("Unicorn horn dust", dust.displayName());
	}

	/** Neither member reachable: listed and said so, never routed to. */
	@Test
	public void anUnobtainableAlternativeIsShownButNotRouted() throws Exception
	{
		QuestItems items = itemsFor(FAIRYTALE, new HashMap<>(), plan(NEITHER));
		QuestItems.Total dramen = total(items, "Dramen staff");
		assertNotNull(dramen);
		assertTrue(dramen.shopNames().isEmpty());
		assertTrue(dramen.dropSourceNames().isEmpty());
		assertEquals("Dramen staff  (none reachable)", dramen.displayName());
		assertTrue(dramen.plan().describe().contains("none can be reached"));
	}
}
