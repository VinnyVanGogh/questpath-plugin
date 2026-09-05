package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><b>{@code routeDepth} narrows positions, not eligibility.</b> {@link
 * QuestpathConfig#questStartArrivalDepth} lets the player cap how far down
 * their saved route this method even looks -- {@code routeDepth} route
 * steps, counted from the route's own head, whether or not each one turns
 * out startable. A depth of 1 means only {@code route.upcoming().get(0)} is
 * ever considered, however many further steps are themselves eligible; a
 * {@code SKILL_BLOCKED} step sitting at position 0 is still excluded by the
 * {@code startable} filter, exactly as it always was, so narrowing the
 * position window can never let a blocked quest through. It only ever
 * removes from what {@link #of} would otherwise offer, never adds. <b>It
 * does not touch off-route candidates at all</b> -- an {@code ELIGIBLE} or
 * {@code BOOSTABLE} quest that is not on the saved route has no "position"
 * for a depth to be measured against, so every one of those is offered
 * regardless of {@code routeDepth}'s value. A depth of {@code 0} means no
 * limit: every outstanding route step is checked, which is this method's
 * behaviour from before this parameter existed.
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
	 * @param routeDepth how many of the route's outstanding steps, counted
	 *     from the head, are even considered -- see this class's own doc for
	 *     what that does and, just as importantly, what it leaves alone.
	 *     {@code 0} or negative means no limit: every outstanding route step
	 *     is considered, same as before this parameter existed.
	 * @return quest name to its start point, for every quest the account can
	 *     actually begin and has not already begun. Never null.
	 */
	static Map<String, WorldPoint> of(QuestGraph graph, NextQuestStatus route, QuestVerdicts verdicts,
		Set<String> inProgressQuestNames, int routeDepth)
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

		// Route steps at or past routeDepth are dropped from the final result
		// outright, not merely left off this loop's insertion order: startable
		// is a flat set with no notion of route position, so without this,
		// the unconditional pass over startable below would simply re-add a
		// too-far route step anyway, the moment it noticed the step was
		// otherwise eligible -- silently undoing the whole cap. Only route
		// step names go in here; an off-route quest is never a route step at
		// any position, so it can never be excluded by this and always
		// survives to the final pass regardless of routeDepth.
		Set<String> routeStepsBeyondDepth = new HashSet<>();

		Map<String, WorldPoint> candidates = new LinkedHashMap<>();
		if (route != null)
		{
			List<NextQuestStatus.Step> upcoming = route.upcoming();
			int limit = routeDepth <= 0 ? upcoming.size() : Math.min(routeDepth, upcoming.size());
			for (int i = 0; i < limit; i++)
			{
				String questName = upcoming.get(i).questName();
				if (startable.contains(questName))
				{
					addStartCandidate(graph, candidates, questName);
				}
			}
			for (int i = limit; i < upcoming.size(); i++)
			{
				routeStepsBeyondDepth.add(upcoming.get(i).questName());
			}
		}
		for (String name : startable)
		{
			if (routeStepsBeyondDepth.contains(name))
			{
				continue;
			}
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
