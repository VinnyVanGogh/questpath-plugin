package com.vinnyvangogh.questpath;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * The plugin, with Quest Helper nowhere on the classpath.
 *
 * <p>The first Hub submission ships without the Quest Helper integration
 * (Board, 2026-11-14), so nothing this plugin loads may depend on Quest Helper
 * being installed, and none of the integration's settings may ship: a config
 * key freezes at the first published version, so one published for a feature
 * that is not there would have to stay forever.
 *
 * <p><b>What this does not prove.</b> {@code startUp} needs RuneLite's whole
 * injected stack -- a live {@code Client}, {@code ClientThread}, overlay and
 * toolbar managers -- and none of those can be built in a unit test. This
 * proves the plugin class and its config load and construct with Quest Helper
 * absent; the plugin actually starting in a client without Quest Helper is a
 * check for a human at {@code ./gradlew run}.
 */
public class QuestHelperAbsentTest
{
	@Test
	public void questHelperIsNotOnTheTestClasspath()
	{
		try
		{
			Class.forName("com.questhelper.QuestHelperPlugin");
			fail("Quest Helper is on the test classpath, so this test proves nothing about running without it");
		}
		catch (ClassNotFoundException expected)
		{
			// The point: everything below runs without it.
		}
	}

	@Test
	public void thePluginLoadsAndConstructsWithoutQuestHelper()
	{
		assertNotNull(new QuestpathPlugin());
		for (Field field : QuestpathPlugin.class.getDeclaredFields())
		{
			assertFalse("QuestpathPlugin still holds a Quest Helper type: " + field,
				field.getType().getName().contains("QuestHelper"));
		}
	}

	@Test
	public void noQuestHelperSettingShips()
	{
		Set<String> keys = new TreeSet<>();
		for (Method method : QuestpathConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
				assertFalse("a Quest Helper config item would freeze at first publish: " + item.keyName(),
					item.section().equals("questHelper"));
			}
		}
		for (String removed : new String[]{"autoAdvanceQuestHelper", "questHelperArrivalPrompt", "questStartArrivalDepth"})
		{
			assertFalse(removed + " must not ship without the integration it configures", keys.contains(removed));
		}
	}
}
