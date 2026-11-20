package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * The build rules this plugin saved before Build Guard took over editing them.
 *
 * <p>Read-only now. They stay in the per-account config pair they were written
 * to ({@code getRSProfileConfiguration}, never the profile-scoped pair, which
 * a second account under the same RuneLite profile would share), so nothing is
 * lost: with Build Guard installed they are handed over once, and without it
 * they remain as the read-only "Your saved build".
 *
 * <p>Both config keys are frozen; renaming either would silently drop a
 * player's rules.
 */
@Singleton
class LocalBuildRules
{
	/** Where the rules were written. */
	static final String KEY = "localBuildRules";

	/** Set once the rules have been sent to Build Guard, per account. */
	static final String MIGRATED_KEY = "buildRulesMigrated";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	LocalBuildRules(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Every rule this account saved here. Empty before the account has finished
	 * logging in, which is the absence of a claim rather than a claim of none.
	 */
	synchronized List<BuildRule> all()
	{
		if (configManager == null || configManager.getRSProfileKey() == null)
		{
			return Collections.emptyList();
		}
		return BuildRuleCodec.decode(gson, configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, KEY));
	}

	/** Whether the saved rules have already gone to Build Guard. */
	synchronized boolean migrated()
	{
		return configManager != null && configManager.getRSProfileKey() != null
			&& Boolean.parseBoolean(configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, MIGRATED_KEY));
	}

	/** Records the hand-over. The rules themselves are left where they are. */
	synchronized void markMigrated()
	{
		if (configManager != null && configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, MIGRATED_KEY, "true");
		}
	}
}
