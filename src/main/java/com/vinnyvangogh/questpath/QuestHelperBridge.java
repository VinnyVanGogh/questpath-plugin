package com.vinnyvangogh.questpath;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands a quest off to Quest Helper's own "start this quest" flow.
 *
 * <p>Quest Helper (Zoinkwiz/quest-helper) exposes no public way for another
 * plugin to say "start this quest" -- confirmed by reading its source, not
 * assumed. The logic that does it lives in
 * {@code com.questhelper.managers.QuestMenuHandler#startUpQuest(String)}, a
 * <b>public</b> method, but the only instance of it is a <b>private</b> field
 * -- {@code com.questhelper.QuestHelperPlugin#questMenuHandler}, injected by
 * Guice, no getter -- so every reflective call in this file exists because
 * that field is the only door.
 *
 * <p>A second name is depended on the same way, and used only as a
 * pre-check: {@code com.questhelper.questinfo.QuestHelperQuest#getByName
 * (String)}, a public static method that returns null for a quest name Quest
 * Helper does not carry. It is checked before ever calling
 * {@code startUpQuest}, because that method is {@code void} and silently
 * does nothing on a name it does not recognise -- called blind, this class
 * could never tell "worked" from "unsupported quest".
 *
 * <p><b>What breaks, and how it is noticed.</b> If Quest Helper renames
 * {@code questMenuHandler}, changes {@code startUpQuest}'s or
 * {@code getByName}'s signature, or removes either method, every call here
 * starts returning {@link Availability#UNAVAILABLE} instead of working. That
 * failure is loud: a warn-level log line names the field or method that
 * stopped resolving, at the moment it stops resolving. It is never silent
 * and never reported as success. All of it is confined to this one file --
 * no other class in this plugin calls {@code getDeclaredField} or reaches
 * into {@code com.questhelper.*} -- so a Quest Helper refactor is a
 * one-file fix.
 *
 * <p>None of this requires Quest Helper on this project's compile or test
 * classpath: every call after finding the running plugin instance goes
 * through {@code java.lang.reflect}, resolved through that instance's own
 * class loader rather than this plugin's, because RuneLite loads external
 * plugins each on their own loader.
 */
@Slf4j
class QuestHelperBridge
{
	/** Quest Helper's own plugin class, matched by name -- see the class doc. */
	static final String PLUGIN_CLASS_NAME = "com.questhelper.QuestHelperPlugin";

	/**
	 * The private field on {@link #PLUGIN_CLASS_NAME} that holds the one
	 * object capable of starting a quest. See the class doc for what happens
	 * when this name goes stale.
	 */
	static final String MENU_HANDLER_FIELD_NAME = "questMenuHandler";

	/** The public method on that field's runtime type that starts a quest. */
	static final String START_METHOD_NAME = "startUpQuest";

	/** Quest Helper's own quest-name lookup, used only as a pre-check -- see the class doc. */
	static final String QUEST_LOOKUP_CLASS_NAME = "com.questhelper.questinfo.QuestHelperQuest";

	/** The public static method on {@link #QUEST_LOOKUP_CLASS_NAME} that performs that lookup. */
	static final String LOOKUP_METHOD_NAME = "getByName";

	/**
	 * The two quests Quest Helper runs as several separately named helpers
	 * rather than one, so {@code getByName} on the plain game name returns
	 * null for both even though Quest Helper supports them --
	 * {@code QuestMenuHandler#startUpQuest} special-cases exactly these two
	 * literal strings ahead of its own name lookup, picking the right
	 * sub-helper itself. Read from that method; there is no other way to
	 * know it, and no third quest works this way.
	 */
	private static final Set<String> HANDLED_BY_NAME_ALONE = Set.of("Shield of Arrav", "Recipe for Disaster");

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

	private final Supplier<Collection<?>> plugins;
	private final Predicate<Object> pluginEnabled;
	private final String pluginClassName;
	private final String menuHandlerFieldName;
	private final String startMethodName;
	private final String questLookupClassName;
	private final String lookupMethodName;

	/**
	 * Production wiring: the real Quest Helper coordinates, the real plugin
	 * list.
	 *
	 * @param plugins every plugin RuneLite currently knows about, e.g.
	 *     {@code pluginManager::getPlugins}
	 * @param pluginEnabled whether a given plugin instance is switched on,
	 *     e.g. {@code plugin -> pluginManager.isPluginEnabled((Plugin) plugin)}
	 */
	QuestHelperBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled)
	{
		this(plugins, pluginEnabled, PLUGIN_CLASS_NAME, MENU_HANDLER_FIELD_NAME, START_METHOD_NAME,
			QUEST_LOOKUP_CLASS_NAME, LOOKUP_METHOD_NAME);
	}

	/**
	 * Test seam: every coordinate this class depends on is overridable, so a
	 * test can point it at a small fixture class instead of the real Quest
	 * Helper, which is not on this project's classpath.
	 *
	 * <p>A test built on this constructor pins the reflection <i>logic</i> --
	 * that the field is found, the method is invoked, and each failure
	 * degrades to the right {@link Availability}. It proves nothing about
	 * whether the real Quest Helper still has a field named
	 * {@code questMenuHandler}; that is what the warn log at runtime is for,
	 * not this class.
	 */
	QuestHelperBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled,
		String pluginClassName, String menuHandlerFieldName, String startMethodName,
		String questLookupClassName, String lookupMethodName)
	{
		this.plugins = plugins;
		this.pluginEnabled = pluginEnabled;
		this.pluginClassName = pluginClassName;
		this.menuHandlerFieldName = menuHandlerFieldName;
		this.startMethodName = startMethodName;
		this.questLookupClassName = questLookupClassName;
		this.lookupMethodName = lookupMethodName;
	}

	/** Whether questName can be handed to Quest Helper right now, and why not when it cannot. */
	Availability availability(String questName)
	{
		return resolve(questName).availability;
	}

	/**
	 * Hands questName to Quest Helper's own start flow.
	 *
	 * @return true only when the reflective call reached Quest Helper's
	 *     {@code startUpQuest(String)} without throwing. That is not proof
	 *     Quest Helper visibly started anything -- the method is
	 *     {@code void} and no-ops in silence on a name it does not
	 *     recognise -- only that this side of the handoff worked. False
	 *     means either the account never got that far (call
	 *     {@link #availability} first to know why) or the reflective call
	 *     itself failed, which is logged at warn.
	 */
	boolean start(String questName)
	{
		Resolution resolution = resolve(questName);
		if (resolution.availability != Availability.AVAILABLE)
		{
			return false;
		}
		try
		{
			Method start = resolution.handler.getClass().getMethod(startMethodName, String.class);
			start.invoke(resolution.handler, questName);
			return true;
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}(String) rejected \"{}\" -- integration may be out of date",
				resolution.handler.getClass().getName(), startMethodName, questName, e);
			return false;
		}
	}

	private Resolution resolve(String questName)
	{
		Object plugin = findPlugin();
		if (plugin == null)
		{
			return Resolution.of(Availability.NOT_INSTALLED);
		}
		if (!isEnabledSafely(plugin))
		{
			return Resolution.of(Availability.NOT_ENABLED);
		}

		Object handler = menuHandler(plugin);
		if (handler == null)
		{
			// Either the field could not be reached (logged in menuHandler)
			// or Quest Helper has not finished its own startUp() yet, which
			// is not a refactor and gets no warning -- the next right-click
			// tries again.
			return Resolution.of(Availability.UNAVAILABLE);
		}

		if (!HANDLED_BY_NAME_ALONE.contains(questName))
		{
			Boolean supported = supportsQuest(plugin, questName);
			if (supported == null)
			{
				return Resolution.of(Availability.UNAVAILABLE);
			}
			if (!supported)
			{
				return Resolution.of(Availability.UNSUPPORTED_QUEST);
			}
		}

		return Resolution.of(Availability.AVAILABLE, handler);
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

	/** Reaches the private field named {@link #menuHandlerFieldName}, or null when it cannot. */
	private Object menuHandler(Object plugin)
	{
		try
		{
			Field field = plugin.getClass().getDeclaredField(menuHandlerFieldName);
			field.setAccessible(true);
			return field.get(plugin);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {} field is missing or unreachable -- integration may be out of date",
				menuHandlerFieldName, e);
			return null;
		}
	}

	/**
	 * Whether Quest Helper's own name lookup resolves questName.
	 *
	 * @return {@code Boolean.TRUE} or {@code Boolean.FALSE} for a genuine
	 *     answer, or {@code null} when the lookup itself could not be
	 *     reached -- distinct outcomes, because the first is "Quest Helper
	 *     does not know this quest" and the second is "this integration is
	 *     broken", and only one of those is worth a warning.
	 */
	private Boolean supportsQuest(Object plugin, String questName)
	{
		try
		{
			ClassLoader loader = plugin.getClass().getClassLoader();
			Class<?> lookupClass = loader == null
				? Class.forName(questLookupClassName)
				: Class.forName(questLookupClassName, false, loader);
			Method getByName = lookupClass.getMethod(lookupMethodName, String.class);
			return getByName.invoke(null, questName) != null;
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			log.warn("Quest Helper's {}.{}(String) is missing or unreachable -- integration may be out of date",
				questLookupClassName, lookupMethodName, e);
			return null;
		}
	}

	/** What {@link #resolve} concluded, and the handler to invoke when it concluded {@link Availability#AVAILABLE}. */
	private static final class Resolution
	{
		private final Availability availability;
		private final Object handler;

		private Resolution(Availability availability, Object handler)
		{
			this.availability = availability;
			this.handler = handler;
		}

		static Resolution of(Availability availability)
		{
			return of(availability, null);
		}

		static Resolution of(Availability availability, Object handler)
		{
			return new Resolution(availability, handler);
		}
	}
}
