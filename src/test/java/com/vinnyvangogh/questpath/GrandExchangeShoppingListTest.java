package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link GrandExchangeShoppingList#of}: satisfied totals never become a
 * line, an outstanding total's quantity and item id pass through unchanged,
 * and the order is plain alphabetical -- deliberately not {@link
 * QuestItems.Total}'s own in-progress/ran-out priority, which exists to
 * sequence a walking trip this list has no need to sequence. See {@link
 * GrandExchangeShoppingList}'s own class doc for why.
 */
public class GrandExchangeShoppingListTest
{
	private static QuestItems.Total total(String name, int itemId, int wanted, long owned)
	{
		QuestItems.Total total = new QuestItems.Total(name, itemId, owned, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		total.add(new QuestItems.Line(name, wanted, owned, false), "Sheep Herder");
		return total;
	}

	@Test
	public void nullOrEmptyTotalsProduceAnEmptyList()
	{
		assertTrue(GrandExchangeShoppingList.of(null).isEmpty());
		assertTrue(GrandExchangeShoppingList.of(Collections.emptyList()).isEmpty());
	}

	@Test
	public void satisfiedTotalsAreNeverLines()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Bucket", 100, 2, 2),
			total("Rope", 101, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals);

		assertEquals(1, lines.size());
		assertEquals("Rope", lines.get(0).name());
	}

	/** Quantity and item id pass through untouched -- this class only reorders and filters. */
	@Test
	public void outstandingCountAndItemIdPassThrough()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Ball of wool", 42, 5, 2));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals);

