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
	private final Map<QuestStatus, List<QuestGraph.Quest>> byStatus = new EnumMap<>(QuestStatus.class);
	private final Map<Long, String> details = new java.util.HashMap<>();
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
		Boosts boosts = new Boosts(graph.boosts());

		for (QuestGraph.Quest quest : graph.quests())
		{
			QuestStatus status = classifier.classify(quest);
			verdicts.byStatus.computeIfAbsent(status, key -> new ArrayList<>()).add(quest);
			if (status == QuestStatus.BOOSTABLE || status == QuestStatus.SKILL_BLOCKED)
			{
				String detail = shortfall(quest, account, boosts);
				if (!detail.isEmpty())
				{
					verdicts.details.put(quest.id(), detail);
				}
			}
		}
		return verdicts;
	}

	/**
	 * Which skills are short for a quest, by how much, and what boosts them.
	 *
	 * <p>"Cooking 38/40 (+2: Summer pie)" for a gap a boost covers, and
	 * "Cooking 30/40 (-10)" for one it does not. Only the quest's own skill
	 * requirements are listed; a quest short a prerequisite as well still
	 * reports its own levels, the same precedence the classifier uses.
	 */
	static String shortfall(QuestGraph.Quest quest, PlayerState account, Boosts boosts)
	{
		List<String> parts = new ArrayList<>();
		for (QuestGraph.Requirement requirement : quest.requirements())
		{
			int have = account.levelIn(requirement.skill());
			int need = requirement.level();
			if (have >= need)
			{
				continue;
			}
			String text = requirement.skill() + " " + have + "/" + need;
			Boosts.Best best = requirement.boostable() ? boosts.bestFor(requirement.skill(), have) : Boosts.NONE;
			if (best.levels() >= need - have)
			{
				text += " (boost +" + best.levels() + ": " + best.item() + ")";
			}
			else
			{
				text += " (" + (have - need) + (best.levels() > 0
					? ", " + best.item() + " only +" + best.levels() : "") + ")";
			}
			parts.add(text);
		}
		return String.join(", ", parts);
	}

	/** The shortfall line for a BOOSTABLE or SKILL_BLOCKED quest, or empty. */
	String detail(QuestGraph.Quest quest)
	{
		String found = details.get(quest.id());
		return found == null ? "" : found;
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
	 * A cheap identity for what is rendered, so an unchanged list is not
	 * rebuilt.
	 *
	 * The panel refreshes on every experience drop, and every group now lists
	 * in full -- around 200 labels. Rebuilding those several times a second
	 * while training is work for a picture that did not change.
	 */
	int signature()
	{
		int hash = rulesKnown ? 1 : 0;
		// Levels moving changes a shortfall without moving any count.
		hash = hash * 31 + details.hashCode();
		for (QuestStatus status : QuestStatus.values())
		{
			hash = hash * 31 + count(status);
		}
		return hash;
	}

	/**
	 * The statuses worth a section when nothing is being searched for.
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

	/**
	 * The statuses worth a section while searching.
	 *
	 * COMPLETE is included here and only here. Hiding it is right for the
	 * default view and wrong for a search: typing the name of a quest you have
	 * finished returned nothing at all, which reads as "no such quest" rather
	 * than "you did that one".
	 */
	static QuestStatus[] searchSections()
	{
		return new QuestStatus[]{
			QuestStatus.ELIGIBLE,
			QuestStatus.BOOSTABLE,
			QuestStatus.SKILL_BLOCKED,
			QuestStatus.QUEST_BLOCKED,
			QuestStatus.BUILD_BLOCKED,
			QuestStatus.UNREACHABLE,
			QuestStatus.COMPLETE,
		};
	}

	/** A heading a player would recognise, rather than the enum's name. */
	static String label(QuestStatus status)
	{
		switch (status)
		{
			case ELIGIBLE:
				// "Can do now", not "Do now". This heading sits under "Can I
				// do this quest?" and lists every quest the account is
				// eligible for -- 53 of them on a mature account, nearly all
				// of which are not on the route and are not being
				// recommended. An imperative reads as an instruction, and
				// directly below the "Next on your route" section it reads as
				// a claim that these are the plan. It is a statement about
				// capability, so it is phrased as one.
				return "Can do now";
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
