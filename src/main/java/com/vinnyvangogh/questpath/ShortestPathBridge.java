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
 *
 * <p><b>{@link #eventPoster} must land the actual {@code EventBus#post} call
 * on the client thread -- this class does not do that for you.</b> This is
 * the opposite convention from {@link QuestHelperBridge}: {@code
 * QuestManager.startUpQuest} checks {@code client.isClientThread()} itself
 * and defers internally, so {@link QuestHelperBridge#start} is safe to call
 * from any thread, including the panel button's own Swing EDT. Shortest
 * Path's {@code onPluginMessage} does no such check -- disassembling it
 * shows an unconditional assertion that it is already running on the client
 * thread, with nothing to get itself there if it is not, and {@code
 * EventBus#post} invokes every subscriber synchronously on whatever thread
 * called {@code post} itself. A bare {@code eventBus::post} reference used
 * to be wired in here, straight from a Swing button's action listener, and
 * every click threw {@code AssertionError} out of Shortest Path's handler
 * before {@link #route} ever returned -- fixed by building {@link
 * #eventPoster} with {@link #clientThreadEventPoster} instead, see that
 * method's own doc for the shape of the fix and {@code
 * QuestpathPlugin#startUp} for where it is actually wired. That {@code
 * AssertionError} is also the reason {@link #route}'s catch
 * below is not narrowed to {@code RuntimeException} alone: {@code
 * AssertionError} extends {@code Error}, not {@code Exception}, so a bare
 * {@code catch (RuntimeException e)} never saw it. Confirmed the same way
 * everything else in this class was: {@code javap -c} against {@code
 * EventBus.post} in the installed jar shows its per-subscriber exception
 * table covers only {@code java.lang.Exception} -- so {@link
 * RouteOutcome#SENT}'s own doc, which describes {@code post} swallowing a
 * subscriber's thrown exception, was correspondingly narrower than it read:
 * true for anything under {@code Exception}, never true for an {@code
 * Error}, which is exactly the class {@code AssertionError} belongs to and
 * exactly why this one reached all the way up to RuneLite's own top-level
 * uncaught-exception handler instead of being quietly absorbed.
 *
 * <p><b>Shortest Path clears its own path on arrival, on its own, with no
 * event this plugin could react to even if it wanted to.</b> A report:
 * talking to an NPC (the Brimhaven bartender) appeared to clear the active
 * path. Checked rather than assumed -- {@code javap -c -p} against the
 * installed jar's {@code ShortestPathPlugin.onGameTick} shows it comparing
 * the player's live position against every current target every tick and
 * calling {@code setTarget(-1)} (its own internal "clear" call) the moment
 * any one of them is within {@code ShortestPathConfig#reachedDistance()}
 * tiles (5, by default) -- with no reference to menu actions, dialogue, or
 * any interaction at all. {@code onMenuOptionClicked} is the only other path
 * clearer in that jar, and it only fires on an explicit right-click "Clear"
 * on the path itself, matched by literal option and target text. There is
 * no {@code onInteractingChanged}, no chat listener, and nothing else
 * subscribed that could react to talking to an NPC. Walking up to talk to
 * someone standing near wherever Shortest Path's own target was is, by
 * construction, walking within a few tiles of that target -- so the path
 * clearing at that moment is Shortest Path's own arrival detection doing
 * exactly what it is built to do, coincident with the interaction rather
 * than caused by it. This plugin posts a target set and nothing else; it
 * has no path of its own to clear and nothing here reacts to arrival,
 * interaction, or dialogue in any way.
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
		/**
		 * {@link #eventPoster} threw while this method's own try/catch could
		 * still see it. Logged at warn already.
		 *
		 * <p>What that catch can actually see now depends entirely on which
		 * thread called {@link #route}, because a correctly-wired {@link
		 * #eventPoster} hands the real {@code EventBus#post} call off to the
		 * client thread rather than making it inline -- see the class doc's
		 * client-thread paragraph for why that hand-off exists at all.
		 * Called already on the client thread (rare for this button, which
		 * is Swing), {@code ClientThread#invoke} runs the post immediately
		 * and inline, so a failure at or below {@code post} -- including a
		 * subscriber-level {@code AssertionError}, per {@link #route}'s own
		 * multi-catch -- surfaces here as {@code CALL_FAILED} exactly as it
		 * always could. Called off the client thread -- the normal case,
		 * the panel button's own Swing action listener -- {@code
		 * ClientThread#invoke} only enqueues the runnable and returns
		 * immediately without running it, so this catch cannot see a
		 * failure inside {@code post} or the subscriber at all; that
		 * deferred runnable is instead run, and any exception from it
		 * caught and merely logged, by RuneLite's own {@code
		 * ClientThread#invokeList} on a later game tick, confirmed by {@code
		 * javap -c} against the installed jar to wrap each queued task in
		 * {@code catch (Throwable e)} before logging {@code "Exception in
		 * invoke"} and moving on -- {@code Throwable}, not the narrower
		 * {@code Exception} {@code EventBus#post} itself catches (see {@link
		 * #SENT}'s own doc), which is exactly why routing through the client
		 * thread also fixed the original {@code AssertionError} propagating
		 * all the way out to RuneLite's top-level uncaught-exception
		 * handler, not merely relocated where it surfaced. In that deferred
		 * case, {@code CALL_FAILED} can only ever mean the hand-off itself
		 * failed to enqueue -- essentially never, in practice.
		 */
		CALL_FAILED,
		/**
		 * The event was built and handed to {@link #eventPoster} without
		 * throwing. <b>Not</b> confirmation that Shortest Path received or
		 * acted on it -- there is no public read-back for that, unlike
		 * {@link QuestHelperBridge#start}, which can ask Quest Helper what
		 * it actually selected. Off the client thread (see {@link
		 * #CALL_FAILED}'s own doc for why that is the normal case for this
		 * button), {@code SENT} means only "handed off for posting on the
		 * client thread", weaker even than "reached {@code EventBus#post}" --
		 * the actual post has not necessarily happened yet when this fires.
		 * Once it does run, {@code EventBus#post} is fire-and-forget by
		 * design regardless of which thread called it: it delivers to every
		 * current subscriber synchronously and returns, whether zero of them
		 * were Shortest Path or one of them was and quietly declined the
		 * target. This is the strongest claim honestly available.
		 *
		 * <p>It is blinder than that description alone suggests. {@code
		 * javap -c} against {@code net.runelite.client.eventbus.EventBus} in
		 * the installed {@code client-1.12.37.jar} confirms {@code post}
		 * wraps <i>each subscriber's invocation individually</i> in a
		 * try/catch whose exception table covers only {@code
		 * java.lang.Exception} -- not the broader {@code Throwable} an
		 * uninformed reading of "fire-and-forget" might suggest -- handing
		 * anything caught to an internal handler and continuing to the next
		 * subscriber, never rethrowing to the poster. An {@code Exception}
		 * from Shortest Path's own {@code onPluginMessage} -- it was
		 * disassembled too, and does real work with the target set before
		 * handing it to its pathfinder -- would therefore never surface here
		 * as {@link #CALL_FAILED} even when this method's own try/catch runs
		 * inline; an {@code Error} from it (an {@code AssertionError}, say)
		 * is the one thing that exception table does not cover, which is
		 * exactly what this class shipped without a thread hand-off for
		 * before it was fixed -- see the class doc. Beyond that one gap,
		 * {@code SENT} means exactly "the call reached {@code EventBus#post}
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
	 * Posts an event to every current subscriber -- <b>not</b> safely a bare
	 * {@code eventBus::post} reference in production; see the class doc's
	 * client-thread paragraph for why a real caller must route this through
	 * {@code ClientThread#invoke} instead. A {@code Consumer<Object>} rather
	 * than a narrower {@code Consumer<PluginMessage>} only so production
	 * wiring can pass a lambda around that hand-off directly; every real
	 * call still only ever posts a {@link PluginMessage}.
	 */
	private final Consumer<Object> eventPoster;

	/**
	 * Production wiring: the real plugin list, the real event bus.
	 *
	 * @param plugins every plugin RuneLite currently knows about, e.g.
	 *     {@code pluginManager::getPlugins}
	 * @param pluginEnabled whether a given plugin instance is switched on,
	 *     e.g. {@code plugin -> pluginManager.isPluginEnabled((Plugin) plugin)}
	 * @param eventPoster posts to RuneLite's shared event bus -- build this
	 *     with {@link #clientThreadEventPoster}, e.g. {@code
	 *     ShortestPathBridge.clientThreadEventPoster(clientThread::invoke,
	 *     eventBus::post)}, never a bare {@code eventBus::post} reference;
	 *     see the class doc's client-thread paragraph for why
	 */
	ShortestPathBridge(Supplier<Collection<?>> plugins, Predicate<Object> pluginEnabled, Consumer<Object> eventPoster)
	{
		this(plugins, pluginEnabled, PLUGIN_CLASS_NAME, eventPoster);
	}

	/**
	 * Builds an {@link #eventPoster} that always lands {@code post} on the
	 * client thread, whichever thread calls the returned {@code Consumer} --
	 * see this class's own doc for why that hand-off is required at all and
	 * what happened here without it.
	 *
	 * <p>Takes the hand-off itself as a plain {@code Consumer<Runnable>}
	 * rather than the concrete {@code ClientThread}, the same reason {@link
	 * QuestHelperBridge}'s own {@code postVerification} seam does: it lets a
	 * test stand in for "the client thread" with a small fixture instead of
	 * a real, Guice-injected {@code ClientThread} instance, which cannot be
	 * constructed usably outside RuneLite's own container. Production
	 * wiring passes {@code clientThread::invoke}, whose signature already
	 * matches {@code Consumer<Runnable>} exactly.
	 *
	 * @param onClientThread runs a task on the client thread, deferring it
	 *     if the caller is not already on that thread -- production wiring
	 *     passes {@code clientThread::invoke}
	 * @param post the actual post call once on the client thread, e.g.
	 *     {@code eventBus::post}
	 */
	static Consumer<Object> clientThreadEventPoster(Consumer<Runnable> onClientThread, Consumer<Object> post)
	{
		return object -> onClientThread.accept(() -> post.accept(object));
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
	 *     this method itself never defers, unlike {@link
	 *     QuestHelperBridge#start}. That is narrower than "everything here is
	 *     synchronous", though: a correctly-wired {@link #eventPoster} hands
	 *     the actual {@code EventBus#post} call off to the client thread, so
	 *     when this is called off that thread (the normal case for the panel
	 *     button) the real post has usually not happened yet by the time
	 *     {@code onOutcome} fires -- see {@link RouteOutcome#SENT}'s own doc.
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
		// RuntimeException | AssertionError, not a bare RuntimeException and
		// not the broader Throwable: this class doc's client-thread
		// paragraph explains exactly why AssertionError specifically must be
		// caught here (Shortest Path's own onPluginMessage asserts it is on
		// the client thread, and that assertion is an Error, not an
		// Exception, so a RuntimeException-only catch silently let it
		// through before this fix). Not widened to Throwable: something like
		// OutOfMemoryError or StackOverflowError surfacing from a post this
		// deep means the JVM itself is in trouble, and reporting that as an
		// ordinary CALL_FAILED chat line -- rather than letting it propagate
		// to whatever crash handling exists above this -- would hide a
		// problem far bigger than "Shortest Path didn't get the route".
		catch (RuntimeException | AssertionError e)
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
