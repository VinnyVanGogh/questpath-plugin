package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether the diary route should nudge the player about a specific
 * outstanding task right now.
 *
 * <p>Pure, no client and no clock of its own -- every {@link Instant} is a
 * parameter, the same shape {@link ShopRouteGuard} already takes. See that
 * class's own doc for the argument made in full: a clock read inside the
 * class under test can only ever be exercised by waiting on a real one, and
 * the interesting behaviour here -- did the cooldown just expire, is the
 * player one tile inside or outside the radius -- is exactly the kind of
 * boundary a real clock cannot be trusted to land a test on.
 *
 * <p><b>Four independent reasons to say no, checked in the same order this
 * class's own doc lists them.</b> Any one of them is enough:
 *
 * <ul>
 * <li>the feature's own config toggle is off;
 * <li>the nearest actionable task is further than {@link #PROXIMITY_RADIUS_TILES};
 * <li>a shop route or a quest route is already under way, per {@link
 * ShopRouteGuard#blocksQuestRouting} and the account's own in-progress route
 * quest state -- the same two checks {@code QuestpathPlugin} already applies
 * before an automatic Quest Helper start, reused rather than re-derived so a
 * diary nudge cannot fire in a moment the rest of the plugin has already
 * decided the player is busy elsewhere;
 * <li>this exact task was nudged too recently, per {@link #NUDGE_COOLDOWN}.
 * </ul>
 *
 * <p>Nothing here decides whether the task is actionable in the first place
 * -- that is {@link DiaryRouteStops}' job, upstream of this class entirely.
 * This only answers "given a nearest actionable stop, is right now a good
 * moment to say so out loud."
 */
final class DiaryRouteGuard
{
	private DiaryRouteGuard()
	{
	}

	/**
	 * How close the nearest actionable task must be before this plugin says
	 * anything about it, in tiles -- {@link
	 * net.runelite.api.coords.WorldPoint#distanceTo}'s own Chebyshev metric,
	 * matching {@link DiaryRouteStops}.
	 *
	 * <p>A chosen value, not a measured one -- there is no existing report to
	 * calibrate against. Wider than an arrival radius (8 tiles, what the
	 * removed quest-start arrival watcher used) and narrower than
	 * {@link ShopApproachWatcher#APPROACH_RADIUS_TILES} (16) deliberately: a
	 * diary task is something to notice in passing while doing something
	 * else, not something to walk out of your way for the way a quest start
	 * or a shop worth a special trip might be, so it sits between "you have
	 * arrived" and "this is a screen's width away."
	 */
	static final int PROXIMITY_RADIUS_TILES = 12;

	/**
	 * How long a nudge for one specific {@code (tier_id, ordinal)} suppresses
	 * a second nudge for that same task, once it has fired.
	 *
	 * <p>A chosen value, not a measured one, matching the order of magnitude
	 * {@link ShopRouteGuard#ACTIVE_WINDOW} already uses for "long enough that
	 * repeating this would be noise, short enough that standing in the same
	 * spot for a session does not permanently silence it." Ten minutes: a
	 * player training or banking near a diary task's location plausibly
	 * stays within the proximity radius for a while, and re-announcing the
	 * same task every tick for that whole time would be exactly the "nudge
	 * every time you walk past a bank" failure mode {@code
	 * docs/PLAN-diary-routing.md} names as the one to avoid.
	 */
	static final Duration NUDGE_COOLDOWN = Duration.ofMinutes(10);

	/**
	 * @param featureEnabled the diary route's own config toggle -- {@code
	 *     QuestpathConfig#overlayDiaryRoute()}
	 * @param distanceTiles tiles from the player to the nearest actionable
	 *     task's chosen destination, per {@link DiaryRouteStops.Stop#distanceTiles()}
	 *     -- already {@code Integer.MAX_VALUE} for an unknown player position
	 *     or a cross-plane destination, both of which correctly never nudge
	 * @param shopRouteBlocks whether a shop route sent to Shortest Path is
	 *     still active, per {@link ShopRouteGuard#blocksQuestRouting}
	 * @param questRouteInProgress whether a quest on the account's saved
	 *     route is currently {@code IN_PROGRESS}, the same signal {@code
	 *     QuestpathPlugin#routeQuestInProgress} already tracks for the
	 *     identical purpose ahead of an automatic Quest Helper start
	 * @param lastNudgedAt when this exact task was last nudged, or null when
	 *     it never has been this session
	 * @param now the current time, read once by the caller
	 * @return whether to nudge about this task right now
	 */
	static boolean shouldNudge(boolean featureEnabled, int distanceTiles, boolean shopRouteBlocks,
		boolean questRouteInProgress, Instant lastNudgedAt, Instant now)
	{
		if (!featureEnabled)
		{
			return false;
		}
		if (distanceTiles > PROXIMITY_RADIUS_TILES)
		{
			return false;
		}
		if (shopRouteBlocks || questRouteInProgress)
		{
			return false;
		}
		if (lastNudgedAt != null && now.isBefore(lastNudgedAt.plus(NUDGE_COOLDOWN)))
		{
			return false;
		}
		return true;
	}
}
