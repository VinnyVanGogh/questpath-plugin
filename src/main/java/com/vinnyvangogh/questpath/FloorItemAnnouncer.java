package com.vinnyvangogh.questpath;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides when to say "Bucket nearby": once per item spawn, the first time
 * it is in range, and at most one line every few seconds however many land
 * at once.
 *
 * <p>Edge-triggered like {@link ShopApproachWatcher}: a spawn is said once
 * and never again while it lies there, in range or not. It is forgotten only
 * when it despawns, so the next one dropped on the same tile is news. When
 * several are waiting, the nearest goes first, and every waiting spawn of the
 * same item counts as said with it -- ten bones on the floor are one "Bones
 * nearby", not ten.
 *
 * <p>Pure: handed sightings and a clock, no client.
 */
final class FloorItemAnnouncer
{
	/** The least time between two lines, so a pile of drops is not a sentence each. */
	static final long MIN_GAP_MILLIS = 4000;

	/** One needed item lying in range: its spawn identity, name and distance in tiles. */
	static final class Sighting
	{
		final Object spawn;
		final String name;
		final int distance;

		Sighting(Object spawn, String name, int distance)
		{
			this.spawn = spawn;
			this.name = name;
			this.distance = distance;
		}
	}

	private final Set<Object> said = new HashSet<>();
	private long lastSpokenAt = Long.MIN_VALUE;

	/**
	 * The item to say now, or null.
	 *
	 * @param inRange needed items within the radius this tick
	 * @param now     milliseconds, from any steady clock
	 */
	String observe(Collection<Sighting> inRange, long now)
	{
		Sighting nearest = null;
		for (Sighting sighting : inRange)
		{
			if (said.contains(sighting.spawn))
			{
				continue;
			}
			if (nearest == null || sighting.distance < nearest.distance)
			{
				nearest = sighting;
			}
		}
		if (nearest == null || (lastSpokenAt != Long.MIN_VALUE && now - lastSpokenAt < MIN_GAP_MILLIS))
		{
			return null;
		}
		for (Sighting sighting : inRange)
		{
			if (sighting.name.equalsIgnoreCase(nearest.name))
			{
				said.add(sighting.spawn);
			}
		}
		lastSpokenAt = now;
		return nearest.name;
	}

	/** The spawn is gone; one landing in its place later is said again. */
	void forget(Object spawn)
	{
		said.remove(spawn);
	}

	/** On logout and when the plugin stops. */
	void clear()
	{
		said.clear();
		lastSpokenAt = Long.MIN_VALUE;
	}
}
