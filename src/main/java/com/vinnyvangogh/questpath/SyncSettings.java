package com.vinnyvangogh.questpath;

/**
 * The sync configuration, as the panel shows it back.
 *
 * <p>RuneLite's config panel is several clicks away and behind a different
 * icon, so the Sync tab shows the settings as well as their effect. "Sent 3m
 * ago" does not say which server it went to, and that is the first thing
 * anyone checks when it went somewhere unexpected.
 *
 * <p>The token is never shown, only whether one is set. It is marked secret in
 * the config for a reason, and reprinting it in a panel that ends up in
 * screenshots would undo that.
 */
class SyncSettings
{
	private final boolean enabled;
	private final String serverUrl;
	private final boolean hasToken;

	SyncSettings(boolean enabled, String serverUrl, boolean hasToken)
	{
		this.enabled = enabled;
		this.serverUrl = serverUrl;
		this.hasToken = hasToken;
	}

	static SyncSettings from(QuestpathConfig config)
	{
		String token = config.pairingToken();
		return new SyncSettings(
			config.syncEnabled(),
			config.serverUrl() == null || config.serverUrl().trim().isEmpty()
				? "not set" : config.serverUrl().trim(),
			token != null && !token.isEmpty());
	}

	boolean enabled()
	{
		return enabled;
	}

	String serverUrl()
	{
		return serverUrl;
	}

	boolean hasToken()
	{
		return hasToken;
	}
}
