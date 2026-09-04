package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Decides whether the player just arrived at the start of any startable
 * quest, so Quest Helper can be opened for it without being asked every time.
 *
 * <p>Pure state machine, no client, no config and no {@link
 * QuestHelperBridge} -- the same separation {@link AutoAdvancePlanner} keeps.
 * Whether Quest Helper can actually take the quest, and whether the player
 * wants any of this at all, are QuestpathPlugin's business, not this class's;
 * keeping them apart is what makes "which transition counts" testable
 * without a running game.
 *
 * <p><b>Tracks a caller-supplied candidate set, not one quest.</b> An earlier
 * version tracked only the saved route's single "next" quest, which meant a
 * startable quest that was not the route head -- Demon Slayer, say, on an
 * account whose route was pointed elsewhere -- could never fire no matter how
 * long the player stood on top of its start NPC. That is a real report, not a
 * hypothetical: a player stood next to Aris, who starts Demon Slayer, and
 * nothing happened, because Demon Slayer was not the route's head.
 *
 * <p>Widening this to "every quest in the graph, checked with a fresh
 * availability lookup every tick" would reintroduce exactly the cost this
 * class's own history warns against -- see the refreshPending doc on {@code
 * QuestpathPlugin}. Instead, the caller precomputes a small candidate map
 * (name to start point) once per account-state refresh -- see {@code
 * QuestpathPlugin#questStartCandidates} -- and this class only ever does a
 * distance comparison per candidate, per tick, against tiles it was already
 * handed. No classification, no graph scan, and no per-tick allocation
 * beyond the arrival list itself, which is empty on almost every tick.
 *
 * <p>The guard against firing on login, on a resync, or on the burst of
 * events that establishes the baseline is still a {@link #primed} flag, same
 * as the single-quest version, because it has to answer a question a
 * per-quest baseline cannot: the very first call this watcher ever receives
 * (or the first after {@link #reset}) must never fire, however near the
 * player already is to however many candidates -- there is nothing real yet
 * to compare any of them against. After that first call, though, a quest
 * that is new to the candidate set -- the route just advanced to it, or it
 * just became eligible -- is judged against its own absent baseline, which
 * reads as "not near before", so it can fire on the very tick it appears if
 * the player already happens to be standing there. That mirrors what the
 * single-quest version did when the route head changed under an
 * already-primed watcher, and is why {@link #primed} and {@link
 * #nearByQuest} are two separate pieces of state rather than one.
 */
class QuestStartArrivalWatcher
{
	/**
	 * How close counts as "arrived", in tiles -- {@link WorldPoint#distanceTo}
	 * Chebyshev distance, and cross-plane is never "near" (that method
	 * returns {@code Integer.MAX_VALUE} when the two points are on different
	 * planes; confirmed against the compiled {@code
	 * net.runelite.api.coords.WorldPoint} in {@code runelite-api-1.12.37.jar}
	 * via {@code javap -c}, not assumed). Quest start points carry no plane
	 * of their own -- questpath's export omits one -- so every start point is
	 * treated as plane 0; a player on any other plane above or below a
	 * quest's actual door correctly never triggers this, rather than firing
	 * confidently for the wrong floor.
	 *
	 * <p>Chosen as a considered middle ground, not measured against a running
	 * game -- and since checked against a real report: Demon Slayer's start
	 * in the bundle is (3203, 3424); the OSRS Wiki's own {@code {{Map}}}
	 * coordinate for Aris, who starts it, is (3204, 3425) -- one tile away.
	 * The report of this never firing next to Aris was the single-quest
	 * tracking gap this class now closes, not a radius too small to reach
	 * her; 8 tiles covers that gap sixty times over. A start coordinate names
	 * the NPC or object the quest begins at, and a player walking up to
	 * interact with it stops a tile or two short -- so anything under about 4
	 * risks never firing at all. Against that, the bundled graph's 205 known
	 * start points have a median nearest-neighbour gap of 28 tiles, so 8
	 * stays comfortably inside "you have arrived here" without reaching into
	 * the next street over.
	 *
	 * <p>Unlike the single-quest version, this class now checks several
	 * candidates at once, so a radius that also reaches a second candidate's
	 * start is a real (if rare) risk here in a way it never was before --
	 * two quests both starting within 8 tiles of each other. {@link #observe}
	 * handles that honestly: both can report an arrival on the same tick
	 * rather than one being silently preferred.
	 */
	static final int ARRIVAL_RADIUS_TILES = 8;

	/**
	 * Whether the player was within range of each tracked quest, keyed by
	 * quest name, as of the last real observation that named it.
	 *
	 * <p>A quest name with no entry has never been observed, or was dropped
	 * because a later observation no longer offered it as a candidate --
	 * completed, no longer eligible, or the route moved past it. Either way,
	 * its next sighting is treated as a fresh baseline, the same rule {@link
	 * #reset} restores for everything at once.
	 */
	private final Map<String, Boolean> nearByQuest = new HashMap<>();

	/** Whether a real observation has ever been recorded -- see the class doc. */
	private boolean primed;

	/**
	 * Forgets what it last saw. Called on login, on plugin startUp, and on
	 * logout -- every point at which the next observation must be treated as
	 * a fresh baseline rather than a continuation, same triggers {@link
	 * AutoAdvancePlanner#reset} uses and for the same reason: otherwise a
	 * route from a different account, or from before the plugin last
	 * started, would be compared against this one.
	 */
	void reset()
	{
		primed = false;
		nearByQuest.clear();
	}

	/**
	 * What just happened, and what -- if anything -- should follow.
	 *
	 * <p>Safe to call every tick regardless of game state: every parameter
	 * degrades to "nothing to report" on its own when the plugin cannot yet
	 * answer it -- an empty or null {@code candidates} map (no route, no
	 * eligible quests, or the client has not loaded quest states yet) or a
	 * null {@code player} (not logged in) both simply mean nothing is close
	 * enough, which is the same honest answer a real "you are on the other
	 * side of the map" reading would give. Nothing here corrupts the
	 * baseline the way observing an unjudged quest-completion set would for
	 * {@link AutoAdvancePlanner}: the very next tick with real data
	 * re-establishes the truth on its own.
	 *
	 * @param candidates every quest worth checking distance to right now,
	 *     name to its start point -- see {@code
	 *     QuestpathPlugin#questStartCandidates} for how that set is built and
	 *     capped. Never mutated.
	 * @param player the player's current location, or null when it cannot be
	 *     read right now
	 * @return the name of every candidate the player transitioned from
	 *     outside {@link #ARRIVAL_RADIUS_TILES} (or from no baseline at all)
	 *     to inside it for, this tick. Empty on almost every call. Never
	 *     twice for the same standing-still arrival -- closing Quest Helper
	 *     afterwards changes nothing this class reads, so it cannot
	 *     re-trigger one -- and never for the observation that establishes a
	 *     quest's baseline.
	 */
	List<String> observe(Map<String, WorldPoint> candidates, WorldPoint player)
	{
		boolean wasPrimed = primed;
		primed = true;

		if (candidates == null || candidates.isEmpty() || player == null)
		{
			// Nothing to track this tick. Cleared rather than left stale: a
			// quest that drops out of the candidate set (completed, no
			// longer eligible, route moved past it) and later comes back
			// must be treated as a fresh baseline, not "still here from
			// before".
			nearByQuest.clear();
			return List.of();
		}

		// Drop tracked quests the caller is no longer offering. Left in
		// place, a quest that fell out of the candidate set would keep its
		// old "was near" reading, and if it ever came back into range of the
		// same tile for an unrelated reason, that stale reading -- not a
		// real fresh arrival -- would decide whether this fires.
		nearByQuest.keySet().retainAll(candidates.keySet());

		List<String> arrived = new ArrayList<>();
		for (Map.Entry<String, WorldPoint> candidate : candidates.entrySet())
		{
			String questName = candidate.getKey();
			WorldPoint start = candidate.getValue();
			boolean nearNow = start != null && player.distanceTo(start) <= ARRIVAL_RADIUS_TILES;
			Boolean previouslyNear = nearByQuest.put(questName, nearNow);
			// A quest absent from the map has no baseline -- new to the
			// candidate set, or the very first sighting after reset() --
			// which reads as "was not near", same as an explicit false. Only
			// wasPrimed can suppress that: on the watcher's very first real
			// call there is nothing genuine to compare *anything* against,
			// so nothing fires no matter how near the player already is.
			boolean wasNear = previouslyNear != null && previouslyNear;
			if (wasPrimed && nearNow && !wasNear)
			{
				arrived.add(questName);
			}
		}
		return arrived;
	}
}
