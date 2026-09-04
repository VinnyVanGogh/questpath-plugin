package com.vinnyvangogh.questpath;

import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestStartArrivalWatcher}'s state machine against fabricated
 * positions and quest names -- no client, no graph, no Quest Helper. What
 * matters here is purely which transitions count as an arrival and which do
 * not; whether a real quest's start point is where the wiki says it is, or
 * whether Quest Helper actually opens, is somebody else's test.
 *
 * <p>Covers the multi-candidate shape directly: several quests tracked at
 * once, one dropping out of the candidate set, and a quest new to the set
 * firing immediately on an already-primed watcher -- the case an earlier,
 * single-quest version of this class could not represent at all, which is
 * exactly the report ("stood next to Aris, nothing happened") that widened
 * it.
 */
public class QuestStartArrivalWatcherTest
{
	private static final WorldPoint START = new WorldPoint(3208, 3214, 0);
	private static final WorldPoint FAR_AWAY = new WorldPoint(3208, 3214 + 500, 0);
	private static final WorldPoint JUST_INSIDE = new WorldPoint(
		3208 + QuestStartArrivalWatcher.ARRIVAL_RADIUS_TILES, 3214, 0);
	private static final WorldPoint JUST_OUTSIDE = new WorldPoint(
		3208 + QuestStartArrivalWatcher.ARRIVAL_RADIUS_TILES + 1, 3214, 0);

