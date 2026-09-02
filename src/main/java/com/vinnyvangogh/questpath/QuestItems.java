package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the next quests need, against what is actually in the bank.
 *
 * <p>This is the plugin's whole reason to exist. The wiki knows what a quest
 * needs and where to buy it; the hiscores know your levels; WikiSync knows
 * your quests. None of them can see a bank. The client can, so this is the one
 * answer no external tool can give.
 */
class QuestItems
{
	/** How many quests to work through. More is a scroll, not an answer. */
	private static final int QUESTS_SHOWN = 3;

	private final List<QuestNeeds> quests = new ArrayList<>();

	/** One quest's item list, each line ticked against what is held. */
	static class QuestNeeds
	{
		private final String questName;
		private final List<Line> lines = new ArrayList<>();

		QuestNeeds(String questName)
		{
			this.questName = questName;
		}

		String questName()
		{
			return questName;
		}

		List<Line> lines()
		{
			return lines;
		}

		/** How many required lines are satisfied. Optional ones do not count. */
		int have()
		{
			int count = 0;
			for (Line line : lines)
			{
				if (!line.optional() && line.held())
				{
					count++;
				}
			}
			return count;
		}

		/** How many lines are required at all. */
		int needed()
		{
			int count = 0;
			for (Line line : lines)
			{
				if (!line.optional())
				{
					count++;
				}
			}
			return count;
		}

		boolean ready()
		{
			return needed() > 0 && have() == needed();
		}
	}

	/** One item line, and whether the account holds it. */
	static class Line
	{
		private final String name;
		private final int wanted;
		private final long owned;
		private final boolean optional;

		Line(String name, int wanted, long owned, boolean optional)
		{
			this.name = name;
			this.wanted = wanted;
			this.owned = owned;
			this.optional = optional;
		}

		String name()
		{
			return name;
		}

		long owned()
		{
			return owned;
		}

		boolean optional()
		{
			return optional;
		}

		/**
		 * Whether the account has enough.
		 *
		 * A line with no stated count is satisfied by holding any at all: the
		 * wiki did not say how many, and inventing a threshold would fail a
		 * line for not meeting a number nobody set.
		 */
		boolean held()
		{
			return wanted > 0 ? owned >= wanted : owned > 0;
		}

		/** What to show on the right of the row. */
		String describe()
		{
			if (wanted > 0)
			{
				return owned + " / " + wanted;
			}
			return owned > 0 ? String.valueOf(owned) : "none";
		}
	}

	private QuestItems()
	{
	}

	/**
	 * Builds the list for the quests this account can actually start.
	 *
	 * Doable quests only. A shopping list for a quest whose prerequisites are
	 * unfinished is a list of things to buy and then leave in the bank for a
	 * fortnight.
	 *
	 * @param held quantities by lowercased item name, from the client's own
	 *     bank, inventory and equipment
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held)
	{
		QuestItems out = new QuestItems();
		QuestClassifier classifier = new QuestClassifier(graph, account);

		for (QuestGraph.Quest quest : graph.quests())
		{
			if (out.quests.size() >= QUESTS_SHOWN)
			{
				break;
			}
			if (!classifier.classify(quest).doable() || quest.items().isEmpty())
			{
				continue;
			}

			QuestNeeds needs = new QuestNeeds(quest.name());
			for (QuestGraph.Item item : quest.items())
			{
				long owned = held.getOrDefault(item.name().toLowerCase(), 0L);
				needs.lines.add(new Line(item.name(), item.quantity(), owned, item.optional()));
			}
			out.quests.add(needs);
		}
		return out;
	}

	List<QuestNeeds> quests()
	{
		return quests;
	}

	boolean isEmpty()
	{
		return quests.isEmpty();
	}
}
