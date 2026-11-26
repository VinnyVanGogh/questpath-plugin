package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

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
	private final List<Total> allTotals = new ArrayList<>();
	private final List<Total> recommendedTotals = new ArrayList<>();
	private final List<Pickup> pickups = new ArrayList<>();
	private boolean bankSeen;
	private GroupStorageView storage = GroupStorageView.NONE;
	private Freshness freshness = Freshness.CARRIED_ONLY;
	private boolean graphCarriesRecommended;
	private long recommendedCoins;
	private int magicLevel = 99;

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

		/** How many required lines are satisfied. Optional ones and recommended ones do not count. */
		int have()
		{
			int count = 0;
			for (Line line : lines)
			{
				if (!line.optional() && !line.unresolved() && !line.recommended() && line.held())
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
				if (!line.optional() && !line.unresolved() && !line.recommended())
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
				if (!line.recommended() && line.unresolved())
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
		private final List<String> dropSourceNames;
		private final String groupName;
		private final List<String> notes;
		private final boolean recommended;
		private PlanNote plan = PlanNote.NONE;
		private long equivalentOwned;
		private long inGroupStorage;
		private List<String> members = Collections.emptyList();

		/**
		 * Every tier of the category this line came from, in the wiki's order
		 * -- empty for a line that named one item outright.
		 *
		 * <p>{@link #owned()} already counts all of them; this is what lets a
		 * surface say so, and lets {@link #totalsOf} count them too instead of
		 * only the one tier {@link #name()} happens to be.
		 */
		List<String> members()
		{
			return members;
		}

		/** Records every tier of the category. Returns this line, for chaining. */
		Line withMembers(List<String> names)
		{
			this.members = names == null ? Collections.emptyList() : names;
			return this;
		}

		/**
		 * Attaches questpath's decision for this line -- see {@link PlanNote}.
		 * Returns this line, for chaining at construction.
		 */
		Line withPlan(PlanNote note)
		{
			this.plan = note == null ? PlanNote.NONE : note;
			return this;
		}

		/** questpath's decision for this line; {@link PlanNote#NONE} when it made none. */
		PlanNote plan()
		{
			return plan;
		}

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
			this(name, itemId, wanted, owned, optional, unresolved, sources, shopNames, null);
		}

		/** The full shape, including the monsters that drop it. */
		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources, List<String> shopNames, List<String> dropSourceNames)
		{
			this(name, itemId, wanted, owned, optional, unresolved, sources, shopNames,
				dropSourceNames, "");
		}

		/**
		 * The full shape plus the category this line came from, when it came
		 * from one.
		 *
		 * <p>name is then the member questpath chose, not the wiki's phrase,
		 * because a member is something the player can search for and tick
		 * against a bank. groupName keeps the phrase so the panel can still
		 * say what the choice was made out of.
		 */
		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources, List<String> shopNames, List<String> dropSourceNames,
			String groupName)
		{
			this(name, itemId, wanted, owned, optional, unresolved, sources, shopNames,
				dropSourceNames, groupName, null);
		}

		/**
		 * The full shape plus the wiki's own caveats for this line.
		 *
		 * <p>Carried rather than interpreted -- see QuestGraph.Item#notes for
		 * why, and for the iron bars that went unbought because these used to
		 * stop at the bundle boundary.
		 */
		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources, List<String> shopNames, List<String> dropSourceNames,
			String groupName, List<String> notes)
		{
			this(name, itemId, wanted, owned, optional, unresolved, sources, shopNames,
				dropSourceNames, groupName, notes, false);
		}

		Line(String name, int itemId, int wanted, long owned, boolean optional, boolean unresolved,
			List<String> sources, List<String> shopNames, List<String> dropSourceNames,
			String groupName, List<String> notes, boolean recommended)
		{
			this.groupName = groupName;
			this.notes = notes;
			this.name = name;
			this.itemId = itemId;
			this.wanted = wanted;
			this.owned = owned;
			this.optional = optional;
			this.unresolved = unresolved;
			this.sources = sources;
			this.shopNames = shopNames;
			this.dropSourceNames = dropSourceNames;
			this.recommended = recommended;
		}

		/**
		 * The monsters that drop this, for an item no shop sells.
		 *
		 * <p>Bat bones are the case: nothing stocks them, so the shopping
		 * trip used to step over the item entirely and say nothing about
		 * where to get it. Matched against the bundle's drop locations.
		 */
		List<String> dropSourceNames()
		{
			return dropSourceNames == null ? Collections.emptyList() : dropSourceNames;
		}

		/**
		 * A wiki line questpath could not match to a real item.
		 *
		 * name() is then the wiki's own words, there is no id, and held() is
		 * meaningless -- nothing can be counted against a phrase. Shown
		 * anyway, marked, because the alternative is a list that is quietly
		 * short while claiming to be the list.
		 */
		/** The wiki's own parentheticals for this line, verbatim. */
		List<String> notes()
		{
			return notes == null ? Collections.emptyList() : notes;
		}

		/**
		 * The category this line came from -- "Pickaxe" -- or empty when it
		 * named an item outright. {@link #name()} is the chosen member.
		 */
		String groupName()
		{
			return groupName == null ? "" : groupName;
		}

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
			return sources == null ? Collections.emptyList() : sources;
		}

		/**
		 * The shops -- by name, matched against {@link QuestGraph#shop} -- that
		 * sell this. Empty when questpath knows no placeable shop for it, which
		 * a shortest-path caller must render as no route, never as an empty one.
		 */
		List<String> shopNames()
		{
			return shopNames == null ? Collections.emptyList() : shopNames;
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

		boolean recommended()
		{
			return recommended;
		}

		long equivalentOwned()
		{
			return equivalentOwned;
		}

		void setEquivalentOwned(long equivalentOwned)
		{
			this.equivalentOwned = equivalentOwned;
		}

		/**
		 * How much of {@link #owned()} is in group storage rather than on the
		 * account. Zero for anyone who is not a group ironman.
		 */
		long inGroupStorage()
		{
			return inGroupStorage;
		}

		/** Whether the account needs to fetch from group storage to have enough. */
		boolean needsGroupStorage()
		{
			return storageNeeded(inGroupStorage, owned, equivalentOwned, required());
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
			return owned + equivalentOwned >= required();
		}

		/** What to show on the right of the row. */
		String describe()
		{
			return (owned + equivalentOwned) + " / " + required()
				+ (needsGroupStorage() ? STORAGE_SUFFIX : "");
		}
	}

	/** Appended to a count that leans on group storage. */
	static final String STORAGE_SUFFIX = ", in group storage";

	/**
	 * Whether what is on the account (bank, bags, worn) falls short of
	 * required and storage is what makes up the difference -- the only case
	 * worth sending the player to the storage chest for.
	 */
	private static boolean storageNeeded(long inStorage, long owned, long equivalent, long required)
	{
		return inStorage > 0 && (owned - inStorage) + equivalent < required;
	}

	/**
	 * What questpath's item plan said about one line, reduced to what the
	 * list renders and routes by -- see {@link ItemPlan}. Nothing in here is
	 * decided by the plugin: the choice and the make steps are questpath's,
	 * and the only local input is which make steps' materials are still in
	 * hand ({@link ItemPlan.Item#makeQuantityGiven}).
	 */
	static final class PlanNote
	{
		/** No decision: questpath sent no plan, or nothing about this line. */
		static final PlanNote NONE = new PlanNote(null, 0L, Collections.<String>emptyList());

		private final ItemPlan.Item decided;
		private final long makeQuantity;
		private final List<String> makeInstructions;

		PlanNote(ItemPlan.Item decided, long makeQuantity, List<String> makeInstructions)
		{
			this.decided = decided;
			this.makeQuantity = makeQuantity;
			this.makeInstructions = makeInstructions;
		}

		/** Whether questpath decided anything for this line. */
		boolean decided()
		{
			return decided != null;
		}

		/** How many the still-possible make steps cover. */
		long makeQuantity()
		{
			return makeQuantity;
		}

		/** The still-possible make steps, in questpath's words. */
		List<String> makeInstructions()
		{
			return makeInstructions;
		}

		/** Whether every member of an "X or Y" line is out of reach. */
		boolean unobtainable()
		{
			return decided != null && decided.unobtainable();
		}

		/**
		 * One sentence for a tooltip, or null when there is nothing to say:
		 * the make steps, the choice among members, or why nothing was made.
		 */
		String describe()
		{
			if (decided == null)
			{
				return null;
			}
			List<String> parts = new ArrayList<>();
			for (String step : makeInstructions)
			{
				parts.add("Make: " + step);
			}
			if (decided.hasAlternatives())
			{
				String members = phrase(decided.members(), 4);
				switch (decided.choice())
				{
					case "held":
						parts.add("Any of " + members + " will do; you hold " + decided.target() + ".");
						break;
					case "make":
						parts.add("Any of " + members + " will do; " + decided.target() + " can be made from what you own.");
						break;
					case "reachable":
						parts.add("Any of " + members + " will do; " + decided.target() + " is the first you can get.");
						break;
					case "unobtainable":
						parts.add("Any of " + members + " will do, but none can be reached on this account right now.");
						break;
					default:
						break;
				}
			}
			if (makeInstructions.isEmpty())
			{
				for (String why : decided.makeBlocked())
				{
					parts.add("Not made: " + why);
				}
			}
			return parts.isEmpty() ? null : String.join(" ", parts);
		}
	}

	/**
	 * Applies questpath's decision to one graph line: the member to target,
	 * where that member comes from, and what can be made from what is held.
	 *
	 * <p>Package-private and static so it can be tested without a client.
	 *
	 * @param plan the push response's item plan, or null when there is none --
	 *     the line is then returned exactly as the graph gave it, and nothing
	 *     claims a choice was made
	 */
	static Line planned(QuestGraph graph, QuestGraph.Item item, ItemPlan plan, Map<String, Long> held)
	{
		return planned(graph, item, plan, held, false);
	}

	static Line planned(QuestGraph graph, QuestGraph.Item item, ItemPlan plan, Map<String, Long> held,
		boolean recommended)
	{
		String name = item.name();
		int itemId = item.itemId();
		List<String> sources = item.sources();
		List<String> shopNames = item.shopNames();
		List<String> drops = item.dropSourceNames();
		ItemPlan.Item decided = plan == null ? null : plan.forRequirement(item.name());
		PlanNote note = PlanNote.NONE;
		if (decided != null)
		{
			String target = decided.target();
			if (target != null && !target.equalsIgnoreCase(item.name()))
			{
				// Retargeted to a member this line did not name: where that
				// member comes from is whatever the graph says for it
				// elsewhere, or nothing -- never the other member's shops.
				QuestGraph.Item other = graph == null ? null : graph.itemNamed(target);
				name = target;
				itemId = decided.targetItemId() > 0 ? decided.targetItemId()
					: other == null ? 0 : other.itemId();
				sources = other == null ? Collections.<String>emptyList() : other.sources();
				shopNames = other == null ? Collections.<String>emptyList() : other.shopNames();
				drops = other == null ? Collections.<String>emptyList() : other.dropSourceNames();
			}
			if (decided.unobtainable())
			{
				// Shown, never routed to: no member can be reached.
				sources = Collections.emptyList();
				shopNames = Collections.emptyList();
				drops = Collections.emptyList();
			}
			note = new PlanNote(decided, decided.makeQuantityGiven(held), decided.instructionsGiven(held));
		}
		long owned = held.getOrDefault(name.toLowerCase(), 0L);
		return new Line(name, itemId, item.quantity(), owned, item.optional(), item.unresolved(), sources,
			shopNames, drops, "", item.notes(), recommended).withPlan(note);
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
	static Map<String, Long> effectiveHeld(Map<String, Long> held, boolean bankSeen,
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
			return sources == null ? Collections.emptyList() : sources;
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
		return of(graph, account, held, bankSeen, Collections.emptySet(),
			Collections.emptySet());
	}

	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming)
	{
		return of(graph, account, held, bankSeen, upcoming, Collections.emptySet());
	}

	/**
	 * @param upcoming the next few quests on the route, in order, undone. The
	 *     pickup list is drawn from these -- see pickupsIn
	 * @param plan quest names on the account's saved questpath route. When it
	 *     has any, it is the scope; when it is empty the list falls back to
	 *     whatever is doable right now.
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, Collections.emptyMap());
	}

	/**
	 * @param knownItems what questpath already holds for the account, by
	 *     lowercased name. Used only when the bank has not been opened this
	 *     session, and never to reduce a live count -- see effectiveHeld.
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan, Map<String, Long> knownItems)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems,
			Collections.emptyMap());
	}

	/**
	 * @param carried what is in the account's bags and worn right now, by
	 *     lowercased name -- never the bank, and never widened by a previous
	 *     session. Decides which shortfalls are gathering trips already under
	 *     way; see Total.inProgress
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems, carried,
			Collections.<String>emptySet());
	}

	/**
	 * @param placeholders lowercased names the bank is holding an empty slot
	 *     for. Not owned -- see Total.ranOut for why they are worth knowing
	 *     about anyway
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried, Set<String> placeholders)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems, carried, placeholders, null);
	}

	/**
	 * @param itemPlan questpath's per-account item plan from the last push,
	 *     or null when there is none -- see {@link #planned}
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried, Set<String> placeholders, ItemPlan itemPlan)
	{
		return of(graph, account, held, bankSeen, upcoming, plan, knownItems, carried, placeholders, itemPlan,
			GroupStorageView.NONE);
	}

	/**
	 * @param storage group storage as this account counts it -- {@link
	 *     GroupStorageView#NONE} for anyone who is not a group ironman. Added
	 *     to what is held after the account's own counts are settled, so a
	 *     stale questpath record can never swallow it; see {@link
	 *     GroupStorageView#addTo}
	 */
	static QuestItems of(QuestGraph graph, PlayerState account, Map<String, Long> held, boolean bankSeen,
		Set<String> upcoming, Set<String> plan, Map<String, Long> knownItems,
		Map<String, Long> carried, Set<String> placeholders, ItemPlan itemPlan,
		GroupStorageView storage)
	{
		GroupStorageView view = storage == null ? GroupStorageView.NONE : storage;
		QuestItems out = build(graph, account, view.addTo(effectiveHeld(held, bankSeen, knownItems)), bankSeen,
			upcoming, plan, knownItems, carried, placeholders, itemPlan);
		out.storage = view;
		out.tagGroupStorage();
		return out;
	}

	/** Marks every line and total with what of it is in group storage. */
	private void tagGroupStorage()
	{
		if (storage.snapshot() == null)
		{
			return;
		}
		for (QuestNeeds needs : quests)
		{
			for (Line line : needs.lines())
			{
				line.inGroupStorage = storage.count(line.name());
			}
		}
		for (List<Total> list : Arrays.asList(totals, allTotals))
		{
			for (Total total : list)
			{
				total.inGroupStorage = storage.count(total.name());
			}
		}
	}

	/** Group storage as these counts include it. */
	GroupStorageView groupStorage()
	{
		return storage;
	}

	/**
	 * @param held already widened by {@link #effectiveHeld} and group storage
	 * @param knownItems only to decide {@link Freshness}; already folded into held
	 */
	private static QuestItems build(QuestGraph graph, PlayerState account, Map<String, Long> held,
		boolean bankSeen, Set<String> upcoming, Set<String> plan,
		Map<String, Long> knownItems, Map<String, Long> carried, Set<String> placeholders,
		ItemPlan itemPlan)
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
		Set<String> scope = new HashSet<>();
		for (String name : plan)
		{
			if (name != null)
			{
				scope.add(name.toLowerCase());
			}
		}

		for (QuestGraph.Quest quest : graph.quests())
		{
			boolean hasItems = quest.items() != null && !quest.items().isEmpty();
			boolean hasRec = quest.recommendedItems() != null && !quest.recommendedItems().isEmpty();
			if (!hasItems && !hasRec)
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
			if (hasItems)
			{
				for (QuestGraph.Item item : quest.items())
				{
					processItem(needs, item, graph, account, held, itemPlan, false);
				}
			}
			if (hasRec)
			{
				for (QuestGraph.Item item : quest.recommendedItems())
				{
					processItem(needs, item, graph, account, held, itemPlan, true);
				}
			}
			out.quests.add(needs);
		}

		int magicLevel = account == null ? 99 : account.levelIn("magic");
		out.magicLevel = magicLevel;
		// Whether the bundle can answer "what is recommended" at all. A v12
		// graph has no recommended_items on any quest, and the toggle on
		// top of it must read as "this graph cannot say", never as "nothing
		// is recommended" -- the same distinction PanelSections draws
		// between unbuilt and empty.
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (!quest.recommendedItems().isEmpty())
			{
				out.graphCarriesRecommended = true;
				break;
			}
		}
		// Recommended coins are a balance to bring, not a purchase: nobody
		// buys coins at a counter, and a "Coins x100" buy line would be the
		// list asking for something no shop and no Grand Exchange slot can
		// give. Summed here and kept out of every total below; the required
		// path's own coin lines are untouched.
		for (QuestNeeds needs : out.quests)
		{
			for (Line line : needs.lines())
			{
				if (line.recommended() && !line.optional() && !line.unresolved()
					&& isCoins(line.itemId(), line.name()))
				{
					out.recommendedCoins += line.required();
				}
			}
		}
		out.totals.addAll(totalsOf(out.quests, held, carried, placeholders, magicLevel, false));
		out.allTotals.addAll(totalsOf(out.quests, held, carried, placeholders, magicLevel, true));
		for (Total t : out.allTotals)
		{
			if (t.recommended())
			{
				out.recommendedTotals.add(t);
			}
		}
		out.pickups.addAll(pickupsIn(graph, upcoming, out.totals));
		return out;
	}

	private static List<String> memberNamesOf(List<QuestGraph.Alternative> alternatives)
	{
		List<String> names = new ArrayList<>();
		for (QuestGraph.Alternative alt : alternatives)
		{
			names.add(alt.name());
		}
		return names;
	}

	private static Total newTotal(String name, Line line, Map<String, Long> held,
		Map<String, Long> carried, Set<String> placeholders)
	{
		if (line.members().isEmpty())
		{
			return new Total(name, line.itemId(),
				held.getOrDefault(name.toLowerCase(), 0L),
				carried == null ? 0L : carried.getOrDefault(name.toLowerCase(), 0L),
				placeholders != null && placeholders.contains(name.toLowerCase()),
				line.sources(), line.shopNames(), line.dropSourceNames());
		}
		return new Total(name, line.itemId(),
			sumHeld(line.members(), held),
			sumHeld(line.members(), carried),
			placeholders != null && anyIn(line.members(), placeholders),
			line.sources(), line.shopNames(), line.dropSourceNames())
			.withMembers(line.members(), held);
	}

	private static boolean anyIn(List<String> names, Set<String> lowerCaseSet)
	{
		for (String name : names)
		{
			if (lowerCaseSet.contains(name.toLowerCase()))
			{
				return true;
			}
		}
		return false;
	}

	/** The sum of what is held of each named item, keyed by lower-case name. */
	private static long sumHeld(List<String> names, Map<String, Long> counts)
	{
		long total = 0;
		if (counts == null)
		{
			return 0;
		}
		for (String name : names)
		{
			total += counts.getOrDefault(name.toLowerCase(), 0L);
		}
		return total;
	}

	private static void processItem(QuestNeeds needs, QuestGraph.Item item, QuestGraph graph,
		PlayerState account, Map<String, Long> held, ItemPlan itemPlan, boolean recommended)
	{
		String name = item.name();
		int itemId = item.itemId();
		String groupName = "";
		long owned;
		if (item.grouped())
		{
			groupName = item.groupName();
			owned = ItemGroupChoice.heldAcross(item.alternatives(), held);
			ItemGroupChoice.Choice choice = ItemGroupChoice.choose(
				item.alternatives(), levelsFor(item.alternatives(), account),
				ItemGroupChoice.wieldOnlySkills(item.alternatives()), held);
			if (choice.any())
			{
				name = choice.best().name();
				itemId = choice.best().itemId();
			}
			Line line = new Line(name, itemId, item.quantity(), owned,
				item.optional(), item.unresolved(), item.sources(), item.shopNames(),
				item.dropSourceNames(), groupName, item.notes(), recommended);
			needs.lines.add(line.withMembers(memberNamesOf(item.alternatives())));
		}
		else
		{
			Line plannedLine = planned(graph, item, itemPlan, held, recommended);
			TeleportEquivalence.Recipe recipe = TeleportEquivalence.findRecipe(plannedLine.itemId(), plannedLine.name());
			if (recipe != null)
			{
				int magicLevel = account == null ? 99 : account.levelIn("magic");
				long extraTablets = Math.max(0L, TeleportEquivalence.tabletsHeld(recipe, held) - plannedLine.owned());
				long runeCasts = (magicLevel >= recipe.magicLevel()) ? TeleportEquivalence.runesToCasts(recipe, held) : 0L;
				plannedLine.setEquivalentOwned(runeCasts + extraTablets);
			}
			needs.lines.add(plannedLine);
		}
	}

	/**
	 * The account's levels in the skills a set actually mentions.
	 *
	 * <p>Built per set rather than passing every skill, because only the
	 * skills a member states can bind, and a map of twenty-four levels for a
	 * question about Mining is noise in a stack trace.
	 *
	 * <p><b>This used to argue that every stated requirement counts, Attack
	 * included, and that was wrong.</b> The argument was that questpath cannot
	 * tell which of a pickaxe's two columns the quest needs, so counting both
	 * was the safe direction. It is not safe, it is just wrong: a pickaxe in
	 * the inventory mines exactly as well as one in the weapon slot, so the
	 * Attack column never gates using it at all. Counting it capped an account
	 * with 41 Mining and 1 Attack at an iron pickaxe when it could perfectly
	 * well use a rune one.
	 *
	 * <p>Which requirements are wield-only is now derived from the set --
	 * see {@link ItemGroupChoice#wieldOnlySkills} -- rather than assumed
	 * either way.
	 */
	private static Map<String, Integer> levelsFor(
		List<QuestGraph.Alternative> alternatives, PlayerState account)
	{
		Map<String, Integer> out = new HashMap<>();
		if (alternatives == null || account == null)
		{
			return out;
		}
		for (QuestGraph.Alternative alt : alternatives)
		{
			for (QuestGraph.Requirement req : alt.requirements())
			{
				out.putIfAbsent(req.skill(), account.levelIn(req.skill()));
			}
		}
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
	private static List<Pickup> pickupsIn(QuestGraph graph, Set<String> upcoming, List<Total> totals)
	{
		List<Pickup> out = new ArrayList<>();
		if (upcoming == null || upcoming.isEmpty())
		{
			return out;
		}

		Map<String, Total> wanted = new LinkedHashMap<>();
		for (Total total : totals)
		{
			if (!total.covered())
			{
				wanted.put(total.name().toLowerCase(), total);
			}
		}
		if (wanted.isEmpty())
		{
			return out;
		}

		Set<String> lowerUpcoming = new HashSet<>();
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
		Map<String, Long> carried, Set<String> placeholders, int magicLevel, boolean includeRecommended)
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
				if (!includeRecommended && line.recommended())
				{
					continue;
				}
				if (line.recommended() && isCoins(line.itemId(), line.name()))
				{
					// A balance, not a buy -- see recommendedCoins().
					continue;
				}
				// An "any tier" line is satisfied by whichever member is held, so
				// its total counts every member, not only the one named.
				Total total = merged.computeIfAbsent(line.name(),
					name -> newTotal(name, line, held, carried, placeholders));
				total.add(line, needs.questName());
			}
		}

		for (Total total : merged.values())
		{
			TeleportEquivalence.Recipe recipe = TeleportEquivalence.findRecipe(total.itemId(), total.name());
			if (recipe != null)
			{
				long extraTablets = Math.max(0L, TeleportEquivalence.tabletsHeld(recipe, held) - total.owned());
				long runeCasts = (magicLevel >= recipe.magicLevel()) ? TeleportEquivalence.runesToCasts(recipe, held) : 0L;
				total.setEquivalentOwned(runeCasts + extraTablets);
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
		private final List<String> dropSourceNames;
		private final List<String> questNames = new ArrayList<>();
		private int wanted;
		private int quests;
		private boolean anyUnstated;
		private PlanNote plan = PlanNote.NONE;
		private boolean recommended = true;
		private long equivalentOwned;
		private long inGroupStorage;

		Total(String name, int itemId, long owned, long carried, boolean placeholder,
			List<String> sources)
		{
			this(name, itemId, owned, carried, placeholder, sources, null);
		}

		/** The full shape: every field a Total can carry, including where to walk to buy it. */
		Total(String name, int itemId, long owned, long carried, boolean placeholder,
			List<String> sources, List<String> shopNames)
		{
			this(name, itemId, owned, carried, placeholder, sources, shopNames, null);
		}

		/**
		 * The wiki's own caveats for this item, verbatim, merged across every
		 * quest line that asked for it.
		 *
		 * <p>Accumulated by {@link #add} rather than fixed at construction,
		 * because two quests can want the same item for different reasons and
		 * only one of them may carry the caveat that matters.
		 */
		List<String> notes()
		{
			return notes;
		}

		private final List<String> notes = new ArrayList<>();

		private final Map<String, Long> memberCounts = new LinkedHashMap<>();

		/**
		 * Records every tier of an "any tier" requirement and how many of each
		 * the account holds, in the wiki's order. Returns this, for chaining.
		 */
		Total withMembers(List<String> names, Map<String, Long> held)
		{
			for (String member : names)
			{
				memberCounts.put(member, held == null ? 0L : held.getOrDefault(member.toLowerCase(), 0L));
			}
			return this;
		}

		/** Every tier that satisfies this, with how many are held; empty for a single named item. */
		Map<String, Long> memberCounts()
		{
			return memberCounts;
		}

		/**
		 * One tooltip line listing every tier and what is held of each, or null
		 * for a single named item. Tiers held are marked; the rest are listed
		 * bare so the player sees the whole set, not only the one named.
		 */
		String tiersNote()
		{
			if (memberCounts.size() < 2)
			{
				return null;
			}
			List<String> parts = new ArrayList<>();
			for (Map.Entry<String, Long> e : memberCounts.entrySet())
			{
				parts.add(e.getValue() > 0 ? e.getKey() + " (" + e.getValue() + ")" : e.getKey());
			}
			return "Any of: " + String.join(", ", parts);
		}

		/** The full shape, including the monsters that drop it. */
		Total(String name, int itemId, long owned, long carried, boolean placeholder,
			List<String> sources, List<String> shopNames, List<String> dropSourceNames)
		{
			this.name = name;
			this.itemId = itemId;
			this.owned = owned;
			this.carried = carried;
			this.placeholder = placeholder;
			this.sources = sources;
			this.shopNames = shopNames;
			this.dropSourceNames = dropSourceNames;
		}

		/**
		 * The monsters that drop this -- see {@link Line#dropSourceNames()}.
		 * Empty for anything a shop sells, and for anything the bundle could
		 * not place a dropper for.
		 */
		List<String> dropSourceNames()
		{
			return dropSourceNames == null ? Collections.emptyList() : dropSourceNames;
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
			return sources == null ? Collections.emptyList() : sources;
		}

		/**
		 * The shops -- see {@link Line#shopNames()} -- that sell this. Every
		 * Line folded into a Total shares one item name and therefore one shop
		 * list; this is simply the first one seen.
		 */
		List<String> shopNames()
		{
			return shopNames == null ? Collections.emptyList() : shopNames;
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

		/**
		 * A detached duplicate, so a caller can merge into it without
		 * touching the list every other surface is reading -- see {@link
		 * TeleportEquivalence#decompose} for the frame-by-frame climb that
		 * made this necessary.
		 */
		Total copy()
		{
			Total out = new Total(name, itemId, owned, carried, placeholder, sources, shopNames,
				dropSourceNames);
			out.wanted = wanted;
			out.quests = quests;
			out.anyUnstated = anyUnstated;
			out.plan = plan;
			out.recommended = recommended;
			out.equivalentOwned = equivalentOwned;
			out.inGroupStorage = inGroupStorage;
			out.questNames.addAll(questNames);
			out.notes.addAll(notes);
			out.memberCounts.putAll(memberCounts);
			return out;
		}

		/**
		 * Folds another total for the same item into this one: counts add,
		 * quests and caveats are appended without repeats, and the row is
		 * only recommended if both were.
		 */
		void absorb(Total other)
		{
			wanted += other.wanted;
			quests += other.quests;
			anyUnstated |= other.anyUnstated;
			recommended &= other.recommended;
			for (String q : other.questNames)
			{
				if (!questNames.contains(q))
				{
					questNames.add(q);
				}
			}
			for (String note : other.notes)
			{
				if (!notes.contains(note))
				{
					notes.add(note);
				}
			}
		}

		void add(Line line, String questName)
		{
			if (!line.recommended())
			{
				this.recommended = false;
			}
			// questpath's plan is per item across the whole scope, so every
			// line of one item carries the same note: the first decided one
			// stands, never a sum -- adding would make one horn grind twice.
			if (!plan.decided() && line.plan().decided())
			{
				plan = line.plan();
			}
			quests++;
			if (questName != null && !questName.isEmpty())
			{
				questNames.add(questName);
			}
			// Merged across quests, de-duplicated, order preserved. Two quests
			// wanting the same item can carry different caveats and only one
			// may be the one that changes what you buy -- so they accumulate
			// rather than the first winning.
			for (String note : line.notes())
			{
				if (note != null && !note.isEmpty() && !notes.contains(note))
				{
					notes.add(note);
				}
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

		boolean recommended()
		{
			return recommended;
		}

		long equivalentOwned()
		{
			return equivalentOwned;
		}

		void setEquivalentOwned(long equivalentOwned)
		{
			this.equivalentOwned = equivalentOwned;
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
			return placeholder && (owned + equivalentOwned) <= 0;
		}

		/**
		 * How many more to get: required, less what is held, less what
		 * questpath's plan says can be made from what is held. Zero once
		 * covered, never negative.
		 */
		long outstanding()
		{
			return Math.max(0L, required() - (owned + equivalentOwned) - plan.makeQuantity());
		}

		/** questpath's decision for this item; {@link PlanNote#NONE} when it made none. */
		PlanNote plan()
		{
			return plan;
		}

		/**
		 * Whether nothing needs fetching: enough is held, or what is short
		 * can be made from what is held. Every surface that sends the player
		 * somewhere -- the shop route, shop and ground highlights, the Grand
		 * Exchange list -- asks this rather than {@link #satisfied()}.
		 */
		boolean covered()
		{
			return satisfied() || (owned + equivalentOwned) + plan.makeQuantity() >= required();
		}

		/**
		 * The name as a list row shows it: "(make)" when questpath says to
		 * make it from what you hold, "(none reachable)" when no member of
		 * its "X or Y" can be got, the plain name otherwise.
		 */
		String displayName()
		{
			if (makeFromOwned())
			{
				return name + "  (make)";
			}
			if (!satisfied() && plan.unobtainable())
			{
				return name + "  (none reachable)";
			}
			return name;
		}

		/** Whether this is short but questpath says to make it from what you hold. */
		boolean makeFromOwned()
		{
			return !satisfied() && plan.makeQuantity() > 0;
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
			return (owned + equivalentOwned) >= required();
		}

		String describe()
		{
			return (owned + equivalentOwned) + " / " + required()
				+ (needsGroupStorage() ? STORAGE_SUFFIX : "");
		}

		/** How much of {@link #owned()} is in group storage; see {@link Line#inGroupStorage}. */
		long inGroupStorage()
		{
			return inGroupStorage;
		}

		/** Whether the account needs to fetch from group storage to have enough. */
		boolean needsGroupStorage()
		{
			return storageNeeded(inGroupStorage, owned, equivalentOwned, required());
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

	/** Every needed item, summed across every doable quest (required items only). */
	List<Total> totals()
	{
		return totals;
	}

	List<Total> totals(boolean includeRecommended)
	{
		return includeRecommended ? allTotals : totals;
	}

	/**
	 * The totals as the Grand Exchange list wants them: recommended extras
	 * in or out, and each uncovered recommended teleport either left as its
	 * tablet or swapped for the runes to cast it, per {@code form}.
	 *
	 * <p>Required teleports are never rewritten here, whatever the form --
	 * the player's choice is about the extras this toggle added, not about
	 * the list that was there before it. An account whose Magic level
	 * cannot cast the spell keeps the tablet in either form; see {@link
	 * TeleportEquivalence#decompose}.
	 */
	List<Total> grandExchangeTotals(boolean includeRecommended, TeleportForm form)
	{
		List<Total> base = totals(includeRecommended);
		if (!includeRecommended || form != TeleportForm.RUNES)
		{
			return base;
		}
		return TeleportEquivalence.decompose(base, true, magicLevel);
	}

	/**
	 * Whether the quest graph this was built from carries recommended items
	 * for any quest at all. False for a version-12 bundle, where "include
	 * recommended items" can only ever add nothing -- and the panel must
	 * say that, rather than let an empty section pass for "nothing is
	 * recommended".
	 */
	boolean graphCarriesRecommended()
	{
		return graphCarriesRecommended;
	}

	/**
	 * Coins the plan's quests recommend bringing, summed. Zero when none
	 * do. Never on a shopping list: coins are not bought, so this is shown
	 * as a balance to have rather than a row to buy.
	 */
	long recommendedCoins()
	{
		return recommendedCoins;
	}

	/** The account's Magic level the totals were priced at; 99 with no account. */
	int magicLevel()
	{
		return magicLevel;
	}

	/** Whether an item row is coins -- by id when the bundle has one, by name when it does not. */
	static boolean isCoins(int itemId, String name)
	{
		return itemId == ItemID.COINS || (name != null && name.trim().equalsIgnoreCase("coins"));
	}

	List<Total> allTotals()
	{
		return allTotals;
	}

	List<Total> recommendedTotals()
	{
		return recommendedTotals;
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
		hash = hash * 31 + storage.state().ordinal();
		hash = hash * 31 + (storage.snapshot() == null ? 0 : Long.hashCode(storage.snapshot().seenAtMillis()));
		for (QuestNeeds needs : quests)
		{
			hash = hash * 31 + needs.questName().hashCode();
			for (Line line : needs.lines())
			{
				hash = hash * 31 + line.name().hashCode();
				hash = hash * 31 + Long.hashCode(line.owned());
				hash = hash * 31 + Long.hashCode(line.equivalentOwned());
				hash = hash * 31 + Long.hashCode(line.plan().makeQuantity());
				hash = hash * 31 + (line.plan().decided() ? 1 : 0);
				hash = hash * 31 + (line.recommended() ? 1 : 0);
			}
		}
		return hash;
	}

	boolean isEmpty()
	{
		return quests.isEmpty();
	}
}
