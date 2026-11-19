package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The route when no questpath server is there to supply one: the quests of
 * the chosen types this account can do, in an order that respects
 * prerequisites.
 *
 * <p>Deliberately modest. It is built from the verdicts the bundled planner
 * port already produces, so a quest that a build rule or a capped skill
 * forbids never appears, and one that needs training first is left off until
 * the training is done rather than guessed at. Order is prerequisites first,
 * then alphabetical, which is stable between runs: a route that reshuffles
 * every refresh is a route nobody can follow. questpath's own planner
 * (optimising for cost and time) remains the richer answer for anyone who
 * runs it.
 */
final class LocalRoute
{
	private LocalRoute()
	{
	}

	/** Statuses whose quests belong on a route: doable now, or doable after a prerequisite. */
	private static boolean routable(QuestStatus status)
	{
		return status.doable() || status == QuestStatus.QUEST_BLOCKED;
	}

	/** Quest names in the order to do them. Empty, never null. */
	static List<String> of(QuestGraph graph, PlayerState account, Set<LocalProfile.QuestType> types)
	{
		if (graph == null || account == null || types == null || types.isEmpty())
		{
			return new ArrayList<>();
		}

		Map<Long, QuestGraph.Quest> everything = new HashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			everything.put(quest.id(), quest);
		}
		List<QuestGraph.Quest> pending = candidates(graph, account, types);
		Set<Long> placed = new HashSet<>();
		List<String> route = new ArrayList<>();

		// Whatever is never placed has a prerequisite that is neither done
		// nor routable (skill-blocked, another type, forbidden). It is left off.
		while (placeNext(pending, everything, placed, route, account))
		{
			// one quest per pass
		}
		return route;
	}

	/** Routable quests of the chosen types, alphabetical so ties break the same way every time. */
	private static List<QuestGraph.Quest> candidates(QuestGraph graph, PlayerState account,
		Set<LocalProfile.QuestType> types)
	{
		QuestVerdicts verdicts = QuestVerdicts.of(graph, account);
		List<QuestGraph.Quest> found = new ArrayList<>();
		for (QuestStatus status : QuestStatus.values())
		{
			if (routable(status))
			{
				for (QuestGraph.Quest quest : verdicts.quests(status))
				{
					if (types.contains(typeOf(quest)))
					{
						found.add(quest);
					}
				}
			}
		}
		found.sort(Comparator.comparing(QuestGraph.Quest::name));
		return found;
	}

	private static LocalProfile.QuestType typeOf(QuestGraph.Quest quest)
	{
		return quest.members() ? LocalProfile.QuestType.MEMBERS : LocalProfile.QuestType.FREE_TO_PLAY;
	}

	/** Moves the first quest whose prerequisites are all met onto the route; false when none can. */
	private static boolean placeNext(List<QuestGraph.Quest> pending, Map<Long, QuestGraph.Quest> everything,
		Set<Long> placed, List<String> route, PlayerState account)
	{
		for (int i = 0; i < pending.size(); i++)
		{
			QuestGraph.Quest quest = pending.get(i);
			if (prerequisitesMet(quest, everything, placed, account))
			{
				placed.add(quest.id());
				route.add(quest.name());
				pending.remove(i);
				return true;
			}
		}
		return false;
	}

	private static boolean prerequisitesMet(QuestGraph.Quest quest, Map<Long, QuestGraph.Quest> everything,
		Set<Long> placed, PlayerState account)
	{
		for (long id : quest.prerequisites())
		{
			QuestGraph.Quest prerequisite = everything.get(id);
			if (prerequisite == null)
			{
				return false;
			}
			if (!placed.contains(id) && !account.isComplete(prerequisite.name()))
			{
				return false;
			}
		}
		return true;
	}
}
