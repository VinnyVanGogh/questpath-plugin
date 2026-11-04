package com.vinnyvangogh.questpath;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands a quest off to Quest Helper's own "start this quest" flow.
 *
 * <p>The whole handoff is reachable through Quest Helper's <b>public</b>
 * API: {@code QuestHelperPlugin#getQuestManager()} returns the manager that
 * can start a quest, {@code QuestHelperQuest#getByName(String)} resolves a
 * quest name to the object that manager needs, {@code
 * QuestManager#startUpQuest(QuestHelper, boolean)} does the starting, and
 * {@code QuestHelperPlugin#getSelectedQuest()} reads back what actually got
 * selected. None of that is reached by {@code getDeclaredField} or {@code
 * setAccessible} -- this class touches no private state in Quest Helper at
 * all.
 *
 * <p><b>How that was confirmed, and why it was not obvious.</b> An earlier
 * version of this class believed the opposite: that {@code
 * QuestHelperPlugin}'s fields were the only door, because reading Quest
 * Helper's GitHub source shows {@code questMenuHandler} and {@code
 * questManager} declared {@code private} with no visible getter. That
 * belief was wrong, and the source was not lying so much as incomplete --
 * Quest Helper's fields carry Lombok's {@code @Getter}, which generates the
 * accessor at compile time. The generated method exists in the {@code
 * .class} file and never appears in the {@code .java} source a human or an
 * LLM reads on GitHub. Reading source and reasoning "no getter, so private
 * field it is" misses exactly this case. What settled it was {@code javap}
 * against the actually-installed plugin jar in
 * {@code ~/.runelite/plugins/quest-helper_*.jar}, which lists {@code public
 * QuestManager getQuestManager()} and {@code public QuestHelper
 * getSelectedQuest()} in the bytecode whether or not they appear in any
 * source file. <b>The lesson, because it will recur:</b> a library built
 * with Lombok (or any other accessor-generating annotation processor) can
 * expose more publicly than its source shows, and the only way to be sure
 * is to check the compiled artifact actually being run, not the source
 * being read.
 *
 * <p>A second public name is depended on the same way, and used only as a
 * pre-check: {@code QuestHelperQuest#getByName(String)}, which returns null
 * for a quest name Quest Helper does not carry. It is checked before ever
 * calling {@code startUpQuest}, because that method is {@code void} and
 * silently does nothing on a helper it was not given -- called blind, this
 * class could never tell "worked" from "unsupported quest".
 *
 * <p><b>Calling {@code startUpQuest} is not proof it worked.</b> Quest
 * Helper's own {@code QuestManager} silently does nothing after a call in
 * three separate ways: it no-ops if the account is not logged in, it starts
 * a quest and immediately shuts it back down if the quest's first step
 * resolves to null, and even a genuine start may open no sidebar at all if
 * the player has {@code autoOpenSidebar} off. From the caller's side of a
 * {@code void} method, all three look identical to success. So this class
 * reads back what Quest Helper actually did: {@code
 * QuestHelperPlugin#getSelectedQuest()} names whichever quest Quest Helper
 * actually selected, if any. {@link #start} compares that against the name
 * asked for and only then reports success.
 *
 * <p>{@code QuestManager#startUpQuest(QuestHelper, boolean)} dispatches
 * through {@code ClientThread#invokeLater} when not already called from it,
 * so the selection is not set synchronously with our call. The read-back is
 * deliberately deferred through {@link #postVerification} (RuneLite's real
 * {@code clientThread::invokeLater} in production) rather than performed
 * inline, so it runs after Quest Helper's own deferred write -- both go
 * through the same queue, in the order they were posted.
 *
 * <p>"Shield of Arrav" and "Recipe for Disaster" are not single {@code
 * QuestHelperQuest} entries -- Quest Helper tracks each as several
 * separately named sub-helpers instead ("Shield of Arrav - Phoenix Gang" /
 * "Shield of Arrav - Black Arm Gang", and "RFD - " followed by a chapter
 * name for every Recipe for Disaster sub-quest), so {@code getByName} on
 * the plain game name returns null for both. {@link
 * #SPLIT_QUEST_DEFAULT_HELPER} maps each plain name to one concrete
 * sub-helper name to actually request: Recipe for Disaster always resolves
 * to "RFD - Start" because Quest Helper's own generic dispatch does exactly
 * that regardless of where the account already is in the questline --
 * confirmed by decompiling its handling of that literal string, not
 * guessed. Shield of Arrav's real dispatch instead reads the player's
 * on-screen world position against a zone private to Quest Helper, which
 * this class does not have and will not duplicate -- copying undiscoverable
 * coordinates out of decompiled bytecode is exactly the kind of silent,
 * unnoticed drift this integration exists to avoid, so it always requests
 * "Shield of Arrav - Black Arm Gang" instead. That is a known, deliberate
 * simplification versus what Quest Helper's own menu would have picked
 * standing in the wrong spot, not a bug; Quest Helper's own UI still lets
 * the player switch gangs afterward. {@link #matchesStarted} treats a
 * selection starting with "Shield of Arrav" or "RFD" as a match for these
 * two requested names, because what Quest Helper actually selects can still
 * diverge from what was requested -- its own state machine may move the
 * selection to whichever chapter the account's progress actually calls for.
 *
 * <p><b>What breaks, and how it is noticed.</b> If Quest Helper renames
 * {@code getQuestManager}, {@code startUpQuest}, {@code getByName}, {@code
 * getSelectedQuest}, {@code getQuest}, or {@code QuestHelperQuest#getName},
 * changes any of their signatures, or removes any of them, every call here
 * starts returning {@link Availability#UNAVAILABLE} (for the pre-check
 * names) or {@link StartOutcome#UNVERIFIABLE} (for the read-back names)
 * instead of working. That failure is loud: a warn-level log line names the
 * method that stopped resolving, at the moment it stops resolving. It is
 * never silent and never reported as success. All of it is confined to this
 * one file -- no other class in this plugin reflects into {@code
 * com.questhelper.*} -- so a Quest Helper refactor is a one-file fix.
 *
 * <p>None of this requires Quest Helper on this project's compile or test
 * classpath: every call after finding the running plugin instance goes
 * through {@code java.lang.reflect}, resolved through that instance's own
 * class loader rather than this plugin's, because RuneLite loads external
 * plugins each on their own loader. That is the reason reflection is still
 * here at all -- it is late binding to an unavailable-at-compile-time type,
 * never a way to reach past an access modifier.
 */
@Slf4j
class QuestHelperBridge
{
	/** Quest Helper's own plugin class, matched by name -- see the class doc. */
	static final String PLUGIN_CLASS_NAME = "com.questhelper.QuestHelperPlugin";

	/** The public method on {@link #PLUGIN_CLASS_NAME} that returns its quest manager. */
	static final String QUEST_MANAGER_METHOD_NAME = "getQuestManager";

	/** The public method on the manager that starts a quest, given the resolved helper. */
	static final String START_METHOD_NAME = "startUpQuest";

	/** {@code startUpQuest}'s first parameter type, needed to resolve its exact overload reflectively. */
	static final String QUEST_HELPER_CLASS_NAME = "com.questhelper.questhelpers.QuestHelper";

	/** Quest Helper's own quest-name lookup, used both as a pre-check and to resolve what to start. */
	static final String QUEST_LOOKUP_CLASS_NAME = "com.questhelper.questinfo.QuestHelperQuest";

	/** The public static method on {@link #QUEST_LOOKUP_CLASS_NAME} that performs that lookup. */
	static final String LOOKUP_METHOD_NAME = "getByName";

	/** The public method on {@link #PLUGIN_CLASS_NAME} that names whatever quest Quest Helper actually selected, if any. */
	static final String SELECTED_QUEST_METHOD_NAME = "getSelectedQuest";

	/** The public method on a selected quest that names its {@code QuestHelperQuest} constant. */
	static final String QUEST_METHOD_NAME = "getQuest";

	/** The public method on a {@code QuestHelperQuest} constant that names it. */
	static final String QUEST_NAME_METHOD_NAME = "getName";

	/**
	 * questpath's quest names mapped to the helper name Quest Helper actually
	 * registers, for every quest where the two disagree.
	 *
	 * <p><b>Reported from play: both Recipe for Disaster sub-quests showed
	 * "Not supported by Quest Helper" when Quest Helper supports all ten.</b>
	 * This map only carried the bare string "Recipe for Disaster", and
	 * questpath names the sub-quests after the wiki's own page titles --
	 * "Recipe for Disaster/Another Cook's Quest". Those matched nothing, so
	 * {@code getByName} returned null and the menu reported the quest
	 * unsupported. It was a naming mismatch presented as a capability gap,
	 * which is the worst way to get this wrong: the player believes the other
	 * plugin cannot do it.
	 *
	 * <p>The right-hand side is Quest Helper's own display name, read from
	 * the installed jar with {@code javap -c -p} against {@code
	 * com.questhelper.questinfo.QuestHelperQuest} rather than guessed -- the
	 * enum constants and their strings do not follow one pattern
	 * ({@code RECIPE_FOR_DISASTER_WARTFACE_AND_BENTNOZE} is "RFD - Wartface &
	 * Bentnoze" and is questpath's "Freeing the Goblin generals"; {@code
	 * MONKEY_AMBASSADOR} is "Freeing King Awowogei"; {@code FINALE} is
	 * "Defeating the Culinaromancer"). Anything derived from questpath's own
	 * name by string surgery would have got at least three of these wrong.
	 *
	 * <p><b>The display names are not stable.</b> Quest Helper v4.18.1
	 * (Zoinkwiz/quest-helper#2843, the release the Plugin Hub pins at
	 * {@code 02315c94}) renamed "RFD - Dwarf" to "RFD - Mountain Dwarf" and
	 * "RFD - Monkey Ambassador" to "RFD - King Awowogei" without renaming the
	 * enum constants. Until this map followed, those two chapters reported
	 * "Not supported by Quest Helper" again. {@code QuestHelperBridgeTest}
	 * pins every right-hand side to the names at that commit; when the Hub
	 * pin moves, re-read the enum and update both together.
	 *
	 * <p>Map.ofEntries rather than Map.of: there are eleven pairs and Map.of
	 * stops at ten. Package-private so the test can check every value.
	 */
	static final Map<String, String> SPLIT_QUEST_DEFAULT_HELPER = Map.ofEntries(
		Map.entry("Shield of Arrav", "Shield of Arrav - Black Arm Gang"),
		Map.entry("Recipe for Disaster", "RFD - Start"),
		Map.entry("Recipe for Disaster/Another Cook's Quest", "RFD - Start"),
		Map.entry("Recipe for Disaster/Freeing the Mountain Dwarf", "RFD - Mountain Dwarf"),
		Map.entry("Recipe for Disaster/Freeing the Goblin generals", "RFD - Wartface & Bentnoze"),
		Map.entry("Recipe for Disaster/Freeing Pirate Pete", "RFD - Pirate Pete"),
		Map.entry("Recipe for Disaster/Freeing the Lumbridge Guide", "RFD - Lumbridge Guide"),
		Map.entry("Recipe for Disaster/Freeing Evil Dave", "RFD - Evil Dave"),
		Map.entry("Recipe for Disaster/Freeing King Awowogei", "RFD - King Awowogei"),
		Map.entry("Recipe for Disaster/Freeing Sir Amik Varze", "RFD - Sir Amik Varze"),
		Map.entry("Recipe for Disaster/Freeing Skrach Uglogwee", "RFD - Skrach Uglogwee"),
		Map.entry("Recipe for Disaster/Defeating the Culinaromancer", "RFD - Finale"));

	/** What a right-click on a quest row can conclude about starting it in Quest Helper. */
	enum Availability
	{
		/** Reachable. The menu item is the action itself. */
		AVAILABLE("Start in Quest Helper"),
		NOT_INSTALLED("Quest Helper is not installed"),
		NOT_ENABLED("Quest Helper is not enabled"),
		UNSUPPORTED_QUEST("Not supported by Quest Helper"),
		/** Quest Helper is installed and enabled, but something reflection depends on did not resolve. */
		UNAVAILABLE("Quest Helper integration unavailable");

		private final String menuLabel;

		Availability(String menuLabel)
		{
			this.menuLabel = menuLabel;
		}

		/**
		 * The exact text to put in front of the player.
		 *
		 * <p>Doubles as the reason: a disabled menu item nobody can hover
		 * (Swing does not deliver mouse events, and so no tooltip, to a
		 * disabled component) still has to say why, so the why is the label
		 * itself rather than something only a tooltip would carry.
		 */
		String menuLabel()
		{
			return menuLabel;
		}
	}

	/**
	 * What became of a {@link #start} call, reported once Quest Helper has
	 * had a chance to react to it rather than the moment the reflective call
	 * returns. See the class doc for why the two are not the same thing.
	 */
	enum StartOutcome
	{
		/**
		 * {@link #availability} was not {@link Availability#AVAILABLE} at
		 * the moment the call actually landed -- availability changed
		 * between building the menu and clicking it, or a caller skipped
		 * the check. The handoff was never attempted.
		 */
		UNAVAILABLE,
		/**
		 * The reflective call to {@code startUpQuest} itself threw. Logged
		 * at warn already; see {@link #start}'s doc.
		 */
		CALL_FAILED,
		/** Quest Helper's own selection now names the quest that was asked for. */
		CONFIRMED,
		/**
		 * Quest Helper never selected it, and the account was not logged in
		 * at the moment of the read-back -- the one silent-failure path
		 * this class can name from outside. See the class doc.
		 */
		NOT_LOGGED_IN,
		/**
		 * Quest Helper never selected it, and the account was logged in.
		 * This is genuinely ambiguous from outside {@code QuestManager}: it
		 * covers both "the quest's first step resolved to null and Quest
		 * Helper shut itself back down" and any other reason Quest Helper
		 * declined to select it. Reported honestly as "did not start"
		 * rather than guessed at.
		 */
		NOT_SELECTED,
		/**
		 * The read-back itself could not be reached -- one of the {@code
		 * getSelectedQuest}/{@code getQuest}/{@code getName} names is
		 * missing or unreachable. Logged at warn. The handoff may still
		 * have worked; this class cannot tell, and does not claim to.
		 */
		UNVERIFIABLE,
	}

	private final Supplier<Collection<?>> plugins;
	private final Predicate<Object> pluginEnabled;
	private final String pluginClassName;
	private final String questManagerMethodName;
	private final String startMethodName;
	private final String questHelperClassName;
	private final String questLookupClassName;
	private final String lookupMethodName;
	private final String selectedQuestMethodName;
	private final String questMethodName;
	private final String questNameMethodName;

	/**
	 * Runs a read-back after Quest Helper has had a chance to act on {@link
	 * #start}'s call, on whatever thread Quest Helper itself needed for
	 * that -- RuneLite's {@code clientThread::invokeLater} in production.
	 * See the class doc.
	 */
	private final Consumer<Runnable> postVerification;

	/**
	 * Whether the account is logged in right now, checked at read-back time
	 * rather than call time because the two can straddle a login/logout.
	 * Answered by this plugin's own {@code Client}, not Quest Helper's --
	 * {@code net.runelite.api.Client} is RuneLite's shared game state, not
	 * something private to Quest Helper.
	 */
	private final BooleanSupplier loggedIn;

	/**
	 * Production wiring: the real Quest Helper coordinates, the real plugin
	 * list.
	 *
	 * @param plugins every plugin RuneLite currently knows about, e.g.
	 *     {@code pluginManager::getPlugins}
	 * @param pluginEnabled whether a given plugin instance is switched on,
	 *     e.g. {@code plugin -> pluginManager.isPluginEnabled((Plugin) plugin)}
	 * @param postVerification schedules the post-call read-back, e.g.
	 *     {@code clientThread::invokeLater}
	 * @param loggedIn whether the account is logged in right now, e.g.
	 *     {@code () -> client.getGameState() == GameState.LOGGED_IN}
	 */
	QuestHelperBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled,
		Consumer<Runnable> postVerification, BooleanSupplier loggedIn)
	{
		this(plugins, pluginEnabled, PLUGIN_CLASS_NAME, QUEST_MANAGER_METHOD_NAME, START_METHOD_NAME,
			QUEST_HELPER_CLASS_NAME, QUEST_LOOKUP_CLASS_NAME, LOOKUP_METHOD_NAME, SELECTED_QUEST_METHOD_NAME,
			QUEST_METHOD_NAME, QUEST_NAME_METHOD_NAME, postVerification, loggedIn);
	}

	/**
	 * Test seam: every coordinate this class depends on is overridable, so a
	 * test can point it at a small fixture class instead of the real Quest
	 * Helper, which is not on this project's classpath.
	 *
	 * <p>A test built on this constructor pins the reflection <i>logic</i> --
	 * that the manager is found, the helper is resolved and started, and
	 * each failure degrades to the right {@link Availability} or {@link
	 * StartOutcome}. It proves nothing about whether the real Quest Helper
	 * still exposes {@code getQuestManager()}, {@code getSelectedQuest()},
	 * {@code startUpQuest(QuestHelper, boolean)}, or {@code
	 * QuestHelperQuest#getByName}/{@code getQuest}/{@code getName} on the
	 * shapes this class expects. That is what the warn-level log at runtime
	 * is for -- see {@link QuestHelperBridge}'s class doc -- not this suite.
	 */
	QuestHelperBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled,
		String pluginClassName, String questManagerMethodName, String startMethodName,
		String questHelperClassName, String questLookupClassName, String lookupMethodName,
		String selectedQuestMethodName, String questMethodName, String questNameMethodName,
		Consumer<Runnable> postVerification, BooleanSupplier loggedIn)
	{
		this.plugins = plugins;
		this.pluginEnabled = pluginEnabled;
		this.pluginClassName = pluginClassName;
		this.questManagerMethodName = questManagerMethodName;
		this.startMethodName = startMethodName;
		this.questHelperClassName = questHelperClassName;
		this.questLookupClassName = questLookupClassName;
		this.lookupMethodName = lookupMethodName;
		this.selectedQuestMethodName = selectedQuestMethodName;
		this.questMethodName = questMethodName;
		this.questNameMethodName = questNameMethodName;
		this.postVerification = postVerification;
		this.loggedIn = loggedIn;
	}

	/** Whether questName can be handed to Quest Helper right now, and why not when it cannot. */
	Availability availability(String questName)
	{
		return resolve(questName).availability;
	}

	/**
	 * Whether Quest Helper currently has any quest selected -- the signal
	 * {@link QuestStartArrivalGate} uses for "the player is already using
	 * Quest Helper for something", so an arrival at a different quest's
	 * start does not hijack it. See {@link QuestStartArrivalGate}'s class
	 * doc for why this, and not an account-level {@code IN_PROGRESS} quest,
	 * is the right signal.
	 *
	 * <p>Reuses the same {@code getSelectedQuest()}/{@code getQuest()}/
	 * {@code getName()} read-back chain {@link #verify} already depends on
	 * -- see the class doc for how that was confirmed public. Unlike {@link
	 * #availability}, this asks nothing about a specific quest, so there is
	 * no per-quest pre-check to run first.
	 *
	 * <p>Not gated on {@link #resolve}: Quest Helper being not installed or
	 * not enabled trivially has nothing selected, so both answer false with
	 * no warning. Only a genuine failure to read back a plugin that is
	 * present and enabled logs one, the same as every other reflective read
	 * in this class.
	 *
	 * @return true only when Quest Helper is present, enabled, and reports a
	 *     non-null selected quest. A reflection failure -- a renamed or
	 *     removed method -- is logged and treated as false rather than true:
	 *     a bridge that cannot answer must not be the reason an
	 *     otherwise-legitimate arrival is silently blocked forever.
	 */
	boolean hasSelectedQuest()
	{
		Object plugin = findPlugin();
		if (plugin == null || !isEnabledSafely(plugin))
		{
			return false;
		}
		try
		{
			return selectedQuestName(plugin) != null;
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}()/{}()/{}() read-back is missing or unreachable"
					+ " -- could not tell whether a quest is already selected",
				pluginClassName, selectedQuestMethodName, questMethodName, questNameMethodName, e);
			return false;
		}
	}

	/**
	 * Hands questName to Quest Helper's own start flow, and reports what
	 * actually happened once Quest Helper has had a chance to react --
	 * never at the moment the reflective call returns, because that call is
	 * {@code void} and can no-op in three distinct, silent ways. See the
	 * class doc.
	 *
	 * @param questName the quest to start
	 * @param onOutcome called exactly once with the result. For {@link
	 *     StartOutcome#UNAVAILABLE} and {@link StartOutcome#CALL_FAILED} it
	 *     is called before this method returns; for every other outcome it
	 *     is called later, from whatever thread {@link #postVerification}
	 *     runs its task on -- callers that touch Swing from it must marshal
	 *     back to the EDT themselves, the way any other cross-thread
	 *     RuneLite callback would.
	 */
	void start(String questName, Consumer<StartOutcome> onOutcome)
	{
		Resolution resolution = resolve(questName);
		if (resolution.availability != Availability.AVAILABLE)
		{
			onOutcome.accept(StartOutcome.UNAVAILABLE);
			return;
		}
		try
		{
			ClassLoader loader = resolution.plugin.getClass().getClassLoader();
			Class<?> questHelperClass = loader == null
				? Class.forName(questHelperClassName)
				: Class.forName(questHelperClassName, false, loader);
			Method start = resolution.manager.getClass().getMethod(startMethodName, questHelperClass, boolean.class);
			start.invoke(resolution.manager, resolution.helper, Boolean.TRUE);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}({}, boolean) rejected \"{}\" -- integration may be out of date",
				resolution.manager.getClass().getName(), startMethodName, questHelperClassName, questName, e);
			onOutcome.accept(StartOutcome.CALL_FAILED);
			return;
		}

		// Not read back inline: QuestManager#startUpQuest defers through its
		// own client-thread queue when not already called from it, so the
		// selection this checks for may not exist yet. postVerification
		// goes through that same queue, so by the time this runs, Quest
		// Helper's own deferred write already has.
		Object plugin = resolution.plugin;
		postVerification.accept(() -> onOutcome.accept(verify(plugin, questName)));
	}

	/** The read-back itself: what Quest Helper actually selected, compared against what was asked for. */
	private StartOutcome verify(Object plugin, String questName)
	{
		String selectedName;
		try
		{
			selectedName = selectedQuestName(plugin);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}()/{}()/{}() read-back is missing or unreachable"
					+ " -- could not confirm whether \"{}\" actually started",
				pluginClassName, selectedQuestMethodName, questMethodName, questNameMethodName, questName, e);
			return StartOutcome.UNVERIFIABLE;
		}
		if (matchesStarted(questName, selectedName))
		{
			return StartOutcome.CONFIRMED;
		}
		return loggedIn.getAsBoolean() ? StartOutcome.NOT_SELECTED : StartOutcome.NOT_LOGGED_IN;
	}

	/**
	 * Whether selectedName is what Quest Helper would select after being
	 * asked to start questName.
	 *
	 * <p>Usually exact equality. "Shield of Arrav" and "Recipe for
	 * Disaster" are not -- see the class doc -- Quest Helper's own state can
	 * move the selection to a different sub-helper than the one requested,
	 * and every one of those sub-helper names is read from {@code
	 * QuestHelperQuest}'s own constants: "Shield of Arrav - Phoenix Gang" /
	 * "Shield of Arrav - Black Arm Gang", and "RFD - " followed by a
	 * chapter name for all ten Recipe for Disaster sub-quests. A shared
	 * prefix, not a guess.
	 */
	private static boolean matchesStarted(String questName, String selectedName)
	{
		if (selectedName == null)
		{
			return false;
		}
		if (questName.equals(selectedName))
		{
			return true;
		}
		if ("Shield of Arrav".equals(questName))
		{
			return selectedName.startsWith("Shield of Arrav");
		}
		if ("Recipe for Disaster".equals(questName))
		{
			return selectedName.startsWith("RFD");
		}
		return false;
	}

	/**
	 * Reaches {@code plugin.getSelectedQuest().getQuest().getName()}, or
	 * null at any nullable step along that chain -- which is not a failure,
	 * it is Quest Helper honestly saying nothing is selected.
	 *
	 * @throws ReflectiveOperationException when a method in that chain is
	 *     missing or unreachable, which is a failure -- distinct from a
	 *     null in the chain, and the caller treats the two differently.
	 */
	private String selectedQuestName(Object plugin) throws ReflectiveOperationException
	{
		Method getSelectedQuest = plugin.getClass().getMethod(selectedQuestMethodName);
		Object selectedQuest = getSelectedQuest.invoke(plugin);
		if (selectedQuest == null)
		{
			return null;
		}

		Method getQuest = selectedQuest.getClass().getMethod(questMethodName);
		Object quest = getQuest.invoke(selectedQuest);
		if (quest == null)
		{
			return null;
		}

		Method getName = quest.getClass().getMethod(questNameMethodName);
		return (String) getName.invoke(quest);
	}

	private Resolution resolve(String questName)
	{
		Object plugin = findPlugin();
		if (plugin == null)
		{
			return Resolution.of(Availability.NOT_INSTALLED, null, null, null);
		}
		if (!isEnabledSafely(plugin))
		{
			return Resolution.of(Availability.NOT_ENABLED, null, null, null);
		}

		Object manager = questManager(plugin);
		if (manager == null)
		{
			// Either getQuestManager() itself could not be reached (logged
			// in questManager()) or it answered null because Quest Helper
			// has not finished its own startUp() yet, which is not a
			// refactor and gets no warning there -- the next right-click
			// tries again.
			return Resolution.of(Availability.UNAVAILABLE, null, null, null);
		}

		Object helper;
		try
		{
			helper = lookupHelper(plugin, helperLookupName(questName));
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}(String) is missing or unreachable -- integration may be out of date",
				questLookupClassName, lookupMethodName, e);
			return Resolution.of(Availability.UNAVAILABLE, null, null, null);
		}
		if (helper == null)
		{
			return Resolution.of(Availability.UNSUPPORTED_QUEST, null, null, null);
		}

		return Resolution.of(Availability.AVAILABLE, manager, helper, plugin);
	}

	/** The name actually requested from {@code getByName} -- see {@link #SPLIT_QUEST_DEFAULT_HELPER}. */
	private static String helperLookupName(String questName)
	{
		return SPLIT_QUEST_DEFAULT_HELPER.getOrDefault(questName, questName);
	}

	private boolean isEnabledSafely(Object plugin)
	{
		try
		{
			return pluginEnabled.test(plugin);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read Quest Helper's enabled state", e);
			return false;
		}
	}

	private Object findPlugin()
	{
		Collection<?> loaded;
		try
		{
			loaded = plugins.get();
		}
		catch (RuntimeException e)
		{
			log.warn("Could not list loaded plugins while looking for Quest Helper", e);
			return null;
		}
		if (loaded == null)
		{
			return null;
		}
		for (Object candidate : loaded)
		{
			if (candidate != null && pluginClassName.equals(candidate.getClass().getName()))
			{
				return candidate;
			}
		}
		return null;
	}

	/** Calls the public {@link #questManagerMethodName} getter, or null when it cannot be reached. */
	private Object questManager(Object plugin)
	{
		try
		{
			Method getManager = plugin.getClass().getMethod(questManagerMethodName);
			return getManager.invoke(plugin);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}() is missing or unreachable -- integration may be out of date",
				pluginClassName, questManagerMethodName, e);
			return null;
		}
	}

	/**
	 * Quest Helper's own name lookup, which doubles as both the pre-check
	 * and the source of the object {@link #start} actually hands to {@code
	 * startUpQuest}.
	 *
	 * @return the resolved helper, or null when Quest Helper genuinely does
	 *     not recognise helperName -- distinct from a thrown {@link
	 *     ReflectiveOperationException}, because the first is "Quest Helper
	 *     does not know this quest" and the second is "this integration is
	 *     broken", and only one of those is worth a warning.
	 */
	private Object lookupHelper(Object plugin, String helperName) throws ReflectiveOperationException
	{
		ClassLoader loader = plugin.getClass().getClassLoader();
		Class<?> lookupClass = loader == null
			? Class.forName(questLookupClassName)
			: Class.forName(questLookupClassName, false, loader);
		Method getByName = lookupClass.getMethod(lookupMethodName, String.class);
		return getByName.invoke(null, helperName);
	}

	/**
	 * What {@link #resolve} concluded: the manager to invoke, the resolved
	 * helper to pass it, and the plugin instance to read back from -- all
	 * null unless availability concluded {@link Availability#AVAILABLE}.
	 */
	private static final class Resolution
	{
		private final Availability availability;
		private final Object manager;
		private final Object helper;
		private final Object plugin;

		private Resolution(Availability availability, Object manager, Object helper, Object plugin)
		{
			this.availability = availability;
			this.manager = manager;
			this.helper = helper;
			this.plugin = plugin;
		}

		static Resolution of(Availability availability, Object manager, Object helper, Object plugin)
		{
			return new Resolution(availability, manager, helper, plugin);
		}
	}
}
