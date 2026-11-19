package com.vinnyvangogh.questpath.scenarios;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.eventbus.EventBus;

/**
 * A second, private {@link EventBus} with exactly one subscriber registered: the plugin under test. Posting here
 * reaches that plugin's {@code @Subscribe} methods and nobody else, so a scenario aimed at one plugin cannot
 * trigger any other plugin in the client. No reflection: {@code EventBus.register} is the same public call the
 * client makes for every plugin.
 *
 * <p>The owner is looked up on the first post (the plugin may not be running yet when the sidebar starts) and
 * registered once; if the plugin is stopped and started again the new instance is registered in its place.
 * Objects the plugin registers on the client's own bus by itself (an overlay, a helper) are not on this bus.
 */
final class PrivateBus implements Consumer<Object>
{
	private final EventBus bus = new EventBus();
	private final Supplier<Object> owner;
	private Object registered;

	/** {@code owner}: the running plugin instance, or null when it is not running. */
	PrivateBus(Supplier<Object> owner)
	{
		this.owner = owner;
	}

	@Override
	public synchronized void accept(Object event)
	{
		Object plugin = owner.get();
		if (plugin == null)
		{
			throw new ScenarioException("the plugin under test is not running, so there is nobody to send to;"
				+ " enable it, or choose 'All plugins'");
		}
		if (plugin != registered)
		{
			close();
			bus.register(plugin);
			registered = plugin;
		}
		bus.post(event);
	}

	/** Takes the plugin off the private bus. Safe to call twice. */
	synchronized void close()
	{
		if (registered != null)
		{
			bus.unregister(registered);
			registered = null;
		}
	}
}
