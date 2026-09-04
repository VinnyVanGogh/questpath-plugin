package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestHelperBridge}'s reflection <i>logic</i> -- that it finds a
 * field by name, invokes a method by name, and degrades to the right
 * {@link QuestHelperBridge.Availability} when each step is missing, unset, or
 * throws.
 *
 * <p><b>What this class does not prove.</b> Quest Helper is not on this
 * project's compile or test classpath, so nothing here touches the real
 * {@code com.questhelper.QuestHelperPlugin}. Every fixture below is a small
 * stand-in built for this file, wired in through
 * {@link QuestHelperBridge}'s test constructor, which overrides the class,
 * field and method names the production constructor hard-codes. A passing
 * suite says the bridge's own logic is correct; it says nothing about
 * whether the real Quest Helper still has a field named
 * {@code questMenuHandler}. That is what the warn-level log at runtime is
 * for -- see {@link QuestHelperBridge}'s class doc -- not this suite.
 */
public class QuestHelperBridgeTest
{
	/** Stand-in for {@code com.questhelper.QuestHelperPlugin}: one private field, nothing else. */
	private static class FakePlugin
	{
		private Object menuHandler;
	}

	/** Stand-in for {@code com.questhelper.managers.QuestMenuHandler}: one public method that can be told to fail. */
	private static class FakeMenuHandler
	{
		private final List<String> started = new ArrayList<>();
		private boolean throwOnStart;

		public void start(String questName)
		{
			if (throwOnStart)
			{
				throw new RuntimeException("Quest Helper's handler rejected the call");
			}
			started.add(questName);
		}
	}

	/** Stand-in for {@code com.questhelper.questinfo.QuestHelperQuest#getByName}: a public static lookup. */
	private static class FakeLookup
	{
		private static final Set<String> KNOWN = new HashSet<>();

		public static Object lookup(String questName)
		{
			return KNOWN.contains(questName) ? questName : null;
		}
	}

	@Before
	public void resetFakeLookup()
	{
		// Static by necessity -- getByName is static on the real class too --
		// so it has to be cleared between tests rather than rebuilt.
		FakeLookup.KNOWN.clear();
	}

	private static QuestHelperBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled)
	{
		return bridge(plugins, enabled, "menuHandler", "start", FakeLookup.class.getName(), "lookup");
	}

	private static QuestHelperBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		String fieldName, String startMethod, String lookupClass, String lookupMethod)
	{
		return new QuestHelperBridge(plugins, enabled, FakePlugin.class.getName(), fieldName, startMethod,
			lookupClass, lookupMethod);
	}

	@Test
	public void notInstalledWhenNoMatchingPluginIsLoaded()
	{
		QuestHelperBridge bridge = bridge(java.util.Collections::emptyList, plugin -> true);
		assertEquals(QuestHelperBridge.Availability.NOT_INSTALLED, bridge.availability("Cook's Assistant"));
		assertFalse(bridge.start("Cook's Assistant"));
	}

	@Test
	public void notEnabledWhenThePluginSaysSo()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> false);
		assertEquals(QuestHelperBridge.Availability.NOT_ENABLED, bridge.availability("Cook's Assistant"));
		assertFalse(bridge.start("Cook's Assistant"));
	}

	/** A thrown enabled-check is treated as "no", not as "we don't know" -- fail closed, not fail open. */
	@Test
	public void notEnabledWhenTheEnabledCheckItselfThrows()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p ->
		{
			throw new RuntimeException("cannot read plugin state");
		});
		assertEquals(QuestHelperBridge.Availability.NOT_ENABLED, bridge.availability("Cook's Assistant"));
	}

	@Test
	public void unavailableWhenTheHandlerFieldDoesNotExist()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true,
			"noSuchField", "start", FakeLookup.class.getName(), "lookup");
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
		assertFalse(bridge.start("Cook's Assistant"));
	}

	/** The field exists but Guice has not populated it yet -- not a refactor, same degraded state regardless. */
	@Test
	public void unavailableWhenTheHandlerFieldIsStillNull()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
	}

	@Test
	public void unavailableWhenTheLookupClassCannotBeFound()
	{
		FakePlugin plugin = new FakePlugin();
		plugin.menuHandler = new FakeMenuHandler();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true,
			"menuHandler", "start", "com.example.DoesNotExist", "lookup");
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
	}

	@Test
	public void unsupportedQuestWhenTheLookupResolvesNothing()
	{
		FakePlugin plugin = new FakePlugin();
		plugin.menuHandler = new FakeMenuHandler();
		// FakeLookup.KNOWN left empty: the lookup runs and genuinely says no.
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.UNSUPPORTED_QUEST, bridge.availability("Cook's Assistant"));
		assertFalse(bridge.start("Cook's Assistant"));
	}

	@Test
	public void availableAndStartsWhenTheLookupResolves()
	{
		FakePlugin plugin = new FakePlugin();
		FakeMenuHandler handler = new FakeMenuHandler();
		plugin.menuHandler = handler;
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Cook's Assistant"));
		assertTrue(bridge.start("Cook's Assistant"));
		assertEquals(List.of("Cook's Assistant"), handler.started);
	}

	/**
	 * "Shield of Arrav" and "Recipe for Disaster" bypass the lookup entirely --
	 * the real QuestMenuHandler special-cases exactly these two literal names
	 * because Quest Helper tracks each as several sub-helpers instead of one,
	 * so {@code getByName} on the plain name would wrongly say unsupported.
	 */
	@Test
	public void splitQuestNamesAreAvailableWithoutTheLookupResolvingThem()
	{
		FakePlugin plugin = new FakePlugin();
		FakeMenuHandler handler = new FakeMenuHandler();
		plugin.menuHandler = handler;
		// Deliberately not added to FakeLookup.KNOWN.

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Shield of Arrav"));
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Recipe for Disaster"));
		assertTrue(bridge.start("Shield of Arrav"));
		assertEquals(List.of("Shield of Arrav"), handler.started);
	}

	/** Availability said yes; the handoff itself still fails. start() reports it, never pretends it worked. */
	@Test
	public void startReportsFalseWhenTheHandlerThrows()
	{
		FakePlugin plugin = new FakePlugin();
		FakeMenuHandler handler = new FakeMenuHandler();
		handler.throwOnStart = true;
		plugin.menuHandler = handler;
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Cook's Assistant"));
		assertFalse(bridge.start("Cook's Assistant"));
		assertTrue("the failed attempt is not recorded as started", handler.started.isEmpty());
	}

	@Test
	public void everyAvailabilityCarriesANonEmptyLabel()
	{
		for (QuestHelperBridge.Availability availability : QuestHelperBridge.Availability.values())
		{
			assertFalse(availability.name(), availability.menuLabel().trim().isEmpty());
		}
	}
}
