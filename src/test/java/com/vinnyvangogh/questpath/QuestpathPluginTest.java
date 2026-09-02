package com.vinnyvangogh.questpath;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a developer-mode RuneLite client with this plugin side-loaded.
 * Run it with {@code ./gradlew run}. Not a test — the real tests live beside
 * it.
 */
public class QuestpathPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(QuestpathPlugin.class);
		RuneLite.main(args);
	}
}
