package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Why the account cannot finish one quest yet: what is missing, in a form a
 * popup and a chat line can both print.
 *
 * <p>Pure. Graph, account and quest name in; a warning or null out. A null is
 * "nothing to warn about" -- the quest is finished, unknown to the bundle, or
 * every requirement is met (a gap a boost reaches does not count as unmet).
 */
final class QuestWarning
{
	/**
	 * Quest points a quest asks for. The bundled graph carries skill levels
	 * and prerequisite quests but no quest-point gate, so the few quests with
	 * one are listed here. OSRS Wiki quest pages, "Requirements".
	 */
	private static final Map<String, Integer> QUEST_POINTS_NEEDED = new HashMap<>();

	static
	{
		QUEST_POINTS_NEEDED.put("dragon slayer i", 32);
		QUEST_POINTS_NEEDED.put("heroes' quest", 55);
		QUEST_POINTS_NEEDED.put("legends' quest", 107);
		QUEST_POINTS_NEEDED.put("recipe for disaster", 175);
		QUEST_POINTS_NEEDED.put("dragon slayer ii", 200);
	}

	private final String questName;
	private final int pointsHave;
	private final int pointsNeed;
	private final List<String> quests;
	private final List<QuestBlockers.Gap> gaps;

	private QuestWarning(String questName, int pointsHave, int pointsNeed,
		List<String> quests, List<QuestBlockers.Gap> gaps)
	{
		this.questName = questName;
		this.pointsHave = pointsHave;
		this.pointsNeed = pointsNeed;
		this.quests = quests;
		this.gaps = gaps;
	}

	/**
	 * The quest Quest Helper (or the game) means, in the bundled graph's
	 * spelling, or null when the name is not one this graph knows.
	 *
	 * Case, curly apostrophes and surrounding space are forgiven; nothing
	 * else is guessed. An unknown name is silence, never a wrong warning.
	 */
	static QuestGraph.Quest resolve(QuestGraph graph, String helperName)
	{
		if (graph == null || helperName == null)
		{
			return null;
		}
		String wanted = normalise(helperName);
		if (wanted.isEmpty())
		{
			return null;
		}
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (normalise(quest.name()).equals(wanted))
			{
				return quest;
			}
		}
		return null;
	}

	private static String normalise(String name)
	{
		return name.trim().replace('’', '\'').replace('‘', '\'').toLowerCase(Locale.ROOT);
	}

	/** The warning for this quest, or null when nothing stands in the way. */
	static QuestWarning of(QuestGraph graph, PlayerState account, QuestGraph.Quest quest)
	{
		if (graph == null || account == null || quest == null || account.isComplete(quest.name()))
		{
			return null;
		}

		QuestBlockers blockers = QuestBlockers.of(graph, account, quest);
		List<QuestBlockers.Gap> uncovered = new ArrayList<>();
		for (QuestBlockers.Gap gap : blockers.skills())
		{
			if (!gap.covered())
			{
				uncovered.add(gap);
			}
		}

		Integer need = QUEST_POINTS_NEEDED.get(normalise(quest.name()));
		int have = account.questPoints();
		boolean pointsShort = need != null && have != PlayerState.UNKNOWN_QUEST_POINTS && have < need;

		if (!pointsShort && blockers.quests().isEmpty() && uncovered.isEmpty())
		{
			return null;
		}
		return new QuestWarning(quest.name(), have, pointsShort ? need : 0,
			new ArrayList<>(blockers.quests()), new ArrayList<>(blockers.skills()));
	}

	String questName()
	{
		return questName;
	}

	boolean pointsShort()
	{
		return pointsNeed > 0;
	}

	List<String> quests()
	{
		return Collections.unmodifiableList(quests);
	}

	List<QuestBlockers.Gap> gaps()
	{
		return Collections.unmodifiableList(gaps);
	}

	/** One line per missing thing, the same text for the popup and the chat. */
	List<String> lines()
	{
		List<String> lines = new ArrayList<>();
		if (pointsShort())
		{
			lines.add(pointsNeed + " quest points (you have " + pointsHave + ")");
		}
		if (!quests.isEmpty())
		{
			lines.add("Quests first: " + String.join(", ", quests));
		}
		for (QuestBlockers.Gap gap : gaps)
		{
			String line = gap.need() + " " + gap.skill() + " (you have " + gap.have() + ")";
			if (gap.covered())
			{
				line += ", a boost covers it: " + gap.boost().item();
			}
			lines.add(line);
		}
		return lines;
	}

	/** The headline, short enough for a chat line. */
	String headline()
	{
		return "You can't finish " + questName + " yet:";
	}
}
