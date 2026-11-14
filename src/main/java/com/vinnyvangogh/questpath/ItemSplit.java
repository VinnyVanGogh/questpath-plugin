package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.Transfer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One quest's item list split across a party: who already has what, who
 * should hand a spare to whom, and what is still left to get.
 *
 * <p>Every member doing the quest needs their own copy -- a group ironman
 * quest is done once per account, not once per group. Spares are handed over
 * before anything is bought or gathered: a member holding more than they need
 * covers someone short, and a member who has already finished the quest
 * gives everything they hold of it. What nobody can cover is left with the
 * member who is short, since they are the one who needs it in hand.
 *
 * <p>Two honesty rules, the same ones the Items tab keeps:
 * <ul>
 * <li>A member who shared no item counts is <em>unknown</em>, never
 * "holds nothing" -- telling them to buy what may be sitting in their bank is
 * the confident wrong answer.</li>
 * <li>Lines this split cannot work with -- optional ones, wiki lines the
 * catalogue could not match, and category lines like "any pickaxe" -- are
 * counted in {@link #unsplit()} rather than dropped, so the list never
 * passes itself off as everything the quest needs.</li>
 * </ul>
 *
 * <p>Group storage is not counted here. RuneLite only exposes it while a
 * member has it open; the group shopping list ({@code
 * groupshop.GroupShoppingList}) counts the last snapshot and says whose and
 * how old it is, which a per-quest split has no room to say.
 */
final class ItemSplit
{
	/** One item line, split. */
	static final class Line
	{
		private final String name;
		private final int itemId;
		private final long perMember;
		private final boolean quantityStated;
		private final boolean obtainable;
		private final long totalNeeded;
		private final List<Transfer> transfers;
		private final Map<String, Long> toGet;
		private final List<String> unknown;

		Line(String name, int itemId, long perMember, boolean quantityStated, boolean obtainable,
			long totalNeeded, List<Transfer> transfers, Map<String, Long> toGet, List<String> unknown)
		{
			this.name = name;
			this.itemId = itemId;
			this.perMember = perMember;
			this.quantityStated = quantityStated;
			this.obtainable = obtainable;
			this.totalNeeded = totalNeeded;
			this.transfers = Collections.unmodifiableList(transfers);
			this.toGet = Collections.unmodifiableMap(toGet);
			this.unknown = Collections.unmodifiableList(unknown);
		}

		String name()
		{
			return name;
		}

		int itemId()
		{
			return itemId;
		}

		/** What each member doing the quest needs. */
		long perMember()
		{
			return perMember;
		}

		/**
		 * False when the wiki named the item without a count. The split then
		 * assumes one each, and the panel must say "at least".
		 */
		boolean quantityStated()
		{
			return quantityStated;
		}

		/** Whether the wiki says it can be picked up during the quest. */
		boolean obtainable()
		{
			return obtainable;
		}

		/** Across every member doing the quest. */
		long totalNeeded()
		{
			return totalNeeded;
		}

		List<Transfer> transfers()
		{
			return transfers;
		}

		/** Still short after every hand-over, by member, in party order. */
		Map<String, Long> toGet()
		{
			return toGet;
		}

		/** Members doing the quest whose holdings this client was not told. */
		List<String> unknown()
		{
			return unknown;
		}
	}

	private final List<String> doers;
	private final List<Line> lines;
	private final int unsplit;
	private final List<String> membersWithoutItems;
	private final List<String> membersWithoutBank;

	private ItemSplit(List<String> doers, List<Line> lines, int unsplit, List<String> membersWithoutItems,
		List<String> membersWithoutBank)
	{
		this.doers = Collections.unmodifiableList(doers);
		this.lines = Collections.unmodifiableList(lines);
		this.unsplit = unsplit;
		this.membersWithoutItems = Collections.unmodifiableList(membersWithoutItems);
		this.membersWithoutBank = Collections.unmodifiableList(membersWithoutBank);
	}

	static ItemSplit of(QuestGraph.Quest quest, List<GroupMember> members)
	{
		List<GroupMember> doing = new ArrayList<>();
		List<String> doers = new ArrayList<>();
		List<String> withoutItems = new ArrayList<>();
		List<String> withoutBank = new ArrayList<>();
		for (GroupMember member : members)
		{
			if (member.state().isComplete(quest.name()))
			{
				continue;
			}
			doing.add(member);
			doers.add(member.name());
			if (!member.itemsKnown())
			{
				withoutItems.add(member.name());
			}
			else if (!member.bankSeen())
			{
				withoutBank.add(member.name());
			}
		}

		List<Line> lines = new ArrayList<>();
		int unsplit = 0;
		for (QuestGraph.Item item : quest.items())
		{
			if (item.optional() || item.unresolved() || item.grouped() || item.name().isEmpty())
			{
				unsplit++;
				continue;
			}
			lines.add(line(item, members, doing));
		}
		return new ItemSplit(doers, lines, unsplit, withoutItems, withoutBank);
	}

	private static Line line(QuestGraph.Item item, List<GroupMember> members, List<GroupMember> doing)
	{
		boolean stated = item.quantity() > 0;
		long need = stated ? item.quantity() : 1;

		// What each member can spare: everything for someone not doing the
		// quest, anything over their own need for someone who is.
		Map<GroupMember, Long> spare = new IdentityHashMap<>();
		for (GroupMember member : members)
		{
			if (!member.itemsKnown())
			{
				continue;
			}
			long held = member.held(item.name());
			long keep = doing.contains(member) ? need : 0;
			if (held > keep)
			{
				spare.put(member, held - keep);
			}
		}

		List<Transfer> transfers = new ArrayList<>();
		Map<String, Long> toGet = new LinkedHashMap<>();
		List<String> unknown = new ArrayList<>();
		for (GroupMember member : doing)
		{
			if (!member.itemsKnown())
			{
				unknown.add(member.name());
				continue;
			}
			long shortBy = need - Math.min(need, member.held(item.name()));
			for (GroupMember giver : members)
			{
				if (shortBy == 0)
				{
					break;
				}
				Long available = spare.get(giver);
				if (giver == member || available == null || available == 0)
				{
					continue;
				}
				long given = Math.min(available, shortBy);
				transfers.add(new Transfer(giver.name(), member.name(), given));
				spare.put(giver, available - given);
				shortBy -= given;
			}
			if (shortBy > 0)
			{
				toGet.put(member.name(), shortBy);
			}
		}
		return new Line(item.name(), item.itemId(), need, stated, item.obtainable(), need * doing.size(),
			transfers, toGet, unknown);
	}

	/** Members still doing the quest, in party order. */
	List<String> doers()
	{
		return doers;
	}

	List<Line> lines()
	{
		return lines;
	}

	/** Item lines not split: optional, unmatched, or a category rather than one item. */
	int unsplit()
	{
		return unsplit;
	}

	/** Members doing the quest who shared no item counts. */
	List<String> membersWithoutItems()
	{
		return membersWithoutItems;
	}

	/** Members doing the quest whose counts cover only what they carry -- a lower bound. */
	List<String> membersWithoutBank()
	{
		return membersWithoutBank;
	}
}
