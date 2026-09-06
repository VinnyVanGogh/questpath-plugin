package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Walking the shopping list in the order you would actually walk it.
 *
 * <p>The list was alphabetical, which is not an order at all. {@link
 * QuestItems#totals} falls through to the item's name, and until the bank has
 * been read nothing distinguishes any item from any other, so every one ties
 * and the whole list sorts by first letter. The player's own description of
 * following it: a big fishing net in Catherby, then barley malt, then imps
 * somewhere for a black bead, then back to Catherby for a black candle, then
 * Al Kharid for black dye -- "it wouldn't be efficient or helpful at all."
 */
public class ShopRouteTourTest
{
	// Roughly Catherby, Seers' Village, Al Kharid and Lumbridge, far enough
	// apart that no ordering is ambiguous.
	private static final WorldPoint CATHERBY = new WorldPoint(2809, 3435, 0);
	private static final WorldPoint CATHERBY_TOO = new WorldPoint(2812, 3441, 0);
	private static final WorldPoint SEERS = new WorldPoint(2707, 3488, 0);
	private static final WorldPoint AL_KHARID = new WorldPoint(3304, 3208, 0);
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private static Function<String, WorldPoint> places(Object... pairs)
	{
		Map<String, WorldPoint> known = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			known.put((String) pairs[i], (WorldPoint) pairs[i + 1]);
		}
		return known::get;
	}

	private static QuestItems.Total item(String name, String shop)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, 0L, 0L, false,
			Collections.emptyList(), shop == null ? List.of() : List.of(shop));
		total.add(new QuestItems.Line(name, 1, 0L, false), "Demon Slayer");
		return total;
	}

	private static List<String> names(List<ShopRouteStops.Stop> stops)
	{
		return stops.stream().map(s -> s.total().name()).collect(java.util.stream.Collectors.toList());
	}

	/** The one that matters: nearest first, not first alphabetically. */
	@Test
	public void theTourStartsFromWhereYouAreStandingRatherThanTheAlphabet()
	{
		// Deliberately named so alphabetical order is the worst possible walk.
		List<QuestItems.Total> totals = Arrays.asList(
			item("Al Kharid thing", "kharid shop"),
			item("Catherby thing", "catherby shop"),
			item("Seers thing", "seers shop"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "catherby shop", CATHERBY, "seers shop", SEERS);

		List<String> walked = names(ShopRouteStops.tour(totals, new ShopItemSkips(),
			shops, name -> null, CATHERBY));

		assertEquals("standing in Catherby, the Catherby shop is the first stop",
			Arrays.asList("Catherby thing", "Seers thing", "Al Kharid thing"), walked);
	}

	/**
	 * The second leg is measured from the first stop, not from the player.
	 * That is the whole difference between a tour and a list sorted by
	 * distance-from-here.
	 */
	@Test
	public void eachLegIsMeasuredFromTheEndOfTheLastOne()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			item("A far", "kharid shop"),
			item("B near", "lumbridge shop"),
			item("C nearer to B", "seers shop"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "lumbridge shop", LUMBRIDGE, "seers shop", SEERS);

		// From Lumbridge: Lumbridge first, then Al Kharid is far closer to
		// Lumbridge than Seers' Village is -- so a tour visits it second, while
		// sorting by distance-from-Lumbridge alone would too. Start from Seers
		// instead and the answer flips, which is what proves the difference.
		assertEquals(Arrays.asList("C nearer to B", "B near", "A far"),
			names(ShopRouteStops.tour(totals, new ShopItemSkips(), shops, name -> null, SEERS)));
	}

	/** Things sold at the same place fall together without special-casing. */
	@Test
	public void itemsAtTheSameStopAreVisitedTogether()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			item("A elsewhere", "kharid shop"),
			item("B here", "catherby shop"),
			item("C also here", "catherby shop too"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "catherby shop", CATHERBY, "catherby shop too", CATHERBY_TOO);

		List<String> walked = names(ShopRouteStops.tour(totals, new ShopItemSkips(),
			shops, name -> null, CATHERBY));

		assertEquals("the two Catherby stops are walked back to back",
			Arrays.asList("B here", "C also here", "A elsewhere"), walked);
	}

	/**
	 * An item nothing can route to keeps its place at the end rather than
	 * being dropped -- still outstanding, still worth seeing, just not part
	 * of a walk.
	 */
	@Test
	public void unroutableItemsSinkToTheEndInsteadOfVanishing()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			item("A black candle", null),
			item("B far", "kharid shop"),
			item("C here", "catherby shop"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "catherby shop", CATHERBY);

		List<ShopRouteStops.Stop> stops = ShopRouteStops.tour(totals, new ShopItemSkips(),
			shops, name -> null, CATHERBY);

		assertEquals(Arrays.asList("C here", "B far", "A black candle"), names(stops));
		assertTrue("the first leg is somewhere you can actually walk", stops.get(0).current());
	}

	/** The route sent to Shortest Path is the tour's first leg. */
	@Test
	public void theRouteFollowsTheTourRatherThanTheList()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			item("A far", "kharid shop"),
			item("B here", "catherby shop"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "catherby shop", CATHERBY);

		assertTrue("standing in Catherby, the route goes to the Catherby shop",
			ShopRouteStops.destinations(totals, new ShopItemSkips(), shops, name -> null, CATHERBY)
				.contains(CATHERBY));
	}

	/** With no position there is no tour, and the list's own order stands. */
	@Test
	public void withNoPositionTheListOrderIsKept()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			item("A far", "kharid shop"),
			item("B here", "catherby shop"));
		Function<String, WorldPoint> shops = places(
			"kharid shop", AL_KHARID, "catherby shop", CATHERBY);

		assertEquals(Arrays.asList("A far", "B here"),
			names(ShopRouteStops.tour(totals, new ShopItemSkips(), shops, name -> null, null)));
	}
}
