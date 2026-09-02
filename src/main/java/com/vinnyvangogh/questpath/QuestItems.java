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
	/**
	 * How many quests to work through.
	 *
	 * The panel scrolls, so this is about rebuild cost rather than space:
	 * every entry is Swing components thrown away and rebuilt whenever the
	 * account changes. Twelve is a long shopping trip and a cheap rebuild.
	 */
	private static final int QUESTS_SHOWN = 12;

	private final List<QuestNeeds> quests = new ArrayList<>();
	private boolean bankSeen;
	private int hiddenQuests;

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
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen)
	{
		QuestItems out = new QuestItems();
		out.bankSeen = bankSeen;
		QuestClassifier classifier = new QuestClassifier(graph, account);

		for (QuestGraph.Quest quest : graph.quests())
		{
			if (!classifier.classify(quest).doable() || quest.items().isEmpty())
			{
				continue;
			}
			if (out.quests.size() >= QUESTS_SHOWN)
			{
				out.hiddenQuests++;
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

	/**
	 * Whether the counts can be trusted as "you do not have this".
	 *
	 * False means the bank has not been opened this session, so every count
	 * here is inventory and equipment only -- and a row reading "none" means
	 * "not on you", which is a different claim from "not owned".
	 */
	boolean bankSeen()
	{
		return bankSeen;
	}

	/** Doable quests with item lists that did not fit. */
	int hiddenQuests()
	{
		return hiddenQuests;
	}

	/**
	 * A cheap identity for what is rendered, so an unchanged list is not
	 * rebuilt.
	 *
	 * The panel refreshes on every experience drop, and rebuilding a few
	 * hundred Swing components several times a second while training is work
	 * for a picture that did not change.
	 */
	int signature()
	{
		int hash = (bankSeen ? 1 : 0) * 31 + hiddenQuests;
		for (QuestNeeds needs : quests)
		{
			hash = hash * 31 + needs.questName().hashCode();
			for (Line line : needs.lines())
			{
				hash = hash * 31 + line.name().hashCode();
				hash = hash * 31 + Long.hashCode(line.owned());
			}
		}
		return hash;
	}

	boolean isEmpty()
	{
		return quests.isEmpty();
	}
}
