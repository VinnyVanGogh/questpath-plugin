package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import net.runelite.api.coords.WorldPoint;

/**
 * Who you were last buying from, and where they were standing.
 *
 * <p>Exists so a {@link FloatingNotes} line can appear over the shopkeeper
 * rather than over the player. The moment worth speaking at -- an item's last
 * unit landing -- arrives a tick or two after the purchase that caused it, by
 * which point the plugin is looking at a shopping list rather than at an NPC.
 * So the NPC is remembered when the shop opens and read back when there is
 * something to say.
 *
 * <p><b>Remembered by tile, not by reference.</b> Holding the {@code NPC}
 * itself would mean holding a scene object across region loads and logouts,
 * and reading its position later could touch a stale actor. A shopkeeper does
 * not wander, so where they stood when the shop opened is where they are a
 * few seconds later, and a tile is inert.
 *
 * <p><b>Bounded by time.</b> Without an expiry, finishing an item by chopping
 * a tree an hour later would put the words over a shopkeeper three regions
 * away -- who would not be drawn at all, so the note would simply vanish with
 * no explanation. {@link #FRESHNESS} is short enough that only a purchase can
 * plausibly still be in scope.
 */
class ShopkeeperMemory
{
	/**
	 * How long after a shop opens the shopkeeper still counts as the person
	 * being spoken to.
	 *
	 * <p>Long enough to cover buying several things in one visit, short
	 * enough that walking away and gathering something ends it.
	 */
	static final Duration FRESHNESS = Duration.ofSeconds(15);

	private String name = "";
	private WorldPoint at;
	private Instant seenAt;

	/**
	 * Records the shopkeeper currently being traded with. A missing name or
	 * tile is ignored rather than stored blank -- an unnamed NPC at the map
	 * origin is not somebody to put words above.
	 */
	void remember(String npcName, WorldPoint where, Instant now)
	{
		if (npcName == null || npcName.isEmpty() || where == null || now == null)
		{
			return;
		}
		this.name = npcName;
		this.at = where;
		this.seenAt = now;
	}

	/** Where the shopkeeper was, or null when there is no fresh one. */
	WorldPoint location(Instant now)
	{
		return fresh(now) ? at : null;
	}

	/** The shopkeeper's name, or empty when there is no fresh one. */
	String name(Instant now)
	{
		return fresh(now) ? name : "";
	}

	private boolean fresh(Instant now)
	{
		if (at == null || seenAt == null || now == null)
		{
			return false;
		}
		return Duration.between(seenAt, now).compareTo(FRESHNESS) < 0;
	}

	/** Forgets whoever it was -- on logout, and when the plugin stops. */
	void forget()
	{
		name = "";
		at = null;
		seenAt = null;
	}
}
