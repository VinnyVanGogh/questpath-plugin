package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * This account's last group storage snapshot, kept in the RuneLite profile
 * so it outlives a logout and a client restart.
 *
 * <p>The game only shows storage to a member who has it open. Holding the
 * snapshot only in memory meant it was gone on every relog, and the item
 * lists went back to "you need a blood rune" with one sitting in storage.
 *
 * <p>Stored per RS profile ({@code setRSProfileConfiguration}), so it is
 * already scoped to one account: logging into another account reads that
 * account's snapshot, or none, never this one's. Quest items only, as
 * recorded -- the same filter what is shared with the party already carries.
 */
final class GroupStorageStore
{
	/** RS-profile key in {@link QuestpathConfig#GROUP}. Frozen once published. */
	static final String KEY = "groupStorageSnapshot";

	/** The stored shape. Field names are the persisted format. */
	static final class Snapshot
	{
		long at;
		Map<String, Long> counts;

		Snapshot()
		{
		}

		Snapshot(Map<String, Long> counts, long at)
		{
			this.counts = counts;
			this.at = at;
		}
	}

	private GroupStorageStore()
	{
	}

	static String encode(Gson gson, Map<String, Long> counts, long at)
	{
		return gson.toJson(new Snapshot(counts == null ? new HashMap<>() : counts, at));
	}

	/**
	 * The stored snapshot, or null when there is none or it cannot be read.
	 * Unreadable is treated as never seen -- "unknown", not "empty".
	 */
	static Snapshot decode(Gson gson, String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return null;
		}
		try
		{
			Snapshot snapshot = gson.fromJson(stored, Snapshot.class);
			if (snapshot == null || snapshot.counts == null || snapshot.at <= 0)
			{
				return null;
			}
			return snapshot;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}
}
