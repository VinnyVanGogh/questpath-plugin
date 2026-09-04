package com.vinnyvangogh.questpath;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/**
 * Decides whether the player just arrived at the start of the next quest on
 * their saved route, so Quest Helper can be opened for it without being
 * asked every time.
 *
 * <p>Pure state machine, no client, no config and no {@link
 * QuestHelperBridge} -- the same separation {@link AutoAdvancePlanner} keeps.
 * Whether Quest Helper can actually take the quest, and whether the player
 * wants any of this at all, are QuestpathPlugin's business, not this class's;
 * keeping them apart is what makes "which transition counts" testable
 * without a running game.
 *
 * <p>The guard against firing on login, on a resync, or on the burst of
 * events that establishes the baseline is the same shape as {@link
 * AutoAdvancePlanner}'s: the first observation after a {@link #reset} can
 * never itself be an arrival, because there is nothing yet to compare it
 * against -- it only ever records whether the player already happens to be
 * standing there. Unlike {@link AutoAdvancePlanner}, that guard cannot reuse
 * a null-as-baseline sentinel, because "not near anything" is itself a
 * perfectly legitimate real reading here (most ticks, for most players), so
 * a dedicated {@code primed} flag says explicitly whether the previous
 * reading was real.
 *
 * <p>Tracks only the route's single "next" quest, never the whole route.
 * {@link AutoAdvancePlanner} already treats that one name as the actionable
 * head, and checking every upcoming quest's start point on every tick is
 * exactly the per-tick, scales-with-the-quest-list cost this plugin's own
 * history warns against -- see the refreshPending doc on {@code
 * QuestpathPlugin}. One quest, one distance check, once per tick.
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
	 * game. A start coordinate names the NPC or object the quest begins at,
	 * and a player walking up to interact with it stops a tile or two short
	 * -- so anything under about 4 risks never firing at all. Against that,
	 * the bundled graph's 205 known start points have a median
	 * nearest-neighbour gap of 28 tiles, so 8 stays comfortably inside "you
	 * have arrived here" without reaching into the next street over. Because
	 * this class only ever compares distance to the one quest actually
	 * queued next -- never scanning all 218 -- a radius that happens to also
	 * reach a different quest's start is not a misfire risk the way it would
	 * be for a scan across the whole graph: there is nothing else being
	 * checked to confuse it with.
	 */
	static final int ARRIVAL_RADIUS_TILES = 8;

	/** Whether the previous observation was a real one, not the baseline-establishing first call. */
	private boolean primed;

	/** Whether the player was within range of {@link #trackedQuest} at the last real observation. */
	private boolean nearBefore;

	/** The quest name the last observation's nearness applies to, so a changed head is never read as "still here". */
	private String trackedQuest;

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
		nearBefore = false;
		trackedQuest = null;
	}

	/** What just happened, and what -- if anything -- should follow. */
	enum Action
	{
		/** No transition worth acting on: nothing changed, or nothing has been observed yet. */
		NONE,
		/** The player just came within range of the tracked quest's start. */
		ARRIVED
	}

	/** One decision: what happened, and the quest name it involved. */
	static final class Decision
	{
		private static final Decision NONE = new Decision(Action.NONE, null);

		private final Action action;
		private final String questName;

		private Decision(Action action, String questName)
		{
			this.action = action;
			this.questName = questName;
		}

		static Decision none()
		{
			return NONE;
		}

		static Decision arrived(String questName)
		{
			return new Decision(Action.ARRIVED, questName);
		}

		Action action()
		{
			return action;
		}

		/** The quest just arrived at. Meaningful only for {@link Action#ARRIVED}. */
		String questName()
		{
			return questName;
		}
	}

	/**
	 * What, if anything, should happen now that the player's position and
	 * the route's head are both known for this tick.
	 *
	 * <p>Safe to call every tick regardless of game state: unlike {@link
	 * AutoAdvancePlanner#observe}, which must never see an unjudgeable pass
	 * at all, every parameter here degrades to "not near" on its own when
	 * the plugin cannot yet answer it -- a null {@code nextQuestName} (no
	 * saved route, or the client has not loaded quest states yet), a null
	 * {@code start} (no route head, or the bundle carries no start point for
	 * it), or a null {@code player} (not logged in) all simply mean nothing
	 * is close enough, which is the same honest answer a real "you are on
	 * the other side of the map" reading would give. Nothing here corrupts
	 * the baseline the way observing an unjudged quest-completion set would
	 * for {@link AutoAdvancePlanner}: the very next tick with real data
	 * re-establishes the truth on its own.
	 *
	 * @param nextQuestName the saved route's current head, or null when
	 *     there is none to check
	 * @param start nextQuestName's start point, or null when the bundle
	 *     carries none for it
	 * @param player the player's current location, or null when it cannot
	 *     be read right now
	 * @return {@link Action#ARRIVED} exactly on the tick the player
	 *     transitions from outside {@link #ARRIVAL_RADIUS_TILES} (or from
	 *     tracking a different quest, or from nothing tracked at all) to
	 *     inside it for nextQuestName. Never twice in a row for the same
	 *     standing-still arrival -- closing Quest Helper afterwards changes
	 *     nothing this class reads, so it cannot re-trigger one -- and never
	 *     for the observation that establishes the baseline.
	 */
	Decision observe(String nextQuestName, WorldPoint start, WorldPoint player)
	{
		boolean nearNow = nextQuestName != null && start != null && player != null
			&& player.distanceTo(start) <= ARRIVAL_RADIUS_TILES;

		boolean sameQuestAsBefore = Objects.equals(trackedQuest, nextQuestName);
		boolean wasNear = primed && sameQuestAsBefore && nearBefore;

		boolean wasPrimed = primed;
		primed = true;
		nearBefore = nearNow;
		trackedQuest = nextQuestName;

		if (wasPrimed && nearNow && !wasNear)
		{
			return Decision.arrived(nextQuestName);
		}
		return Decision.none();
	}
}
