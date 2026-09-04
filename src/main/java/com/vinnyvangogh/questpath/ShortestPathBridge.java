package com.vinnyvangogh.questpath;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;

/**
 * Hands a set of world points to the Shortest Path plugin's own pathfinder.
 *
 * <p>Unlike {@link QuestHelperBridge}, this class touches nothing private in
 * the plugin it talks to. Shortest Path (`Skretzo/shortest-path`) listens for
 * RuneLite's own {@code PluginMessage} event -- {@code
 * net.runelite.client.events.PluginMessage}, a public class shipped inside
 * {@code runelite-client} itself, not inside Shortest Path -- and reacts by
 * restarting its pathfinder toward whatever is in the message's data map.
 * Posting one is therefore two calls against RuneLite's own verified public
 * API ({@code new PluginMessage(namespace, name, data)} and {@code
 * EventBus#post(Object)}), never a reflective call into
 * {@code shortestpath.*}. Both were confirmed the same way {@link
 * QuestHelperBridge}'s public surface was confirmed -- {@code javap} against
 * the actually-installed dependency, here
 * {@code ~/.gradle/caches/modules-2/files-2.1/net.runelite/client/1.12.37/*&#47;client-1.12.37.jar}
 * -- rather than trusted from source alone:
 *
 * <pre>
 * public final class net.runelite.client.events.PluginMessage {
 *   public net.runelite.client.events.PluginMessage(String, String, Map&lt;String, Object&gt;);
 *   ...
 * }
 * public class net.runelite.client.eventbus.EventBus {
 *   public void post(Object);
 *   ...
 * }
 * </pre>
 *
 * <p><b>{@link #PLUGIN_CLASS_NAME}, {@link #CONFIG_GROUP}, {@link
 * #MESSAGE_NAME} and {@link #TARGET_KEY} are now verified, not merely quoted
 * from source.</b> Shortest Path was not installed when this class was
 * first written, and the four constants above came only from reading its
 * GitHub source (`src/main/java/shortestpath/ShortestPathPlugin.java`,
 * quoted in `docs/shop-routing-research.md` &#167;6 in the `osrs` repo).
 * {@link QuestHelperBridge}'s whole cautionary tale is that GitHub source
 * can be incomplete, and the only real fix is {@code javap} against the
 * compiled artifact actually being run. Shortest Path is now installed --
 * {@code ~/.runelite/plugins/shortest-path_j65TV2lGDTkVcJlwg4jIvqU_Z2mHP1lUWx9t9lfkfRY.jar}
 * -- and that fix has since been applied: {@code javap -c -p} against that
 * jar's {@code shortestpath/ShortestPathPlugin.class} confirms {@code
 * CONFIG_GROUP = "shortestpath"}, {@code PLUGIN_MESSAGE_PATH = "path"} and
 * {@code PLUGIN_MESSAGE_TARGET = "target"} byte for byte -- note the
 * package is bare {@code shortestpath}, not {@code com.shortestpath}, which
 * matches {@link #PLUGIN_CLASS_NAME} exactly. Disassembling {@code
 * onPluginMessage} itself goes further than the three constants this class
 * uses: after matching the namespace and message name, it reads three keys
 * out of the data map via {@code getOrDefault} -- {@code "start"}, {@code
 * "target"} and {@code "config"} -- then branches on the target value's
 * type: {@code instanceof WorldPoint}, {@code instanceof Integer} (a
 * pre-packed tile, via {@code shortestpath.WorldPointUtil.packWorldPoint}),
 * or {@code instanceof Set}, iterating a {@code Set} and packing each
 * element the same way. That is exactly the {@code Set<WorldPoint>} shape
 * {@link #route} sends, confirmed against the running artifact rather than
 * assumed from its published source. Two things beyond what this class
 * needed, recorded here so the next person does not have to re-derive them
 * by decompiling again: {@code "start"} lets a caller override the
 * pathfinder's own starting tile instead of using the player's position,
 * and {@code "config"} accepts a map of config overrides applied before the
 * route is computed -- this class uses neither.
 *
 * <p>What that verification does and does not prove: it confirms the
 * <i>message</i> {@link #route} builds is exactly the shape the installed
 * plugin's handler reads, key for key and type for type. It does not
 * confirm a route visibly appears in the client -- {@code EventBus#post} is
 * fire-and-forget by design, the decompiled handler fires no event and
 * returns nothing on success, so there was never going to be a public
 * read-back the way {@link QuestHelperBridge#start} has one, and seeing an
 * actual line drawn on screen still needs a human at a running client.
 * {@link #route}'s only failure mode it can detect is {@code
 * EventBus#post} itself throwing. Treat {@link RouteOutcome#SENT} as "the
 * event went out, built exactly as the installed plugin expects it", never
 * as "a route is now visibly drawn" -- see that constant's own doc.
 *
 * <p><b>Why {@link #availability()} still degrades the same way {@link
 * QuestHelperBridge} does.</b> Even though nothing here reflects into
 * Shortest Path, {@link #findPlugin} still has to find the running instance
 * to know whether posting is worth attempting at all, using the identical
 * {@code Supplier<Collection<?>>}/{@code Predicate<Object>} shape
 * {@link QuestHelperBridge} takes -- reused rather than reinvented, because
 * it is already the house pattern for "is this other plugin here and
 * switched on". A missing or disabled Shortest Path must be visible and
 * distinct from a Shortest Path that is present but declined the event, for
 * the same reason {@link QuestHelperBridge}'s three-way split matters: a
 * silent no-op reads as "nothing needed" to a player, whichever feature it
 * is attached to.
 */
