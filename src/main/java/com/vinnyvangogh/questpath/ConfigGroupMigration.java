package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;

/**
 * Carries settings saved under the pre-rename config group over to the
 * current one, once.
 *
 * <p>The plugin was {@code questpath} until the first Plugin Hub submission;
 * that name belongs to another author's live plugin, so the Hub name, the
 * display name and the config group all became Quest Route together. Nothing
 * was published under the old group, so no real user has settings there --
 * but the developer's own profile does, and losing every toggle, the server
 * URL and the per-account loadouts and build rules to a rename is exactly the
 * silent reset the rename rule exists to stop.
 *
 * <p><b>Runs from the config provider, before RuneLite writes defaults.</b>
 * {@code ConfigManager#setDefaultConfiguration} fills every unset key in the
 * group with its default when the plugin's config is first provided, so by
 * {@code startUp} the new group is never empty and "is it empty" cannot tell
 * a fresh install from a migrated one. Run first, the copy lands before the
 * defaults and the defaults only fill what the old group never had.
 *
 * <p><b>Never overwrites, and runs once.</b> A key the new group already
 * holds is left alone, whatever the old group says. {@link #MARKER} is
 * written when the copy finishes, and its presence skips the whole thing on
 * every later start -- including after the player resets the plugin's
 * settings, which unsets only declared config items, never this marker. The
 * old group is read and never written or cleared, so the copy is always
 * recoverable by hand.
 *
 * <p>Plain keys and every RuneScape-profile key ({@link LoadoutStore} and
 * {@link LocalBuildRules} keep per-account data there) are both carried.
 */
@Slf4j
final class ConfigGroupMigration
{
	/** Written into the new group once the copy has run. Not a config item, so a settings reset leaves it. */
	static final String MARKER = "migratedFromQuestpath";

	/** Where config lives. The seam exists because {@code ConfigManager} cannot be built in a unit test. */
	interface Store
	{
		/** The RuneScape profile keys that hold any per-account config at all. */
		List<String> profiles();

		/** Key names in group, without the group prefix. profile null means the plain, non-account keys. */
		List<String> keys(String group, String profile);

		String get(String group, String profile, String key);

		void set(String group, String profile, String key, String value);
	}

	private ConfigGroupMigration()
	{
	}

	/**
	 * Copies every key from from into to that to does not already hold.
	 *
	 * @return how many values were copied; 0 when the marker says this already ran
	 */
	static int migrate(Store store, String from, String to)
	{
		if (store.get(to, null, MARKER) != null)
		{
			return 0;
		}
		List<String> profiles = new ArrayList<>();
		profiles.add(null);
		profiles.addAll(store.profiles());

		int copied = 0;
		for (String profile : profiles)
		{
			copied += copyProfile(store, from, to, profile);
		}
		store.set(to, null, MARKER, "true");
		return copied;
	}

	private static int copyProfile(Store store, String from, String to, String profile)
	{
		int copied = 0;
		for (String key : store.keys(from, profile))
		{
			String value = store.get(from, profile, key);
			if (value == null || store.get(to, profile, key) != null)
			{
				continue;
			}
			store.set(to, profile, key, value);
			copied++;
		}
		return copied;
	}

	/** The production {@link Store}, over RuneLite's own {@link ConfigManager}. */
	static Store of(ConfigManager configManager)
	{
		return new Store()
		{
			@Override
			public List<String> profiles()
			{
				List<String> keys = new ArrayList<>();
				for (RuneScapeProfile profile : configManager.getRSProfiles())
				{
					keys.add(profile.getKey());
				}
				return keys;
			}

			@Override
			public List<String> keys(String group, String profile)
			{
				if (profile != null)
				{
					return configManager.getRSProfileConfigurationKeys(group, profile, "");
				}
				String prefix = group + ".";
				List<String> keys = new ArrayList<>();
				for (String whole : configManager.getConfigurationKeys(prefix))
				{
					keys.add(whole.substring(prefix.length()));
				}
				return keys;
			}

			@Override
			public String get(String group, String profile, String key)
			{
				return configManager.getConfiguration(group, profile, key);
			}

			@Override
			public void set(String group, String profile, String key, String value)
			{
				configManager.setConfiguration(group, profile, key, value);
			}
		};
	}

	/** Runs {@link #migrate} against the live config, logging rather than throwing: a failed copy must not stop the plugin loading. */
	static void run(ConfigManager configManager)
	{
		try
		{
			int copied = migrate(of(configManager), QuestpathConfig.LEGACY_GROUP, QuestpathConfig.GROUP);
			if (copied > 0)
			{
				log.debug("Quest Route: carried {} setting(s) over from the old \"{}\" config group",
					copied, QuestpathConfig.LEGACY_GROUP);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Quest Route: could not carry settings over from the old config group", e);
		}
	}
}