		assertEquals(1, lines.size());
		GrandExchangeShoppingList.Line line = lines.get(0);
		assertEquals("Ball of wool", line.name());
		assertEquals(42, line.itemId());
		assertEquals(3L, line.outstanding());
	}

	/**
	 * Plain alphabetical, case-insensitively -- not the in-progress/ran-out
	 * priority {@code QuestItems#totals()} sorts by for a walking tour. Built
	 * out of alphabetical order, and out of the id order they were added in,
	 * so a sort that silently fell back to either would be caught.
	 */
	@Test
	public void orderIsPlainAlphabeticalRegardlessOfProgress()
	{
		QuestItems.Total zebra = total("Zebra head", 1, 5, 0);
		QuestItems.Total apple = total("apple", 2, 5, 0);
		QuestItems.Total mango = total("Mango", 3, 5, 0);

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			Arrays.asList(zebra, apple, mango));

		assertEquals(Arrays.asList("apple", "Mango", "Zebra head"),
			Arrays.asList(lines.get(0).name(), lines.get(1).name(), lines.get(2).name()));
	}

	// ------------------------------------------------------------------
	// Queued / next / left: the three states an item can be in at a Grand
	// Exchange counter, and the rules that stop them contradicting each
	// other. See GrandExchangeOffers for what makes an offer live.
	// ------------------------------------------------------------------

	private static GrandExchangeOffers buying(String name, int itemId, int quantity, int bought)
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, net.runelite.api.GrandExchangeOfferState.BUYING,
			itemId, name, quantity, bought);
		return offers;
	}

	/**
	 * An item already on offer stays on the list and is marked, rather than
	 * vanishing from it.
	 *
	 * <p>Dropping it would make the list shorter and the player's job harder:
	 * which of the eight slots are already spent is the thing they most need
	 * to know while standing at the counter.
	 */
	@Test
	public void anItemOnOfferIsMarkedRatherThanDropped()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Air talisman", 1438, 5, 0),
			total("Bucket", 1925, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Air talisman", 1438, 5, 2));

		assertEquals(2, lines.size());
		assertTrue(lines.get(0).queued());
		assertEquals("2 of 5", lines.get(0).order().describe());
		assertFalse(lines.get(1).queued());
	}

	/**
	 * "Next" skips what is already on offer -- the whole point of the pass.
	 * Before it the list went on naming an item that was bought and waiting,
	 * because collection is the only thing {@link QuestItems} can see.
	 */
	@Test
	public void nextSkipsWhatIsAlreadyOnOffer()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Air talisman", 1438, 5, 0),
			total("Bucket", 1925, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Air talisman", 1438, 5, 0));

		// Air talisman sorts first and is still the head of the list.
		assertEquals("Air talisman", lines.get(0).name());
		assertEquals("Bucket", GrandExchangeShoppingList.next(lines).name());
	}

	/**
	 * Everything on offer means there is nothing to suggest, and that has to
	 * read as null rather than as the head of the list.
	 *
	 * <p>A caller that falls back to the head suggests an item the player has
	 * already bought -- the original bug, restored by the fix meant to remove
	 * it.
	 */
	@Test
	public void nextIsNullWhenEverythingIsOnOffer()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Air talisman", 1438, 5, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Air talisman", 1438, 5, 0));

		assertEquals(1, lines.size());
		assertNull(GrandExchangeShoppingList.next(lines));
	}

	/** The two sections partition the list: nothing in both, nothing in neither. */
	@Test
	public void queuedAndLeftPartitionTheList()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Air talisman", 1438, 5, 0),
			total("Bucket", 1925, 1, 0),
			total("Coal", 453, 3, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Bucket", 1925, 1, 0));

		List<GrandExchangeShoppingList.Line> queued = GrandExchangeShoppingList.queued(lines);
		List<GrandExchangeShoppingList.Line> left = GrandExchangeShoppingList.left(lines);

		assertEquals(1, queued.size());
		assertEquals("Bucket", queued.get(0).name());
		assertEquals(2, left.size());
		assertEquals(lines.size(), queued.size() + left.size());
		assertEquals("next is the head of what is left",
			GrandExchangeShoppingList.next(lines).name(), left.get(0).name());
	}

	/** With nothing tracking offers, the list is exactly what it always was. */
	@Test
	public void noOfferTrackerLeavesEveryLineUnqueued()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Air talisman", 1438, 5, 0),
			total("Bucket", 1925, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null);

		assertTrue(GrandExchangeShoppingList.queued(lines).isEmpty());
		assertEquals(2, GrandExchangeShoppingList.left(lines).size());
		assertEquals("Air talisman", GrandExchangeShoppingList.next(lines).name());
	}

	// ------------------------------------------------------------------
	// What the Grand Exchange does not sell. The rule these pin is the
	// project's oldest: never suggest something the account cannot act on.
	// ------------------------------------------------------------------

	/** Only Karamjan rum is untradeable; everything else is on the Grand Exchange. */
	private static GrandExchangeShoppingList.Tradeability exceptRum()
	{
		return (itemId, name) -> "Karamjan rum".equals(name)
			? GrandExchangeShoppingList.Availability.NOT_ON_GE
			: GrandExchangeShoppingList.Availability.BUYABLE;
	}

	/**
	 * An untradeable quest item is never the next thing to buy.
	 *
	 * <p>Karamjan rum is the one the player hit. It sorts before Rope, so a
	 * list that only skipped what was on offer suggested it -- at a counter
	 * that has never sold one and never will.
	 */
	@Test
	public void anUntradeableItemIsNeverSuggested()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Karamjan rum", 431, 1, 0),
			total("Rope", 954, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, null, exceptRum());

		assertEquals("Rope", GrandExchangeShoppingList.next(lines).name());
		assertEquals(1, GrandExchangeShoppingList.left(lines).size());
	}

	/**
	 * It is moved, not dropped. The account still needs it, so a buy list
	 * that quietly shortened itself would leave the trip looking finished.
	 */
	@Test
	public void anUntradeableItemIsListedSeparatelyRatherThanDropped()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Karamjan rum", 431, 1, 0),
			total("Rope", 954, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, null, exceptRum());

		assertEquals(2, lines.size());
		List<GrandExchangeShoppingList.Line> elsewhere = GrandExchangeShoppingList.unbuyable(lines);
		assertEquals(1, elsewhere.size());
		assertEquals("Karamjan rum", elsewhere.get(0).name());
		assertEquals(GrandExchangeShoppingList.Availability.NOT_ON_GE,
			elsewhere.get(0).availability());
	}

	/**
	 * "questpath cannot look this up" stays distinct from "the game does not
	 * trade it" -- both are kept off the buy list, and neither is reported as
	 * the other.
	 */
	@Test
	public void anUnknownItemIsKeptApartFromAnUntradeableOne()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("A light source", 0, 1, 0),
			total("Karamjan rum", 431, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null,
			(itemId, name) -> itemId <= 0
				? GrandExchangeShoppingList.Availability.UNKNOWN
				: GrandExchangeShoppingList.Availability.NOT_ON_GE);

		assertNull("nothing here can be bought at a Grand Exchange",
			GrandExchangeShoppingList.next(lines));
		List<GrandExchangeShoppingList.Line> elsewhere = GrandExchangeShoppingList.unbuyable(lines);
		assertEquals(2, elsewhere.size());
		assertEquals(GrandExchangeShoppingList.Availability.UNKNOWN,
			elsewhere.get(0).availability());
		assertEquals(GrandExchangeShoppingList.Availability.NOT_ON_GE,
			elsewhere.get(1).availability());
	}

	/**
	 * The three sections still partition the list once a fourth state
	 * exists. Every outstanding item is in exactly one of them, which is what
	 * lets the panel's own counts be trusted.
	 */
	@Test
	public void queuedLeftAndUnbuyablePartitionTheList()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Air talisman", 1438, 5, 0),
			total("Bucket", 1925, 1, 0),
			total("Karamjan rum", 431, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			totals, buying("Bucket", 1925, 1, 0), exceptRum());

		assertEquals(lines.size(),
			GrandExchangeShoppingList.queued(lines).size()
				+ GrandExchangeShoppingList.left(lines).size()
				+ GrandExchangeShoppingList.unbuyable(lines).size());
	}

	/** Null in, null out -- there is no list to have a head. */
	@Test
	public void nextOfNullIsNull()
	{
		assertNull(GrandExchangeShoppingList.next(null));
		assertTrue(GrandExchangeShoppingList.unbuyable(null).isEmpty());
		assertTrue(GrandExchangeShoppingList.queued(null).isEmpty());
		assertTrue(GrandExchangeShoppingList.left(null).isEmpty());
	}
}