	private static Map<String, WorldPoint> oneCandidate(String questName, WorldPoint start)
	{
		Map<String, WorldPoint> candidates = new java.util.HashMap<>();
		candidates.put(questName, start);
		return candidates;
	}

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

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), START);

		assertTrue(arrived.isEmpty());
	}

	/** Walking from outside the radius to inside it, after a real baseline, is the case this class exists for. */
	@Test
	public void arrivingFromOutsideTheRadiusFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), START);

		assertEquals(List.of("Cook's Assistant"), arrived);
	}

	/** Staying inside the radius on the next tick is not a second arrival. */
	@Test
	public void stayingInsideTheRadiusDoesNotFireAgain()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);
		watcher.observe(oneCandidate("Cook's Assistant", START), START);

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), START);

		assertTrue(arrived.isEmpty());
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
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);
		List<String> first = watcher.observe(oneCandidate("Cook's Assistant", START), START);
		assertEquals(List.of("Cook's Assistant"), first);

		for (int i = 0; i < 5; i++)
		{
			List<String> repeat = watcher.observe(oneCandidate("Cook's Assistant", START), START);
			assertTrue(repeat.isEmpty());
		}
	}

	/** Leaving the radius and coming back is a genuinely new arrival. */
	@Test
	public void leavingAndReturningFiresAgain()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);
		watcher.observe(oneCandidate("Cook's Assistant", START), START);
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), START);

		assertEquals(List.of("Cook's Assistant"), arrived);
	}

	/** Exactly at the radius counts as arrived; one tile past it does not. */
	@Test
	public void theRadiusBoundaryIsInclusive()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		assertEquals(List.of("Cook's Assistant"),
			watcher.observe(oneCandidate("Cook's Assistant", START), JUST_INSIDE));
	}

	/** One tile past the radius never fires. */
	@Test
	public void justOutsideTheRadiusNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		assertTrue(watcher.observe(oneCandidate("Cook's Assistant", START), JUST_OUTSIDE).isEmpty());
	}

	/**
	 * A quest with no known start point -- 13 of 218 in the real bundle --
	 * must never be treated as arrived at, however close the player stands
	 * to (0, 0) or anywhere else. The plugin is expected to omit such quests
	 * from the candidate map entirely, but a null value must still degrade
	 * safely rather than crash if one slips through.
	 */
	@Test
	public void aNullStartPointNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Rogue Trader", null), FAR_AWAY);

		List<String> arrived = watcher.observe(oneCandidate("Rogue Trader", null), START);

		assertTrue(arrived.isEmpty());
	}

	/** No candidates at all -- no route, no eligible quests -- is quietly never an arrival. */
	@Test
	public void noCandidatesNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(Map.of(), FAR_AWAY);

		List<String> arrived = watcher.observe(Map.of(), START);

		assertTrue(arrived.isEmpty());
	}

	/** The player's location being unknown (not logged in) is never near anything. */
	@Test
	public void aNullPlayerLocationNeverFires()
	{
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), null);

		assertTrue(arrived.isEmpty());
	}

	/**
	 * A quest new to the candidate set, while the player already happens to
	 * be standing at its start, is a fresh arrival for that quest -- it must
	 * not be suppressed just because the watcher was already tracking
	 * something else a moment ago. This is the exact shape of the route
	 * advancing to a new head while the player has not moved.
	 */
	@Test
	public void aQuestNewToTheCandidateSetCanFireImmediatelyIfAlreadyThere()
	{
		WorldPoint otherStart = new WorldPoint(3200, 3200, 0);
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);
		watcher.observe(oneCandidate("Cook's Assistant", START), START);

		List<String> arrived = watcher.observe(oneCandidate("Restless Ghost", otherStart), otherStart);

		assertEquals(List.of("Restless Ghost"), arrived);
	}

	/**
	 * The whole point of widening past one quest: two independent candidates
	 * are tracked at once, each judged on its own baseline, and one leaving
	 * the set (the route moved past it, or it stopped being eligible) does
	 * not disturb the other's tracking.
	 */
	@Test
	public void tracksSeveralCandidatesIndependently()
	{
		WorldPoint demonSlayerStart = new WorldPoint(3203, 3424, 0);
		WorldPoint cooksStart = START;
		Map<String, WorldPoint> both = Map.of(
			"Demon Slayer", demonSlayerStart,
			"Cook's Assistant", cooksStart);

		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(both, FAR_AWAY);

		// Walks up to Demon Slayer's start only -- Cook's Assistant is still
		// far away and must not also report an arrival.
		List<String> arrived = watcher.observe(both, demonSlayerStart);
		assertEquals(List.of("Demon Slayer"), arrived);

		// Demon Slayer is completed (or the route moved on) and drops out of
		// the candidate set. Cook's Assistant remains trackable on its own.
		Map<String, WorldPoint> onlyCooks = oneCandidate("Cook's Assistant", cooksStart);
		List<String> secondArrival = watcher.observe(onlyCooks, cooksStart);
		assertEquals(List.of("Cook's Assistant"), secondArrival);
	}

	/**
	 * Two candidates whose starts both happen to be within the radius of
	 * where the player walks can both report an arrival on the same tick --
	 * neither is silently preferred over the other.
	 */
	@Test
	public void twoCandidatesInRangeAtOnceBothFire()
	{
		WorldPoint a = new WorldPoint(3200, 3200, 0);
		WorldPoint b = new WorldPoint(3202, 3200, 0);
		Map<String, WorldPoint> both = Map.of("Quest A", a, "Quest B", b);

		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(both, FAR_AWAY);

		List<String> arrived = watcher.observe(both, new WorldPoint(3201, 3200, 0));

		assertEquals(2, arrived.size());
		assertTrue(arrived.contains("Quest A"));
		assertTrue(arrived.contains("Quest B"));
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
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);
		watcher.observe(oneCandidate("Cook's Assistant", START), START);

		watcher.reset();

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), START);
		assertTrue("reset must make the next call a baseline, not a transition", arrived.isEmpty());
	}

	/** Different planes are never "near", even at the same x/y -- see the class doc on distanceTo. */
	@Test
	public void differentPlanesNeverFire()
	{
		WorldPoint upstairs = new WorldPoint(3208, 3214, 1);
		QuestStartArrivalWatcher watcher = new QuestStartArrivalWatcher();
		watcher.observe(oneCandidate("Cook's Assistant", START), FAR_AWAY);

		List<String> arrived = watcher.observe(oneCandidate("Cook's Assistant", START), upstairs);

		assertTrue(arrived.isEmpty());
	}
}
