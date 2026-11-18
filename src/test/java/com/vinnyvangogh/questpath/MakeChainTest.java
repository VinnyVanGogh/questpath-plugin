package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Made items route as a chain, and bosses and Slayer-locked monsters are
 * never a route stop.
 *
 * <p>Board, 2026-11-14: "Quest Route: next up Molten glass at Thermonuclear
 * smoke devil -- 42 stops left" on a low-level Group Ironman. Molten glass is
 * a Bucket of sand and a Soda ash at a furnace; the route knew only shops and
 * drops, and the only droppers are two bosses.
 *
 * <p>Built against {@code quest-graph-v14-recipes.json}, which is also the
 * schema the questpath export is asked to produce.
 */
public class MakeChainTest
{
	private static final Gson GSON = new Gson();
	private static final WorldPoint PLAYER = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint THERMY = new WorldPoint(2380, 9450, 0);
	private static final WorldPoint MUSPAH = new WorldPoint(2900, 10350, 0);
	private static final WorldPoint SAND_SHOP = new WorldPoint(3014, 3204, 0);
	private static final WorldPoint SEAWEED_SHOP = new WorldPoint(2800, 3440, 0);
	private static final WorldPoint LUMBRIDGE_FURNACE = new WorldPoint(3226, 3255, 0);

	private QuestGraph graph;
	private Map<String, Integer> levels;
	private Map<String, Long> held;
	private Map<String, List<String>> holders;

	@Before
	public void loadFixture() throws IOException
	{
		try (Reader reader = new InputStreamReader(
			MakeChainTest.class.getResourceAsStream("quest-graph-v14-recipes.json"), StandardCharsets.UTF_8))
		{
			graph = QuestGraph.parse(GSON, reader);
		}
		levels = new HashMap<>();
		levels.put("slayer", 5);
		levels.put("crafting", 10);
		levels.put("cooking", 10);
		held = new HashMap<>();
		holders = new HashMap<>();
	}

	private Function<String, WorldPoint> shops()
	{
		Map<String, WorldPoint> index = new HashMap<>();
		for (QuestGraph.Shop shop : graph.shops())
		{
			index.put(shop.name(), new WorldPoint(shop.x(), shop.y(), shop.plane()));
		}
		return index::get;
	}

	private Function<String, WorldPoint> drops()
	{
		Map<String, WorldPoint> index = new HashMap<>();
		for (QuestGraph.DropLocation drop : graph.dropLocations())
		{
			index.put(drop.sourceName().toLowerCase(), new WorldPoint(drop.x(), drop.y(), drop.plane()));
		}
		return name -> index.get(name.toLowerCase());
	}

	private RouteSources sources()
	{
		return sources(RouteSources.Index.of(graph));
	}

	private RouteSources sources(RouteSources.Index index)
	{
		return new RouteSources(index, drops(), new RouteSources.Account()
		{
			@Override
			public int level(String skill)
			{
				return levels.getOrDefault(skill.toLowerCase(), 1);
			}

			@Override
			public int combatLevel()
			{
				return 20;
			}

			@Override
			public long held(String item)
			{
				return held.getOrDefault(item.toLowerCase(), 0L);
			}

			@Override
			public List<String> groupHolders(String item)
			{
				return holders.getOrDefault(item.toLowerCase(), Collections.emptyList());
			}
		});
	}

	private QuestItems.Total moltenGlass()
	{
		QuestGraph.Item item = graph.itemNamed("Molten glass");
		QuestItems.Total total = new QuestItems.Total(item.name(), item.itemId(), 0L, 0L, false,
			item.sources(), item.shopNames(), item.dropSourceNames());
		total.add(new QuestItems.Line(item.name(), 1, 0L, false), "The Glass Quest");
		return total;
	}

