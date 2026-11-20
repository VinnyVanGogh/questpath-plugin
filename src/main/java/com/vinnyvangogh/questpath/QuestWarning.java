package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What stands between the account and one quest; null from {@link #of} means nothing does. */
final class QuestWarning
{
	/** Quest-point gates the bundled graph lacks (OSRS Wiki quest pages). */
	private static final Map<String, Integer> POINTS = Map.of("dragon slayer i", 32, "heroes' quest", 55,
		"legends' quest", 107, "recipe for disaster", 175, "dragon slayer ii", 200);

	private final String questName;
	private final List<String> lines;

	private QuestWarning(String questName, List<String> lines)
	{
		this.questName = questName;
		this.lines = lines;
	}

	/** The graph quest a name means (case and curly apostrophes forgiven), or null if unknown. */
	static QuestGraph.Quest resolve(QuestGraph graph, String name)
	{
		if (graph == null || name == null)
		{
			return null;
		}
		String wanted = normalise(name);
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
		return name.trim().replace('’', '\'').toLowerCase(Locale.ROOT);
	}

	/** The warning, or null when the quest is done or every requirement is met or boost-covered. */
	static QuestWarning of(QuestGraph graph, PlayerState account, QuestGraph.Quest quest)
	{
		if (graph == null || account == null || quest == null || account.isComplete(quest.name()))
		{
			return null;
		}
		List<String> lines = new ArrayList<>();
		Integer need = POINTS.get(normalise(quest.name()));
		int have = account.questPoints();
		boolean pointsShort = need != null && have != PlayerState.UNKNOWN_QUEST_POINTS && have < need;
		if (pointsShort)
		{
			lines.add(need + " quest points (you have " + have + ")");
		}
		QuestBlockers blockers = QuestBlockers.of(graph, account, quest);
		if (!blockers.quests().isEmpty())
		{
			lines.add("Quests first: " + String.join(", ", blockers.quests()));
		}
		boolean uncovered = false;
		for (QuestBlockers.Gap gap : blockers.skills())
		{
			uncovered |= !gap.covered();
			lines.add(gap.need() + " " + gap.skill() + " (you have " + gap.have() + ")"
				+ (gap.covered() ? ", covered by " + gap.boost().item() : ""));
		}
		boolean unmet = pointsShort || uncovered || !blockers.quests().isEmpty();
		return unmet ? new QuestWarning(quest.name(), lines) : null;
	}

	String questName()
	{
		return questName;
	}

	List<String> lines()
	{
		return lines;
	}

	String headline()
	{
		return "You can't finish " + questName + " yet:";
	}
}
