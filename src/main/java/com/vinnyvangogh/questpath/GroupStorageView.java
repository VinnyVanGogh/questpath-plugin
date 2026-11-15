package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.AccountKind;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Group storage as this account's own item lists count it.
 *
 * <p>For a Group Ironman, what sits in group storage is something the account
 * has: it only has to walk over and withdraw it. Telling them to buy a blood
 * rune while one is in storage is the confident wrong answer this exists to
 * stop. Every other account type has no storage, and its lists are left
 * exactly as they were.
 *
 * <p>Three states, kept apart on purpose:
 * <ul>
 * <li>{@link State#NOT_GROUP_IRONMAN}: storage plays no part.</li>
 * <li>{@link State#UNSEEN}: a group ironman whose storage nobody has opened
 * where this client could hear about it. Unknown, never "empty" -- items stay
 * needed, and the lists say to open storage once.</li>
 * <li>{@link State#SEEN}: the freshest snapshot, this account's own or any
 * group member's ({@link GroupStorage#freshest}), counts as held.</li>
 * </ul>
 *
 * <p>Storage is shared, so it is added to what the account holds but never
 * folded into the bank: rows say "in group storage", so the player goes to
 * the storage chest rather than searching a bank it is not in.
 */
final class GroupStorageView
{
	enum State
	{
		NOT_GROUP_IRONMAN,
		UNSEEN,
		SEEN
	}

	/** No storage: what every account that is not a group ironman gets. */
	static final GroupStorageView NONE = new GroupStorageView(State.NOT_GROUP_IRONMAN, null);

	private final State state;
	private final GroupStorage snapshot;

	private GroupStorageView(State state, GroupStorage snapshot)
	{
		this.state = state;
		this.snapshot = snapshot;
	}

	/**
	 * @param kind this account's trading rules; anything but a group ironman
	 *     ignores storage entirely
	 * @param snapshots this account's own snapshot and every group member's,
	 *     any of them null; the newest wins
	 */
	static GroupStorageView of(AccountKind kind, Collection<GroupStorage> snapshots)
	{
		if (kind != AccountKind.GROUP_IRONMAN)
		{
			return NONE;
		}
		GroupStorage freshest = GroupStorage.freshest(snapshots);
		return freshest == null
			? new GroupStorageView(State.UNSEEN, null)
			: new GroupStorageView(State.SEEN, freshest);
	}

	State state()
	{
		return state;
	}

	/** The snapshot counted, or null unless {@link State#SEEN}. */
	GroupStorage snapshot()
	{
		return snapshot;
	}

	/** How many of an item storage holds, by lower-cased name; 0 when not counted. */
	long count(String name)
	{
		return snapshot == null ? 0L : snapshot.count(name);
	}

	/**
	 * What the account holds plus what is in storage, summed. Summed, not
	 * maxed: storage is a separate chest from the account's own bank, so a
	 * blood rune in each is two blood runes. The input is never modified.
	 */
	Map<String, Long> addTo(Map<String, Long> held)
	{
		if (snapshot == null || snapshot.counts().isEmpty())
		{
			return held;
		}
		Map<String, Long> out = new LinkedHashMap<>(held);
		for (Map.Entry<String, Long> entry : snapshot.counts().entrySet())
		{
			out.merge(entry.getKey(), entry.getValue(), Long::sum);
		}
		return out;
	}

	/**
	 * One sentence for the top of an item list, or null when storage plays
	 * no part.
	 */
	String note(long nowMillis)
	{
		switch (state)
		{
			case UNSEEN:
				return "Group storage not seen yet: open it once, and what is in it counts here.";
			case SEEN:
				String by = snapshot.seenBy().isEmpty() ? "" : " by " + snapshot.seenBy();
				return "Counts include group storage, seen" + by + " " + age(nowMillis - snapshot.seenAtMillis())
					+ ". Rows marked \"in group storage\" are there to withdraw.";
			default:
				return null;
		}
	}

	/** "just now", "5m ago", "2h ago", "3d ago". A negative age (clock skew) reads as just now. */
	static String age(long millis)
	{
		long minutes = millis / 60_000L;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}

	/** Every snapshot worth comparing: this account's own, then each member's. */
	static List<GroupStorage> snapshots(GroupStorage own, Collection<GroupMember> members)
	{
		List<GroupStorage> out = new ArrayList<>();
		out.add(own);
		for (GroupMember member : members)
		{
			out.add(member.groupStorage());
		}
		return out;
	}
}
