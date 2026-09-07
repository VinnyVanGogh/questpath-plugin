package com.vinnyvangogh.questpath;

import java.util.List;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link GrandExchangeOffers}: which offer states count as an item
 * already asked for, which stop counting the instant they change, and how a
 * slot is matched back to a shopping-list line.
 *
 * <p>Every one of these is a rule the player can see the effect of. An item
 * that fails to leave the list after an offer is placed is the bug this
 * class was written for; an item that fails to come <em>back</em> after an
 * offer is cancelled is the same bug pointing the other way, and it is worse
 * -- the player is told they have bought something they have not.
 */
public class GrandExchangeOffersTest
{
	private static final int AIR_TALISMAN = 1438;
	private static final int BUCKET = 1925;

	/**
	 * A placed buy offer is the moment the list must move on, not collection.
	 *
	 * <p>Collection is the only moment {@link QuestItems} can see, which is
	 * exactly why this class exists: between placing and collecting, the
	 * player has spent a slot and the list has no other way to know it.
	 */
	@Test
	public void aPlacedBuyOfferCountsBeforeAnythingIsCollected()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 5, 0);

		GrandExchangeOffers.Order order = offers.forItem(AIR_TALISMAN, "Air talisman");
		assertNotNull("a live buy offer must be visible to the shopping list", order);
		assertEquals(5, order.quantity());
		assertEquals(0, order.bought());
		assertFalse("still filling, so not ready to collect", order.complete());
	}

	/** A finished offer is still queued -- it is waiting to be collected, not gone. */
	@Test
	public void aBoughtOfferStillCountsUntilItIsCollected()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BOUGHT, AIR_TALISMAN, "Air talisman", 5, 5);

		GrandExchangeOffers.Order order = offers.forItem(AIR_TALISMAN, "Air talisman");
		assertNotNull(order);
		assertTrue("finished buying, so this one is a click from done", order.complete());
		assertEquals("5 of 5 -- collect", order.describe());
	}

	/**
	 * Cancelling an offer puts the item back at once.
	 *
	 * <p>The player's own words for this: modify the offer and it should go
	 * back until it is replaced. Waiting for the collection box to be emptied
	 * would leave the list claiming an item was handled while the offer that
	 * would have handled it no longer exists.
	 */
	@Test
	public void cancellingAnOfferPutsTheItemBackImmediately()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(3, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 5, 0);
		assertNotNull(offers.forItem(AIR_TALISMAN, "Air talisman"));

		offers.update(3, GrandExchangeOfferState.CANCELLED_BUY, AIR_TALISMAN, "Air talisman", 5, 2);

		assertNull("an aborted offer is not an item you have asked for",
			offers.forItem(AIR_TALISMAN, "Air talisman"));
		assertTrue(offers.isEmpty());
	}

	/** Collecting empties the slot; from there {@link QuestItems} owns the answer. */
	@Test
	public void collectingClearsTheSlot()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(1, GrandExchangeOfferState.BOUGHT, BUCKET, "Bucket", 2, 2);
		offers.update(1, GrandExchangeOfferState.EMPTY, 0, null, 0, 0);

		assertNull(offers.forItem(BUCKET, "Bucket"));
	}

	/**
	 * Selling five of something must never tick a list that wants five of it.
	 *
	 * <p>The states are a single enum covering both directions, so a "is
	 * there an offer for this" check written without thinking about it reads
	 * a sell as a buy -- and the item vanishes off the shopping list at the
	 * exact moment the player is getting rid of it.
	 */
	@Test
	public void sellingIsNeverBuying()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.SELLING, BUCKET, "Bucket", 5, 0);
		offers.update(1, GrandExchangeOfferState.SOLD, AIR_TALISMAN, "Air talisman", 5, 5);

		assertNull(offers.forItem(BUCKET, "Bucket"));
		assertNull(offers.forItem(AIR_TALISMAN, "Air talisman"));
		assertTrue(offers.isEmpty());
	}

	/**
	 * Two slots buying the same thing are two offers, not one overwriting the
	 * other.
	 *
	 * <p>Keying by item instead of by slot passes every single-offer test
	 * above and then loses an offer here -- and worse, the first slot's later
	 * {@code EMPTY} would erase the second one's record too.
	 */
	@Test
	public void twoSlotsBuyingTheSameItemAreBothRemembered()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 5, 1);
		offers.update(1, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 5, 4);

		assertEquals(2, offers.all().size());
		// The nearly-finished one is the one worth reporting.
		assertEquals(4, offers.forItem(AIR_TALISMAN, "Air talisman").bought());

		offers.update(0, GrandExchangeOfferState.EMPTY, 0, null, 0, 0);
		assertNotNull("clearing one slot must not clear the other",
			offers.forItem(AIR_TALISMAN, "Air talisman"));
	}

	/**
	 * Matched by name when questpath holds no item id.
	 *
	 * <p>{@link QuestItems.Total#itemId()} is 0 for anything the bundle has
	 * no id for, and those lines are exactly as worth ticking as the rest. An
	 * id-only match would silently never fire for them.
	 */
	@Test
	public void anItemWithNoIdIsMatchedByName()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 1, 0);

		assertNotNull("case and padding differ between the wiki's name and the game's",
			offers.forItem(0, "  air TALISMAN "));
		assertNull(offers.forItem(0, "Air tiara"));
	}

	/** A null name never matches a null name -- that is two unknowns, not a pair. */
	@Test
	public void unknownNamesDoNotMatchEachOther()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, null, 1, 0);

		assertNull(offers.forItem(0, null));
		assertNotNull("the id still matches", offers.forItem(AIR_TALISMAN, null));
	}

	/** Logging out forgets every slot: the next login may be another account. */
	@Test
	public void clearForgetsEverySlot()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 1, 0);
		offers.clear();

		assertTrue(offers.isEmpty());
		assertNull(offers.forItem(AIR_TALISMAN, "Air talisman"));
	}

	/** Listed by name then slot, so the panel does not reshuffle under the player. */
	@Test
	public void offersAreListedByNameThenSlot()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(5, GrandExchangeOfferState.BUYING, BUCKET, "Bucket", 1, 0);
		offers.update(2, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 1, 0);
		offers.update(0, GrandExchangeOfferState.BUYING, AIR_TALISMAN, "Air talisman", 1, 0);

		List<GrandExchangeOffers.Order> all = offers.all();
		assertEquals(3, all.size());
		assertEquals("Air talisman", all.get(0).name());
		assertEquals(0, all.get(0).slot());
		assertEquals(2, all.get(1).slot());
		assertEquals("Bucket", all.get(2).name());
	}
}
