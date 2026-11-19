package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Board, 2026-11-18: standing in Al Kharid with blood runes, bronze bars and
 * buckets on the list, the trip said "next up ... Obli's General Store" --
 * a shop in Shilo Village -- when each of those could be bought where the
 * player stood. The label also gave no hint where Obli's was, or which of
 * the needed items it was for.
 */
public class ShopRouteNearestTest
{
	private static final WorldPoint AL_KHARID = new WorldPoint(3300, 3190, 0);
	private static final WorldPoint OBLIS = new WorldPoint(2830, 2970, 0);
	private static final WorldPoint KHARID_RUNES = new WorldPoint(3310, 3185, 0);
	private static final WorldPoint KHARID_SMITH = new WorldPoint(3295, 3180, 0);
	private static final WorldPoint KHARID_GENERAL = new WorldPoint(3315, 3182, 0);

	private static final String OBLIS_NAME = "Obli's General Store";
	private static final String RUNES = "Al Kharid Rune Shop";
	private static final String SMITH = "Zeke's Superior Scimitars";
	private static final String GENERAL = "Al Kharid General Store";

	private static ShopPlaces places()
	{
		Map<String, WorldPoint> at = new HashMap<>();
		at.put(OBLIS_NAME, OBLIS);
		at.put(RUNES, KHARID_RUNES);
		at.put(SMITH, KHARID_SMITH);
		at.put(GENERAL, KHARID_GENERAL);
		Map<String, String> town = new HashMap<>();
		town.put(OBLIS_NAME, "Shilo Village");
		town.put(RUNES, "Al Kharid");
		town.put(SMITH, "Al Kharid");
		town.put(GENERAL, "Al Kharid");
		return new ShopPlaces(at::get, town::get);
	}

	private static QuestItems.Total item(String name, String... shops)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, 0L, 0L, false,
			Collections.emptyList(), Arrays.asList(shops));
		total.add(new QuestItems.Line(name, 1, 0L, false), "Some Quest");
		return total;
	}

	/** Listed first, only Obli's sells it, so list order would name Shilo. */
	private static List<QuestItems.Total> alKharidList()
	{
		return Arrays.asList(
			item("Anti-dragon shield", OBLIS_NAME),
			item("Blood rune", RUNES, OBLIS_NAME),
			item("Bronze bar", SMITH, GENERAL),
			item("Bucket", GENERAL, OBLIS_NAME));
	}

	private static List<ItemCompletionWatcher.Completion> completed()
	{
		return Collections.singletonList(new ItemCompletionWatcher.Completion("Rope", 1, "Some Quest"));
	}

	@Test
	public void nextStopFromAlKharidIsNeverOblis()
	{
		ShopRouteAdvance.Decision decision = ShopRouteAdvance.observe(true, completed(), alKharidList(),
			new ShopItemSkips(), places(), name -> null, AL_KHARID);

		assertEquals(ShopRouteAdvance.Action.REROUTE, decision.action());
		assertTrue("named stop is an Al Kharid one: " + decision.nextShop(),
			decision.nextShop().endsWith("(Al Kharid)"));
		assertFalse(decision.nextShop().contains("Obli"));
		// Shilo may stay among the candidates for an item it also sells --
		// Shortest Path picks by real distance -- but the Al Kharid shop must
		// be offered, and the stop for an Obli's-only item must not be.
		assertTrue("the line goes to Al Kharid", decision.destinations().stream()
			.anyMatch(p -> p.getX() > 3000));
	}

	@Test
	public void theStopNamesItsAreaFromTheBundle()
	{
		QuestItems.Total bucket = item("Bucket", GENERAL, OBLIS_NAME);
		ShopRouteStops.Destination far = ShopRouteStops.destinationOf(bucket, places(), name -> null, null);
		assertEquals("Al Kharid General Store (Al Kharid)", far.label());

		String line = ShopRouteStops.destinationLine(
			item("Anti-dragon shield", OBLIS_NAME), places(), name -> null, AL_KHARID);
		assertTrue(line, line.contains("Obli's General Store (Shilo Village)"));
	}

	@Test
	public void aShopWithNoAreaIsJustItsName()
	{
		ShopRouteStops.Destination d = ShopRouteStops.destinationOf(item("Bucket", "Plain shop"),
			name -> AL_KHARID, name -> null, null);
		assertEquals("Plain shop", d.label());
	}

	@Test
	public void theStopSaysWhatElseItSuppliesThatIsStillNeeded()
	{
		List<QuestItems.Total> totals = alKharidList();
		ShopRouteStops.Destination stop = ShopRouteStops.destinationOf(totals.get(3), places(), name -> null,
			AL_KHARID);

		assertEquals(Arrays.asList("Bronze bar"),
			ShopRouteStops.alsoSupplies(stop, totals.get(3), totals, new ShopItemSkips()));

		String line = ShopRouteStops.destinationLine(totals.get(3), places(), name -> null, AL_KHARID, totals,
			new ShopItemSkips());
		assertTrue(line, line.contains("Al Kharid General Store (Al Kharid)"));
	}
}
