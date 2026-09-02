package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the Quests tab shows: every quest, judged, grouped by what to do about
 * it.
 *
 * <p>Grouped rather than listed because 218 rows is a scroll, and the grouping
 * is the answer. "Do these now", "these need levels", "these are off the table
 * while your build stands" are three different actions, and the last one is
 * the one no other quest tool tells you.
 */
class QuestVerdicts
{
	/** How many quests to name per group before saying how many more there are. */
	private static final int SHOWN_PER_GROUP = 8;

	private final Map<QuestStatus, List<QuestGraph.Quest>> byStatus = new EnumMap<>(QuestStatus.class);
	private final boolean rulesKnown;

	private QuestVerdicts(boolean rulesKnown)
	{
		this.rulesKnown = rulesKnown;
	}

	/**
	 * Judges every quest in the graph.
	 *
	 * @param rulesKnown whether the account's build rules have been received.
	 *     Without them nothing can be BUILD_BLOCKED, so every verdict is "as
	 *     far as I know" rather than an answer, and the panel has to say so.
	 */
	static QuestVerdicts of(QuestGraph graph, PlayerState account)
	{
		QuestVerdicts verdicts = new QuestVerdicts(account.hasRules());
		QuestClassifier classifier = new QuestClassifier(graph, account);

		for (QuestGraph.Quest quest : graph.quests())
		{
			QuestStatus status = classifier.classify(quest);
			verdicts.byStatus.computeIfAbsent(status, key -> new ArrayList<>()).add(quest);
		}
		return verdicts;
	}

	/**
	 * Whether the verdicts are complete.
	 *
	 * False means no build rules were available, so nothing could be judged
	 * against them -- a pure's quest list rendered as if it were a main's.
	 */
	boolean rulesKnown()
	{
		return rulesKnown;
	}

	int count(QuestStatus status)
	{
		return quests(status).size();
	}

	List<QuestGraph.Quest> quests(QuestStatus status)
	{
		List<QuestGraph.Quest> found = byStatus.get(status);
		return found == null ? Collections.emptyList() : found;
	}

	/**
	 * The first few quests of a status, for a panel that cannot show 57 of
	 * them.
	 */
	List<QuestGraph.Quest> sample(QuestStatus status)
	{
		List<QuestGraph.Quest> all = quests(status);
		return all.size() <= SHOWN_PER_GROUP ? all : all.subList(0, SHOWN_PER_GROUP);
	}

	/**
	 * How many of a status were not shown.
	 *
	 * Reported rather than silently truncated: a list that stops at eight
	 * without saying so reads as a complete list of eight.
	 */
	int hidden(QuestStatus status)
	{
		return Math.max(0, count(status) - SHOWN_PER_GROUP);
	}

	/**
	 * The statuses worth a section, in the order a player acts on them.
	 *
	 * COMPLETE is omitted: it is the largest group and the least useful, and
	 * the count is already on the summary line. REFUSED is omitted because
	 * the plugin has no way to learn it -- that flag lives in questpath.
	 */
	static QuestStatus[] sections()
	{
		return new QuestStatus[]{
			QuestStatus.ELIGIBLE,
			QuestStatus.BOOSTABLE,
			QuestStatus.SKILL_BLOCKED,
			QuestStatus.QUEST_BLOCKED,
			QuestStatus.BUILD_BLOCKED,
			QuestStatus.UNREACHABLE,
		};
	}

	/** A heading a player would recognise, rather than the enum's name. */
	static String label(QuestStatus status)
	{
		switch (status)
		{
			case ELIGIBLE:
				return "Do now";
			case BOOSTABLE:
				return "With a boost";
			case SKILL_BLOCKED:
				return "Needs levels";
			case QUEST_BLOCKED:
				return "Needs other quests";
			case BUILD_BLOCKED:
				return "Breaks your build";
			case UNREACHABLE:
				return "Blocked by a prerequisite";
			case COMPLETE:
				return "Done";
			case REFUSED:
				return "Refused";
			default:
				return status.name();
		}
	}
}