@Slf4j
class ShortestPathBridge
{
	/**
	 * Shortest Path's own plugin class, matched by name -- see the class
	 * doc. Confirmed against the installed jar's compiled class file, not
	 * only its source.
	 */
	static final String PLUGIN_CLASS_NAME = "shortestpath.ShortestPathPlugin";

	/**
	 * The {@code PluginMessage} namespace Shortest Path listens on.
	 * {@code ShortestPathPlugin.CONFIG_GROUP} in its own source -- confirmed
	 * byte for byte against the installed jar via {@code javap -constants};
	 * see the class doc.
	 */
	static final String CONFIG_GROUP = "shortestpath";

	/**
	 * The {@code PluginMessage} name Shortest Path listens for.
	 * {@code ShortestPathPlugin.PLUGIN_MESSAGE_PATH} in its own source --
	 * confirmed byte for byte against the installed jar via {@code javap
	 * -constants}; see the class doc.
	 */
	static final String MESSAGE_NAME = "path";

	/**
	 * The data-map key Shortest Path reads its destination set from.
	 * {@code ShortestPathPlugin.PLUGIN_MESSAGE_TARGET} in its own source --
	 * confirmed byte for byte against the installed jar via {@code javap
	 * -constants}. Disassembling {@code onPluginMessage} confirms the value
	 * may be a single {@code WorldPoint}, a packed {@code int}, or a {@code
	 * Set} of either; this class only ever sends the {@code Set<WorldPoint>}
	 * shape, which is exactly the "several shops, nearest wins" case
	 * questpath has, and exactly the branch the handler's {@code
	 * instanceof Set} check walks. See the class doc.
	 */
	static final String TARGET_KEY = "target";

	/** Whether a route can be handed to Shortest Path right now, and why not when it cannot. */
	enum Availability
	{
		/** Reachable. */
		AVAILABLE("Send shop route to Shortest Path"),
		NOT_INSTALLED("Shortest Path is not installed"),
		NOT_ENABLED("Shortest Path is not enabled"),
		/** Present and enabled, but something this class depends on did not resolve. */
		UNAVAILABLE("Shortest Path integration unavailable");

		private final String menuLabel;

		Availability(String menuLabel)
		{
			this.menuLabel = menuLabel;
		}

		/** The exact text to put in front of the player -- doubles as the reason. */
		String menuLabel()
		{
			return menuLabel;
		}
	}

	/** What became of a {@link #route} call. */
	enum RouteOutcome
	{
		/** {@link #availability()} was not {@link Availability#AVAILABLE} when the call landed. */
		UNAVAILABLE,
		/**
		 * {@link #route} was called with an empty or null destination set.
		 * Never posted -- an empty target set is not a route, and Shortest
		 * Path's own handling of one is not something this class has
		 * checked.
		 */
		NO_DESTINATIONS,
		/** {@code EventBus#post} itself threw. Logged at warn already. */
		CALL_FAILED,
		/**
		 * The event was built and handed to {@code EventBus#post} without
		 * throwing. <b>Not</b> confirmation that Shortest Path received or
		 * acted on it -- there is no public read-back for that, unlike
		 * {@link QuestHelperBridge#start}, which can ask Quest Helper what
		 * it actually selected. {@code EventBus#post} is fire-and-forget by
		 * design: it delivers to every current subscriber synchronously and
		 * returns, whether zero of them were Shortest Path or one of them
		 * was and quietly declined the target. This is the strongest claim
		 * honestly available.
		 *
		 * <p>It is blinder than that description alone suggests. {@code
		 * javap -c} against {@code net.runelite.client.eventbus.EventBus} in
		 * the installed {@code client-1.12.37.jar} confirms {@code post}
		 * wraps <i>each subscriber's invocation individually</i> in a
		 * try/catch that hands any thrown exception to an internal handler
		 * and continues to the next subscriber -- it never rethrows to the
		 * poster. So a genuine failure inside Shortest Path's own {@code
		 * onPluginMessage} -- it was disassembled too, and does real work
		 * with the target set before handing it to its pathfinder -- would
		 * never surface here as {@link #CALL_FAILED}; this method's own
		 * try/catch can only ever catch an exception from building the
		 * {@code PluginMessage} itself or from {@code post} before it
		 * reaches a subscriber, not from the subscriber's body. {@code SENT}
		 * therefore means exactly "the call reached {@code EventBus#post}
		 * without incident", nothing stronger, however the report from
		 * inside Shortest Path may have actually gone -- which is why
		 * {@link #route} logs the destination count at info on this outcome:
		 * it is the only thing left to check when a player reports the
		 * button did nothing.
		 */
		SENT,
	}

