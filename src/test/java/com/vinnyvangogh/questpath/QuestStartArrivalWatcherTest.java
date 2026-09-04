package com.vinnyvangogh.questpath;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Pins {@link QuestStartArrivalWatcher}'s state machine against fabricated
 * positions and quest names -- no client, no graph, no Quest Helper. What
 * matters here is purely which transitions count as an arrival and which do
 * not; whether a real quest's start point is where the wiki says it is, or
 * whether Quest Helper actually opens, is somebody else's test.
 */
public class QuestStartArrivalWatcherTest
{
	private static final WorldPoint START = new WorldPoint(3208, 3214, 0);
	private static final WorldPoint FAR_AWAY = new WorldPoint(3208, 3214 + 500, 0);
	private static final WorldPoint JUST_INSIDE = new WorldPoint(
		3208 + QuestStartArrivalWatcher.ARRIVAL_RADIUS_TILES, 3214, 0);
	private static final WorldPoint JUST_OUTSIDE = new WorldPoint(
		3208 + QuestStartArrivalWatcher.ARRIVAL_RADIUS_TILES + 1, 3214, 0);

	/**
	 * The very first observation can never be an arrival, even when the
	 * player is already standing on the start tile -- there is nothing yet
	 * to compare it against. This is the guard against firing on login, on a
	 * resync, or on the sweep that establishes the baseline.
	 */
	@Test
	public void theFirstObservationIsNeverAnArrivalEvenWhenAlreadyThere()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, START);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/** Walking from outside the radius to inside it, after a real baseline, is the case this class exists for. */
	@Test
	public void arrivingFromOutsideTheRadiusFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, START);

		assertEquals(QuestStartArrivalWatcher.Action.ARRIVED, decision.action());
		assertEquals("Cook's Assistant", decision.questName());
	}

	/** Staying inside the radius on the next tick is not a second arrival. */
	@Test
	public void stayingInsideTheRadiusDoesNotFireAgain()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);
		watcher.observe("Cook's Assistant", START, START);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, START);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/**
	 * Closing Quest Helper changes nothing this class reads -- the player's
	 * position and the route's head are unaffected -- so standing still
	 * after an arrival must never read as a second one, whether or not
	 * something else closed a window in the meantime.
	 */
	@Test
	public void aRepeatObservationWhileStillNearDoesNotRefireRegardlessOfWhatElseHappened()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);
		QuestStartArrivalWatcher.Decision first = watcher.observe("Cook's Assistant", START, START);
		assertEquals(QuestStartArrivalWatcher.Action.ARRIVED, first.action());

		for (int i = 0; i < 5; i++)
		{
			QuestStartArrivalWatcher.Decision repeat = watcher.observe("Cook's Assistant", START, START);
			assertEquals(QuestStartArrivalWatcher.Action.NONE, repeat.action());
		}
	}

	/** Leaving the radius and coming back is a genuinely new arrival. */
	@Test
	public void leavingAndReturningFiresAgain()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);
		watcher.observe("Cook's Assistant", START, START);
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, START);

		assertEquals(QuestStartArrivalWatcher.Action.ARRIVED, decision.action());
	}

	/** Exactly at the radius counts as arrived; one tile past it does not. */
	@Test
	public void theRadiusBoundaryIsInclusive()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		assertEquals(QuestStartArrivalWatcher.Action.ARRIVED,
			watcher.observe("Cook's Assistant", START, JUST_INSIDE).action());
	}

	/** One tile past the radius never fires. */
	@Test
	public void justOutsideTheRadiusNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		assertEquals(QuestStartArrivalWatcher.Action.NONE,
			watcher.observe("Cook's Assistant", START, JUST_OUTSIDE).action());
	}

	/**
	 * A quest with no known start point -- 13 of 218 in the real bundle --
	 * must never be treated as arrived at, however close the player stands
	 * to (0, 0) or anywhere else. The plugin is expected to pass a null
	 * start whenever QuestGraph.Quest#hasStart() is false.
	 */
	@Test
	public void aNullStartPointNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Rogue Trader", null, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Rogue Trader", null, START);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/** No saved route at all -- a null quest name -- is quietly never an arrival. */
	@Test
	public void aNullQuestNameNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(null, null, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe(null, null, START);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/** The player's location being unknown (not logged in) is never near anything. */
	@Test
	public void aNullPlayerLocationNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, null);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/**
	 * The route advancing to a new quest, while the player already happens
	 * to be standing at its start, is a fresh arrival for the new quest --
	 * it must not be suppressed just because the player was "near something"
	 * a moment ago.
	 */
	@Test
	public void aChangedRouteHeadCanFireImmediatelyIfAlreadyThere()
	{
		WorldPoint otherStart = new WorldPoint(3200, 3200, 0);
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);
		watcher.observe("Cook's Assistant", START, START);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Restless Ghost", otherStart, otherStart);

		assertEquals(QuestStartArrivalWatcher.Action.ARRIVED, decision.action());
		assertEquals("Restless Ghost", decision.questName());
	}

	/**
	 * reset() forces the very next observation back into baseline-only mode,
	 * the same guard the very first call gets -- this is what login, a
	 * resync, and a plugin toggle-off-then-on all rely on.
	 */
	@Test
	public void resetForcesTheNextObservationToBeABaselineAgain()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);
		watcher.observe("Cook's Assistant", START, START);

		watcher.reset();

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, START);
		assertEquals("reset must make the next call a baseline, not a transition",
			QuestStartArrivalWatcher.Action.NONE, decision.action());
	}

	/** Different planes are never "near", even at the same x/y -- see the class doc on distanceTo. */
	@Test
	public void differentPlanesNeverFire()
	{
		WorldPoint upstairs = new WorldPoint(3208, 3214, 1);
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe("Cook's Assistant", START, FAR_AWAY);

		QuestStartArrivalWatcher.Decision decision = watcher.observe("Cook's Assistant", START, upstairs);

		assertEquals(QuestStartArrivalWatcher.Action.NONE, decision.action());
	}
}
