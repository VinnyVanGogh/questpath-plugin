package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
	private final List<QuestNeeds> quests = new ArrayList<>();
	private final List<Total> totals = new ArrayList<>();
	private final List<Pickup> pickups = new ArrayList<>();
	private boolean bankSeen;

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
		private final int itemId;
		private final int wanted;
		private final long owned;
		private final boolean optional;
		private final List<String> sources;

		Line(String name, int itemId, int wanted, long owned, boolean optional, List<String> sources)
		{
			this.name = name;
			this.itemId = itemId;
			this.wanted = wanted;
			this.owned = owned;
			this.optional = optional;
			this.sources = sources;
		}

		/** Convenience for callers that do not care about obtain sources -- mainly tests. */
		Line(String name, int wanted, long owned, boolean optional)
		{
			this(name, 0, wanted, owned, optional, null);
		}

		String name()
		{
			return name;
		}

		/**
		 * The game's own item id, or 0 when questpath holds none.
		 *
		 * What the overlays draw the sprite from. Zero means "draw nothing",
		 * never item 0 -- see QuestGraph.Item.itemId().
		 */
		int itemId()
		{
			return itemId;
		}

		/**
		 * Where to get this item, best first, as short display lines carried
		 * verbatim from the bundle. Empty when questpath knows no source --
		 * the panel must render that as no tooltip, never an invented one.
		 */
		List<String> sources()
		{
			return sources == null ? java.util.Collections.emptyList() : sources;
		}

		long owned()
		{
			return owned;
		}

		/** The count the wiki stated, or 0 when it stated none. */
		int wanted()
		{
			return wanted;
		}

		boolean optional()
		{
			return optional;
		}

		/**
		 * How many are needed. A line the wiki gave no count for needs one.
		 *
		 * One is the reading the line itself carries: naming an item without
		 * a number means bring it, and "bring it" is one. Showing "none" for
		 * a line like that -- as an earlier version did -- said nothing about
		 * whether you were short.
		 */
		int required()
		{
			return Math.max(1, wanted);
		}

		/** Whether the account has enough. */
		boolean held()
		{
			return owned >= required();
		}

		/** What to show on the right of the row. */
		String describe()
		{
			return owned + " / " + required();
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
	/** One thing to grab while you are already inside a quest. */
	static class Pickup
	{
		private final String questName;
		private final String itemName;
		private final int itemId;
		private final int alsoWantedBy;

		Pickup(String questName, String itemName, int itemId, int alsoWantedBy)
		{
			this.questName = questName;
			this.itemName = itemName;
			this.itemId = itemId;
			this.alsoWantedBy = alsoWantedBy;
		}

		/** The game's own item id, or 0 when questpath holds none. */
		int itemId()
		{
			return itemId;
		}

		String questName()
		{
			return questName;
		}

		String itemName()
		{
			return itemName;
		}

		/** How many other quests on the list want it. */
		int alsoWantedBy()
		{
			return alsoWantedBy;
		}
	}

	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen)
	{
		return of(graph, account, held, bankSeen, java.util.Collections.emptySet());
	}

	/**
	 * @param inProgress names of quests the client reports as started but not
	 *     finished, so the list can say what is worth grabbing while you are
	 *     already in one
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> inProgress)
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

			QuestNeeds needs = new QuestNeeds(quest.name());
			for (QuestGraph.Item item : quest.items())
			{
				long owned = held.getOrDefault(item.name().toLowerCase(), 0L);
				needs.lines.add(new Line(item.name(), item.itemId(), item.quantity(), owned,
					item.optional(), item.sources()));
			}
			out.quests.add(needs);
		}

		out.totals.addAll(totalsOf(out.quests, held));
		out.pickups.addAll(pickupsIn(graph, inProgress, out.totals));
		return out;
	}

	/**
	 * Things you can pick up inside a quest you are already doing, that
	 * something else on the list also wants.
	 *
	 * Only worth knowing while you are actually there, which is why it is
	 * driven by the quests the client reports as started. Quest Helper's own
	 * selected quest would be the more precise signal and is not readable:
	 * it lives in memory in another plugin, with no config key, no event, and
	 * no shared class to reach it through. What the game itself calls "in
	 * progress" is the honest substitute, and is arguably the better question
	 * anyway -- it is where you are, not where a panel is pointed.
	 */
	private static List<Pickup> pickupsIn(QuestGraph graph, java.util.Set<String> inProgress, List<Total> totals)
	{
		List<Pickup> out = new ArrayList<>();
		if (inProgress == null || inProgress.isEmpty())
		{
			return out;
		}

		Map<String, Total> wanted = new LinkedHashMap<>();
		for (Total total : totals)
		{
			if (!total.satisfied())
			{
				wanted.put(total.name().toLowerCase(), total);
			}
		}
		if (wanted.isEmpty())
		{
			return out;
		}

		java.util.Set<String> lowerInProgress = new java.util.HashSet<>();
		for (String name : inProgress)
		{
			if (name != null)
			{
				lowerInProgress.add(name.toLowerCase());
			}
		}

		for (QuestGraph.Quest quest : graph.quests())
		{
			if (!lowerInProgress.contains(quest.name().toLowerCase()))
			{
				continue;
			}
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.obtainable())
				{
					continue;
				}
				Total total = wanted.get(item.name().toLowerCase());
				if (total == null)
				{
					continue;
				}
				out.add(new Pickup(quest.name(), item.name(), item.itemId(), total.quests()));
			}
		}
		return out;
	}

	/**
	 * One shopping list across every quest, rather than a list per quest.
	 *
	 * Coins wanted by two quests is one trip to the bank for the sum, not two
	 * separate lines to add up in your head. This is the view you read before
	 * setting off; the per-quest lists below are what you check when you get
	 * back.
	 *
	 * Optional lines are excluded. They are advice, not shopping, and folding
	 * them in would inflate every total with things nobody has to bring.
	 */
	private static List<Total> totalsOf(List<QuestNeeds> quests, Map<String, Long> held)
	{
		Map<String, Total> merged = new LinkedHashMap<>();

		for (QuestNeeds needs : quests)
		{
			for (Line line : needs.lines())
			{
				if (line.optional())
				{
					continue;
				}
				Total total = merged.computeIfAbsent(line.name(),
					name -> new Total(name, line.itemId(),
						held.getOrDefault(name.toLowerCase(), 0L), line.sources()));
				total.add(line);
			}
		}

		List<Total> out = new ArrayList<>(merged.values());
		// Short first: what you are missing is the point of the list, and a
		// long tail of things you already have would bury it.
		out.sort((a, b) ->
		{
			if (a.satisfied() != b.satisfied())
			{
				return a.satisfied() ? 1 : -1;
			}
			return a.name().compareToIgnoreCase(b.name());
		});
		return out;
	}

	/** One item summed across every doable quest that needs it. */
	static class Total
	{
		private final String name;
		private final int itemId;
		private final long owned;
		private final List<String> sources;
		private int wanted;
		private int quests;
		private boolean anyUnstated;

		Total(String name, int itemId, long owned, List<String> sources)
		{
			this.name = name;
			this.itemId = itemId;
			this.owned = owned;
			this.sources = sources;
		}

		/** Convenience for callers that do not care about obtain sources -- mainly tests. */
		Total(String name, long owned)
		{
			this(name, 0, owned, null);
		}

		/**
		 * The game's own item id, or 0 when questpath holds none. Every Line
		 * folded into a Total shares one item name and therefore one id; this
		 * is the first one seen.
		 */
		int itemId()
		{
			return itemId;
		}

		/**
		 * Where to get this item -- see Line.sources(). Every Line folded
		 * into a Total shares one item name, so they all carry the same
		 * source list; this is simply the first one seen.
		 */
		List<String> sources()
		{
			return sources == null ? java.util.Collections.emptyList() : sources;
		}

		/**
		 * Folds one quest's line into the total.
		 *
		 * Stated counts add up; unstated ones do not. That asymmetry is the
		 * whole point: the wiki states a number for what a quest consumes
		 * ("25 bones", "10 bananas", "60 coins") and omits it for what a quest
		 * merely requires you to have ("a spade", "a ghostspeak amulet", "a
		 * pestle and mortar"). Adding one per quest for the second kind
		 * produced "Ghostspeak amulet x3" -- three quests want one, and one is
		 * all anybody needs.
		 *
		 * It is a heuristic on the wiki's own habit, not a fact about the
		 * game, and it will be wrong for a consumable nobody counted.
		 */
		void add(Line line)
		{
			quests++;
			if (line.wanted() > 0)
			{
				wanted += line.wanted();
			}
			else
			{
				anyUnstated = true;
			}
		}

		String name()
		{
			return name;
		}

		long owned()
		{
			return owned;
		}

		int quests()
		{
			return quests;
		}

		/**
		 * How many to bring: every stated count added up, plus one if any
		 * quest wants it without saying how many.
		 *
		 * The unstated part contributes one no matter how many quests want it,
		 * because what the wiki leaves uncounted is almost always a tool you
		 * keep rather than something you spend.
		 */
		int required()
		{
			return wanted + (anyUnstated ? 1 : 0);
		}

		boolean satisfied()
		{
			return owned >= required();
		}

		String describe()
		{
			return owned + " / " + required();
		}

		/** Whether more than one quest wants this, for the row's tooltip. */
		boolean sharedAcrossQuests()
		{
			return quests > 1;
		}
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

	/** Every needed item, summed across every doable quest. */
	List<Total> totals()
	{
		return totals;
	}

	/** Things worth grabbing inside a quest already under way. */
	List<Pickup> pickups()
	{
		return pickups;
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
		int hash = (bankSeen ? 1 : 0) * 31 + pickups.size();
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
