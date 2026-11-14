package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a shop route the player just sent to Shortest Path is still recent
 * enough to hold this plugin's own route-adjacent chat back -- today the
 * diary nudge ({@link DiaryRouteGuard#shouldNudge}).
 *
 * <p>Written first for the Quest Helper integration, removed for the first
 * Plugin Hub submission: starting a quest in Quest Helper made Quest Helper
 * post its own Shortest Path route, which replaced the shop route the player
 * had just sent. The guard outlived that integration because a shop trip
 * underway is still the wrong moment to suggest walking somewhere else.
 *
 * <p>Pure decision, no client and no clock of its own: every {@link Instant}
 * is a parameter, so the actual expiry window is exercised directly rather
 * than by waiting on a real clock in a test.
 */
final class ShopRouteGuard
{
	/**
	 * How long a sent shop route keeps holding the diary nudge back, from
	 * the moment it was sent, if nothing clears it sooner --
	 * see {@link #blocksQuestRouting}'s own doc for what "sooner" means.
	 *
	 * <p>A safety net, not the primary signal: the primary signal is {@code
	 * QuestpathPlugin} clearing its own tracked send the moment a shop is
	 * actually observed open, which is the real "this leg of the trip is
	 * done" event. This window exists for the trip that is abandoned rather
	 * than finished -- cancelled, walked away from, or Shortest Path simply
	 * never got there -- so that a shop route sent once does not suppress
	 * every future nudge for the rest of the session. Five minutes,
	 * matching the order of magnitude {@code
	 * QuestpathPlugin#EXPERIENCE_PUSH_INTERVAL} already uses for "still
	 * fresh enough to act on" elsewhere in this plugin, not a value measured
	 * against how long a real shopping trip takes.
	 */
	static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);

	private ShopRouteGuard()
	{
	}

	/**
	 * @param sentAt when the shop-route button last actually sent a route
	 *     ({@link ShortestPathBridge.RouteOutcome#SENT}), or null when no
	 *     route has been sent this session or the tracked send was already
	 *     cleared -- see {@link #ACTIVE_WINDOW}'s own doc for the two ways
	 *     that happens
	 * @param now the current time, read once by the caller and passed in
	 *     rather than read here -- see the class doc
	 * @return true only when a route was sent and {@link #ACTIVE_WINDOW} has
	 *     not yet elapsed since; false for a null {@code sentAt}, which
	 *     covers both "never sent" and "already cleared" identically, since
	 *     neither should block anything
	 */
	static boolean blocksQuestRouting(Instant sentAt, Instant now)
	{
		if (sentAt == null)
		{
			return false;
		}
		return now.isBefore(sentAt.plus(ACTIVE_WINDOW));
	}
}
