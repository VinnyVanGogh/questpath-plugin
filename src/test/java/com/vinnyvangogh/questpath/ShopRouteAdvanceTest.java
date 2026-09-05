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
 * Moving a shop trip on to its next stop, and knowing when to stop moving it.
 *
 * <p>The two cases worth guarding are both ways of getting stuck rather than
 * ways of going wrong: a trip that never advances (the bug this closes) and
 * a trip that clears the player's path with nothing to replace it (the bug
 * an over-eager fix would introduce).
 */
public class ShopRouteAdvanceTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint NEARBY = new WorldPoint(3232, 3218, 0);

	private static Function<String, WorldPoint> placing(String... names)
	{
		Map<String, WorldPoint> known = new HashMap<>();
		for (String name : names)
		{
			known.put(name, NEARBY);
		}
		return known::get;
	}

	private static QuestItems.Total total(String name, int wanted, long owned, List<String> shops)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, owned, 0L, false,
			Collections.emptyList(), shops);
		total.add(new QuestItems.Line(name, wanted, owned, false), "Sheep Herder");
		return total;
	}

	private static List<ItemCompletionWatcher.Completion> completed()
	{
		return Collections.singletonList(new ItemCompletionWatcher.Completion("Rope", 1, "Sheep Herder"));
	}

	private static ShopRouteAdvance.Decision observe(boolean routeActive,
		List<ItemCompletionWatcher.Completion> completions, List<QuestItems.Total> totals)
	{
		return ShopRouteAdvance.observe(routeActive, completions, totals, new ShopItemSkips(),
			placing("Near shop"), LUMBRIDGE);
	}

	/** No trip running means nothing to advance, whatever just finished. */
	@Test
	public void nothingHappensWhenNoTripIsRunning()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Bucket", 1, 0, Collections.singletonList("Near shop")));

		assertEquals(ShopRouteAdvance.Action.NOTHING,
			observe(false, completed(), totals).action());
	}

	/**
	 * A trip is not disturbed by a tick where nothing finished. Re-routing
	 * on every tick would fight whatever the player is doing.
	 */
	@Test
	public void nothingHappensWhenNothingFinished()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Bucket", 1, 0, Collections.singletonList("Near shop")));

		assertEquals(ShopRouteAdvance.Action.NOTHING,
			observe(true, Collections.emptyList(), totals).action());
	}

	/** The ordinary case: something finished, something is left, so move on. */
	@Test
	public void finishingAnItemSendsTheTripToWhatIsLeft()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 1, Collections.singletonList("Near shop")),
			total("Bucket", 1, 0, Collections.singletonList("Near shop")));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.REROUTE, decision.action());
		assertEquals("Bucket", decision.nextItem());
		assertEquals("Near shop", decision.nextShop());
		assertEquals(1, decision.remaining());
		assertTrue(decision.destinations().contains(NEARBY));
	}

	/** The last item finishing ends the trip rather than re-sending it. */
	@Test
	public void finishingTheLastItemEndsTheTrip()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			total("Rope", 1, 1, Collections.singletonList("Near shop")));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.FINISHED, decision.action());
		assertTrue(decision.destinations().isEmpty());
	}

	/**
	 * Stops remaining that nobody can be routed to end the trip too, rather
	 * than re-sending an empty destination set.
	 *
	 * <p>This is the case an over-eager fix gets wrong. Handing Shortest
	 * Path nothing clears the player's path and leaves them standing in a
	 * shop with no route and no explanation -- indistinguishable from the
	 * plugin having crashed. What is still outstanding is on the Items tab,
	 * which is the honest home for a stop nobody can walk to.
	 */
	@Test
	public void remainingItemsWithNowhereToGoEndTheTripInsteadOfClearingThePath()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 1, Collections.singletonList("Near shop")),
			total("Raw chompy", 1, 0, Collections.emptyList()));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.FINISHED, decision.action());
		assertEquals("and it still knows a stop is outstanding", 1, decision.remaining());
	}

	/**
	 * A stop nothing sells is stepped over, and the trip names one it can
	 * actually walk to.
	 *
	 * <p>This test previously asserted the opposite -- that the unbuyable
	 * item became the next stop with an empty shop name. That was the
	 * behaviour on screen and it was the bug: Bat bones headed a real
	 * shopping list purely because the list's last tiebreak is alphabetical,
	 * so the trip announced an item no shop stocks and pointed nowhere. It is
	 * still on the list and still outstanding; it is just not what the line
	 * on the ground leads to.
	 */
	@Test
	public void anUnplaceableStopIsSteppedOverRatherThanNamed()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Rope", 1, 1, Collections.singletonList("Near shop")),
			total("Raw chompy", 1, 0, Collections.emptyList()),
			total("Bucket", 1, 0, Collections.singletonList("Near shop")));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.REROUTE, decision.action());
		assertEquals("Bucket", decision.nextItem());
		assertEquals("Near shop", decision.nextShop());
		assertEquals("the unbuyable one is still counted as outstanding",
			2, decision.remaining());
	}
}
