package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pins {@link ConfigGroupMigration}: settings saved under the old {@code
 * questpath} group reach the new {@code questroute} group once, a value the
 * new group already holds is never overwritten, and a second start copies
 * nothing -- including after the player has changed or cleared a migrated
 * value.
 */
public class ConfigGroupMigrationTest
{
	private static final String OLD = QuestpathConfig.LEGACY_GROUP;
	private static final String NEW = QuestpathConfig.GROUP;
	private static final String ACCOUNT = "rsprofile.abc123";

	/** In-memory config: whole key ("group[.profile].key") to value, the way ConfigManager stores it. */
	private static final class MemoryStore implements ConfigGroupMigration.Store
	{
		final Map<String, String> values = new TreeMap<>();
		final List<String> profiles = new ArrayList<>();

		private static String whole(String group, String profile, String key)
		{
			return group + "." + (profile == null ? "" : profile + ".") + key;
		}

		void put(String group, String profile, String key, String value)
		{
			values.put(whole(group, profile, key), value);
		}

		@Override
		public List<String> profiles()
		{
			return profiles;
		}

		@Override
		public List<String> keys(String group, String profile)
		{
			String prefix = whole(group, profile, "");
			List<String> keys = new ArrayList<>();
			for (String whole : values.keySet())
			{
				String rest = whole.startsWith(prefix) ? whole.substring(prefix.length()) : null;
				// A plain-key listing must not see account keys, as ConfigManager's own does not.
				if (rest != null && (profile != null || !rest.startsWith("rsprofile.")))
				{
					keys.add(rest);
				}
			}
			return keys;
		}

		@Override
		public String get(String group, String profile, String key)
		{
			return values.get(whole(group, profile, key));
		}

		@Override
		public void set(String group, String profile, String key, String value)
		{
			put(group, profile, key, value);
		}
	}

	@Test
	public void copiesEveryOldPlainAndAccountKeyIntoAnEmptyNewGroup()
	{
		MemoryStore store = new MemoryStore();
		store.profiles.add(ACCOUNT);
		store.put(OLD, null, "syncEnabled", "true");
		store.put(OLD, null, "serverUrl", "http://127.0.0.1:8080");
		store.put(OLD, ACCOUNT, "loadouts", "[{\"name\":\"pure\"}]");

		int copied = ConfigGroupMigration.migrate(store, OLD, NEW);

		assertEquals(3, copied);
		assertEquals("true", store.get(NEW, null, "syncEnabled"));
		assertEquals("http://127.0.0.1:8080", store.get(NEW, null, "serverUrl"));
		assertEquals("[{\"name\":\"pure\"}]", store.get(NEW, ACCOUNT, "loadouts"));
	}

	@Test
	public void neverOverwritesAValueTheNewGroupAlreadyHolds()
	{
		MemoryStore store = new MemoryStore();
		store.put(OLD, null, "serverUrl", "http://old");
		store.put(OLD, null, "syncEnabled", "true");
		store.put(NEW, null, "serverUrl", "http://new");

		int copied = ConfigGroupMigration.migrate(store, OLD, NEW);

		assertEquals(1, copied);
		assertEquals("http://new", store.get(NEW, null, "serverUrl"));
		assertEquals("true", store.get(NEW, null, "syncEnabled"));
	}

	/** Once means once: a value the player changes or clears after the copy is never put back. */
	@Test
	public void runsOnceEvenAfterTheMigratedValueIsChangedOrCleared()
	{
		MemoryStore store = new MemoryStore();
		store.put(OLD, null, "syncEnabled", "true");
		store.put(OLD, null, "serverUrl", "http://old");
		ConfigGroupMigration.migrate(store, OLD, NEW);

		store.put(NEW, null, "syncEnabled", "false");
		store.values.remove("questroute.serverUrl");

		assertEquals(0, ConfigGroupMigration.migrate(store, OLD, NEW));
		assertEquals("false", store.get(NEW, null, "syncEnabled"));
		assertNull(store.get(NEW, null, "serverUrl"));
	}

	@Test
	public void aFreshInstallWithNoOldGroupCopiesNothingButStillMarksItDone()
	{
		MemoryStore store = new MemoryStore();

		assertEquals(0, ConfigGroupMigration.migrate(store, OLD, NEW));
		assertEquals("true", store.get(NEW, null, ConfigGroupMigration.MARKER));
	}

	@Test
	public void theOldGroupIsReadAndNeverWritten()
	{
		MemoryStore store = new MemoryStore();
		store.put(OLD, null, "syncEnabled", "true");

		ConfigGroupMigration.migrate(store, OLD, NEW);

		assertEquals("true", store.get(OLD, null, "syncEnabled"));
		assertNull(store.get(OLD, null, ConfigGroupMigration.MARKER));
	}
}
