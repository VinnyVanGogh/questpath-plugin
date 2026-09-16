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
import static org.junit.Assert.assertFalse;
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
			placing("Near shop"), placing("Near monster"), LUMBRIDGE);
	}

	/** A total whose only source is something you have to kill for it. */
	private static QuestItems.Total dropOnly(String name, int wanted, long owned, String monster)
	{
		QuestItems.Total total = new QuestItems.Total(name, 0, owned, 0L, false,
			Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList(monster));
		total.add(new QuestItems.Line(name, wanted, owned, false), "Sheep Herder");
		return total;
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

	/**
	 * A stop you have to kill something for keeps the trip going.
	 *
	 * <p><b>The report this closes.</b> "The shop route overlay only stays up
	 * for one item, and then from there it's not showing anymore." Every
	 * ShopRouteStops call in here resolved stops with shops only, while
	 * ShopRouteOverlay had always drawn its list with a drop locator too. So
	 * the panel could say "Toad crunchies -> Tortoise" and this could not see
	 * that stop at all: destinations came back empty, FINISHED was reported,
	 * the trip was cleared and the overlay stood down -- one item in, over a
	 * list that still had items on it, announcing "shopping done" while
	 * doing it.
	 */
	@Test
	public void aMonsterStopIsSomewhereToGo()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			dropOnly("Toad crunchies", 1, 0, "Near monster"));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals("a drop source is a stop, not the end of the trip",
			ShopRouteAdvance.Action.REROUTE, decision.action());
		assertEquals("Toad crunchies", decision.nextItem());
		assertEquals(1, decision.remaining());
		assertFalse("and it has somewhere to send you", decision.destinations().isEmpty());
	}

	/**
	 * A shop stop and a monster stop on the same trip both count, and the
	 * trip is not over until neither is left.
	 */
	@Test
	public void shopsAndMonstersShareOneTrip()
	{
		List<QuestItems.Total> totals = List.of(
			total("Bucket", 1, 0, Collections.singletonList("Near shop")),
			dropOnly("Toad crunchies", 1, 0, "Near monster"));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.REROUTE, decision.action());
		assertEquals("both stops are still outstanding", 2, decision.remaining());
	}

	/**
	 * The trip really is over when the only thing left is a monster nobody
	 * can place.
	 *
	 * <p>The guard against over-correcting: a drop source with no tile is
	 * still nowhere to walk to, and re-sending an empty destination set would
	 * clear the player's path and leave them standing there. FINISHED is the
	 * honest answer, and it is the same one an unplaceable shop already got.
	 */
	@Test
	public void anUnplaceableMonsterStillEndsTheTrip()
	{
		List<QuestItems.Total> totals = Collections.singletonList(
			dropOnly("Toad crunchies", 1, 0, "Somewhere nobody has a tile for"));

		assertEquals(ShopRouteAdvance.Action.FINISHED,
			observe(true, completed(), totals).action());
	}

	/**
	 * The monster stop can be the one you are sent to, and is the one named.
	 *
	 * <p>Separate from {@link #aMonsterStopIsSomewhereToGo} because a
	 * different line does this work. {@code ShopRouteStops#next} picks the
	 * current stop as "the first one that can actually be walked to", and a
	 * shop-only locator can never walk to a monster -- so with the drop stop
	 * listed first, a drop-blind call silently steps over it and announces
	 * the shop behind it instead. The trip keeps running either way, which is
	 * why the other tests do not catch it; what changes is that chat says
	 * "next up Bucket" while the line on the ground goes to the tortoises.
	 *
	 * <p>Verified by mutation: reverting the {@code next(...)} call to its
	 * shop-only overload fails this test and nothing else in the class.
	 */
	@Test
	public void theMonsterStopIsTheOneAnnouncedWhenItIsTheOneRoutedTo()
	{
		List<QuestItems.Total> totals = List.of(
			dropOnly("Toad crunchies", 1, 0, "Near monster"),
			total("Bucket", 1, 0, Collections.singletonList("Near shop")));

		ShopRouteAdvance.Decision decision = observe(true, completed(), totals);

		assertEquals(ShopRouteAdvance.Action.REROUTE, decision.action());
		assertEquals("the first stop that can be walked to is the monster",
			"Toad crunchies", decision.nextItem());
	}
}
