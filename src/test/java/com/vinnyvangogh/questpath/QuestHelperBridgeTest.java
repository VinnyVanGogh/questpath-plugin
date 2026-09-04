package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestHelperBridge}'s reflection <i>logic</i> -- that it finds a
 * method by name, invokes it, and degrades to the right {@link
 * QuestHelperBridge.Availability} or {@link QuestHelperBridge.StartOutcome}
 * when each step is missing, unset, or throws.
 *
 * <p><b>What this class does not prove.</b> Quest Helper is not on this
 * project's compile or test classpath, so nothing here touches the real
 * {@code com.questhelper.QuestHelperPlugin}. Every fixture below is a small
 * stand-in built for this file, wired in through
 * {@link QuestHelperBridge}'s test constructor, which overrides the class
 * and method names the production constructor hard-codes. A passing suite
 * says the bridge's own logic is correct; it says nothing about whether the
 * real Quest Helper still exposes {@code getQuestManager()}, {@code
 * getSelectedQuest()}, {@code startUpQuest(QuestHelper, boolean)}, or
 * {@code QuestHelperQuest#getByName}/{@code getQuest}/{@code getName} on the
 * shapes this class expects. That is what the warn-level log at runtime is
 * for -- see {@link QuestHelperBridge}'s class doc -- not this suite.
 */
public class QuestHelperBridgeTest
{
	/** Stand-in for {@code com.questhelper.questhelpers.QuestHelper}: the fake lookup just hands back the name itself. */
	private static final String QUEST_HELPER_CLASS_NAME = String.class.getName();

	/** Stand-in for {@code com.questhelper.QuestHelperPlugin}: a public quest-manager getter and a public selected-quest getter, nothing else. */
	private static class FakePlugin
	{
		private Object questManager;
		private Object selectedQuest;

		public Object getQuestManager()
		{
			return questManager;
		}

		public Object getSelectedQuest()
		{
			return selectedQuest;
		}
	}

	/** Stand-in for {@code com.questhelper.managers.QuestManager}: one public method that can be told to fail. */
	private static class FakeManager
	{
		private final List<String> started = new ArrayList<>();
		private boolean throwOnStart;

		public void startUpQuest(String questHelperName, boolean showSidebar)
		{
			if (throwOnStart)
			{
				throw new RuntimeException("Quest Helper's manager rejected the call");
			}
			started.add(questHelperName);
		}
	}

	/** Stand-in for {@code com.questhelper.questinfo.QuestHelperQuest#getByName}: a public static lookup that resolves a known name to itself. */
	private static class FakeLookup
	{
		private static final Set<String> KNOWN = new HashSet<>();

		public static Object lookup(String helperName)
		{
			return KNOWN.contains(helperName) ? helperName : null;
		}
	}

	/** Stand-in for {@code com.questhelper.questhelpers.QuestHelper}: one getter back to its quest constant. */
	private static class FakeSelectedQuest
	{
		private final Object quest;

		FakeSelectedQuest(Object quest)
		{
			this.quest = quest;
		}

		public Object getQuest()
		{
			return quest;
		}
	}

	/** Stand-in for a {@code QuestHelperQuest} constant: one getter for its display name. */
	private static class FakeQuestRef
	{
		private final String name;

		FakeQuestRef(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}
	}

	/** Sets plugin's selected quest so the read-back names selectedQuestName, or clears it when null. */
	private static void select(FakePlugin plugin, String selectedQuestName)
	{
		plugin.selectedQuest = selectedQuestName == null ? null : new FakeSelectedQuest(new FakeQuestRef(selectedQuestName));
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
		return bridge(plugins, enabled, Runnable::run, () -> true);
	}

	/** Overrides only the read-back timing and login state; every reflected name stays at its fixture default. */
	private static QuestHelperBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		Consumer<Runnable> postVerification, BooleanSupplier loggedIn)
	{
		return bridge(plugins, enabled, "getQuestManager", "startUpQuest", QUEST_HELPER_CLASS_NAME,
			FakeLookup.class.getName(), "lookup", "getSelectedQuest", "getQuest", "getName",
			postVerification, loggedIn);
	}

	/** Overrides the manager/lookup names only, e.g. to point at a name that does not exist. */
	private static QuestHelperBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		String questManagerMethodName, String startMethodName, String questHelperClassName,
		String lookupClass, String lookupMethod)
	{
		return bridge(plugins, enabled, questManagerMethodName, startMethodName, questHelperClassName,
			lookupClass, lookupMethod, "getSelectedQuest", "getQuest", "getName", Runnable::run, () -> true);
	}

	private static QuestHelperBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		String questManagerMethodName, String startMethodName, String questHelperClassName,
		String lookupClass, String lookupMethod, String selectedQuestMethod, String questMethod,
		String questNameMethod, Consumer<Runnable> postVerification, BooleanSupplier loggedIn)
	{
		return new QuestHelperBridge(plugins, enabled, FakePlugin.class.getName(), questManagerMethodName,
			startMethodName, questHelperClassName, lookupClass, lookupMethod, selectedQuestMethod,
			questMethod, questNameMethod, postVerification, loggedIn);
	}

	/** Runs start() and hands back whatever outcome it reported -- postVerification here runs inline, synchronously. */
	private static QuestHelperBridge.StartOutcome outcomeOf(QuestHelperBridge bridge, String questName)
	{
		AtomicReference<QuestHelperBridge.StartOutcome> result = new AtomicReference<>();
		bridge.start(questName, result::set);
		return result.get();
	}

	@Test
	public void notInstalledWhenNoMatchingPluginIsLoaded()
	{
		QuestHelperBridge bridge = bridge(java.util.Collections::emptyList, plugin -> true);
		assertEquals(QuestHelperBridge.Availability.NOT_INSTALLED, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.UNAVAILABLE, outcomeOf(bridge, "Cook's Assistant"));
	}

	@Test
	public void notEnabledWhenThePluginSaysSo()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> false);
		assertEquals(QuestHelperBridge.Availability.NOT_ENABLED, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.UNAVAILABLE, outcomeOf(bridge, "Cook's Assistant"));
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
	public void unavailableWhenTheQuestManagerMethodDoesNotExist()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true,
			"noSuchMethod", "startUpQuest", QUEST_HELPER_CLASS_NAME, FakeLookup.class.getName(), "lookup");
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.UNAVAILABLE, outcomeOf(bridge, "Cook's Assistant"));
	}

	/** getQuestManager() resolves but Guice has not populated it yet -- not a refactor, same degraded state regardless. */
	@Test
	public void unavailableWhenGetQuestManagerReturnsNull()
	{
		FakePlugin plugin = new FakePlugin();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
	}

	@Test
	public void unavailableWhenTheLookupClassCannotBeFound()
	{
		FakePlugin plugin = new FakePlugin();
		plugin.questManager = new FakeManager();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true,
			"getQuestManager", "startUpQuest", QUEST_HELPER_CLASS_NAME, "com.example.DoesNotExist", "lookup");
		assertEquals(QuestHelperBridge.Availability.UNAVAILABLE, bridge.availability("Cook's Assistant"));
	}

	@Test
	public void unsupportedQuestWhenTheLookupResolvesNothing()
	{
		FakePlugin plugin = new FakePlugin();
		plugin.questManager = new FakeManager();
		// FakeLookup.KNOWN left empty: the lookup runs and genuinely says no.
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.UNSUPPORTED_QUEST, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.UNAVAILABLE, outcomeOf(bridge, "Cook's Assistant"));
	}

	/**
	 * The call reaching {@code startUpQuest} without throwing is necessary but not
	 * sufficient -- the read-back has to agree Quest Helper actually selected it
	 * before this reports {@link QuestHelperBridge.StartOutcome#CONFIRMED}.
	 */
	@Test
	public void confirmedWhenQuestHelperActuallySelectsIt()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, "Cook's Assistant");
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.CONFIRMED, outcomeOf(bridge, "Cook's Assistant"));
		assertEquals(List.of("Cook's Assistant"), manager.started);
	}

	/**
	 * "Shield of Arrav" resolves to one concrete sub-helper this class always
	 * requests -- see {@link QuestHelperBridge}'s class doc for why that is a
	 * deliberate simplification of Quest Helper's own location-based pick,
	 * not a guess at it.
	 */
	@Test
	public void shieldOfArravAlwaysRequestsTheBlackArmGangSubHelper()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, "Shield of Arrav - Black Arm Gang");
		FakeLookup.KNOWN.add("Shield of Arrav - Black Arm Gang");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Shield of Arrav"));
		assertEquals(QuestHelperBridge.StartOutcome.CONFIRMED, outcomeOf(bridge, "Shield of Arrav"));
		assertEquals(List.of("Shield of Arrav - Black Arm Gang"), manager.started);
	}

	/**
	 * Recipe for Disaster always requests "RFD - Start", matching Quest
	 * Helper's own unconditional dispatch for that literal name. Quest
	 * Helper's own state can still move the actual selection to a different
	 * chapter afterward, which is exactly what {@code matchesStarted}'s
	 * "RFD" prefix exists to tolerate.
	 */
	@Test
	public void recipeForDisasterConfirmsEvenWhenQuestHelperSelectsADifferentChapter()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, "RFD - Dwarf");
		FakeLookup.KNOWN.add("RFD - Start");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Recipe for Disaster"));
		assertEquals(QuestHelperBridge.StartOutcome.CONFIRMED, outcomeOf(bridge, "Recipe for Disaster"));
		assertEquals(List.of("RFD - Start"), manager.started);
	}

	/** Logged in, called, but Quest Helper's own selection never became the quest asked for. */
	@Test
	public void notSelectedWhenLoggedInButQuestHelperNeverSelectedIt()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, null);
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.StartOutcome.NOT_SELECTED, outcomeOf(bridge, "Cook's Assistant"));
		// The call itself still went through -- this is Quest Helper silently
		// declining after the fact, not this side failing to call it.
		assertEquals(List.of("Cook's Assistant"), manager.started);
	}

	/**
	 * The one silent-failure path {@link QuestHelperBridge} can name: the
	 * account was not logged in at read-back time, checked via this plugin's
	 * own {@code Client} rather than anything reflected out of Quest Helper.
	 */
	@Test
	public void notLoggedInWhenTheAccountWasNotLoggedInAtReadBackTime()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, null);
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true, Runnable::run, () -> false);
		assertEquals(QuestHelperBridge.StartOutcome.NOT_LOGGED_IN, outcomeOf(bridge, "Cook's Assistant"));
	}

	/** The read-back chain itself cannot be reached -- honestly unverifiable, never guessed at as success. */
	@Test
	public void unverifiableWhenTheSelectedQuestMethodDoesNotExist()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true,
			"getQuestManager", "startUpQuest", QUEST_HELPER_CLASS_NAME, FakeLookup.class.getName(), "lookup",
			"noSuchSelectedQuestMethod", "getQuest", "getName", Runnable::run, () -> true);
		assertEquals(QuestHelperBridge.StartOutcome.UNVERIFIABLE, outcomeOf(bridge, "Cook's Assistant"));
		// Still genuinely called Quest Helper -- only the read-back is broken.
		assertEquals(List.of("Cook's Assistant"), manager.started);
	}

	/** Availability said yes; the handoff itself still fails. start() reports it, never pretends it worked. */
	@Test
	public void callFailedReportsWhenTheManagerThrows()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		manager.throwOnStart = true;
		plugin.questManager = manager;
		FakeLookup.KNOWN.add("Cook's Assistant");

		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(QuestHelperBridge.Availability.AVAILABLE, bridge.availability("Cook's Assistant"));
		assertEquals(QuestHelperBridge.StartOutcome.CALL_FAILED, outcomeOf(bridge, "Cook's Assistant"));
		assertTrue("the failed attempt is not recorded as started", manager.started.isEmpty());
	}

	/**
	 * Pins the deferred read-back itself, not just its result: the outcome must
	 * not exist until whatever postVerification was given actually runs, the
	 * way {@code clientThread::invokeLater} would run it later rather than
	 * inline. A bridge that read back immediately would pass every other test
	 * in this file by accident, on a fixture with no real asynchrony to expose
	 * it -- this is the one that would not.
	 */
	@Test
	public void readBackIsDeferredUntilPostVerificationRunsIt()
	{
		FakePlugin plugin = new FakePlugin();
		FakeManager manager = new FakeManager();
		plugin.questManager = manager;
		select(plugin, "Cook's Assistant");
		FakeLookup.KNOWN.add("Cook's Assistant");

		List<Runnable> queued = new ArrayList<>();
		QuestHelperBridge bridge = bridge(() -> List.of(plugin), p -> true, queued::add, () -> true);

		AtomicReference<QuestHelperBridge.StartOutcome> result = new AtomicReference<>();
		bridge.start("Cook's Assistant", result::set);

		assertEquals("the call itself is not deferred", List.of("Cook's Assistant"), manager.started);
		assertNull("the outcome must wait for postVerification to run", result.get());
		assertEquals(1, queued.size());

		queued.get(0).run();
		assertEquals(QuestHelperBridge.StartOutcome.CONFIRMED, result.get());
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
