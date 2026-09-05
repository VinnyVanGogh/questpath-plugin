package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Which quests are worth checking the player's distance to this refresh --
 * the precomputed set {@link QuestStartArrivalWatcher} is handed once per
 * account-state change, per {@code QuestpathPlugin#questStartCandidates}'s
 * own field doc.
 *
 * <p>Pure and client-free, the same separation {@link QuestClassifier} and
 * {@link QuestVerdicts} keep, so the fix this class exists for can be pinned
 * without a running game: an earlier version of {@code QuestpathPlugin} added
 * every step of the saved route as a candidate with <b>no eligibility check
 * at all</b>, then separately added every {@link QuestStatus#ELIGIBLE} quest.
 * Shadow of the Storm sat on the route on an account well short of its
 * levels, so it became a candidate anyway and started the moment the player
 * walked near it -- {@code QuestHelperBridge#availability} answers "can Quest
 * Helper run this", which said nothing about whether this account was
 * allowed to.
 *
 * <p><b>Two filters, not one.</b> {@link #of} keeps only quests {@code
 * verdicts} calls {@link QuestStatus#ELIGIBLE} or {@link
 * QuestStatus#BOOSTABLE} -- startable right now, boost included, because the
 * classifier has already confirmed a boost genuinely closes the shortfall
 * rather than merely being permitted on the requirement. Every other verdict
 * -- {@code SKILL_BLOCKED}, {@code QUEST_BLOCKED}, {@code BUILD_BLOCKED},
 * {@code UNREACHABLE}, {@code COMPLETE}, {@code REFUSED} -- must never
 * appear here. Separately, {@code inProgressQuestNames} removes anything the
 * client already reports as begun: {@link QuestClassifier} judges a quest's
 * own levels and prerequisites, not whether the quest itself has already
 * been started, so an in-progress quest that still meets every requirement
 * classifies exactly as a fresh one would and would otherwise keep
 * re-arriving every time the player walked back through its start area.
 */
final class QuestStartCandidates
{
	private QuestStartCandidates()
	{
	}

	/**
	 * @param graph the bundled quest graph, or null when it failed to load --
	 *     nothing can be a candidate without start points to check
	 * @param route the account's saved route, or null when none is saved or
	 *     the client has not loaded quest states yet
	 * @param verdicts this refresh's judgement of every quest in the graph,
	 *     or null when the account is not known yet (no rules received, no
	 *     snapshot read). Null returns no candidates at all rather than
	 *     falling back to "every route step" -- an unjudged account is
	 *     exactly the state the bug above shipped in
	 * @param inProgressQuestNames quest names the client reports as started
	 *     but not finished, per {@code QuestpathPlugin#inProgressQuestNames}.
	 *     Never null; pass an empty set when nothing is known.
	 * @return quest name to its start point, for every quest the account can
	 *     actually begin and has not already begun. Never null.
	 */
	static Map<String, WorldPoint> of(QuestGraph graph, NextQuestStatus route, QuestVerdicts verdicts,
		Set<String> inProgressQuestNames)
	{
		if (graph == null || verdicts == null)
		{
			return Collections.emptyMap();
		}

		Set<String> startable = new HashSet<>();
		for (QuestGraph.Quest quest : verdicts.quests(QuestStatus.ELIGIBLE))
		{
			startable.add(quest.name());
		}
		for (QuestGraph.Quest quest : verdicts.quests(QuestStatus.BOOSTABLE))
		{
			startable.add(quest.name());
		}
		startable.removeAll(inProgressQuestNames);

		Map<String, WorldPoint> candidates = new LinkedHashMap<>();
		if (route != null)
		{
			for (NextQuestStatus.Step step : route.upcoming())
			{
				if (startable.contains(step.questName()))
				{
					addStartCandidate(graph, candidates, step.questName());
				}
			}
		}
		for (String name : startable)
		{
			addStartCandidate(graph, candidates, name);
		}
		return candidates;
	}

	/** Adds questName's start point to candidates if the bundle knows one and it is not already there. */
	private static void addStartCandidate(QuestGraph graph, Map<String, WorldPoint> candidates, String questName)
	{
		if (questName == null || candidates.containsKey(questName))
		{
			return;
		}
		QuestGraph.Quest quest = graph.byName(questName);
		if (quest == null || !quest.hasStart())
		{
			return;
		}
		// Plane is always 0: questpath's export carries none, so every start
		// point is treated as ground level -- see QuestStartArrivalWatcher's
		// class doc for why that means a player on a different plane
		// correctly never reads as arrived, rather than firing confidently
		// for the wrong floor.
		candidates.put(questName, new WorldPoint(quest.startX(), quest.startY(), 0));
	}
}
