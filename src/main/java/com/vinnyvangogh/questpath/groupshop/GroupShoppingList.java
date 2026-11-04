package com.vinnyvangogh.questpath.groupshop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a whole group still has to get, item by item: "you all need 6
 * buckets, the group holds 2, get 28".
 *
 * <p>The order holdings are spent in is the order a group would actually
 * spend them:
 * <ol>
 * <li><b>Your own first.</b> What a member holds covers their own need
 * before anyone else's. This is the only offset an untradeable item, or a
 * regular ironman's holding, ever makes.</li>
 * <li><b>Then spares, between members who can trade.</b> A tradeable spare
 * passes only between accounts {@link AccountKind#canTradeWith} allows --
 * group ironmen with each other, normal accounts with each other. A member
 * who needs none of an item spares all of it.</li>
 * <li><b>Then group storage</b>, for group ironmen only, from one
 * deduplicated snapshot ({@link GroupStorage#freshest}).</li>
 * </ol>
 *
 * <p>Two honesty rules, the same ones {@code ItemSplit} keeps in Quest Path:
 * a member who shared no item counts is <em>unknown</em>, and their need is
 * reported in {@link Line#unknownNeed()} rather than folded into {@link
 * Line#stillNeeded()} -- telling the group to buy what may be in that
 * member's bank is the confident wrong answer. And a stale member is still
 * counted, but named on every line they touch, so the reader knows which
 * numbers may have moved.
 *
 * <p>Pure: no client, no UI, no Quest Path types. Copy the package.
 */
public final class GroupShoppingList
{
	/** Whether the game lets an item change hands at all. */
	public interface Tradeability
	{
		boolean tradeable(int itemId, String name);
	}

	/** One member's part of one line. */
	public static final class Share
	{
		private final String member;
		private final long need;
		private final long held;
		private final long received;
		private final long fromStorage;
		private final long shortBy;
		private final boolean unknown;
		private final boolean stale;

		Share(String member, long need, long held, long received, long fromStorage, long shortBy, boolean unknown,
			boolean stale)
		{
			this.member = member;
			this.need = need;
			this.held = held;
			this.received = received;
			this.fromStorage = fromStorage;
			this.shortBy = shortBy;
			this.unknown = unknown;
			this.stale = stale;
		}

		public String member()
		{
			return member;
		}

		/** Across every quest counted for this member. */
		public long need()
		{
			return need;
		}

		/** What they hold themselves; 0 when {@link #unknown()}. */
		public long held()
		{
			return held;
		}

		/** Handed over by teammates. */
		public long received()
		{
			return received;
		}

		/** Taken from group storage. */
		public long fromStorage()
		{
			return fromStorage;
		}

		/** Still to get after every offset; 0 when {@link #unknown()}, whose need is not judged. */
		public long shortBy()
		{
			return shortBy;
		}

		/** True when the member shared no item counts. */
		public boolean unknown()
		{
			return unknown;
		}

		public boolean stale()
		{
			return stale;
		}
	}

	/** One item, across the group. */
	public static final class Line
	{
		private final String name;
		private final int itemId;
		private final boolean tradeable;
		private final boolean quantityStated;
		private final boolean obtainable;
		private final Set<String> quests;
		private final List<Share> shares;
		private final List<Transfer> transfers;
		private final Map<String, Long> holders;
		private final long storageHeld;
		private final long storageUsed;

		Line(String name, int itemId, boolean tradeable, boolean quantityStated, boolean obtainable, Set<String> quests,
			List<Share> shares, List<Transfer> transfers, Map<String, Long> holders, long storageHeld, long storageUsed)
		{
			this.name = name;
			this.itemId = itemId;
			this.tradeable = tradeable;
			this.quantityStated = quantityStated;
			this.obtainable = obtainable;
			this.quests = Collections.unmodifiableSet(quests);
			this.shares = Collections.unmodifiableList(shares);
			this.transfers = Collections.unmodifiableList(transfers);
			this.holders = Collections.unmodifiableMap(holders);
			this.storageHeld = storageHeld;
			this.storageUsed = storageUsed;
		}

		public String name()
		{
			return name;
		}

		public int itemId()
		{
			return itemId;
		}

		/** Whether spares and storage could count; false means only each member's own holding did. */
		public boolean tradeable()
		{
			return tradeable;
		}

		/** False when some quest named the item without a count, so totals are floors. */
		public boolean quantityStated()
		{
			return quantityStated;
		}

		/** True when every quest asking for this says it can be picked up during the quest. */
		public boolean obtainable()
		{
			return obtainable;
		}

		public Set<String> quests()
		{
			return quests;
		}

		/** One per member who needs it, in the order members were given. */
		public List<Share> shares()
		{
			return shares;
		}

		public List<Transfer> transfers()
		{
			return transfers;
		}

		/** Everyone who holds any, needed or not, in member order. */
		public Map<String, Long> holders()
		{
			return holders;
		}

		/** What group storage holds of it, by the snapshot used. */
		public long storageHeld()
		{
			return storageHeld;
		}

		public long storageUsed()
		{
			return storageUsed;
		}

		/** Every member's need added up, unknown members included. */
		public long totalNeeded()
		{
			long total = 0;
			for (Share share : shares)
			{
				total += share.need();
			}
			return total;
		}

		/** The headline: how many more the group has to get. Excludes unknown members' need. */
		public long stillNeeded()
		{
			long total = 0;
			for (Share share : shares)
			{
				total += share.shortBy();
			}
			return total;
		}

		/** Need from members who shared no counts, kept out of {@link #stillNeeded()}. */
		public long unknownNeed()
		{
			long total = 0;
			for (Share share : shares)
			{
				total += share.unknown() ? share.need() : 0;
			}
			return total;
		}

		/** Members whose numbers on this line come from a stale sync. */
		public List<String> staleMembers()
		{
			List<String> out = new ArrayList<>();
			for (Share share : shares)
			{
				if (share.stale())
				{
					out.add(share.member());
				}
			}
			return out;
		}

		/** The share for one member, or null when they need none. */
		public Share share(String member)
		{
			for (Share share : shares)
			{
				if (share.member().equals(member))
				{
					return share;
				}
			}
			return null;
		}
	}

	private final List<MemberNeeds> members;
	private final GroupStorage storage;
	private final Tradeability tradeability;
	private final List<Line> lines;

	private GroupShoppingList(List<MemberNeeds> members, GroupStorage storage, Tradeability tradeability,
		List<Line> lines)
	{
		this.members = members;
		this.storage = storage;
		this.tradeability = tradeability;
		this.lines = Collections.unmodifiableList(lines);
	}

	/**
	 * @param members everyone to plan for, in display order; a member who
	 *     left the party is simply not passed
	 * @param storage group storage, or null when nobody has seen it
	 * @param tradeability null treats every item as untradeable -- the
	 *     conservative answer, offsetting only each member's own holding
	 */
	public static GroupShoppingList of(List<MemberNeeds> members, GroupStorage storage, Tradeability tradeability)
	{
		List<MemberNeeds> copy = Collections.unmodifiableList(new ArrayList<>(
			members == null ? Collections.emptyList() : members));
		Map<String, ItemTally> tallies = new LinkedHashMap<>();
		for (MemberNeeds member : copy)
		{
			for (ItemNeed need : member.needs())
			{
				if (need.count() > 0 && !need.item().isEmpty())
				{
					tallies.computeIfAbsent(need.key(), k -> new ItemTally(need)).add(member, need);
				}
			}
		}
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<String, ItemTally> entry : tallies.entrySet())
		{
			ItemTally tally = entry.getValue();
			boolean tradeable = tradeability != null && tradeability.tradeable(tally.itemId, tally.name);
			lines.add(new ItemAllocation(entry.getKey(), tally, copy, storage, tradeable).toLine());
		}
		lines.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		return new GroupShoppingList(copy, storage, tradeability, lines);
	}

	/** Every line, sorted by item name. */
	public List<Line> lines()
	{
		return lines;
	}

	/** The members planned for, in the order given. */
	public List<String> memberNames()
	{
		List<String> out = new ArrayList<>();
		for (MemberNeeds member : members)
		{
			out.add(member.name());
		}
		return out;
	}

	/** Every quest any member's needs came from, sorted. */
	public List<String> quests()
	{
		Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Line line : lines)
		{
			out.addAll(line.quests());
		}
		return new ArrayList<>(out);
	}

	/** Members who shared no item counts at all. */
	public List<String> membersWithoutItems()
	{
		List<String> out = new ArrayList<>();
		for (MemberNeeds member : members)
		{
			if (!member.itemsKnown())
			{
				out.add(member.name());
			}
		}
		return out;
	}

	/** Members whose counts are only what they carry. */
	public List<String> membersWithoutBank()
	{
		List<String> out = new ArrayList<>();
		for (MemberNeeds member : members)
		{
			if (member.itemsKnown() && !member.bankSeen())
			{
				out.add(member.name());
			}
		}
		return out;
	}

	/** The storage snapshot used, or null. */
	public GroupStorage storage()
	{
		return storage;
	}

	/**
	 * The list narrowed for display.
	 *
	 * <p>A quest filter recomputes rather than hides: "what do we still need
	 * for Witch's Potion" spends every holding on that quest alone, which is
	 * a different number from that quest's slice of the all-quests total.
	 * A member filter only hides -- the totals stay the group's.
	 *
	 * @param quest a quest name, or null for every quest
	 * @param member a member name, or null for everyone
	 * @param stillNeededOnly drop lines the group already has covered
	 */
	public List<Line> filtered(String quest, String member, boolean stillNeededOnly)
	{
		List<Line> source = lines;
		if (quest != null)
		{
			List<MemberNeeds> narrowed = new ArrayList<>();
			for (MemberNeeds needs : members)
			{
				narrowed.add(needs.onlyQuest(quest));
			}
			source = of(narrowed, storage, tradeability).lines();
		}
		List<Line> out = new ArrayList<>();
		for (Line line : source)
		{
			if (member != null && line.share(member) == null)
			{
				continue;
			}
			if (stillNeededOnly && line.stillNeeded() == 0 && line.unknownNeed() == 0)
			{
				continue;
			}
			out.add(line);
		}
		return out;
	}

	/** Every need for one item, gathered across members before anything is spent. */
	private static final class ItemTally
	{
		private final String name;
		private final int itemId;
		private boolean stated = true;
		private boolean obtainable = true;
		private final Set<String> quests = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		private final Map<MemberNeeds, Long> need = new LinkedHashMap<>();

		ItemTally(ItemNeed first)
		{
			this.name = first.item();
			this.itemId = first.itemId();
		}

		void add(MemberNeeds member, ItemNeed item)
		{
			need.merge(member, item.count(), Long::sum);
			quests.add(item.quest());
			stated &= item.quantityStated();
			obtainable &= item.obtainable();
		}
	}

	/** Spends holdings against one item's needs, in the order the class doc gives. */
	private static final class ItemAllocation
	{
		private final String key;
		private final ItemTally tally;
		private final List<MemberNeeds> members;
		private final boolean tradeable;
		private final Map<MemberNeeds, long[]> slots = new LinkedHashMap<>();
		private final Map<MemberNeeds, Long> spare = new LinkedHashMap<>();
		private final List<Transfer> transfers = new ArrayList<>();
		private final long storageHeld;
		private long storageLeft;

		// slot layout: need, held, received, fromStorage, shortBy
		private static final int NEED = 0;
		private static final int HELD = 1;
		private static final int RECEIVED = 2;
		private static final int STORAGE = 3;
		private static final int SHORT = 4;

		ItemAllocation(String key, ItemTally tally, List<MemberNeeds> members, GroupStorage storage,
			boolean tradeable)
		{
			this.key = key;
			this.tally = tally;
			this.members = members;
			this.tradeable = tradeable;
			this.storageHeld = storage == null ? 0L : storage.count(key);
			this.storageLeft = tradeable ? storageHeld : 0L;
			spendOwn();
			if (tradeable)
			{
				pool();
				spendStorage();
			}
		}

		private void spendOwn()
		{
			for (MemberNeeds member : members)
			{
				long held = member.held(key);
				long need = tally.need.getOrDefault(member, 0L);
				long own = Math.min(held, need);
				if (member.itemsKnown() && held > own)
				{
					spare.put(member, held - own);
				}
				if (need > 0)
				{
					long shortBy = member.itemsKnown() ? need - own : 0L;
					slots.put(member, new long[]{need, held, 0L, 0L, shortBy});
				}
			}
		}

		private void pool()
		{
			for (Map.Entry<MemberNeeds, long[]> entry : slots.entrySet())
			{
				MemberNeeds taker = entry.getKey();
				long[] slot = entry.getValue();
				for (MemberNeeds giver : members)
				{
					if (slot[SHORT] == 0)
					{
						break;
					}
					long available = spare.getOrDefault(giver, 0L);
					if (giver == taker || available == 0 || !giver.kind().canTradeWith(taker.kind()))
					{
						continue;
					}
					long given = Math.min(available, slot[SHORT]);
					spare.put(giver, available - given);
					slot[RECEIVED] += given;
					slot[SHORT] -= given;
					transfers.add(new Transfer(giver.name(), taker.name(), given));
				}
			}
		}

		private void spendStorage()
		{
			for (Map.Entry<MemberNeeds, long[]> entry : slots.entrySet())
			{
				long[] slot = entry.getValue();
				if (storageLeft == 0 || entry.getKey().kind() != AccountKind.GROUP_IRONMAN)
				{
					continue;
				}
				long taken = Math.min(storageLeft, slot[SHORT]);
				storageLeft -= taken;
				slot[STORAGE] += taken;
				slot[SHORT] -= taken;
			}
		}

		Line toLine()
		{
			List<Share> shares = new ArrayList<>();
			for (Map.Entry<MemberNeeds, long[]> entry : slots.entrySet())
			{
				MemberNeeds member = entry.getKey();
				long[] slot = entry.getValue();
				shares.add(new Share(member.name(), slot[NEED], slot[HELD], slot[RECEIVED], slot[STORAGE],
					slot[SHORT], !member.itemsKnown(), member.stale()));
			}
			Map<String, Long> holders = new LinkedHashMap<>();
			for (MemberNeeds member : members)
			{
				if (member.held(key) > 0)
				{
					holders.put(member.name(), member.held(key));
				}
			}
			long storageUsed = (tradeable ? storageHeld : 0L) - storageLeft;
			return new Line(tally.name, tally.itemId, tradeable, tally.stated, tally.obtainable,
				new TreeSet<>(tally.quests), shares, transfers, holders, storageHeld, storageUsed);
		}
	}
}