	private static QuestItems.Total total(String name, List<String> droppers)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList(), droppers);
		total.add(new QuestItems.Line(name, 1, 0L, false), "The Glass Quest");
		return total;
	}

	/** The reported bug: a low account is sent for sand first, never to the smoke devil. */
	@Test
	public void moltenGlassOnALowAccountStartsWithTheSandNotTheBoss()
	{
		List<QuestItems.Total> totals = Collections.singletonList(moltenGlass());

		Set<WorldPoint> destinations =
			ShopRouteStops.destinations(totals, new ShopItemSkips(), shops(), sources(), PLAYER);

		assertTrue("the first leg is the shop selling Bucket of sand", destinations.contains(SAND_SHOP));
		assertFalse("never the Thermonuclear smoke devil", destinations.contains(THERMY));
		assertFalse("never the Phantom Muspah", destinations.contains(MUSPAH));
		assertEquals("-> Port Sarim General Store for Bucket of sand (to make it) -- for The Glass Quest",
			ShopRouteStops.destinationLine(moltenGlass(), shops(), sources(), PLAYER));
	}

	/** With the sand held, the next leg is the soda ash -- itself made, from seaweed. */
	@Test
	public void aNestedRecipeRoutesToTheInnermostIngredient()
	{
		held.put("bucket of sand", 1L);

		ShopRouteStops.Destination leg =
			ShopRouteStops.destinationOf(moltenGlass(), shops(), sources(), PLAYER);

		assertNotNull(leg);
		assertEquals(SEAWEED_SHOP, leg.point());
		assertEquals("Seaweed", leg.ingredient());
	}

	/** With seaweed held, the soda ash is cooked at a range. */
	@Test
	public void aNestedRecipeWithItsInputHeldGoesToItsFacility()
	{
		held.put("bucket of sand", 1L);
		held.put("seaweed", 1L);

		ShopRouteStops.Destination leg =
			ShopRouteStops.destinationOf(moltenGlass(), shops(), sources(), PLAYER);

		assertEquals(ShopRouteStops.Destination.Kind.FACILITY, leg.kind());
		assertEquals("Range", leg.shopName());
		assertEquals("Soda ash", leg.ingredient());
	}

	/** Everything held: the chain ends at the nearest furnace, with what to use on it. */
	@Test
	public void withEveryIngredientHeldTheStopIsTheNearestFurnace()
	{
		held.put("bucket of sand", 1L);
		held.put("soda ash", 1L);

		ShopRouteStops.Destination leg =
			ShopRouteStops.destinationOf(moltenGlass(), shops(), sources(), PLAYER);

		assertEquals(ShopRouteStops.Destination.Kind.FACILITY, leg.kind());
		assertEquals(LUMBRIDGE_FURNACE, leg.point());
		assertEquals(2, leg.candidates().size());
		assertEquals("-> Furnace or 1 other: use Bucket of sand + Soda ash -- for The Glass Quest",
			ShopRouteStops.destinationLine(moltenGlass(), shops(), sources(), PLAYER));
	}

	/** The smoke devil is refused as a boss, and below 93 Slayer as well. */
	@Test
	public void thermonuclearSmokeDevilIsBlockedAsABossAndBelow93Slayer()
	{
		QuestGraph.DropSource thermy = new QuestGraph.DropSource("Thermonuclear smoke devil", 93, 0, true);

		assertEquals("boss, needs 93 Slayer", DropPracticality.blockReason(thermy, 50, 100));
		assertEquals("a boss stays blocked at any Slayer level", "boss",
			DropPracticality.blockReason(thermy, 99, 126));

		QuestGraph.DropSource slayerOnly = new QuestGraph.DropSource("Smoke devil", 93, 0, false);
		assertEquals("needs 93 Slayer", DropPracticality.blockReason(slayerOnly, 92, 126));
		assertTrue(DropPracticality.practical(slayerOnly, 93, 126));

		QuestGraph.DropSource combatGated = new QuestGraph.DropSource("Guard", 0, 40, false);
		assertEquals("needs combat 40", DropPracticality.blockReason(combatGated, 1, 39));

		assertNull("refused in the one place every route reads", sources().apply("Thermonuclear smoke devil"));
		assertNotNull("an ungated monster still resolves", sources().apply("Crab"));
	}

	/** A recipe a level short says so, and the route falls through to a practical drop. */
	@Test
	public void aRecipeALevelShortFallsBackAndSaysWhatIsMissing()
	{
		levels.put("crafting", 0);
		QuestGraph.Recipe recipe = new QuestGraph.Recipe("Crab shell", "",
			Collections.singletonList(new QuestGraph.Requirement("Crafting", 30)),
			Collections.singletonList(new QuestGraph.Material("Crab", 1, Collections.emptyList(),
				Collections.emptyList())));
		RouteSources withRecipe = sources(indexWith(recipe));

		QuestItems.Total shell = total("Crab shell", Collections.singletonList("Crab"));

		assertEquals("-> kill Crab (can't make it yet: needs 30 Crafting) -- for The Glass Quest",
			ShopRouteStops.destinationLine(shell, shops(), withRecipe, PLAYER));
	}

	/** Nothing practical: no stop, a row that says so, and who in the group holds it. */
	@Test
	public void nothingPracticalMeansNoStopAndSaysAskTheGroup()
	{
		holders.put("pet rock of doom", Collections.singletonList("Alice (2)"));
		QuestItems.Total bossOnly = total("Pet rock of doom",
			Collections.singletonList("Thermonuclear smoke devil"));
		List<QuestItems.Total> totals = Collections.singletonList(bossOnly);

		assertTrue("no route stop at all",
			ShopRouteStops.destinations(totals, new ShopItemSkips(), shops(), sources(), PLAYER).isEmpty());
		assertEquals("No practical source: make it or ask the group "
				+ "(Thermonuclear smoke devil: boss, needs 93 Slayer) -- held by Alice (2) -- for The Glass Quest",
			ShopRouteStops.destinationLine(bossOnly, shops(), sources(), PLAYER));
	}

	/** Blocked droppers stay listed, marked, in the item's own sources. */
	@Test
	public void blockedDroppersStayListedWithTheReason()
	{
		List<String> annotated = sources().annotate(graph.itemNamed("Molten glass").sources());

		assertEquals("Drop: Phantom Muspah (15/235) -- boss", annotated.get(0));
		assertEquals("Drop: Thermonuclear smoke devil (1/30) -- boss, needs 93 Slayer", annotated.get(1));
	}

	/**
	 * A graph without the new tables behaves exactly as before: the drop is
	 * routed, because nothing says it is a boss.
	 */
	@Test
	public void anOlderGraphKeepsTodaysBehaviour() throws IOException
	{
		QuestGraph v13 = QuestGraph.parse(GSON, new StringReader("{\"version\":13,\"quests\":[{\"id\":1,"
			+ "\"slug\":\"a\",\"name\":\"A\"}],\"diary_tasks\":[]}"));
		RouteSources old = sources(RouteSources.Index.of(v13));

		assertTrue(v13.recipes().isEmpty());
		assertEquals(THERMY, old.apply("Thermonuclear smoke devil"));
		assertEquals("-> kill Phantom Muspah or 1 other -- for The Glass Quest",
			ShopRouteStops.destinationLine(moltenGlass(), shops(), old, null));
	}

	/** A plain drop locator, as every caller passed before, still routes as it did. */
	@Test
	public void aBareDropLocatorIsStillAccepted()
	{
		assertEquals(THERMY, RouteSources.dropsOnly(drops()).apply("Thermonuclear smoke devil"));
	}

	private RouteSources.Index indexWith(QuestGraph.Recipe recipe)
	{
		String json = GSON.toJson(Collections.singletonMap("recipes", Collections.singletonList(recipe)));
		return RouteSources.Index.of(GSON.fromJson(json, QuestGraph.class));
	}
}
