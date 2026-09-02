package com.vinnyvangogh.questpath;

import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The container mapping, which is the part decidable without a client.
 *
 * Reading an ItemContainer needs the client and the client thread, so it is
 * not exercised here. What is exercised is the thing a wrong constant would
 * silently break: sending the equipment as if it were the bank would look
 * exactly like a bank with eleven items in it.
 */
public class OwnedItemsTest
{
	@Test
	public void everyContainerMapsToTheIdTheClientReports()
	{
		assertEquals(OwnedItems.Container.BANK, OwnedItems.Container.forId(InventoryID.BANK));
		assertEquals(OwnedItems.Container.INVENTORY, OwnedItems.Container.forId(InventoryID.INV));
		assertEquals(OwnedItems.Container.EQUIPMENT, OwnedItems.Container.forId(InventoryID.WORN));
	}

	/**
	 * The game has hundreds of item containers — every shop, every storage
	 * interface, the party room. Only three are ours, and the rest must be
	 * ignored rather than stored under whichever name happened to match.
	 */
	@Test
	public void anyOtherContainerIsNotOurs()
	{
		assertNull(OwnedItems.Container.forId(InventoryID.TRAWLER_REWARDINV));
		assertNull(OwnedItems.Container.forId(-1));
		assertNull(OwnedItems.Container.forId(0));
	}

	/**
	 * These strings are the wire contract with questpath's CHECK constraint.
	 * A rename here is a silent write failure there.
	 */
	@Test
	public void wireNamesMatchWhatQuestpathStores()
	{
		assertEquals("bank", OwnedItems.Container.BANK.wireName());
		assertEquals("inventory", OwnedItems.Container.INVENTORY.wireName());
		assertEquals("equipment", OwnedItems.Container.EQUIPMENT.wireName());
	}

	/**
	 * A container never seen is unknown, not empty. questpath treats an empty
	 * list as "this really is empty" and would wipe a bank nobody opened.
	 */
	@Test
	public void nothingSeenMeansNothingIsSent()
	{
		OwnedItems owned = new OwnedItems();
		assertEquals(true, owned.isEmpty());
		assertEquals(0, owned.forPayload().size());
	}
}
