package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestStartCandidates#of} against the real bundled graph: the
 * fix for the first two of the three arrival-trigger bugs. See {@link
 * QuestStartCandidates}'s own class doc for the reports these close.
 *
 * <p><b>What this proves, and what it does not.</b> This is a pure function
 * over a real {@link QuestGraph} and hand-built {@link PlayerState}/{@link
 * QuestVerdicts} fixtures -- no client, no Quest Helper, no running game. It
 * proves the filtering logic is correct given a verdict and an in-progress
 * set; it says nothing about whether the client's own {@code Quest.getState}
 * reports the right thing, which is exactly the kind of client-dependent
 * question this project keeps out of a pure-logic test.
 */
public class QuestStartCandidatesTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	private static PlayerState playerState(Map<String, Integer> levels, Set<String> completed,
		Set<String> inProgress)
	{
		return new PlayerState(levels, new HashMap<>(), completed, inProgress, Collections.emptyList());
	}

	/** A fresh, level-1 account with no rules -- short of nearly everything, per QuestVerdictsTest. */
	private static PlayerState fresh()
	{
		return playerState(new HashMap<>(), new HashSet<>(), new HashSet<>());
	}

	/**
	 * An account for whom Jungle Potion classifies BOOSTABLE: Herblore 1
	 * against Jungle Potion's boostable Herblore 3 requirement (a Herblore
	 * potion covers a two-level gap at level 1, confirmed against the
	 * bundled boost table), with its one prerequisite -- Druidic Ritual --
	 * completed so the verdict is not QUEST_BLOCKED instead. Verified by
	 * hand against the real bundle, not assumed: with Druidic Ritual left
	 * incomplete, QuestClassifier reports QUEST_BLOCKED for the same
	 * account, because a boostable skill gap does not suppress a missing
	 * prerequisite in the classifier's precedence.
	 */
	private static PlayerState boostableForJunglePotion()
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("herblore", 1);
		Set<String> completed = new HashSet<>();
		completed.add("Druidic Ritual");
		return playerState(levels, completed, new HashSet<>());
	}

	private static QuestGraph.Quest quest(QuestGraph graph, String name)
	{
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (quest.name().equalsIgnoreCase(name))
			{
				return quest;
			}
		}
		return null;
	}

	/** The first quest {@code verdicts} judges as {@code status}, or null if there is none. */
	private static QuestGraph.Quest firstWithStatus(QuestVerdicts verdicts, QuestStatus status)
	{
		List<QuestGraph.Quest> found = verdicts.quests(status);
		return found.isEmpty() ? null : found.get(0);
	}

	/**
	 * The first quest {@code verdicts} judges as {@code status} that also
	 * carries a start point, or null if there is none -- a quest with no
	 * start point can never appear in {@link QuestStartCandidates#of}'s
	 * result regardless of its verdict, so tests asserting a quest *is*
	 * included need one that has a start to begin with.
	 */
	private static QuestGraph.Quest firstWithStatusAndStart(QuestVerdicts verdicts, QuestStatus status)
	{
		for (QuestGraph.Quest quest : verdicts.quests(status))
		{
			if (quest.hasStart())
			{
				return quest;
			}
		}
		return null;
	}

	private static NextQuestStatus routeOf(String... questNames)
	{
		return NextQuestStatus.of(List.of(questNames), Collections.emptySet());
	}

	/**
	 * The root cause of the first report, closed: a quest on the route that
	 * is SKILL_BLOCKED must never become a candidate, however it got onto
	 * the route. This is Shadow of the Storm's exact shape on the reporting
	 * account -- on the route, levels not met.
	 */
	@Test
	public void aSkillBlockedRouteStepNeverBecomesACandidate() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		QuestGraph.Quest blocked = firstWithStatus(verdicts, QuestStatus.SKILL_BLOCKED);
		assertNotNull("a fresh account must have at least one SKILL_BLOCKED quest", blocked);

		NextQuestStatus route = routeOf(blocked.name());
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>());

		assertFalse(blocked.name() + " is SKILL_BLOCKED and must never be a candidate",
			candidates.containsKey(blocked.name()));
	}

	/**
	 * A quest {@code verdicts} calls BOOSTABLE is exactly the case the user
	 * asked to keep: a boost that genuinely covers the shortfall must still
	 * fire, on the route or off it. Jungle Potion (Herblore 3, boostable) is
	 * the concrete case -- see {@link #boostableForJunglePotion}'s own doc
	 * for how the fixture was confirmed.
	 */
	@Test
	public void aBoostableQuestBecomesACandidate() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Quest junglePotion = quest(graph, "Jungle Potion");
		assertNotNull("the bundle carries Jungle Potion", junglePotion);
		assertTrue("Jungle Potion needs a start point for this test to mean anything",
			junglePotion.hasStart());

		QuestVerdicts verdicts = QuestVerdicts.of(graph, boostableForJunglePotion());
		assertEquals(QuestStatus.BOOSTABLE,
			new QuestClassifier(graph, boostableForJunglePotion()).classify(junglePotion));

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, new HashSet<>());

		assertTrue("Jungle Potion is BOOSTABLE and has a start point, so it must be a candidate",
			candidates.containsKey("Jungle Potion"));
	}

	/**
	 * An ELIGIBLE quest not on any saved route is still offered -- the
	 * original behaviour this fix must not regress, since ELIGIBLE quests
	 * were always candidates even before the route-step filter existed.
	 */
	@Test
	public void anEligibleQuestOffRouteIsStillACandidate() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		QuestGraph.Quest eligible = firstWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE);
		assertNotNull("the bundle must carry at least one ELIGIBLE quest with a start point"
			+ " for a fresh account", eligible);

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, new HashSet<>());

		assertTrue(candidates.containsKey(eligible.name()));
	}

	/**
	 * The root cause of the second report, closed: a quest the client
	 * already reports IN_PROGRESS must never be offered again, even though
	 * {@link QuestClassifier} still calls it ELIGIBLE/BOOSTABLE on its own
	 * levels and prerequisites -- it does not ask whether the quest itself
	 * has begun. Walking back through the start area of a quest already
	 * being worked on must find nothing to (re)start.
	 */
	@Test
	public void anInProgressQuestNeverBecomesACandidate() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		QuestGraph.Quest eligible = firstWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE);
		assertNotNull("the bundle must carry at least one ELIGIBLE quest with a start point"
			+ " for a fresh account", eligible);

		Set<String> inProgress = new HashSet<>();
		inProgress.add(eligible.name());
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, inProgress);

		assertFalse(eligible.name() + " is already in progress and must never be a candidate",
			candidates.containsKey(eligible.name()));
	}

	/**
	 * The same in-progress quest, still named on the saved route, must also
	 * be excluded via that path -- the route-step branch and the
	 * off-route branch share one filter, not two.
	 */
	@Test
	public void anInProgressRouteStepNeverBecomesACandidate() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		QuestGraph.Quest eligible = firstWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE);
		assertNotNull("the bundle must carry at least one ELIGIBLE quest with a start point"
			+ " for a fresh account", eligible);

		NextQuestStatus route = routeOf(eligible.name());
		Set<String> inProgress = new HashSet<>();
		inProgress.add(eligible.name());
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, inProgress);

		assertFalse(candidates.containsKey(eligible.name()));
	}

	/**
	 * Without verdicts the account is not judged yet, so nothing is offered
	 * at all -- the fix for the bug itself: an earlier version fell back to
	 * "every route step" here, which is how an unjudged quest started.
	 */
	@Test
	public void noVerdictsMeansNoCandidatesEvenWithARoute() throws Exception
	{
		QuestGraph graph = graph();
		NextQuestStatus route = routeOf(quest(graph, "Cook's Assistant").name());

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, null, new HashSet<>());

		assertTrue(candidates.isEmpty());
	}

	/** No graph, no candidates -- there is nothing to check a start point against. */
	@Test
	public void noGraphMeansNoCandidates() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(null, null, verdicts, new HashSet<>());

		assertTrue(candidates.isEmpty());
	}
}
