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
	public void noServerSyncSettingExists()
	{
		for (java.lang.reflect.Method method : QuestpathConfig.class.getMethods())
		{
			String name = method.getName();
			assertFalse(name + " would reconnect Quest Route to a server",
				name.equals("fetchQuestGraph") || name.equals("syncEnabled")
					|| name.equals("serverUrl") || name.equals("pairingToken"));
		}
	}

	@Test
	public void mainCodeMakesNoHttpRequests() throws java.io.IOException
	{
		try (java.util.stream.Stream<java.nio.file.Path> files =
			java.nio.file.Files.walk(java.nio.file.Paths.get("src/main/java")))
		{
			for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files.filter(
				p -> p.toString().endsWith(".java"))::iterator)
			{
				String source = new String(java.nio.file.Files.readAllBytes(file),
					java.nio.charset.StandardCharsets.UTF_8);
				assertFalse(file + " imports OkHttp; this build is local-only",
					source.contains("import okhttp3"));
			}
		}
	}
}
