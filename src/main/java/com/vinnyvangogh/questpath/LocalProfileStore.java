package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads and writes one {@link LocalProfile} per account as a JSON string in
 * RuneLite's own config, under {@value #GROUP} with the key
 * {@code profile.<account hash>}.
 *
 * <p>Config, not files: Plugin Hub rules do not let a plugin write its own
 * files, and a profile is small. One config value is replaced whole, so a
 * crash can never leave half of a new profile behind.
 *
 * <p>The storage is a constructor argument ({@link Backend}) so tests run
 * against a map instead of a real {@link ConfigManager}.
 *
 * <p>Never throws for bad content. A missing value, an empty one, one that is
 * not JSON, one from a newer build, and one naming an account type this
 * build does not know are all answered with a {@link Result} the caller can
 * show, because the alternative -- a plugin that fails to start over a
 * hand-edited value -- is worse than asking the player to run setup again.
 * A damaged value is moved aside to {@code profile.<hash>.corrupt} rather
 * than deleted, so it can be inspected.
 */
@Slf4j
final class LocalProfileStore
{
	/**
	 * Its own group, not {@link QuestpathConfig#GROUP}: these are data, not
	 * settings, and nothing listening for settings changes should see them.
	 */
	static final String GROUP = "questrouteprofile";

	private static final String PREFIX = "profile.";
	private static final String CORRUPT_SUFFIX = ".corrupt";

	/** Account hashes are numbers; anything else is refused before it reaches a key. */
	private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	/** The few config calls this needs; {@link #of(ConfigManager)} adapts the real one. */
	interface Backend
	{
		String get(String key);

		void set(String key, String value);

		void unset(String key);
	}

	static Backend of(ConfigManager configManager)
	{
		return new Backend()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setConfiguration(GROUP, key, value);
			}

			@Override
			public void unset(String key)
			{
				configManager.unsetConfiguration(GROUP, key);
			}
		};
	}

	enum Status
	{
		/** Nothing saved: first run (or after a reset). */
		ABSENT,
		/** A usable profile. */
		LOADED,
		/** Something was saved but could not be used; it was moved aside. */
		CORRUPT
	}

	static final class Result
	{
		private final Status status;
		private final LocalProfile profile;

		Result(Status status, LocalProfile profile)
		{
			this.status = status;
			this.profile = profile;
		}

		Status status()
		{
			return status;
		}

		/** Non-null only when {@link #status()} is {@link Status#LOADED}. */
		LocalProfile profile()
		{
			return profile;
		}
	}

	private final Backend backend;
	private final Gson gson;

	LocalProfileStore(Backend backend, Gson gson)
	{
		this.backend = backend;
		this.gson = gson;
	}

	/** The account key for a client account hash, or null when it is not known yet (-1). */
	static String keyFor(long accountHash)
	{
		return accountHash == -1L ? null : Long.toUnsignedString(accountHash);
	}

	/** The config key a profile is saved under. */
	static String configKey(String accountKey)
	{
		if (accountKey == null || !SAFE_KEY.matcher(accountKey).matches())
		{
			throw new IllegalArgumentException("unsafe account key");
		}
		return PREFIX + accountKey;
	}

	/** Where a profile that could not be read is set aside. */
	static String corruptKey(String accountKey)
	{
		return configKey(accountKey) + CORRUPT_SUFFIX;
	}

	Result load(String accountKey)
	{
		String key = configKey(accountKey);
		String json = backend.get(key);
		if (json == null)
		{
			return new Result(Status.ABSENT, null);
		}

		LocalProfile profile;
		try
		{
			profile = gson.fromJson(json, LocalProfile.class);
		}
		catch (JsonParseException e)
		{
			log.debug("questroute: profile unreadable: {}", e.toString());
			return quarantine(accountKey, json);
		}
		if (profile == null || !profile.isUsable())
		{
			// An empty value parses to null; a parsed one can still be
			// unusable (unknown account type, newer schema).
			return quarantine(accountKey, json);
		}
		return new Result(Status.LOADED, profile);
	}

	/** Replaces the saved profile for this account in one config write. */
	void save(String accountKey, LocalProfile profile)
	{
		backend.set(configKey(accountKey), gson.toJson(profile));
	}

	/** Wipes the saved profile (and any set-aside copy) for this account. */
	void reset(String accountKey)
	{
		backend.unset(configKey(accountKey));
		backend.unset(corruptKey(accountKey));
	}

	private Result quarantine(String accountKey, String json)
	{
		try
		{
			backend.set(corruptKey(accountKey), json);
			backend.unset(configKey(accountKey));
		}
		catch (RuntimeException e)
		{
			log.debug("questroute: could not move a bad profile aside: {}", e.toString());
		}
		return new Result(Status.CORRUPT, null);
	}
}
