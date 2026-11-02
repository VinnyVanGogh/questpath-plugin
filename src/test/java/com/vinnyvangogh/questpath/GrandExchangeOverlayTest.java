package com.vinnyvangogh.questpath;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GrandExchangeOverlayTest
{
	private static GrandExchangeShoppingList.Line line(String name, int itemId,
		GrandExchangeShoppingList.Availability availability)
	{
		return new GrandExchangeShoppingList.Line(name, itemId, 1, null, availability);
	}

	/**
	 * Issue #1: "Seeds" has no item id because the wiki asks for any seeds,
	 * not one item. That is the data being exactly as specific as the quest,
	 * not questpath failing a lookup, so it must not wear the lookup-failure
	 * label.
	 */
	@Test
	public void anIdLessWikiRequirementIsNotCalledALookupFailure()
	{
		String seeds = GrandExchangeOverlay.reason(
			line("Seeds", 0, GrandExchangeShoppingList.Availability.GENERIC));

		assertEquals("check wiki", seeds);
		assertNotEquals("can't look up", seeds);
	}

	/** A real id the client gave no answer for is still the lookup failure. */
	@Test
	public void anIdTheClientCannotAnswerIsStillALookupFailure()
	{
		assertEquals("can't look up", GrandExchangeOverlay.reason(
			line("Mystery item", 99999, GrandExchangeShoppingList.Availability.UNKNOWN)));
	}

	/**
	 * Every reason a line can be off the buy list reads differently -- none
	 * is printed as another.
	 */
	@Test
	public void everyUnbuyableReasonHasItsOwnWords()
	{
		Set<String> seen = new HashSet<>();
		int unbuyable = 0;
		for (GrandExchangeShoppingList.Availability availability
			: GrandExchangeShoppingList.Availability.values())
		{
			if (availability == GrandExchangeShoppingList.Availability.BUYABLE)
			{
				continue;
			}
			unbuyable++;
			seen.add(GrandExchangeOverlay.reason(line("Item", 1, availability)));
		}
		assertEquals(unbuyable, seen.size());
	}
}
