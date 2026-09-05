package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Going to kill something, when nothing sells it.
 *
 * <p>Reported from play: "for something like Bat bones, if there is no shop,
 * it should route me to the nearest bats." Before this the trip stepped over
 * such an item silently -- it stayed outstanding on the list and the route
 * simply never mentioned it, which is indistinguishable from questpath having
 * nothing to say about it at all.
 */
public class DropRoutingTest
{
	private static final Gson GSON = new Gson();
	private static final WorldPoint SHOP = new WorldPoint(3212, 3246, 0);
	private static final WorldPoint LAIR = new WorldPoint(3100, 3400, 0);

	private static Function<String, WorldPoint> places(Object... pairs)
	{
		Map<String, WorldPoint> known = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			known.put((String) pairs[i], (WorldPoint) pairs[i + 1]);
		}
		return known::get;
	}

	private static QuestItems.Total total(String name, List<String> shops, List<String> droppers)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, 0L, 0L, false,
			Collections.emptyList(), shops, droppers);
		total.add(new QuestItems.Line(name, 1, 0L, false), "Merlin's Crystal");
		return total;
	}

	/** With no shop, the trip goes to something that drops it. */
	@Test
	public void anItemNoShopSellsRoutesToItsDropper()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Bat bones", Collections.emptyList(), Collections.singletonList("Giant bat")));
		Function<String, WorldPoint> shops = places();
		Function<String, WorldPoint> drops = places("Giant bat", LAIR);

		Set<WorldPoint> destinations =
			ShopRouteStops.destinations(totals, new ShopItemSkips(), shops, drops);

		assertTrue("the trip used to say nothing at all here", destinations.contains(LAIR));
	}

	/** And says it is a kill rather than a purchase. */
	@Test
	public void theLabelSaysToKillItRatherThanBuyIt()
	{
		QuestItems.Total bones = total("Bat bones", Collections.emptyList(),
			Collections.singletonList("Giant bat"));

		assertEquals("-> kill Giant bat -- for Merlin's Crystal",
			ShopRouteStops.destinationLine(bones, places(), places("Giant bat", LAIR), null));
	}

	/**
	 * A shop still wins when there is one.
	 *
	 * <p>Not arbitrary: a shop is repeatable and predictable and a drop is a
	 * grind, which is the same ordering item_sources already applies on the
	 * server and the reason an ironman wants a shop whenever one exists.
	 */
	@Test
	public void aShopBeatsADropWhenBothExist()
	{
		QuestItems.Total bones = total("Bones", Collections.singletonList("Bone shop"),
			Collections.singletonList("Giant bat"));

		ShopRouteStops.Destination destination = ShopRouteStops.destinationOf(bones,
			places("Bone shop", SHOP), places("Giant bat", LAIR), null);

		assertEquals("Bone shop", destination.shopName());
		assertFalse(destination.monster());
		assertEquals(SHOP, destination.point());
	}

	/**
	 * A shop the account cannot enter is not a shop, so the dropper takes
	 * over. This is the Fishing Guild case meeting the Bat bones case: the
	 * locator returns null for an unreachable shop, and the fallback must
	 * treat that as "no shop" rather than as "nowhere at all".
	 */
	@Test
	public void anUnreachableShopFallsThroughToTheDropper()
	{
		QuestItems.Total bones = total("Bones", Collections.singletonList("Guild shop"),
			Collections.singletonList("Giant bat"));

		ShopRouteStops.Destination destination = ShopRouteStops.destinationOf(bones,
			places(), places("Giant bat", LAIR), null);

		assertTrue(destination.monster());
		assertEquals("Giant bat", destination.shopName());
	}

	/** An item with neither is still admitted, never invented. */
	@Test
	public void anItemWithNoShopAndNoDropperSaysSo()
	{
		QuestItems.Total obscure = total("Something odd", Collections.emptyList(),
			Collections.emptyList());

		assertEquals("No known shop sells this yet -- for Merlin's Crystal",
			ShopRouteStops.destinationLine(obscure, places(), places(), null));
		assertTrue(ShopRouteStops.destinations(Collections.singletonList(obscure),
			new ShopItemSkips(), places(), places()).isEmpty());
	}

	/** Every placeable dropper is offered, so Shortest Path picks between them. */
	@Test
	public void everyPlaceableDropperIsOffered()
	{
		WorldPoint second = new WorldPoint(2900, 3300, 0);
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Bat bones", Collections.emptyList(), Arrays.asList("Giant bat", "Albino bat")));

		Set<WorldPoint> destinations = ShopRouteStops.destinations(totals, new ShopItemSkips(),
			places(), places("Giant bat", LAIR, "Albino bat", second));

		assertTrue(destinations.contains(LAIR));
		assertTrue("both droppers are offered, not just the first", destinations.contains(second));
	}

	/**
	 * Bat bones really are routable from the shipped bundle -- the exact item
	 * the report named, resolved through the real graph rather than a fixture.
	 */
	@Test
	public void batBonesResolveToARealPlaceInTheBundle() throws Exception
	{
		QuestGraph graph = QuestGraph.load(GSON);

		List<String> droppers = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if ("bat bones".equalsIgnoreCase(item.name()))
				{
					droppers = item.dropSourceNames();
					break;
				}
			}
		}
		assertTrue("the bundle no longer carries bat bones", droppers != null);
		assertFalse("bat bones name no droppers", droppers.isEmpty());

		Map<String, WorldPoint> byName = new HashMap<>();
		for (QuestGraph.DropLocation drop : graph.dropLocations())
		{
			byName.putIfAbsent(drop.sourceName().toLowerCase(),
				new WorldPoint(drop.x(), drop.y(), drop.plane()));
		}

		boolean placed = false;
		for (String dropper : droppers)
		{
			if (byName.containsKey(dropper.toLowerCase()))
			{
				placed = true;
				break;
			}
		}
		assertTrue("no dropper of bat bones has a location in the bundle", placed);
	}
}
