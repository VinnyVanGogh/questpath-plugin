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
	/**
	 * How much the counts in this list can be trusted.
	 *
	 * Three states, not two. The old pair -- bank seen or not -- collapsed
	 * two very different situations into the pessimistic one: an account that
	 * syncs every two minutes has a perfectly good bank on record from the
	 * last session, and rendering that as "I can only see what you are
	 * carrying" was honest but nearly always wrong in the unhelpful
	 * direction. Stale is a much smaller problem than blind, provided the
	 * panel says which it is.
	 */
	enum Freshness
	{
		/** The bank has been opened this session; the counts are current. */
		LIVE,
		/**
		 * No bank this session, but questpath had one on record. Counts come
		 * from that plus whatever is being carried right now.
		 */
		LAST_SYNC,
		/**
		 * No bank this session and nothing on record. Every count is
		 * inventory and equipment only, so "none" means "not on you".
		 */
		CARRIED_ONLY
	}

	private final List<QuestNeeds> quests = new ArrayList<>();
	private final List<Total> totals = new ArrayList<>();
	private final List<Pickup> pickups = new ArrayList<>();
	private boolean bankSeen;
	private Freshness freshness = Freshness.CARRIED_ONLY;

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
				if (!line.optional() && !line.unresolved() && line.held())
				{
					count++;
				}
			}
			return count;
		}

		/**
		 * How many lines are required and countable.
		 *
		 * Unresolved lines are excluded from both halves of the ratio rather
		 * than counted as missing. They cannot be ticked -- there is nothing
		 * to tick them against -- so counting them would make "4 of 5" a
		 * number that never reaches 5 however much you buy. They are still
		 * listed and still marked; see unresolvedCount().
		 */
		int needed()
		{
			int count = 0;
			for (Line line : lines)
			{
				if (!line.optional() && !line.unresolved())
				{
					count++;
				}
			}
			return count;
		}

		/** How many lines this quest has that questpath could not identify. */
		int unresolvedCount()
		{
			int count = 0;
			for (Line line : lines)
			{
				if (line.unresolved())
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
		private final boolean unresolved;
		private final List<String> sources;
		private final List<String> shopNames;

		Line(String name, int itemId, int wanted, long owned, boolean optional, List<String> sources)
		{
			this(name, itemId, wanted, owned, optional, false, sources);
		}

		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources)
		{
			this(name, itemId, wanted, owned, optional, unresolved, sources, null);
		}

		/** The full shape: every line the bundle can carry, including where to walk to buy it. */
		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources, List<String> shopNames)
		{
			this.name = name;
			this.itemId = itemId;
			this.wanted = wanted;
			this.owned = owned;
			this.optional = optional;
			this.unresolved = unresolved;
			this.sources = sources;
			this.shopNames = shopNames;
		}

		/**
		 * A wiki line questpath could not match to a real item.
		 *
		 * name() is then the wiki's own words, there is no id, and held() is
		 * meaningless -- nothing can be counted against a phrase. Shown
		 * anyway, marked, because the alternative is a list that is quietly
		 * short while claiming to be the list.
		 */
		boolean unresolved()
		{
			return unresolved;
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

		/**
		 * The shops -- by name, matched against {@link QuestGraph#shop} -- that
		 * sell this. Empty when questpath knows no placeable shop for it, which
		 * a shortest-path caller must render as no route, never as an empty one.
		 */
		List<String> shopNames()
		{
			return shopNames == null ? java.util.Collections.emptyList() : shopNames;
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
	 * The counts to judge against: live where the client can see, questpath's
	 * record where it cannot.
	 *
	 * The larger of the two per item, never the sum and never the record
	 * alone. The client's map is live but blind to the bank; questpath's is
	 * complete but as old as the last sync, and it already includes whatever
	 * was carried then. Adding them would double-count a stack that appears
	 * in both. Taking the larger keeps a live pickup visible immediately
	 * while still remembering a bank the client has never been shown.
	 *
	 * Untouched when the bank has been opened: a live count is the answer,
	 * and letting a stale record raise it would resurrect items that have
	 * since been spent.
	 */
	static Map<String, Long> effectiveHeldForTest(Map<String, Long> held, boolean bankSeen,
		Map<String, Long> knownItems)
	{
		return effectiveHeld(held, bankSeen, knownItems);
	}

	private static Map<String, Long> effectiveHeld(Map<String, Long> held, boolean bankSeen,
		Map<String, Long> knownItems)
	{
		if (bankSeen || knownItems == null || knownItems.isEmpty())
		{
			return held;
		}
		Map<String, Long> merged = new LinkedHashMap<>(held);
		for (Map.Entry<String, Long> entry : knownItems.entrySet())
		{
			String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
			if (key.isEmpty())
			{
				continue;
			}
			merged.merge(key, entry.getValue() == null ? 0L : entry.getValue(), Math::max);
		}
		return merged;
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
		private final long outstanding;
		private final List<String> sources;

		Pickup(String questName, String itemName, int itemId, int alsoWantedBy, long outstanding,
			List<String> sources)
		{
			this.questName = questName;
			this.itemName = itemName;
			this.itemId = itemId;
			this.alsoWantedBy = alsoWantedBy;
			this.outstanding = outstanding;
			this.sources = sources;
		}

		/**
		 * How many are still needed across everything on the plan.
		 *
		 * The row used to say only how many quests wanted it, which answers
		 * "is this worth carrying" and not "how many do I grab" -- and the
		 * second is the one you act on while standing over the thing.
		 */
		long outstanding()
		{
			return outstanding;
		}

		/**
		 * Where else this item comes from -- see Line.sources().
		 *
		 * Carried because "get this while you are here" is useless without it.
		 * A player told to pick up a banana during Recipe for Disaster has no
		 * way to know whether that means it drops in the quest, is sold in a
		 * shop three squares away, or has to be farmed -- and the flag that
		 * put the row on screen says only that the wiki marked it obtainable
		 * during the quest, which is not the same as saying where.
		 */
		List<String> sources()
		{
			return sources == null ? java.util.Collections.emptyList() : sources;
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
		return of(graph, account, held, bankSeen, java.util.Collections.emptySet(),
			java.util.Collections.emptySet());
	}

	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> upcoming)
	{
		return of(graph, account, held, bankSeen, upcoming, java.util.Collections.emptySet());
	}

	/**
	 * @param upcoming the next few quests on the route, in order, undone. The
	 *     pickup list is drawn from these -- see pickupsIn
	 * @param plan quest names on the account's saved questpath route. When it
	 *     has any, it is the scope; when it is empty the list falls back to
	 *     whatever is doable right now.
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> upcoming, java.util.Set<String> plan)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, java.util.Collections.emptyMap());
	}

	/**
	 * @param knownItems what questpath already holds for the account, by
	 *     lowercased name. Used only when the bank has not been opened this
	 *     session, and never to reduce a live count -- see effectiveHeld.
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> upcoming, java.util.Set<String> plan, Map<String, Long> knownItems)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems,
			java.util.Collections.emptyMap());
	}

	/**
	 * @param carried what is in the account's bags and worn right now, by
	 *     lowercased name -- never the bank, and never widened by a previous
	 *     session. Decides which shortfalls are gathering trips already under
	 *     way; see Total.inProgress
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> upcoming, java.util.Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems, carried,
			java.util.Collections.<String>emptySet());
	}

	/**
	 * @param placeholders lowercased names the bank is holding an empty slot
	 *     for. Not owned -- see Total.ranOut for why they are worth knowing
	 *     about anyway
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		java.util.Set<String> upcoming, java.util.Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried, java.util.Set<String> placeholders)
	{
		QuestItems out = new QuestItems();
		out.bankSeen = bankSeen;
		if (bankSeen)
		{
			out.freshness = Freshness.LIVE;
		}
		else if (knownItems != null && !knownItems.isEmpty())
		{
			out.freshness = Freshness.LAST_SYNC;
		}
		else
		{
			out.freshness = Freshness.CARRIED_ONLY;
		}
		held = effectiveHeld(held, bankSeen, knownItems);
		QuestClassifier classifier = new QuestClassifier(graph, account);

		// The plan, not what is doable today.
		//
		// Scoping to doable quests answered "what can I start right now",
		// which is the wrong question at a shop counter. You are standing in
		// front of the stock; the useful list is everything the plan will ever
		// want, so the trip happens once instead of once per quest becoming
		// available. On a real account that is 171 items rather than 113 --
		// 42 more you could have bought while you were already there.
		//
		// Falls back to doable when no route has been synced, because an
		// account questpath has never seen a plan for still deserves a list,
		// and "everything in the game" is not one.
		java.util.Set<String> scope = new java.util.HashSet<>();
		for (String name : plan)
		{
			if (name != null)
			{
				scope.add(name.toLowerCase());
			}
		}

		for (QuestGraph.Quest quest : graph.quests())
		{
			if (quest.items().isEmpty())
			{
				continue;
			}
			QuestStatus status = classifier.classify(quest);
			// A finished quest never needs shopping for, whichever scope is
			// in force.
			if (status == QuestStatus.COMPLETE)
			{
				continue;
			}
			boolean wanted = scope.isEmpty()
				? status.doable()
				: scope.contains(quest.name().toLowerCase());
			if (!wanted)
			{
				continue;
			}

			QuestNeeds needs = new QuestNeeds(quest.name());
			for (QuestGraph.Item item : quest.items())
			{
				long owned = held.getOrDefault(item.name().toLowerCase(), 0L);
				needs.lines.add(new Line(item.name(), item.itemId(), item.quantity(), owned,
					item.optional(), item.unresolved(), item.sources(), item.shopNames()));
			}
			out.quests.add(needs);
		}

		out.totals.addAll(totalsOf(out.quests, held, carried, placeholders));
		out.pickups.addAll(pickupsIn(graph, upcoming, out.totals));
		return out;
	}

	/**
	 * Things you can pick up during the quests you are about to do, that
	 * something later on the plan also wants.
	 *
	 * Driven by the next few quests on the route rather than by what the
	 * client calls "in progress". In progress was the obvious signal and the
	 * wrong one: Recipe for Disaster stays in progress for hundreds of hours,
	 * and an umbrella quest carries the union of its ten subquests' items --
	 * so "grab the ogre bellows while you are here" appeared permanently,
	 * while standing in Draynor, for an item in the Feldip Hills.
	 *
	 * The next few quests are what "here" actually means for a player working
	 * a route: you are about to walk through them, so an item one of them
	 * drops is genuinely on your way, and a later quest wanting it is the
	 * reason to bother.
	 *
	 * This is still not location awareness -- questpath holds no location for
	 * any item, and a quest spans a lot of ground. It is a much better proxy
	 * than a started flag that never clears.
	 */
	private static List<Pickup> pickupsIn(QuestGraph graph, java.util.Set<String> upcoming, List<Total> totals)
	{
		List<Pickup> out = new ArrayList<>();
		if (upcoming == null || upcoming.isEmpty())
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

		java.util.Set<String> lowerUpcoming = new java.util.HashSet<>();
		for (String name : upcoming)
		{
			if (name != null)
			{
				lowerUpcoming.add(name.toLowerCase());
			}
		}

		// An umbrella quest carries the union of its subquests' items, and
		// Recipe for Disaster stays "in progress" for hundreds of hours. So
		// every item from every subquest looked like something to grab right
		// now, attributed to the umbrella -- "Recipe for Disaster: Ogre
		// bellows" while standing in Draynor, when the bellows are in the
		// Feldip Hills inside one specific subquest.
		//
		// Attributing to the most specific quest that lists the item does not
		// make this location-aware, and nothing here can: questpath holds no
		// location for an item. What it does is stop the name being actively
		// misleading. "Recipe for Disaster/Freeing Skrach Uglogwee: Ogre
		// bellows" says which part of a hundred-hour quest, which is enough to
		// know it is not on your way.
		Map<String, QuestGraph.Quest> mostSpecific = new LinkedHashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (!lowerUpcoming.contains(quest.name().toLowerCase()))
			{
				continue;
			}
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.obtainable() || !wanted.containsKey(item.name().toLowerCase()))
				{
					continue;
				}
				String key = item.name().toLowerCase();
				QuestGraph.Quest held = mostSpecific.get(key);
				// Longer name wins: a subquest is its umbrella's name plus a
				// slash and a part, so it is always the longer of the two and
				// always the more specific.
				if (held == null || quest.name().length() > held.name().length())
				{
					mostSpecific.put(key, quest);
				}
			}
		}

		for (QuestGraph.Quest quest : graph.quests())
		{
			if (!lowerUpcoming.contains(quest.name().toLowerCase()))
			{
				continue;
			}
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.obtainable())
				{
					continue;
				}
				String key = item.name().toLowerCase();
				Total total = wanted.get(key);
				if (total == null || mostSpecific.get(key) != quest)
				{
					continue;
				}
				out.add(new Pickup(quest.name(), item.name(), item.itemId(), total.quests(),
					Math.max(0L, total.required() - total.owned()), item.sources()));
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
	private static List<Total> totalsOf(List<QuestNeeds> quests, Map<String, Long> held,
		Map<String, Long> carried, java.util.Set<String> placeholders)
	{
		Map<String, Total> merged = new LinkedHashMap<>();

		for (QuestNeeds needs : quests)
		{
			for (Line line : needs.lines())
			{
				if (line.optional() || line.unresolved())
				{
					// An unresolved line has no item to merge on and no count
					// to tick, so it cannot join a shopping list. It stays on
					// its quest's own list, where the words at least say what
					// the wiki asked for.
					continue;
				}
				Total total = merged.computeIfAbsent(line.name(),
					name -> new Total(name, line.itemId(),
						held.getOrDefault(name.toLowerCase(), 0L),
						carried == null ? 0L : carried.getOrDefault(name.toLowerCase(), 0L),
						placeholders != null && placeholders.contains(name.toLowerCase()),
						line.sources(), line.shopNames()));
				total.add(line, needs.questName());
			}
		}

		List<Total> out = new ArrayList<>(merged.values());
		// Three bands, in the order they are worth acting on.
		//
		// Short first, because what you are missing is the point of the list
		// and a long tail of things you already have would bury it. Then,
		// ahead of everything else short, the ones you are carrying some of
		// already: eleven oak logs with one in your bags is a woodcutting trip
		// you are standing in the middle of, and it is worth more than a
		// perfectly sorted alphabet telling you about ashes you will buy in
		// Varrock next week. Whether the item happens to start with A decides
		// nothing about whether now is the moment to get it; whether one is
		// already in your inventory decides quite a lot.
		//
		// Within that band, closest to done first -- one log outstanding beats
		// five, because finishing something is worth more than starting a
		// second thing.
		out.sort((a, b) ->
		{
			if (a.satisfied() != b.satisfied())
			{
				return a.satisfied() ? 1 : -1;
			}
			if (a.inProgress() != b.inProgress())
			{
				return a.inProgress() ? -1 : 1;
			}
			if (a.inProgress() && a.outstanding() != b.outstanding())
			{
				return Long.compare(a.outstanding(), b.outstanding());
			}
			// Then the ones the bank is keeping a slot for. An empty
			// placeholder is the game saying you had these and used them up,
			// which is a sharper prompt than an item you have never owned --
			// and at a bank it is the row most likely to be a mistake.
			if (a.ranOut() != b.ranOut())
			{
				return a.ranOut() ? -1 : 1;
			}
			return a.name().compareToIgnoreCase(b.name());
		});
		return out;
	}

	/**
	 * Joins names into readable English, naming at most {@code max} of them
	 * and counting the rest.
	 *
	 * "A", "A and B", "A, B and C", "A, B, C and 4 more". Shared by every
	 * surface that has to say what an item is for in the width of a tooltip.
	 */
	static String phrase(List<String> names, int max)
	{
		if (names == null || names.isEmpty())
		{
			return "";
		}
		int named = Math.min(names.size(), max);
		int rest = names.size() - named;
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < named; i++)
		{
			if (i > 0)
			{
				out.append(i == named - 1 && rest == 0 ? " and " : ", ");
			}
			out.append(names.get(i));
		}
		if (rest > 0)
		{
			out.append(" and ").append(rest).append(" more");
		}
		return out.toString();
	}

	/** One item summed across every doable quest that needs it. */
	static class Total
	{
		private final String name;
		private final int itemId;
		private final long owned;
		private final long carried;
		private final boolean placeholder;
		private final List<String> sources;
		private final List<String> shopNames;
		private final List<String> questNames = new ArrayList<>();
		private int wanted;
		private int quests;
		private boolean anyUnstated;

		Total(String name, int itemId, long owned, long carried, boolean placeholder,
			List<String> sources)
		{
			this(name, itemId, owned, carried, placeholder, sources, null);
		}

		/** The full shape: every field a Total can carry, including where to walk to buy it. */
		Total(String name, int itemId, long owned, long carried, boolean placeholder,
			List<String> sources, List<String> shopNames)
		{
			this.name = name;
			this.itemId = itemId;
			this.owned = owned;
			this.carried = carried;
			this.placeholder = placeholder;
			this.sources = sources;
			this.shopNames = shopNames;
		}

		/** Convenience for callers with no placeholder to report -- mainly tests. */
		Total(String name, int itemId, long owned, long carried, List<String> sources)
		{
			this(name, itemId, owned, carried, false, sources);
		}

		/** Convenience for callers with no separate carried count -- mainly tests. */
		Total(String name, int itemId, long owned, List<String> sources)
		{
			this(name, itemId, owned, 0L, sources);
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
		 * The shops -- see {@link Line#shopNames()} -- that sell this. Every
		 * Line folded into a Total shares one item name and therefore one shop
		 * list; this is simply the first one seen.
		 */
		List<String> shopNames()
		{
			return shopNames == null ? java.util.Collections.emptyList() : shopNames;
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
		/** Convenience for callers with no quest to attribute -- mainly tests. */
		void add(Line line)
		{
			add(line, null);
		}

		void add(Line line, String questName)
		{
			quests++;
			if (questName != null && !questName.isEmpty())
			{
				questNames.add(questName);
			}
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

		/**
		 * How many are in your bags or worn right now, bank excluded.
		 *
		 * Zero is a real answer here and not an absence: it means none of what
		 * you own is on you, which is exactly what distinguishes a banked pile
		 * from a trip in progress.
		 */
		long carried()
		{
			return carried;
		}

		/**
		 * Whether this is a gathering trip already under way: some on you,
		 * still not enough.
		 *
		 * The condition is carried rather than owned on purpose. Oak logs
		 * banked from last week say nothing about what you are doing now;
		 * one in your inventory says you are chopping, and that is the moment
		 * to mention that you need eleven.
		 */
		boolean inProgress()
		{
			return carried > 0 && !satisfied();
		}

		/**
		 * Whether the bank is keeping an empty slot for this and you own none.
		 *
		 * A placeholder is the game's own record that you have had something
		 * and spent all of it -- which is a different situation from never
		 * having had it, and a more urgent one at a bank counter. It is not
		 * ownership and is never counted as any, so this only ever describes
		 * a shortfall.
		 */
		boolean ranOut()
		{
			return placeholder && owned <= 0;
		}

		/** How many more to get. Zero once satisfied, never negative. */
		long outstanding()
		{
			return Math.max(0L, required() - owned);
		}

		int quests()
		{
			return quests;
		}

		/**
		 * Which quests want this, in the order they appear on the plan.
		 *
		 * The count on its own answers "why do I need three of these" and
		 * nothing else. Standing in a shop the question is different and
		 * better: what am I buying this *for* -- which is the difference
		 * between a shopping list and a reason to follow it.
		 */
		List<String> questNames()
		{
			return questNames;
		}

		/**
		 * The quests wanting this, phrased for one line of a tooltip.
		 *
		 * Capped at three named, because the point is to answer "what is this
		 * for" and a tooltip listing eleven quest names answers it worse than
		 * one listing three. The remainder is counted rather than dropped, so
		 * the line never claims a shorter list than the truth.
		 */
		String questSummary()
		{
			return phrase(questNames, 3);
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

	/** How much the counts can be trusted. See {@link Freshness}. */
	Freshness freshness()
	{
		return freshness;
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
