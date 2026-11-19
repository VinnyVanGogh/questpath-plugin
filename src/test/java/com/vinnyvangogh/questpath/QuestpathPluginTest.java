package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.scenarios.EventKinds;
import com.vinnyvangogh.questpath.scenarios.ScenariosPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a developer-mode RuneLite client with this plugin side-loaded,
 * plus the "Scenarios (dev)" sidebar (events, Paste JSON, saved scenarios;
 * see docs/SCENARIOS.md). Run it with {@code ./gradlew run}. Not a test — the
 * real tests live beside it. It lives in src/test, so none of it reaches the
 * Plugin Hub build.
 */
public class QuestpathPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// "This plugin only" sends to this plugin on a private bus.
		ScenariosPlugin.use(EventKinds.core(), QuestpathPlugin.class);
		ExternalPluginManager.loadBuiltin(QuestpathPlugin.class, ScenariosPlugin.class);
		RuneLite.main(args);
	}
}
