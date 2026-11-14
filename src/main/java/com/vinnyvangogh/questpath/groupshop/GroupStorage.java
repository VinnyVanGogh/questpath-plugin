package com.vinnyvangogh.questpath.groupshop;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A Group Ironman group's shared storage, as one member last saw it.
 *
 * <p>The game only shows storage to a member who has it open, so every
 * member may carry a different snapshot of the same chest. They are never
 * summed -- that would count every bucket once per member who looked -- and
 * {@link #freshest} keeps the newest one.
 */
public final class GroupStorage
{
	private final Map<String, Long> counts;
	private final long seenAtMillis;
	private final String seenBy;

	public GroupStorage(Map<String, Long> counts, long seenAtMillis, String seenBy)
	{
		Map<String, Long> lower = new HashMap<>();
		if (counts != null)
		{
			for (Map.Entry<String, Long> entry : counts.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
				{
					lower.merge(entry.getKey().toLowerCase(), entry.getValue(), Long::sum);
				}
			}
		}
		this.counts = lower;
		this.seenAtMillis = seenAtMillis;
		this.seenBy = seenBy == null ? "" : seenBy;
	}

	/** The newest snapshot, or null when there is none. */
	public static GroupStorage freshest(Collection<GroupStorage> snapshots)
	{
		GroupStorage best = null;
		if (snapshots != null)
		{
			for (GroupStorage snapshot : snapshots)
			{
				if (snapshot != null && (best == null || snapshot.seenAtMillis > best.seenAtMillis))
				{
					best = snapshot;
				}
			}
		}
		return best;
	}

	public long count(String item)
	{
		return item == null ? 0L : counts.getOrDefault(item.toLowerCase(), 0L);
	}

	/** When the snapshot was taken, epoch milliseconds, by the member's own clock. */
	public long seenAtMillis()
	{
		return seenAtMillis;
	}

	/** Who opened the storage for this snapshot. */
	public String seenBy()
	{
		return seenBy;
	}
}
