package com.vinnyvangogh.questpath.groupshop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One member, reduced to what a shopping list needs: what they still need,
 * what they hold, and how far to trust it.
 *
 * <p>Built by the host plugin from whatever it knows about the member --
 * Quest Path from its party message and quest graph, a Group Ironman plugin
 * from its own sync. Nothing in here knows where the numbers came from.
 */
public final class MemberNeeds
{
	private final String name;
	private final AccountKind kind;
	private final Map<String, Long> held;
	private final boolean bankSeen;
	private final boolean stale;
	private final List<ItemNeed> needs;

	/**
	 * @param held counts by item name (any case), or null when the member
	 *     shared none -- "unknown", never "holds nothing"
	 * @param bankSeen whether {@code held} includes the bank, or only what is carried
	 * @param stale whether the member's last sync is too old to trust as current
	 */
	public MemberNeeds(String name, AccountKind kind, Map<String, Long> held, boolean bankSeen, boolean stale,
		List<ItemNeed> needs)
	{
		this.name = name == null ? "" : name;
		this.kind = kind == null ? AccountKind.UNKNOWN : kind;
		this.held = held == null ? null : lowerCased(held);
		this.bankSeen = bankSeen;
		this.stale = stale;
		this.needs = Collections.unmodifiableList(new ArrayList<>(needs == null ? Collections.emptyList() : needs));
	}

	private static Map<String, Long> lowerCased(Map<String, Long> raw)
	{
		Map<String, Long> out = new HashMap<>();
		for (Map.Entry<String, Long> entry : raw.entrySet())
		{
			if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
			{
				out.merge(entry.getKey().toLowerCase(), entry.getValue(), Long::sum);
			}
		}
		return out;
	}

	public String name()
	{
		return name;
	}

	public AccountKind kind()
	{
		return kind;
	}

	public boolean itemsKnown()
	{
		return held != null;
	}

	public boolean bankSeen()
	{
		return bankSeen;
	}

	public boolean stale()
	{
		return stale;
	}

	public List<ItemNeed> needs()
	{
		return needs;
	}

	/** This member's count of one item; 0 when not held or not shared. */
	public long held(String item)
	{
		if (held == null || item == null)
		{
			return 0L;
		}
		return held.getOrDefault(item.toLowerCase(), 0L);
	}

	/** The same member with only the needs for one quest. */
	MemberNeeds onlyQuest(String quest)
	{
		List<ItemNeed> kept = new ArrayList<>();
		for (ItemNeed need : needs)
		{
			if (need.quest().equals(quest))
			{
				kept.add(need);
			}
		}
		return new MemberNeeds(name, kind, held, bankSeen, stale, kept);
	}
}
