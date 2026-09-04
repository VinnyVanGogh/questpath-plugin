package com.vinnyvangogh.questpath;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pins {@link AutoAdvancePlanner}'s decision logic against fixed {@link
 * NextQuestStatus} readings -- no client, no config, no Quest Helper, the
 * same way {@link NextQuestStatus}'s own tests would. What actually starts
 * the next quest, and whether the player asked for any of this, is
 * QuestpathPlugin's concern; this only proves when the planner says
 * "advance" and when it correctly says nothing.
 */
public class AutoAdvancePlannerTest
{
	private final AutoAdvancePlanner planner = new AutoAdvancePlanner();

	private static NextQuestStatus route(List<String> order, Set<String> completed)
	{
		return NextQuestStatus.of(order, completed);
	}

	@Before
	public void freshPlanner()
	{
		planner.reset();
	}

	/**
	 * The login sweep: a returning account's route already shows several
	 * quests done the very first time it is observed. Nothing "just"
	 * finished -- there is no earlier observation to compare against -- so
	 * this must report {@link AutoAdvancePlanner.Action#NONE} even though
	 * the head quest is unfinished and plenty behind it are done.
	 */
	@Test
	public void doesNotFireOnTheFirstObservationAfterReset()
	{
		NextQuestStatus loginSweep = route(
			List.of("Cook's Assistant", "Demon Slayer", "Dragon Slayer I"),
			Set.of("Cook's Assistant"));

		AutoAdvancePlanner.Decision decision = planner.observe(loginSweep);

		assertEquals(AutoAdvancePlanner.Action.NONE, decision.action());
	}

	/**
	 * The core case this exists for: the route's current step (Prince Ali
	 * Rescue) finishes between two observations, and Demon Slayer -- the
	 * step behind it -- is what should start next.
	 */
	@Test
	public void firesWhenTheRoutesCurrentStepFinishes()
	{
		List<String> order = List.of("Prince Ali Rescue", "Demon Slayer", "Dragon Slayer I");
		planner.observe(route(order, Set.of())); // baseline: Prince Ali Rescue is next, undone

		AutoAdvancePlanner.Decision decision = planner.observe(route(order, Set.of("Prince Ali Rescue")));

		assertEquals(AutoAdvancePlanner.Action.ADVANCE, decision.action());
		assertEquals("Prince Ali Rescue", decision.finished());
		assertEquals("Demon Slayer", decision.next());
	}

	/**
	 * A quest finishing that is not the route's current step -- some
	 * unrelated side quest done in passing, or a background helper Quest
	 * Helper itself tracks -- must never hijack the helper. The route's own
	 * head is what this class watches, not "anything finished this tick".
	 */
	@Test
	public void doesNotFireForAQuestThatIsNotTheCurrentRouteStep()
	{
		List<String> order = List.of("Prince Ali Rescue", "Demon Slayer");
		planner.observe(route(order, Set.of())); // baseline: Prince Ali Rescue is next, undone

		// The world moved -- some unrelated quest finished, or the account
		// simply ticked forward -- but the route's own head is unchanged.
		AutoAdvancePlanner.Decision decision = planner.observe(route(order, Set.of()));

		assertEquals(AutoAdvancePlanner.Action.NONE, decision.action());
	}

	/** Finishing the last quest on the route reports ROUTE_FINISHED, not ADVANCE to nothing. */
	@Test
	public void reportsRouteFinishedWhenTheLastStepCompletes()
	{
		List<String> order = List.of("Prince Ali Rescue");
		planner.observe(route(order, Set.of()));

		AutoAdvancePlanner.Decision decision = planner.observe(route(order, Set.of("Prince Ali Rescue")));

		assertEquals(AutoAdvancePlanner.Action.ROUTE_FINISHED, decision.action());
		assertEquals("Prince Ali Rescue", decision.finished());
		assertNull(decision.next());
	}

	/**
	 * A transition is reported exactly once. observe() already moved its own
	 * head forward the moment it reported ADVANCE, so asking again with the
	 * same (now-current) state must never repeat it -- the retry loop this
	 * class must never become.
	 */
	@Test
	public void doesNotReportTheSameTransitionTwice()
	{
		List<String> order = List.of("Prince Ali Rescue", "Demon Slayer");
		planner.observe(route(order, Set.of()));
		planner.observe(route(order, Set.of("Prince Ali Rescue"))); // the one real transition

		AutoAdvancePlanner.Decision decision = planner.observe(route(order, Set.of("Prince Ali Rescue")));

		assertEquals(AutoAdvancePlanner.Action.NONE, decision.action());
	}

	/**
	 * A resync that clears the saved route (a rejected push, say) between
	 * two observations must not crash, and has nothing sane to advance to.
	 */
	@Test
	public void doesNotFireWhenTheRouteDisappearsBetweenObservations()
	{
		List<String> order = List.of("Prince Ali Rescue");
		planner.observe(route(order, Set.of()));

		AutoAdvancePlanner.Decision decision = planner.observe(route(List.of(), Set.of()));

		assertEquals(AutoAdvancePlanner.Action.NONE, decision.action());
	}

	/** reset() re-establishes the "first observation" guard, the way a logout or a plugin restart must. */
	@Test
	public void resetMakesTheNextObservationABaselineAgain()
	{
		List<String> order = List.of("Prince Ali Rescue", "Demon Slayer");
		planner.observe(route(order, Set.of()));

		planner.reset();

		// Without the reset this would report ADVANCE -- see
		// firesWhenTheRoutesCurrentStepFinishes. With it, this is treated as
		// a fresh account's first sighting of its route, same as
		// doesNotFireOnTheFirstObservationAfterReset.
		AutoAdvancePlanner.Decision decision = planner.observe(route(order, Set.of("Prince Ali Rescue")));

		assertEquals(AutoAdvancePlanner.Action.NONE, decision.action());
	}
}
