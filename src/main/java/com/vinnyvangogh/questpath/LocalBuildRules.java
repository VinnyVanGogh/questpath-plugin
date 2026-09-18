package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * The build rules the player writes in the plugin itself.
 *
 * <p><b>Why this exists.</b> Until now a build rule could only reach the
 * plugin through a push from a running questpath instance, held in a volatile
 * field and cleared on logout. That is fine for a plugin that is a companion
 * to the web app and fatal for one that has to stand alone: with no rules, the
 * verdict engine never returns BUILD_BLOCKED and the lamp advisor greys
 * nothing — the guard is not merely reduced, it is absent, and it is absent
 * silently, which is worse. The player sees an interface with no warnings on
 * it and reasonably concludes there is nothing to warn about.
 *
 * <p>Stored per RuneScape account, not per RuneLite profile, through
 * {@code getRSProfileConfiguration}/{@code setRSProfileConfiguration} for
 * exactly the reason {@link LoadoutStore} documents at length: the plain
 * pair keys off the active RuneLite profile, so a second account logged in
 * later under the same profile would see and overwrite the first account's
 * rules. A build belongs to the account that is holding it.
 *
 * <p>A thin adapter over {@link ConfigManager} and nothing else. All the
 * logic — parsing, merging, one-rule-per-skill — is in {@link
 * BuildRuleCodec}, where it can be tested without faking a class whose only
 * constructor is private.
 */
@Singleton
class LocalBuildRules
{
	/**
	 * The config key the rules live under.
	 *
	 * <p>Frozen at first release along with the config group: renaming it
	 * silently resets every player's rules, and these are rules whose whole
	 * purpose is to stop an irreversible mistake.
	 */
	static final String KEY = "localBuildRules";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	LocalBuildRules(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Every rule this account has written here.
	 *
	 * <p>Empty before an account has finished logging in, because
	 * {@code getRSProfileKey()} is null until then and there is no honest
	 * answer to "which account's rules" yet. Empty is correct: it is the
	 * absence of a claim, not a claim that the account has no rules.
	 */
	synchronized List<BuildRule> all()
	{
		if (configManager == null || configManager.getRSProfileKey() == null)
		{
			return Collections.emptyList();
		}
		String stored = configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, KEY);
		return BuildRuleCodec.decode(gson, stored);
	}

	/** Whether this account has written any rules of its own. */
	synchronized boolean any()
	{
		return !all().isEmpty();
	}

	/**
	 * Sets or replaces the rule on one skill.
	 *
	 * @return the rules as they now stand, so a caller can refresh without a
	 *     second read
	 */
	synchronized List<BuildRule> set(String skill, String type, int value, String note)
	{
		return save(BuildRuleCodec.set(all(), skill, type, value, note));
	}

	/** Removes this account's rule on one skill. */
	synchronized List<BuildRule> clear(String skill)
	{
		return save(BuildRuleCodec.clear(all(), skill));
	}

	/** Removes every rule this account has written here. */
	synchronized List<BuildRule> clearAll()
	{
		return save(Collections.emptyList());
	}

	private List<BuildRule> save(List<BuildRule> rules)
	{
		if (configManager == null || configManager.getRSProfileKey() == null)
		{
			// Nothing to key the write against. Dropped rather than written
			// to the profile-scoped store as a fallback: that store is shared
			// between accounts, and silently putting one account's caps where
			// another account will read them is the exact failure the
			// RSProfile pair exists to prevent.
			return Collections.emptyList();
		}
		configManager.setRSProfileConfiguration(
			QuestpathConfig.GROUP, KEY, BuildRuleCodec.encode(gson, rules));
		return rules;
	}
}
