package com.vinnyvangogh.questpath;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 * Says what the event thread is stuck inside.
 *
 * <p>Switch timings established that the sidebar's stalls were the event
 * thread being unavailable rather than any one tab being slow to lay out --
 * a thirty-five component tab measured four seconds while a hundred and
 * sixty-seven component tab measured twelve milliseconds. That ruled a lot
 * out and identified nothing, because a queued task's wait time names the
 * task in front of it and never the one actually running.
 *
 * <p>So: a ping is posted every {@link #PING_MILLIS} and, when one goes
 * unanswered past {@link #STALL_MILLIS}, the event thread's stack is dumped
 * where it currently is. The frame at the top is the answer. That is how the
 * tab-switch stall was identified as AWT's component mixing pass rather than
 * a collection or a layout, and it is worth keeping for the next one.
 *
 * <p>Runs on RuneLite's shared scheduler rather than a thread of its own, so
 * {@link #stop()} actually ends it. A hand-rolled {@code while (true)} daemon
 * cannot be cancelled, which means toggling the plugin off and on leaks one
 * poller per toggle -- each still sampling, forever, for a panel that is no
 * longer on screen.
 */
@Slf4j
final class EdtWatchdog
{
	private static final long PING_MILLIS = 120;
	private static final long STALL_MILLIS = 400;

	/** How many frames of the event thread's stack are worth printing. */
	private static final int STACK_DEPTH = 40;

	private final ScheduledExecutorService scheduler;
	private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
	private final List<GarbageCollectorMXBean> collectors =
		ManagementFactory.getGarbageCollectorMXBeans();

	private ScheduledFuture<?> sampling;

	/**
	 * When the event thread last answered a ping.
	 *
	 * Written by the event thread and read by the scheduler, hence volatile.
	 * The gap between it and now is how long the event thread has been
	 * unavailable.
	 */
	private volatile long lastAnswered = System.currentTimeMillis();

	/** Whether the stall currently in progress has already been dumped. */
	private boolean dumpedThisStall;

	private long collectedAtLastSample;

	EdtWatchdog(ScheduledExecutorService scheduler)
	{
		this.scheduler = scheduler;
		this.collectedAtLastSample = totalCollectionMillis();
	}

	/** Begins sampling. Safe to call twice; the second call does nothing. */
	void start()
	{
		if (sampling != null)
		{
			return;
		}
		sampling = scheduler.scheduleWithFixedDelay(this::sample,
			PING_MILLIS, PING_MILLIS, TimeUnit.MILLISECONDS);
	}

	/** Ends sampling. Safe to call when it was never started. */
	void stop()
	{
		if (sampling != null)
		{
			sampling.cancel(false);
			sampling = null;
		}
	}

	/**
	 * One sample: post a ping, then report if the previous ones went
	 * unanswered.
	 *
	 * Nothing here may throw. A scheduled task that throws is silently
	 * cancelled by the executor, which would turn a diagnostic into a
	 * diagnostic that quietly stopped working.
	 */
	private void sample()
	{
		try
		{
			SwingUtilities.invokeLater(() -> lastAnswered = System.currentTimeMillis());

			long collectedNow = totalCollectionMillis();
			long collectedSinceLastSample = collectedNow - collectedAtLastSample;
			collectedAtLastSample = collectedNow;

			long stalledFor = System.currentTimeMillis() - lastAnswered;
			if (stalledFor < STALL_MILLIS)
			{
				dumpedThisStall = false;
				return;
			}
			if (dumpedThisStall)
			{
				// One dump per stall, not one per sample. A four second stall
				// would otherwise print thirty near-identical stacks and bury
				// the one that started it.
				return;
			}
			dumpedThisStall = true;
			dumpEventThread(stalledFor, collectedSinceLastSample);
		}
		catch (RuntimeException e)
		{
			log.debug("questpath perf: watchdog sample failed", e);
		}
	}

	private void dumpEventThread(long stalledFor, long collectedSinceLastSample)
	{
		long eventThreadId = findEventThread();
		if (eventThreadId < 0)
		{
			log.info("questpath perf: EDT stalled {}ms, thread not found", stalledFor);
			return;
		}
		ThreadInfo eventThread = threads.getThreadInfo(eventThreadId, STACK_DEPTH);
		if (eventThread == null)
		{
			return;
		}

		StringBuilder dump = new StringBuilder();
		dump.append("questpath perf: EDT stalled ").append(stalledFor)
			.append("ms in state ").append(eventThread.getThreadState())
			// Reported alongside every stall, because a stack dump cannot
			// distinguish "this frame is slow" from "this frame was suspended
			// by a collection". If the millis here account for the stall, the
			// stack above them is a bystander.
			.append(", ").append(collectedSinceLastSample).append("ms of it collecting\n");
		for (StackTraceElement frame : eventThread.getStackTrace())
		{
			dump.append("    at ").append(frame).append('\n');
		}
		log.info("{}", dump);
	}

	private long totalCollectionMillis()
	{
		long total = 0;
		for (GarbageCollectorMXBean collector : collectors)
		{
			long millis = collector.getCollectionTime();
			if (millis > 0)
			{
				total += millis;
			}
		}
		return total;
	}

	private long findEventThread()
	{
		for (long id : threads.getAllThreadIds())
		{
			ThreadInfo candidate = threads.getThreadInfo(id);
			if (candidate != null && candidate.getThreadName().startsWith("AWT-EventQueue"))
			{
				return id;
			}
		}
		return -1;
	}
}
