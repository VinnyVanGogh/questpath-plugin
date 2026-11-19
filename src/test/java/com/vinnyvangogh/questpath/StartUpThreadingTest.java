package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Regression: toggling the plugin on ran startUp on the Swing thread, startUp
 * called refresh() inline, refresh() reads quest state through a client script
 * that asserts the client thread, so startUp threw and RuneLite turned the
 * plugin straight back off ("Quest Route won't turn on").
 */
public class StartUpThreadingTest
{
	@Test
	public void refreshIsQueuedNotRunInline()
	{
		List<Runnable> queued = new ArrayList<>();
		AtomicInteger refreshed = new AtomicInteger();

		QuestpathPlugin.deferRefresh(queued::add, () -> true, refreshed::incrementAndGet);

		assertEquals("nothing may run on the calling (Swing) thread", 0, refreshed.get());
		assertEquals(1, queued.size());
		queued.get(0).run();
		assertEquals(1, refreshed.get());
	}

	@Test
	public void refreshIsSkippedIfPluginStoppedBeforeClientThreadRuns()
	{
		List<Runnable> queued = new ArrayList<>();
		AtomicInteger refreshed = new AtomicInteger();

		QuestpathPlugin.deferRefresh(queued::add, () -> false, refreshed::incrementAndGet);
		queued.get(0).run();

		assertEquals(0, refreshed.get());
	}

	@Test
	public void graphFetchIsNotASavedSettingAnyMore()
	{
		for (java.lang.reflect.Method method : QuestpathConfig.class.getMethods())
		{
			assertFalse("a saved setting must not be able to switch on a localhost fetch",
				method.getName().equals("fetchQuestGraph"));
		}
		assertFalse("developer flag is off unless set on the JVM",
			Boolean.getBoolean(QuestpathPlugin.DEV_FETCH_PROPERTY));
	}
}
