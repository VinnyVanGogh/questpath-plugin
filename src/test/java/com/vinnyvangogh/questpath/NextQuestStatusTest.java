package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
