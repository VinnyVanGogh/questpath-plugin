package com.vinnyvangogh.questpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Being told about a shop as you come up on it, once.
 *
 * <p>Both failures this replaces were about timing rather than content. The
 * old trigger fired when the shop interface opened, so it announced a shop
 * the player was already standing in -- "if I'm already at the shop, I don't
 * need it to tell me" -- and because it keyed off a widget appearing, it
 * repeated the same line five times in two minutes during a fishing trip.
 */
public class ShopApproachWatcherTest
{
	private static final WorldPoint SHOP = new WorldPoint(3200, 3200, 0);

	private static Map<String, WorldPoint> shops(String... names)
	{
		Map<String, WorldPoint> out = new HashMap<>();
		for (String name : names)
		{
			out.put(name, SHOP);
		}
		return out;
	}

	private static WorldPoint away(int tiles)
	{
		return new WorldPoint(SHOP.getX() + tiles, SHOP.getY(), SHOP.getPlane());
	}

	/** Walking in from outside announces once. */
	@Test
	public void crossingIntoRangeAnnouncesOnce()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");

		assertTrue("the first look only sets a baseline",
			watcher.observe(shops, away(ShopApproachWatcher.APPROACH_RADIUS_TILES + 5)).isEmpty());

		assertEquals(List.of("Harry's Fishing Shop"), watcher.observe(shops, away(3)));
		assertTrue("and stays quiet while you are still there",
			watcher.observe(shops, away(2)).isEmpty());
		assertTrue(watcher.observe(shops, away(1)).isEmpty());
	}

	/**
	 * Already standing in the shop when first observed says nothing.
	 *
	 * <p>This is the login case and the plugin-restart case, and it is also
	 * the complaint: an alert after you have arrived is not a reminder.
	 */
	@Test
	public void alreadyThereOnTheFirstLookSaysNothing()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		assertTrue(watcher.observe(shops("Harry's Fishing Shop"), SHOP).isEmpty());
	}

	/** Leaving and coming back announces again. */
	@Test
	public void leavingAndReturningAnnouncesAgain()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");

		long t = 1_000_000L;
		watcher.observe(shops, away(50), t);
		assertEquals(1, watcher.observe(shops, away(2), t).size());
		assertTrue(watcher.observe(shops, away(50), t + 1000).isEmpty());
		assertEquals("a trip after the cooldown is a second reminder", 1,
			watcher.observe(shops, away(2), t + ShopApproachWatcher.COOLDOWN_MILLIS).size());
	}

	/** Region flapping / running in and out inside the cooldown says nothing more. */
	@Test
	public void flappingInsideTheCooldownIsSilent()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");
		long t = 1_000_000L;
		watcher.observe(shops, away(50), t);
		assertEquals(1, watcher.observe(shops, away(2), t).size());
		for (int i = 1; i <= 20; i++)
		{
			watcher.observe(shops, away(50), t + i * 2000L);
			assertTrue("repeated at flap " + i,
				watcher.observe(shops, away(2), t + i * 2000L + 1000).isEmpty());
		}
		watcher.clear();
		watcher.observe(shops, away(50), t + 50_000);
		assertTrue("clear() must not drop the cooldown",
			watcher.observe(shops, away(2), t + 51_000).isEmpty());
	}

	/** Acting on a shop lifts its cooldown. */
	@Test
	public void actingOnAShopResetsItsCooldown()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");
		watcher.observe(shops, away(50), 0);
		assertEquals(1, watcher.observe(shops, away(2), 1).size());
		watcher.acted("Harry's Fishing Shop");
		watcher.observe(shops, away(50), 2);
		assertEquals(1, watcher.observe(shops, away(2), 3).size());
	}

	/**
	 * Standing still does not repeat, however many ticks pass.
	 *
	 * <p>The old trigger produced five identical lines in two minutes while
	 * the player was fishing. Sixty observations at one spot produce one.
	 */
	@Test
	public void standingStillNeverRepeats()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");
		watcher.observe(shops, away(50));

		int announced = 0;
		for (int tick = 0; tick < 60; tick++)
		{
			announced += watcher.observe(shops, away(2)).size();
		}
		assertEquals(1, announced);
	}

	/** A shop leaving the list does not reset the shops still in it. */
	@Test
	public void oneShopDroppingOutDoesNotReAnnounceTheOthers()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		watcher.observe(shops("Harry's Fishing Shop", "Ned's ropes"), away(50));
		assertEquals(2, watcher.observe(shops("Harry's Fishing Shop", "Ned's ropes"), away(2)).size());

		// Ned's item got bought, so only the fishing shop is a candidate now.
		assertTrue("the shop you never left announced itself again",
			watcher.observe(shops("Harry's Fishing Shop"), away(2)).isEmpty());
	}

	/** With no position there is nothing to judge, and no baseline is lost. */
	@Test
	public void aMissingPositionChangesNothing()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");
		watcher.observe(shops, away(50));

		assertTrue(watcher.observe(shops, null).isEmpty());
		assertEquals("the baseline survived the loading screen",
			1, watcher.observe(shops, away(2)).size());
	}

	/** Logout forgets everything, so the next login is a fresh baseline. */
	@Test
	public void clearForgetsEveryBaseline()
	{
		ShopApproachWatcher watcher = new ShopApproachWatcher();
		Map<String, WorldPoint> shops = shops("Harry's Fishing Shop");
		watcher.observe(shops, away(50));
		watcher.clear();

		assertTrue("a fresh baseline never announces on its first look",
			watcher.observe(shops, away(2)).isEmpty());
	}
}
