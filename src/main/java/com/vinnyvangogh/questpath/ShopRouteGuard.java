package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a shop route the player just sent to Shortest Path should still
 * keep this plugin's own automatic Quest Helper starts -- the arrival prompt
 * and auto-advance -- from firing.
 *
 * <p><b>The report this closes.</b> "The shop route mode is acting weird: it
 * routed to one shop and then immediately switched to routing for quest
 * starts instead of continuing with shops." Both features post to the exact
 * same target: this plugin's shop-route button posts a {@code
 * PluginMessage("shortestpath", "path", ...)} directly (see {@link
 * ShortestPathBridge}), and -- confirmed by {@code javap -c} against the
 * installed {@code quest-helper} jar's {@code
 * DetailedQuestStep.setShortestPath()}, called from every step's own {@code
 * startUp()} -- Quest Helper posts the identical message on its own,
 * unconditionally, the moment a quest step is selected, whenever the
 * player's own {@code QuestHelperConfig#useShortestPath()} setting (off by
 * default) is switched on. Neither plugin knows the other's message exists,
 * so whichever posts last wins.
 *
 * <p>This plugin cannot change Quest Helper's own setting or its own
 * behaviour -- both are outside this codebase, and reflecting in to disable
 * a feature the player deliberately turned on in another plugin's own
 * settings would be a worse surprise than the one being fixed. The only
 * lever available is not calling {@link QuestHelperBridge#start}, which is
 * what selects a new quest in Quest Helper and is therefore what triggers
 * Quest Helper's own post -- while a shop trip the player just started is
 * still presumably underway.
 *
 * <p>Pure decision, no client and no clock of its own: every {@link Instant}
 * is a parameter, the same shape {@link QuestStartArrivalGate} takes its
 * booleans, so the actual expiry window is exercised directly rather than by
 * waiting on a real clock in a test.
 */
final class ShopRouteGuard
{
	/**
	 * How long a sent shop route keeps blocking automatic Quest Helper
	 * starts, from the moment it was sent, if nothing clears it sooner --
	 * see {@link #blocksQuestRouting}'s own doc for what "sooner" means.
	 *
	 * <p>A safety net, not the primary signal: the primary signal is {@code
	 * QuestpathPlugin} clearing its own tracked send the moment a shop is
	 * actually observed open, which is the real "this leg of the trip is
	 * done" event. This window exists for the trip that is abandoned rather
	 * than finished -- cancelled, walked away from, or Shortest Path simply
	 * never got there -- so that a shop route sent once does not suppress
	 * every future arrival for the rest of the session. Five minutes,
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
