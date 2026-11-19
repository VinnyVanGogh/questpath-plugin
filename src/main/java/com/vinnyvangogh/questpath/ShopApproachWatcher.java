package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Notices when you come near a shop that stocks something your plan needs.
 *
 * <p>Replaces announcing on the shop interface opening, which was the wrong
 * moment twice over. It came too late -- "the goal is to have something pop
 * up as a reminder that draws attention, not an alert after you've already
 * arrived" -- and it fired off a widget appearing, which in play produced the
 * same line five times in two minutes while the player was fishing.
 *
 * <p>Edge-triggered on distance: a shop announces
 * once when you cross into range and stays quiet until you have left and come
 * back. Standing in the shop, or running past it twice, says it once.
 *
 * <p>Pure state machine, no client: handed shop tiles and a position, it
 * answers what you have just walked up to. The interesting part is which
 * transitions count, and that is worth testing without a running game.
 */
class ShopApproachWatcher
{
	/**
	 * How close counts as "coming up on it", in tiles.
	 *
	 * <p>Deliberately wider than an arrival radius, which answers a different
	 * question: that fires when you have arrived somewhere and is meant to be
	 * precise. This one is a nudge to look up before you walk
	 * past, so it wants to land while the shop is still ahead of you --
	 * roughly a screen's width rather than arm's length.
	 */
	static final int APPROACH_RADIUS_TILES = 16;

	/**
	 * Whether the player was within range of each shop at the last look.
	 *
	 * <p>Keyed by shop name, and never cleared wholesale: a shop dropping out
	 * of the candidate list (its item got bought) must not reset the memory
	 * of shops still in it.
	 */
	private final Map<String, Boolean> nearBefore = new HashMap<>();

	/**
	 * Shops just come within range, in the order given.
	 *
	 * <p>A shop the player is already standing next to on the very first
	 * observation does not announce. That is the "if I'm already at the shop,
	 * I don't need it to tell me" case, and it is also what stops a login or
	 * a plugin restart inside a shop from firing immediately.
	 *
	 * @param shops shop name to tile, already filtered to shops that stock
	 *     something outstanding and that this account can actually trade at
	 * @param player where the player is, or null when the client has nobody
	 */
	List<String> observe(Map<String, WorldPoint> shops, WorldPoint player)
	{
		return observe(shops, player, System.currentTimeMillis());
	}

	/**
	 * Per-shop cooldown: edge-triggering alone repeated the line whenever
	 * the player ran in and out of range (or a region reload flapped the
	 * position), so a shop that has spoken stays quiet for
	 * {@link #COOLDOWN_MILLIS} -- or until {@link #acted} says the player
	 * dealt with it. The cooldown survives {@link #clear}, which only drops
	 * the near/far baselines.
	 */
	List<String> observe(Map<String, WorldPoint> shops, WorldPoint player, long now)
	{
		if (shops == null)
		{
			return List.of();
		}
		if (player == null)
		{
			// No position to judge from. The baseline is deliberately left
			// untouched rather than cleared, so a loading screen does not
			// re-announce everything the moment the player reappears.
			return List.of();
		}

		List<String> arrived = new ArrayList<>();
		for (Map.Entry<String, WorldPoint> entry : shops.entrySet())
		{
			String name = entry.getKey();
			WorldPoint at = entry.getValue();
			if (at == null)
			{
				continue;
			}
			boolean nearNow = player.distanceTo(at) <= APPROACH_RADIUS_TILES;
			Boolean wasNear = nearBefore.put(name, nearNow);
			if (nearNow && wasNear != null && !wasNear)
			{
				Long said = lastSaid.get(name);
				if (said == null || now - said >= COOLDOWN_MILLIS)
				{
					lastSaid.put(name, now);
					arrived.add(name);
				}
			}
		}
		return arrived;
	}

	/** The player acted on this shop (opened it): its next approach may speak at once. */
	void acted(String shopName)
	{
		lastSaid.remove(shopName);
	}

	/** How long a shop that has spoken stays quiet: five minutes. */
	static final long COOLDOWN_MILLIS = 5 * 60 * 1000L;

	private final Map<String, Long> lastSaid = new HashMap<>();

	/** Forgets every baseline -- on logout, and when the plugin stops. */
	void clear()
	{
		nearBefore.clear();
	}
}
