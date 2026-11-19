package com.vinnyvangogh.questpath.scenarios;

import java.util.function.Consumer;

/**
 * Optional "Dry run" for a plugin's outputs, <b>off by default</b>: a webhook you configure gets real posts unless
 * you turn this on in the sidebar. While on, a wrapped output writes what it would have sent to the scenario log
 * instead of sending it.
 *
 * <p>It only covers outputs the developer launcher routes through {@link #wrap}; it cannot reach into a plugin's
 * own fields. A plugin whose sender is injected (or a {@code Consumer} field the launcher can supply) opts in:
 * <pre>
 *   Consumer&lt;String&gt; send = DryRun.shared().wrap("Discord webhook", realSender);
 * </pre>
 */
public final class DryRun
{
	private static final DryRun SHARED = new DryRun();

	private volatile boolean enabled;
	private volatile Consumer<String> log = line ->
	{
	};

	/** The one switch the sidebar flips. */
	public static DryRun shared()
	{
		return SHARED;
	}

	public boolean enabled()
	{
		return enabled;
	}

	public void enabled(boolean on)
	{
		enabled = on;
	}

	/** Where "would have sent" lines go: the sidebar's log. */
	public void log(Consumer<String> sink)
	{
		log = sink;
	}

	/** {@code real} while the switch is off; while on, {@code real} is never called and the log says what it skipped. */
	public <T> Consumer<T> wrap(String output, Consumer<T> real)
	{
		return value ->
		{
			if (enabled)
			{
				log.accept("dry run: " + output + " not sent: " + value);
			}
			else
			{
				real.accept(value);
			}
		};
	}
}
