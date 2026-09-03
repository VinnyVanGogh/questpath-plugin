package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The three states NEXT_QUEST can be in, and the rule that keeps them
 * distinct: an empty route, a finished route, and a route with work left on
 * it must never render as one another.
 */
public class NextQuestStatusTest
{
	@Test
	public void anEmptyRouteIsNoRoute()
	{
		NextQuestStatus status = NextQuestStatus.of(Collections.emptyList(), Collections.emptySet());

		assertEquals(NextQuestStatus.State.NO_ROUTE, status.state());
		assertNull(status.questName());
	}

	@Test
	public void everyQuestOnTheRouteFinishedIsAllDone()
	{
		List<String> route = Arrays.asList("Cook's Assistant", "Sheep Shearer");
		Set<String> completed = new HashSet<>(route);

		NextQuestStatus status = NextQuestStatus.of(route, completed);

		assertEquals(NextQuestStatus.State.ALL_DONE, status.state());
		assertNull(status.questName());
	}

	@Test
	public void theFirstUnfinishedQuestOnTheRouteIsNext()
	{
		List<String> route = Arrays.asList("Cook's Assistant", "Sheep Shearer", "Dragon Slayer II");
		Set<String> completed = new HashSet<>(Collections.singletonList("Cook's Assistant"));

		NextQuestStatus status = NextQuestStatus.of(route, completed);

		assertEquals(NextQuestStatus.State.NEXT, status.state());
		assertEquals("Sheep Shearer", status.questName());
	}

	/**
	 * A route is saved in order, and that order is what decides "next" --
	 * not alphabetical, not by difficulty. A quest that is finished but
	 * later in the list must not shadow an earlier one still outstanding,
	 * and a quest earlier in the list must win over one later even if both
	 * are outstanding.
	 */
	@Test
	public void orderOnTheRouteDecidesWhichQuestIsNext()
	{
		List<String> route = Arrays.asList("Waterfall Quest", "Cook's Assistant", "Sheep Shearer");
		Set<String> completed = Collections.emptySet();

		NextQuestStatus status = NextQuestStatus.of(route, completed);

		assertEquals("Waterfall Quest", status.questName());
	}

	/**
	 * The whole route is carried, not only its head.
	 *
	 * The sidebar and the overlay both list it, and both were originally
	 * given one quest name -- which reads, next to a browser listing 218
	 * quests, as though the route were one quest long. Finished quests stay
	 * in the list because they are the progress: dropping them would make a
	 * route nearly finished look identical to one nobody has started.
	 */
	@Test
	public void theWholeRouteIsCarriedInSavedOrder()
	{
		List<String> route = Arrays.asList("Cook's Assistant", "Sheep Shearer", "Dragon Slayer II");
		Set<String> completed = new HashSet<>(Collections.singletonList("Cook's Assistant"));

		NextQuestStatus status = NextQuestStatus.of(route, completed);

		assertEquals(3, status.size());
		assertEquals(1, status.doneCount());
		assertEquals(2, status.remaining());

		assertEquals("Cook's Assistant", status.route().get(0).questName());
		assertTrue(status.route().get(0).done());
		assertEquals("Sheep Shearer", status.route().get(1).questName());
		assertFalse(status.route().get(1).done());
	}

	/**
	 * upcoming() is what an overlay draws: on screen, during play, the
	 * finished half of the plan is history and the space is better spent on
	 * what is left.
	 */
	@Test
	public void upcomingSkipsWhatIsAlreadyFinished()
	{
		List<String> route = Arrays.asList("Cook's Assistant", "Sheep Shearer", "Dragon Slayer II");
		Set<String> completed = new HashSet<>(Collections.singletonList("Cook's Assistant"));

		List<NextQuestStatus.Step> upcoming = NextQuestStatus.of(route, completed).upcoming();

		assertEquals(2, upcoming.size());
		assertEquals("Sheep Shearer", upcoming.get(0).questName());
		assertEquals("Dragon Slayer II", upcoming.get(1).questName());
	}

	/**
	 * A route re-saved in questpath with the same next quest but a different
	 * tail must not compare equal.
	 *
	 * The panel caches on this hash and rebuilds only when it changes.
	 * Hashing the head alone would leave the old tail on screen with nothing
	 * to say it was stale, which is the failure the whole-route hash exists
	 * to prevent.
	 */
	@Test
	public void aDifferentTailIsADifferentRoute()
	{
		Set<String> completed = Collections.emptySet();
		NextQuestStatus before = NextQuestStatus.of(
			Arrays.asList("Cook's Assistant", "Sheep Shearer"), completed);
		NextQuestStatus after = NextQuestStatus.of(
			Arrays.asList("Cook's Assistant", "Dragon Slayer II"), completed);

		assertEquals(before.questName(), after.questName());
		assertNotEquals(before, after);
		assertNotEquals(before.hashCode(), after.hashCode());
	}

	/**
	 * An empty route has an empty list, not a null one. Every caller iterates
	 * it without a guard, and NO_ROUTE is the state most likely to be reached
	 * first.
	 */
	@Test
	public void anEmptyRouteStillHasAList()
	{
		NextQuestStatus status = NextQuestStatus.of(Collections.emptyList(), Collections.emptySet());

		assertTrue(status.route().isEmpty());
		assertTrue(status.upcoming().isEmpty());
		assertEquals(0, status.size());
		assertEquals(0, status.remaining());
	}
}