	private final Supplier<Collection<?>> plugins;
	private final Predicate<Object> pluginEnabled;
	private final String pluginClassName;

	/**
	 * Posts an event to every current subscriber, e.g. {@code eventBus::post}.
	 * A {@code Consumer<Object>} rather than a narrower {@code
	 * Consumer<PluginMessage>} only so production wiring can pass the method
	 * reference directly; every real call still only ever posts a {@link
	 * PluginMessage}.
	 */
	private final Consumer<Object> eventPoster;

	/**
	 * Production wiring: the real plugin list, the real event bus.
	 *
	 * @param plugins every plugin RuneLite currently knows about, e.g.
	 *     {@code pluginManager::getPlugins}
	 * @param pluginEnabled whether a given plugin instance is switched on,
	 *     e.g. {@code plugin -> pluginManager.isPluginEnabled((Plugin) plugin)}
	 * @param eventPoster posts to RuneLite's shared event bus, e.g. {@code eventBus::post}
	 */
	ShortestPathBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled, Consumer<Object> eventPoster)
	{
		this(plugins, pluginEnabled, PLUGIN_CLASS_NAME, eventPoster);
	}

	/**
	 * Test seam: the plugin class name is overridable so a test can point
	 * {@link #findPlugin} at a small fixture instead of a real Shortest
	 * Path, which is not on this project's classpath. Nothing else needs a
	 * seam -- unlike {@link QuestHelperBridge}, this class never resolves a
	 * method by name on the found plugin, so there is no reflective step
	 * left to fake.
	 */
	ShortestPathBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled, String pluginClassName,
		Consumer<Object> eventPoster)
	{
		this.plugins = plugins;
		this.pluginEnabled = pluginEnabled;
		this.pluginClassName = pluginClassName;
		this.eventPoster = eventPoster;
	}

	/** Whether a route can be handed to Shortest Path right now, and why not when it cannot. */
	Availability availability()
	{
		Object plugin = findPlugin();
		if (plugin == null)
		{
			return Availability.NOT_INSTALLED;
		}
		if (!isEnabledSafely(plugin))
		{
			return Availability.NOT_ENABLED;
		}
		return Availability.AVAILABLE;
	}

	/**
	 * Posts destinations to Shortest Path as one target set, reporting what
	 * actually happened -- see {@link RouteOutcome} for exactly how much
	 * that claim is worth.
	 *
	 * @param destinations candidate shop locations. Handed to Shortest Path
	 *     as-is; picking the nearest is Shortest Path's own job, not this
	 *     class's.
	 * @param onOutcome called exactly once, before this method returns --
	 *     everything here is synchronous, unlike {@link QuestHelperBridge#start}.
	 */
	void route(Set<WorldPoint> destinations, Consumer<RouteOutcome> onOutcome)
	{
		if (availability() != Availability.AVAILABLE)
		{
			log.info("Not sending a shop route to Shortest Path -- {}", availability());
			onOutcome.accept(RouteOutcome.UNAVAILABLE);
			return;
		}
		if (destinations == null || destinations.isEmpty())
		{
			log.info("Not sending a shop route to Shortest Path -- no destinations to send");
			onOutcome.accept(RouteOutcome.NO_DESTINATIONS);
			return;
		}
		try
		{
			Map<String, Object> data = Map.of(TARGET_KEY, destinations);
			eventPoster.accept(new PluginMessage(CONFIG_GROUP, MESSAGE_NAME, data));
		}
		catch (RuntimeException e)
		{
			log.warn("Posting PluginMessage(\"{}\", \"{}\", ...) with {} destination(s) failed"
				+ " -- Shortest Path may be out of date", CONFIG_GROUP, MESSAGE_NAME, destinations.size(), e);
			onOutcome.accept(RouteOutcome.CALL_FAILED);
			return;
		}
		// The only confirmation this call can honestly give -- see
		// RouteOutcome#SENT's own doc for exactly how much it is worth.
		// Logged at info, not debug, because "was a route actually sent, and
		// how many stops did it carry" is precisely the question a report of
		// "the button does nothing" needs answered from the client log,
		// and EventBus#post itself gives no other way to answer it: a
		// subscriber that throws is caught and silently swallowed by
		// RuneLite's own EventBus before it ever reaches this method's catch
		// block, so SENT here means "the event went out", never "Shortest
		// Path definitely acted on it".
		log.info("Sent {} destination(s) to Shortest Path via PluginMessage(\"{}\", \"{}\", ...)",
			destinations.size(), CONFIG_GROUP, MESSAGE_NAME);
		onOutcome.accept(RouteOutcome.SENT);
	}

	private boolean isEnabledSafely(Object plugin)
	{
		try
		{
			return pluginEnabled.test(plugin);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read Shortest Path's enabled state", e);
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
			log.warn("Could not list loaded plugins while looking for Shortest Path", e);
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
}
