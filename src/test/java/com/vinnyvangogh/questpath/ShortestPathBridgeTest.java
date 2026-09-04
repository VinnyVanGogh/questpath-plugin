package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link ShortestPathBridge}'s own logic: that it finds a plugin by
 * class name and degrades to the right {@link ShortestPathBridge.Availability}
 * when it is missing or disabled, and that {@link ShortestPathBridge#route}
 * posts exactly one event with the right namespace, name and data shape --
 * or reports the right {@link ShortestPathBridge.RouteOutcome} when it
 * cannot.
 *
 * <p><b>What this class does not prove.</b> Shortest Path is not on this
 * project's compile or test classpath -- {@link FakePlugin} stands in for
 * {@code shortestpath.ShortestPathPlugin} so this suite can pin the finding
 * and enabled-check logic without it -- so nothing here runs the real
 * plugin's own {@code onPluginMessage}. That confirmation was done
 * separately, once, by disassembling the plugin actually installed at
 * {@code ~/.runelite/plugins/}: see {@link ShortestPathBridge}'s own class
 * doc for exactly what {@code javap} against the compiled artifact
 * confirmed, and why a decompile is not something worth re-running inside
 * a unit test.
 */
public class ShortestPathBridgeTest
{
	/** Stand-in for {@code shortestpath.ShortestPathPlugin}: an empty marker class, since nothing is ever called on it. */
	private static class FakePlugin
	{
	}

	private static ShortestPathBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled,
		Consumer<Object> eventPoster)
	{
		return new ShortestPathBridge(plugins, enabled, FakePlugin.class.getName(), eventPoster);
	}

	private static ShortestPathBridge bridge(Supplier<Collection<?>> plugins, Predicate<Object> enabled)
	{
		return bridge(plugins, enabled, event ->
		{
			// Discarded: most availability-only tests below never post.
		});
	}

	private static Set<WorldPoint> oneDestination()
	{
		return Set.of(new WorldPoint(3200, 3200, 0));
	}

	private static ShortestPathBridge.RouteOutcome routeOf(ShortestPathBridge bridge, Set<WorldPoint> destinations)
	{
		AtomicReference<ShortestPathBridge.RouteOutcome> result = new AtomicReference<>();
		bridge.route(destinations, result::set);
		return result.get();
	}

	@Test
	public void notInstalledWhenNoMatchingPluginIsLoaded()
	{
		ShortestPathBridge bridge = bridge(java.util.Collections::emptyList, plugin -> true);
		assertEquals(ShortestPathBridge.Availability.NOT_INSTALLED, bridge.availability());
		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
	}

	@Test
	public void notEnabledWhenThePluginSaysSo()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> false);
		assertEquals(ShortestPathBridge.Availability.NOT_ENABLED, bridge.availability());
		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
	}

	/** A thrown enabled-check is treated as "no", not as "we don't know" -- fail closed, not fail open. */
	@Test
	public void notEnabledWhenTheEnabledCheckItselfThrows()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p ->
		{
			throw new RuntimeException("cannot read plugin state");
		});
		assertEquals(ShortestPathBridge.Availability.NOT_ENABLED, bridge.availability());
	}

	/** A thrown plugin listing is treated the same as none found, not as available. */
	@Test
	public void notInstalledWhenListingPluginsThrows()
	{
		ShortestPathBridge bridge = bridge(() ->
		{
			throw new RuntimeException("plugin manager not ready");
		}, p -> true);
		assertEquals(ShortestPathBridge.Availability.NOT_INSTALLED, bridge.availability());
	}

	@Test
	public void availableWhenTheFakePluginIsLoadedAndEnabled()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> true);
		assertEquals(ShortestPathBridge.Availability.AVAILABLE, bridge.availability());
	}

	/** An empty or null destination set is never posted -- there is no route to hand over. */
	@Test
	public void noDestinationsWhenTheSetIsEmpty()
	{
		FakePlugin plugin = new FakePlugin();
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> true, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.NO_DESTINATIONS, routeOf(bridge, Set.of()));
		assertEquals(ShortestPathBridge.RouteOutcome.NO_DESTINATIONS, routeOf(bridge, null));
		assertTrue("nothing should have been posted for an empty route", posted.isEmpty());
	}

	/**
	 * The case the whole class exists for: a real destination set becomes
	 * one {@code PluginMessage("shortestpath", "path", {"target": destinations})}.
	 */
	@Test
	public void sentPostsExactlyOnePluginMessageWithTheDestinationSet()
	{
		FakePlugin plugin = new FakePlugin();
		Set<WorldPoint> destinations = oneDestination();
		List<Object> posted = new ArrayList<>();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> true, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.SENT, routeOf(bridge, destinations));
		assertEquals(1, posted.size());
		PluginMessage message = (PluginMessage) posted.get(0);
		assertEquals("shortestpath", message.getNamespace());
		assertEquals("path", message.getName());
		assertEquals(destinations, message.getData().get("target"));
	}

	/** The event poster throwing is reported, not swallowed or mistaken for success. */
	@Test
	public void callFailedWhenThePosterThrows()
	{
		FakePlugin plugin = new FakePlugin();
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> true, event ->
		{
			throw new RuntimeException("event bus rejected the message");
		});
		assertEquals(ShortestPathBridge.RouteOutcome.CALL_FAILED, routeOf(bridge, oneDestination()));
	}

	/** Availability is re-checked at call time, same discipline as QuestHelperBridge -- not cached from an earlier read. */
	@Test
	public void unavailableAtCallTimeIsNotPosted()
	{
		FakePlugin plugin = new FakePlugin();
		List<Object> posted = new ArrayList<>();
		// Never enabled: availability() is UNAVAILABLE-adjacent (NOT_ENABLED)
		// on every call, so route() must never reach the poster.
		ShortestPathBridge bridge = bridge(() -> List.of(plugin), p -> false, posted::add);

		assertEquals(ShortestPathBridge.RouteOutcome.UNAVAILABLE, routeOf(bridge, oneDestination()));
		assertTrue("an unavailable bridge must never post", posted.isEmpty());
	}
}
