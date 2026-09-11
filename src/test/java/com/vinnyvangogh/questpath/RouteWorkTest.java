package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What the route list cannot say, over the real bundled graph.
 *
 * <p>The two halves fail in opposite directions and both are worth pinning. An
 * outstanding quest wrongly left out reads as "you can start this now" and
 * sends someone walking; a ready quest wrongly listed reads as "this is
 * blocked" and stops them going at all.
 */
public class RouteWorkTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	/** A level-1 account that has finished nothing. */
	private static PlayerState fresh()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
	}

	/** An account holding every skill at level, with nothing finished. */
	private static PlayerState atLevel(int level)
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (net.runelite.api.Skill skill : net.runelite.api.Skill.values())
		{
			if (skill.getName() != null)
			{
				levels.put(skill.getName(), level);
			}
		}
		return new PlayerState(levels, experience, new HashSet<String>(), Collections.emptyList());
	}

	private static NextQuestStatus route(String... questNames) throws Exception
	{
		// A non-empty completed set, because NextQuestStatus is only ever
		// built from one: an account reporting zero finished quests is a
		// client that has not loaded, and the plugin refuses to judge it.
		Set<String> completed = new HashSet<>();
		completed.add("Cook's Assistant");
		return NextQuestStatus.of(Arrays.asList(questNames), completed);
	}

	/**
	 * The section's whole reason for existing: a quest on the plan that is
	 * waiting on levels is named, and so is what it is waiting on.
	 *
	 * <p>Monkey Madness I asks for 53 Agility and 43 Thieving and sits behind
	 * a chain of quests, none of which a level-1 account has.
	 */
	@Test
	public void aQuestWaitingOnWorkIsNamedAndSoIsTheWork() throws Exception
	{
		RouteWork work = RouteWork.of(graph(), fresh(),
			route("Monkey Madness I"), Collections.<PartialRun>emptyList());

		assertEquals("the one quest on the plan was judged", 1, work.considered());
		assertEquals(1, work.outstanding().size());

		RouteWork.Outstanding entry = work.outstanding().get(0);
		assertEquals("Monkey Madness I", entry.questName());
		assertFalse("it must say what is in the way, not merely that something is",
			entry.blockers().isEmpty());
	}

	/**
	 * A quest the account can start right now is not in this section.
	 *
	 * <p>This is the half that would be quietly wrong. A section that listed
	 * every unfinished quest would be a second copy of the route, and the one
	 * thing it claims -- these are the ones needing work -- would be false of
	 * most of its rows.
	 */
	@Test
	public void aStartableQuestIsNotListed() throws Exception
	{
		// Sheep Shearer asks for no levels and no quests, so an account with
		// every skill at 99 can certainly start it. Cook's Assistant would do
		// as well but the harness seeds it as already finished.
		RouteWork work = RouteWork.of(graph(), atLevel(99),
			route("Sheep Shearer"), Collections.<PartialRun>emptyList());

		assertEquals(1, work.considered());
		assertTrue("a quest with every requirement met has nothing to say here",
			work.outstanding().isEmpty());
	}

	/**
	 * Finished quests are not judged at all.
	 *
	 * <p>They cannot need work, and counting them in considered() would make
	 * "3 of 38 need something" quote a denominator that includes quests
	 * nobody has left to do.
	 */
	@Test
	public void finishedQuestsAreNotCounted() throws Exception
	{
		RouteWork work = RouteWork.of(graph(), fresh(),
			route("Cook's Assistant", "Monkey Madness I"), Collections.<PartialRun>emptyList());

		assertEquals("Cook's Assistant is done, so only one quest was judged",
			1, work.considered());
	}

	/**
	 * Closest first, so the list can be read top-down as what to do next.
	 *
	 * <p>A quest a level short is nearer than one four quests short, and a
	 * list that put them the other way round would be answering "what is on
	 * the plan" again rather than "what should I go and do".
	 */
	@Test
	public void theNearestWorkSortsFirst() throws Exception
	{
		// The Knight's Sword wants 10 Mining and no quests; Monkey Madness I
		// wants Tree Gnome Village and The Grand Tree and no levels. Two
		// different kinds of waiting, and training is the one you can start
		// without going anywhere first.
		//
		// Doric's Quest was the obvious pick here and is the wrong one: the
		// Mining it is famous for is needed during the quest, not to start
		// it, so the wiki lists no requirement and neither does our graph.
		// Checked against quest_skill_requirements rather than assumed.
		RouteWork work = RouteWork.of(graph(), fresh(),
			route("Monkey Madness I", "The Knight's Sword"), Collections.<PartialRun>emptyList());

		List<RouteWork.Outstanding> outstanding = work.outstanding();
		assertEquals(2, outstanding.size());
		assertEquals("a levels-only quest outranks one behind a quest chain",
			"The Knight's Sword", outstanding.get(0).questName());
		assertEquals(QuestStatus.SKILL_BLOCKED, outstanding.get(0).status());
		assertEquals(QuestStatus.QUEST_BLOCKED, outstanding.get(1).status());
	}

	/**
	 * A quest the bundle has never heard of is skipped, and skipped quietly
	 * rather than reported as clear.
	 *
	 * <p>A name on the plan the graph cannot resolve means a stale bundle. It
	 * is not evidence that the quest is ready, and the section must not
	 * produce a row it cannot explain.
	 */
	@Test
	public void anUnknownQuestIsNotJudged() throws Exception
	{
		RouteWork work = RouteWork.of(graph(), fresh(),
			route("A Quest That Does Not Exist"), Collections.<PartialRun>emptyList());

		assertEquals("nothing was judged", 0, work.considered());
		assertTrue(work.outstanding().isEmpty());
	}

	/**
	 * No graph, no account or no route all produce nothing rather than a
	 * confident empty list.
	 *
	 * <p>Each means the plugin cannot judge anything yet. The panel draws an
	 * empty result as no section, which is the honest rendering; a section
	 * headed "still needs work" over zero rows would read as "your plan is
	 * clear" on no evidence.
	 */
	@Test
	public void nothingToJudgeProducesNothing() throws Exception
	{
		assertTrue(RouteWork.of(null, fresh(), route("Monkey Madness I"), null).isEmpty());
		assertTrue(RouteWork.of(graph(), null, route("Monkey Madness I"), null).isEmpty());
		assertTrue(RouteWork.of(graph(), fresh(), null, null).isEmpty());
	}

	/**
	 * Partial runs survive a missing graph, because they never came from it.
	 *
	 * <p>Which quests a build stops halfway through is a property of the
	 * build and arrives on the push. Dropping them along with the graph-derived
	 * half would hide the section for a reason that has nothing to do with it.
	 */
	@Test
	public void partialRunsDoNotDependOnTheGraph() throws Exception
	{
		PartialRun run = GSON.fromJson("{\"slug\":\"dragon_slayer_i\","
			+ "\"name\":\"Dragon Slayer I\","
			+ "\"up_to\":\"Up to the anti-dragon shield.\","
			+ "\"for\":\"The anti-dragon shield.\","
			+ "\"why\":\"Finishing grants 18,650 Defence XP.\"}", PartialRun.class);

		RouteWork work = RouteWork.of(null, null, null, Collections.singletonList(run));
		assertEquals(1, work.partial().size());
		assertEquals("Dragon Slayer I", work.partial().get(0).name());
		assertFalse(work.isEmpty());
	}

	/**
	 * A partial run missing any of its three sentences is left out.
	 *
	 * <p>A name with no explanation is exactly the preset note this section
	 * replaces. Drawing it would put a row on screen whose tooltip says
	 * nothing, which reads as "questpath has no opinion" when the truth is
	 * "a field did not arrive".
	 */
	@Test
	public void anIncompletePartialRunIsDropped() throws Exception
	{
		PartialRun run = GSON.fromJson("{\"slug\":\"x\",\"name\":\"X\","
			+ "\"for\":\"Something\"}", PartialRun.class);
		assertFalse("missing up_to and why", run.usable());

		RouteWork work = RouteWork.of(null, null, null, Collections.singletonList(run));
		assertTrue(work.partial().isEmpty());
		assertTrue(work.isEmpty());
	}

	/**
	 * A quest on the plan that a build rule has closed permanently is still
	 * reported, because it is still on the plan.
	 *
	 * <p>Not this section's job to hide it -- the Build tab owns that verdict
	 * -- but leaving it silently out of a section that accounts for every
	 * unfinished quest would make the count above it wrong.
	 */
	@Test
	public void theCountCoversEveryUnfinishedQuestOnThePlan() throws Exception
	{
		RouteWork work = RouteWork.of(graph(), fresh(),
			route("Monkey Madness I", "Doric's Quest", "Cook's Assistant"),
			Collections.<PartialRun>emptyList());

		assertEquals("two unfinished, one done", 2, work.considered());
		assertNotNull(work.outstanding());
		assertTrue("and the outstanding list cannot exceed what was judged",
			work.outstanding().size() <= work.considered());
	}

	/**
	 * When the route's done flags and the account's completed set disagree,
	 * the account wins.
	 *
	 * <p>They arrive as two separate arguments and nothing makes a caller
	 * pass a matching pair. The account is what every other verdict in the
	 * panel is judged against, so a quest it calls finished must not be
	 * listed as needing work -- a row telling someone to go and train for a
	 * quest they finished last week is the most confusing thing this section
	 * could produce.
	 */
	@Test
	public void theAccountBeatsTheRouteOnWhatIsFinished() throws Exception
	{
		// The route is told nothing is done, so every step's done flag is
		// false. The account is told The Knight's Sword is finished.
		NextQuestStatus route = NextQuestStatus.of(
			Arrays.asList("The Knight's Sword"),
			new HashSet<>(Arrays.asList("Cook's Assistant")));

		Set<String> completed = new HashSet<>();
		completed.add("The Knight's Sword");
		PlayerState account = new PlayerState(new HashMap<>(), new HashMap<>(), completed,
			Collections.emptyList());

		RouteWork work = RouteWork.of(graph(), account, route, Collections.<PartialRun>emptyList());
		assertTrue("a quest the account has finished is not outstanding",
			work.outstanding().isEmpty());
	}

	/** An empty route is empty, not null-hostile. */
	@Test
	public void anEmptyRouteIsEmpty() throws Exception
	{
		RouteWork work = RouteWork.of(graph(), fresh(), route(), Collections.<PartialRun>emptyList());
		assertEquals(0, work.considered());
		assertTrue(work.isEmpty());
	}
}
