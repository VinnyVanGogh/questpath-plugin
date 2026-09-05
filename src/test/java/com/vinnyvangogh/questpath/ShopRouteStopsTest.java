package com.vinnyvangogh.questpath;

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which stop is next, and what the overlay is allowed to claim about it.
 *
 * <p>The whole point of pulling this out of {@link ShopRouteOverlay} is that
 * a destination line is a claim about where to walk, and a wrong one costs a
 * trip. The cases that matter are the ones where questpath does not actually
 * know: a shop it cannot place, and an item nothing is known to sell. Those
 * must stay distinguishable from each other and from a real destination,
 * because the failure they guard against -- a confident line pointing
 * nowhere -- looks exactly like a correct one on screen.
 */
public class ShopRouteStopsTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint NEARBY = new WorldPoint(3232, 3218, 0);
	private static final WorldPoint FAR = new WorldPoint(2900, 3400, 0);

	/** A shop-name lookup that can place only the names given to it. */
	private static Function<String, WorldPoint> locates(String... names)
	{
		Map<String, WorldPoint> known = new HashMap<>();
		for (String name : names)
		{
			known.put(name, NEARBY);
		}
		return known::get;
	}

	/** A lookup placing each name at the point paired with it. */
	private static Function<String, WorldPoint> placing(Object... pairs)
	{
		Map<String, WorldPoint> known = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			known.put((String) pairs[i], (WorldPoint) pairs[i + 1]);
		}
		return known::get;
	}

	/**
	 * A total wanting {@code wanted} and holding {@code owned}, attributed to
	 * one quest, sold by the named shops.
	 */
	private static QuestItems.Total total(String name, int wanted, long owned, String quest,
		List<String> shopNames)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, owned, 0L, false,
			Collections.emptyList(), shopNames);
		total.add(new QuestItems.Line(name, wanted, owned, false), quest);
		return total;
	}

	private static QuestItems.Total total(String name, int wanted, long owned)
	{
		return total(name, wanted, owned, "Sheep Herder", Collections.singletonList("General store"));
	}

	/** Only what is still short is a stop. Everything in hand is already done. */
	@Test
	public void satisfiedTotalsAreNotStops()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Bucket", 2, 2),
			total("Rope", 1, 0));

		List<ShopRouteStops.Stop> next = ShopRouteStops.next(totals, new ShopItemSkips(), locates("General store"));

		assertEquals(1, next.size());
		assertEquals("Rope", next.get(0).total().name());
	}

	/** Exactly one stop is the one being walked to, and it is the first. */
	@Test
	public void onlyTheFirstStopIsCurrent()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 0),
			total("Bucket", 2, 0),
			total("Spade", 1, 0));

		List<ShopRouteStops.Stop> next = ShopRouteStops.next(totals, new ShopItemSkips(), locates("General store"));

		assertEquals(3, next.size());
		assertTrue("the first outstanding item is the one being walked to",
			next.get(0).current());
		assertFalse(next.get(1).current());
		assertFalse(next.get(2).current());
	}

	/**
	 * A skip moves the next stop along rather than leaving a gap -- so the
	 * item after a skipped one becomes current, not merely first-in-list.
	 */
	@Test
	public void skippingTheCurrentStopPromotesTheNextOne()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 0),
			total("Bucket", 2, 0));
		ShopItemSkips skips = new ShopItemSkips();
		skips.skip("Rope");

		List<ShopRouteStops.Stop> next = ShopRouteStops.next(totals, skips, locates("General store"));

		assertEquals(1, next.size());
		assertEquals("Bucket", next.get(0).total().name());
		assertTrue("with the skip in place, the next item is the live one",
			next.get(0).current());
	}

	/** A skip is matched however it was cased, the way the skip button stores it. */
	@Test
	public void skipsMatchRegardlessOfCase()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Bucket", 1, 0));
		ShopItemSkips skips = new ShopItemSkips();
		skips.skip("bUcKeT");

		assertTrue(ShopRouteStops.next(totals, skips, locates("General store")).isEmpty());
	}

	/** Undoing a skip puts the stop back. */
	@Test
	public void unskippingRestoresTheStop()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Bucket", 1, 0));
		ShopItemSkips skips = new ShopItemSkips();
		skips.skip("Bucket");
		skips.unskip("Bucket");

		assertEquals(1, ShopRouteStops.next(totals, skips, locates("General store")).size());
	}

	/** A placeable shop is named, with the quest that wants it. */
	@Test
	public void aPlaceableShopIsNamedWithItsQuest()
	{
		QuestItems.Total bucket = total("Bucket", 1, 0, "Sheep Herder",
			Collections.singletonList("General store"));

		assertEquals("-> General store -- for Sheep Herder",
			ShopRouteStops.destinationLine(bucket, locates("General store"), null));
	}

	/**
	 * The first shop with a coordinate wins, not simply the first shop.
	 *
	 * <p>This is the case that makes the fallbacks below worth having: an
	 * item is often sold in several places and only some of them are placed,
	 * so refusing to look past the first name would report "no location" for
	 * an item this plugin can route to perfectly well.
	 */
	@Test
	public void anUnplaceableShopIsPassedOverForOneThatCanBeRouted()
	{
		QuestItems.Total rope = total("Rope", 1, 0, "Sheep Herder",
			Arrays.asList("Nowhere in the bundle", "Ned's ropes"));

		assertEquals("-> Ned's ropes -- for Sheep Herder",
			ShopRouteStops.destinationLine(rope, locates("Ned's ropes"), null));
	}

	/**
	 * With a position to measure from, the nearest shop is named -- not
	 * whichever the bundle listed first.
	 *
	 * <p>This is the bug the {@link ShopRouteStops.Destination} type exists
	 * to close. Shortest Path is handed every shop selling the item and
	 * walks to the closest; the overlay used to name list position one, so
	 * the label could confidently give a shop the player was visibly not
	 * walking towards.
	 */
	@Test
	public void theNearestPlaceableShopIsTheOneNamed()
	{
		QuestItems.Total rope = total("Rope", 1, 0, "Sheep Herder",
			Arrays.asList("Far shop", "Near shop"));
		Function<String, WorldPoint> shops = placing("Far shop", FAR, "Near shop", NEARBY);

		ShopRouteStops.Destination destination =
			ShopRouteStops.destinationOf(rope, shops, LUMBRIDGE);

		assertEquals("Near shop", destination.shopName());
		assertEquals(NEARBY, destination.point());
		assertEquals("and it says the other one was in the running", 1, destination.alternatives());
	}

	/**
	 * When alternatives exist the label admits it, because the named shop is
	 * a straight-line guess at a choice Shortest Path makes by real path
	 * distance.
	 */
	@Test
	public void aLabelWithAlternativesDoesNotOverclaim()
	{
		QuestItems.Total rope = total("Rope", 1, 0, "Sheep Herder",
			Arrays.asList("Far shop", "Near shop"));
		Function<String, WorldPoint> shops = placing("Far shop", FAR, "Near shop", NEARBY);

		assertEquals("-> Near shop or 1 other -- for Sheep Herder",
			ShopRouteStops.destinationLine(rope, shops, LUMBRIDGE));
	}

	/** With no position yet, the first placeable shop is named as before. */
	@Test
	public void withNoPositionTheFirstPlaceableShopIsNamed()
	{
		QuestItems.Total rope = total("Rope", 1, 0, "Sheep Herder",
			Arrays.asList("Far shop", "Near shop"));
		Function<String, WorldPoint> shops = placing("Far shop", FAR, "Near shop", NEARBY);

		assertEquals("Far shop", ShopRouteStops.destinationOf(rope, shops, null).shopName());
	}

	/**
	 * A shop known by name but carrying no tile says so. Silence here would
	 * read as "no stop", and a guessed tile would send the player to the
	 * wrong place -- both worse than admitting the gap.
	 */
	@Test
	public void aShopWithNoCoordinateIsAdmittedRatherThanGuessedAt()
	{
		QuestItems.Total rope = total("Rope", 1, 0, "Sheep Herder",
			Collections.singletonList("Nowhere in the bundle"));

		assertNull(ShopRouteStops.destinationOf(rope, locates(), null));
		assertEquals("Known shop, but no location to route to -- for Sheep Herder",
			ShopRouteStops.destinationLine(rope, locates(), null));
	}

	/** Nothing known to sell it is a different admission, and stays different. */
	@Test
	public void anItemNoShopSellsIsItsOwnAnswer()
	{
		QuestItems.Total chompy = total("Raw chompy", 1, 0, "Recipe for Disaster",
			Collections.emptyList());

		assertEquals("No known shop sells this yet -- for Recipe for Disaster",
			ShopRouteStops.destinationLine(chompy, locates(), null));
	}

	/** With no quest attributed, the line stops rather than trailing "-- for". */
	@Test
	public void anUnattributedStopDropsTheForClause()
	{
		QuestItems.Total bucket = new QuestItems.Total("Bucket", 0, 0L, 0L, false,
			Collections.emptyList(), Collections.singletonList("General store"));
		bucket.add(new QuestItems.Line("Bucket", 1, 0L, false));

		assertEquals("-> General store",
			ShopRouteStops.destinationLine(bucket, locates("General store"), null));
	}

	/**
	 * The trip goes to one stop at a time, not to every shop at once.
	 *
	 * <p>This is the bug the logs caught: the button sent 31 destinations and
	 * three consecutive skips sent 29, 29 and 29, because the shops selling a
	 * skipped item also sell a dozen other things still on the list. The path
	 * never moved, so skipping looked broken. Sending only the current stop's
	 * shops is what makes a skip visible.
	 */
	@Test
	public void onlyTheCurrentStopIsRoutedTo()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 0, "Sheep Herder", Collections.singletonList("Near shop")),
			total("Bucket", 1, 0, "Sheep Herder", Collections.singletonList("Far shop")));
		Function<String, WorldPoint> shops = placing("Far shop", FAR, "Near shop", NEARBY);

		Set<WorldPoint> destinations =
			ShopRouteStops.destinations(totals, new ShopItemSkips(), shops);

		assertEquals("one stop, not both", 1, destinations.size());
		assertTrue(destinations.contains(NEARBY));
	}

	/** And skipping the current stop actually moves where the line goes. */
	@Test
	public void skippingTheCurrentStopMovesTheRoute()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 0, "Sheep Herder", Collections.singletonList("Near shop")),
			total("Bucket", 1, 0, "Sheep Herder", Collections.singletonList("Far shop")));
		Function<String, WorldPoint> shops = placing("Far shop", FAR, "Near shop", NEARBY);

		ShopItemSkips skips = new ShopItemSkips();
		skips.skip("Rope");

		Set<WorldPoint> after = ShopRouteStops.destinations(totals, skips, shops);
		assertEquals(1, after.size());
		assertTrue("the destination changed, which it did not before", after.contains(FAR));
	}

	/**
	 * An item nothing sells is stepped over rather than becoming a stop with
	 * no destination.
	 *
	 * <p>Bat bones sit at the top of a real shopping list purely because the
	 * list's last tiebreak is alphabetical and the bank had not been read, so
	 * everything tied. Naming that the current stop points the trip nowhere.
	 */
	@Test
	public void anItemNoShopSellsIsNotTheCurrentStop()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Bat bones", 1, 0, "Merlin's Crystal", Collections.emptyList()),
			total("Rope", 1, 0, "Sheep Herder", Collections.singletonList("Near shop")));
		Function<String, WorldPoint> shops = placing("Near shop", NEARBY);

		List<ShopRouteStops.Stop> stops = ShopRouteStops.next(totals, new ShopItemSkips(), shops);

		assertEquals("both still listed", 2, stops.size());
		assertFalse("the unbuyable one is not what the trip heads for",
			stops.get(0).current());
		assertTrue(stops.get(1).current());
		assertTrue(ShopRouteStops.destinations(totals, new ShopItemSkips(), shops)
			.contains(NEARBY));
	}

	/**
	 * With nothing routable at all, the first stop is still named so the
	 * overlay can say what is outstanding instead of going blank.
	 */
	@Test
	public void withNothingRoutableTheFirstStopIsStillNamed()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Bat bones", 1, 0, "Merlin's Crystal", Collections.emptyList()));

		List<ShopRouteStops.Stop> stops =
			ShopRouteStops.next(totals, new ShopItemSkips(), locates());

		assertEquals(1, stops.size());
		assertTrue(stops.get(0).current());
		assertTrue("but there is nowhere to send anybody",
			ShopRouteStops.destinations(totals, new ShopItemSkips(), locates()).isEmpty());
	}
}
