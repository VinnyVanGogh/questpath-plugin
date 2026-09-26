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
		assertEquals("2 of 5", lines.get(0).progress());
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

	/**
	 * Progress counts against what the account needs, not against the size of
	 * the offer.
	 *
	 * <p>The reported case exactly: one Pot of flour wanted, ten bought,
	 * which used to read "10 of 10" -- a true sentence about the offer and a
	 * useless one about the trip. The overshoot is shown rather than clamped,
	 * because rounding it to "1 of 1" would hide nine items sitting in the
	 * collection box.
	 */
	@Test
	public void progressCountsAgainstTheNeedNotTheOffer()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Pot of flour", 1929, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Pot of flour", 1929, 10, 10));

		assertEquals("10 of 1 -- collect", lines.get(0).progress());
	}

	/**
	 * Enough having arrived is what makes a line collectable, not the offer
	 * having finished.
	 *
	 * <p>Partial fills reach the collection box as they arrive, so an offer
	 * for ten covers a need for one immediately. Waiting on the offer would
	 * tell the player to keep waiting for something already sitting there.
	 */
	@Test
	public void aPartlyFilledOfferIsCollectableOnceItCoversTheNeed()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Pot of flour", 1929, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Pot of flour", 1929, 10, 3));

		assertTrue(lines.get(0).collectable());
		assertEquals("3 of 1 -- collect", lines.get(0).progress());
	}

	/** Nothing arrived yet is neither collectable nor dressed up as progress. */
	@Test
	public void anUnfilledOfferReadsAsNothingArrivedYet()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Red dye", 1763, 1, 0));

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(totals, buying("Red dye", 1763, 1, 0));

		assertFalse(lines.get(0).collectable());
		assertEquals("0 of 1", lines.get(0).progress());
	}

	/** A line nothing is buying has no progress to report. */
	@Test
	public void anUnqueuedLineHasNoProgress()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Rope", 954, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null);

		assertFalse(lines.get(0).collectable());
		assertEquals("", lines.get(0).progress());
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

	/**
	 * The wiki's caveat reaches the buy list.
	 *
	 * <p>Reported from play. The Knight's Sword asks for "2 or 4 iron bars (4
	 * if you want your own blurite sword)". The count resolves to two, which
	 * is right for finishing the quest and wrong for an ironman who wants the
	 * second sword -- and the sword is worth real money in free-to-play. The
	 * parenthetical said exactly that and stopped at the bundle boundary, so
	 * the player bought two and found out afterwards.
	 *
	 * <p>The note is carried verbatim and never parsed. Nothing here should
	 * ever try to read "4" out of it and adjust the quantity: that would be
	 * questpath guessing at prose, which is what the whole no-fabricated-data
	 * rule exists to stop.
	 */
	@Test
	public void aWikiCaveatReachesTheBuyList()
	{
		QuestItems.Total total = total("Iron bar", 2351, 2, 0);
		total.add(new QuestItems.Line("Iron bar", 2351, 2, 0L, false, false,
			Collections.emptyList(), null, null, "",
			Arrays.asList("4 if you want your own blurite sword")), "The Knight's Sword");

		List<GrandExchangeShoppingList.Line> lines =
			GrandExchangeShoppingList.of(Collections.singletonList(total));

		assertEquals(1, lines.size());
		assertEquals(Arrays.asList("4 if you want your own blurite sword"),
			lines.get(0).notes());
	}

	/** An item the wiki qualifies with nothing carries no note, and never a null. */
	@Test
	public void anItemWithNoCaveatCarriesNoNote()
	{
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			Collections.singletonList(total("Rope", 954, 1, 0)));

		assertTrue(lines.get(0).notes().isEmpty());
	}

	/**
	 * A members item on a free-to-play world is tradeable and still not
	 * buyable, and the buy list has to know the difference.
	 *
	 * <p>Reported from play. The list offered members items on a f2p world,
	 * the client rendered each one "(Members)" in its own search results, and
	 * the game answered every attempt with "You can't buy members-only items
	 * on this world". Never suggesting something the account cannot act on is
	 * the oldest rule this list has, and this broke it while looking entirely
	 * healthy.
	 */
	@Test
	public void aMembersItemOnAFreeWorldIsNotOnTheBuyList()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Dragon axe", 6739, 1, 0),
			total("Rope", 954, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null,
			(itemId, name) -> itemId == 6739
				? GrandExchangeShoppingList.Availability.MEMBERS_ONLY
				: GrandExchangeShoppingList.Availability.BUYABLE);

		List<GrandExchangeShoppingList.Line> toBuy = GrandExchangeShoppingList.left(lines);
		assertEquals(1, toBuy.size());
		assertEquals("Rope", toBuy.get(0).name());
		assertEquals("the next suggestion must never be one the game will refuse",
			"Rope", GrandExchangeShoppingList.next(lines).name());
	}

	/**
	 * Moved, not dropped -- the same rule an untradeable item follows. The
	 * account still needs it, and unlike Karamjan rum it becomes buyable the
	 * moment they hop to a members world, so hiding it outright would be the
	 * worse of the two errors.
	 */
	@Test
	public void aMembersItemIsListedSeparatelyRatherThanDropped()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Dragon axe", 6739, 1, 0),
			total("Rope", 954, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null,
			(itemId, name) -> itemId == 6739
				? GrandExchangeShoppingList.Availability.MEMBERS_ONLY
				: GrandExchangeShoppingList.Availability.BUYABLE);

		assertEquals("both items stay on the list", 2, lines.size());
		List<GrandExchangeShoppingList.Line> elsewhere = GrandExchangeShoppingList.unbuyable(lines);
		assertEquals(1, elsewhere.size());
		assertEquals("Dragon axe", elsewhere.get(0).name());
		assertEquals(GrandExchangeShoppingList.Availability.MEMBERS_ONLY,
			elsewhere.get(0).availability());
	}

	/**
	 * "members world" stays distinct from "not traded", because only one of
	 * them is permanent.
	 *
	 * <p>Reporting a members item as untradeable would tell a player to stop
	 * looking for something a world hop would hand them, which is worse than
	 * saying nothing at all.
	 */
	@Test
	public void aMembersItemIsKeptApartFromAnUntradeableOne()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Dragon axe", 6739, 1, 0),
			total("Karamjan rum", 431, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals, null,
			(itemId, name) -> itemId == 6739
				? GrandExchangeShoppingList.Availability.MEMBERS_ONLY
				: GrandExchangeShoppingList.Availability.NOT_ON_GE);

		List<GrandExchangeShoppingList.Line> elsewhere = GrandExchangeShoppingList.unbuyable(lines);
		assertEquals(2, elsewhere.size());
		assertEquals(GrandExchangeShoppingList.Availability.MEMBERS_ONLY,
			elsewhere.get(0).availability());
		assertEquals(GrandExchangeShoppingList.Availability.NOT_ON_GE,
			elsewhere.get(1).availability());
	}

	/**
	 * On a members world the same item is simply buyable.
	 *
	 * <p>The direction that would be expensive to get backwards: a paying
	 * member silently losing most of their buy list is far harder to notice
	 * than a free player being offered one item too many.
	 */
	@Test
	public void aMembersItemOnAMembersWorldIsOrdinary()
	{
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			Collections.singletonList(total("Dragon axe", 6739, 1, 0)), null,
			(itemId, name) -> GrandExchangeShoppingList.Availability.BUYABLE);

		assertEquals(1, GrandExchangeShoppingList.left(lines).size());
		assertTrue(GrandExchangeShoppingList.unbuyable(lines).isEmpty());
	}

	@Test
	public void recommendedFlagPassesThroughAndSegmentsCorrectly()
	{
		QuestItems.Total reqTotal = new QuestItems.Total("Bucket", 100, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		reqTotal.add(new QuestItems.Line("Bucket", 100, 1, 0L, false, Collections.emptyList()), "Sheep Herder");

		QuestItems.Total recTotal = new QuestItems.Total("Varrock teleport", 8007, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		QuestItems.Line recLine = new QuestItems.Line("Varrock teleport", 8007, 1, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true);
		recTotal.add(recLine, "Demon Slayer");

		assertTrue(recTotal.recommended());
		assertFalse(reqTotal.recommended());

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Arrays.asList(reqTotal, recTotal));
		assertEquals(2, lines.size());

		List<GrandExchangeShoppingList.Line> reqLines = GrandExchangeShoppingList.required(lines);
		List<GrandExchangeShoppingList.Line> recLines = GrandExchangeShoppingList.recommended(lines);

		assertEquals(1, reqLines.size());
		assertEquals("Bucket", reqLines.get(0).name());
		assertFalse(reqLines.get(0).recommended());

		assertEquals(1, recLines.size());
		assertEquals("Varrock teleport", recLines.get(0).name());
		assertTrue(recLines.get(0).recommended());
	}

	@Test
	public void nextPrioritizesRequiredBeforeRecommended()
	{
		QuestItems.Total reqTotal = new QuestItems.Total("Zamorak monk top", 1000, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		reqTotal.add(new QuestItems.Line("Zamorak monk top", 1000, 1, 0L, false, Collections.emptyList()), "Priest in Peril");

		QuestItems.Total recTotal = new QuestItems.Total("Camelot teleport", 8010, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		QuestItems.Line recLine = new QuestItems.Line("Camelot teleport", 8010, 1, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true);
		recTotal.add(recLine, "Merlin's Crystal");

		// "Camelot teleport" comes alphabetically before "Zamorak monk top",
		// but required item "Zamorak monk top" must still be chosen as next().
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Arrays.asList(reqTotal, recTotal));
		GrandExchangeShoppingList.Line next = GrandExchangeShoppingList.next(lines);

		assertEquals("Zamorak monk top", next.name());
		assertFalse(next.recommended());
	}

	@Test
	public void nextSelectsRecommendedWhenAllRequiredCovered()
	{
		QuestItems.Total recTotal = new QuestItems.Total("Camelot teleport", 8010, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		QuestItems.Line recLine = new QuestItems.Line("Camelot teleport", 8010, 1, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true);
		recTotal.add(recLine, "Merlin's Crystal");

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Collections.singletonList(recTotal));
		GrandExchangeShoppingList.Line next = GrandExchangeShoppingList.next(lines);

		assertEquals("Camelot teleport", next.name());
		assertTrue(next.recommended());
	}
}
