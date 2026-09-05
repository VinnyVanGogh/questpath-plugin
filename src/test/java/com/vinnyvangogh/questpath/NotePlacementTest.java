package com.vinnyvangogh.questpath;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where a floating note lands, and what happens when the person it was meant
 * for is not there.
 *
 * <p>The fallback is the whole reason this is a class rather than an if: an
 * item is finished by chopping a tree as often as by buying something, and a
 * note that silently failed to appear at those moments would look like the
 * feature being broken rather than like a setting being honoured.
 */
public class NotePlacementTest
{
	/** Above you means above you, shopkeeper present or not. */
	@Test
	public void abovePlayerIgnoresTheShopkeeper()
	{
		assertTrue(NotePlacement.ABOVE_YOU.overPlayer(true));
		assertTrue(NotePlacement.ABOVE_YOU.overPlayer(false));
		assertFalse("never over the shopkeeper, even when there is one",
			NotePlacement.ABOVE_YOU.overShopkeeper(true));
	}

	/** With a shopkeeper there, the shopkeeper says it and you do not. */
	@Test
	public void aboveShopkeeperSpeaksThroughTheNpc()
	{
		assertTrue(NotePlacement.ABOVE_SHOPKEEPER.overShopkeeper(true));
		assertFalse("not doubled over the player as well",
			NotePlacement.ABOVE_SHOPKEEPER.overPlayer(true));
	}

	/**
	 * With nobody to say it, it still gets said -- over the player, because
	 * that is the only place left.
	 */
	@Test
	public void aboveShopkeeperFallsBackRatherThanGoingSilent()
	{
		assertFalse(NotePlacement.ABOVE_SHOPKEEPER.overShopkeeper(false));
		assertTrue("said somewhere rather than not at all",
			NotePlacement.ABOVE_SHOPKEEPER.overPlayer(false));
	}

	/** Both means both, and still falls back to one when alone. */
	@Test
	public void bothShowsInTwoPlacesAndDegradesToOne()
	{
		assertTrue(NotePlacement.BOTH.overShopkeeper(true));
		assertTrue(NotePlacement.BOTH.overPlayer(true));

		assertFalse(NotePlacement.BOTH.overShopkeeper(false));
		assertTrue(NotePlacement.BOTH.overPlayer(false));
	}

	/** Every setting puts a note somewhere. Silence is never a valid outcome. */
	@Test
	public void everySettingSaysItSomewhere()
	{
		for (NotePlacement placement : NotePlacement.values())
		{
			for (boolean haveShopkeeper : new boolean[]{true, false})
			{
				assertTrue(placement + " with shopkeeper=" + haveShopkeeper + " showed nothing",
					placement.overPlayer(haveShopkeeper) || placement.overShopkeeper(haveShopkeeper));
			}
		}
	}

	/** Only the settings that can land elsewhere admit to a fallback. */
	@Test
	public void onlyTheShopkeeperSettingsDescribeAFallback()
	{
		assertFalse(NotePlacement.ABOVE_YOU.describesFallback());
		assertTrue(NotePlacement.ABOVE_SHOPKEEPER.describesFallback());
		assertTrue(NotePlacement.BOTH.describesFallback());
	}
}
