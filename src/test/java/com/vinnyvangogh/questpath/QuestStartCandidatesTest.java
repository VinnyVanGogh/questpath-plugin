package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.ArrayList;
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
	 * The first {@code count} quests {@code verdicts} judges {@code status}
	 * that also carry a start point, in whatever order {@link
	 * QuestVerdicts#quests} returns them -- the depth tests below build a
	 * route out of these and need several distinct startable quests with
	 * real coordinates, not just one.
	 */
	private static List<String> firstNWithStatusAndStart(QuestVerdicts verdicts, QuestStatus status, int count)
	{
		List<String> found = new ArrayList<>();
		for (QuestGraph.Quest quest : verdicts.quests(status))
		{
			if (quest.hasStart())
			{
				found.add(quest.name());
				if (found.size() == count)
				{
					break;
				}
			}
		}
		return found;
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
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 0);

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

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, new HashSet<>(), 0);

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

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, new HashSet<>(), 0);

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
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, null, verdicts, inProgress, 0);

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
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, inProgress, 0);

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

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, null, new HashSet<>(), 0);

		assertTrue(candidates.isEmpty());
	}

	/** No graph, no candidates -- there is nothing to check a start point against. */
	@Test
	public void noGraphMeansNoCandidates() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);

		Map<String, WorldPoint> candidates = QuestStartCandidates.of(null, null, verdicts, new HashSet<>(), 0);

		assertTrue(candidates.isEmpty());
	}

	/**
	 * routeDepth 0 means unlimited: every outstanding route step that is
	 * itself startable still becomes a candidate, unchanged from before this
	 * parameter existed.
	 */
	@Test
	public void depthZeroOffersEveryOutstandingRouteStep() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<String> eligible = firstNWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE, 4);
		assertTrue("need at least 4 ELIGIBLE quests with a start point for this test to mean anything",
			eligible.size() == 4);

		NextQuestStatus route = routeOf(eligible.toArray(new String[0]));
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 0);

		for (String name : eligible)
		{
			assertTrue(name + " must be a candidate at depth 0 (unlimited)", candidates.containsKey(name));
		}
	}

	/**
	 * A depth of 1 offers only the route's very next quest -- exactly the
	 * "just the next one" case the user asked for. The second and third
	 * route quests are themselves just as ELIGIBLE as the first, so this
	 * only proves anything if they are excluded specifically for sitting
	 * past the depth cutoff, not for some other reason.
	 */
	@Test
	public void depthOneOffersOnlyTheRouteHead() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<String> eligible = firstNWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE, 3);
		assertTrue("need at least 3 ELIGIBLE quests with a start point for this test to mean anything",
			eligible.size() == 3);

		NextQuestStatus route = routeOf(eligible.toArray(new String[0]));
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 1);

		assertTrue(eligible.get(0) + " is the route head and must be a candidate at depth 1",
			candidates.containsKey(eligible.get(0)));
		assertFalse(eligible.get(1) + " is past depth 1 and must not be a candidate",
			candidates.containsKey(eligible.get(1)));
		assertFalse(eligible.get(2) + " is past depth 1 and must not be a candidate",
			candidates.containsKey(eligible.get(2)));
	}

	/** A middle depth offers exactly that many route quests from the head, no more. */
	@Test
	public void aMiddleDepthOffersExactlyThatManyRouteSteps() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<String> eligible = firstNWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE, 5);
		assertTrue("need at least 5 ELIGIBLE quests with a start point for this test to mean anything",
			eligible.size() == 5);

		NextQuestStatus route = routeOf(eligible.toArray(new String[0]));
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 3);

		for (int i = 0; i < 3; i++)
		{
			assertTrue(eligible.get(i) + " is within depth 3 and must be a candidate",
				candidates.containsKey(eligible.get(i)));
		}
		for (int i = 3; i < 5; i++)
		{
			assertFalse(eligible.get(i) + " is past depth 3 and must not be a candidate",
				candidates.containsKey(eligible.get(i)));
		}
	}

	/** A depth larger than the whole route behaves exactly like depth 0 -- nothing is cut off. */
	@Test
	public void aDepthLargerThanTheRouteOffersTheWholeRoute() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<String> eligible = firstNWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE, 3);
		assertTrue("need at least 3 ELIGIBLE quests with a start point for this test to mean anything",
			eligible.size() == 3);

		NextQuestStatus route = routeOf(eligible.toArray(new String[0]));
		// 218 is the whole bundled graph's own quest count (see QuestGraph's
		// class doc), and therefore always at least as large as any real
		// route -- the literal "total number of quests we have" upper bound
		// the sidebar spinner is configured with.
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 218);

		for (String name : eligible)
		{
			assertTrue(name + " must still be a candidate when depth exceeds the route's length",
				candidates.containsKey(name));
		}
	}

	/**
	 * A SKILL_BLOCKED route step must never become a candidate at any depth.
	 * Narrowing the position window can only remove candidates, never let a
	 * blocked quest through a door a smaller window happens to leave open --
	 * this is the guard the depth feature must never weaken.
	 */
	@Test
	public void aSkillBlockedRouteStepNeverBecomesACandidateAtAnyDepth() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		QuestGraph.Quest blocked = firstWithStatus(verdicts, QuestStatus.SKILL_BLOCKED);
		assertNotNull("a fresh account must have at least one SKILL_BLOCKED quest", blocked);

		NextQuestStatus route = routeOf(blocked.name());
		for (int depth : new int[] {0, 1, 3, 218})
		{
			Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(),
				depth);
			assertFalse(blocked.name() + " is SKILL_BLOCKED and must never be a candidate at depth " + depth,
				candidates.containsKey(blocked.name()));
		}
	}

	/**
	 * The one real design decision behind routeDepth, pinned: an ELIGIBLE
	 * quest that is not on the saved route at all has no route position for
	 * a depth to be measured against, so it must remain a candidate even at
	 * the smallest possible non-zero depth.
	 */
	@Test
	public void depthNeverAffectsAnOffRouteCandidate() throws Exception
	{
		QuestGraph graph = graph();
		PlayerState account = fresh();
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<String> eligible = firstNWithStatusAndStart(verdicts, QuestStatus.ELIGIBLE, 2);
		assertTrue("need at least 2 ELIGIBLE quests with a start point for this test to mean anything",
			eligible.size() == 2);

		// Only the first is on the route; the second is deliberately left off it.
		NextQuestStatus route = routeOf(eligible.get(0));
		Map<String, WorldPoint> candidates = QuestStartCandidates.of(graph, route, verdicts, new HashSet<>(), 1);

		assertTrue(eligible.get(1) + " is off-route and must be a candidate regardless of depth",
			candidates.containsKey(eligible.get(1)));
	}
}
