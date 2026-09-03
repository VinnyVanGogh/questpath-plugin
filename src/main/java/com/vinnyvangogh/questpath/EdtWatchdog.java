package com.vinnyvangogh.questpath;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;

/**
 * Temporary diagnostic: says what the event thread is stuck inside.
 *
 * <p>Switch timings established that the sidebar's stalls are the event
 * thread being unavailable rather than any one tab being slow to lay out --
 * a thirty-five component tab measured four seconds while a hundred and
 * sixty-seven component tab measured twelve milliseconds. That rules a lot
 * out and identifies nothing, because a queued task's wait time names the
 * task in front of it and never the one actually running.
 *
 * <p>So: a daemon thread posts a ping every {@link #PING_MILLIS} and, when a
 * ping goes unanswered past {@link #STALL_MILLIS}, dumps the event thread's
 * stack where it currently is. The frame at the top is the answer.
 *
 * <p>Sampling costs one runnable and one stack walk per interval and only
 * walks the stack while something is already wrong, so it is safe to leave
 * running -- but it exists to answer one question and should come out once
 * it has.
 */
@Slf4j
final class EdtWatchdog
{
	private static final long PING_MILLIS = 120;
	private static final long STALL_MILLIS = 400;

	private EdtWatchdog()
	{
	}

	static void start()
	{
		Thread thread = new Thread(EdtWatchdog::loop, "questpath-edt-watchdog");
		thread.setDaemon(true);
		thread.start();
	}

	private static void loop()
	{
		ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		// Collection time is reported alongside every stall, because a stack
		// dump cannot distinguish "this frame is slow" from "this frame was
		// suspended by a collection". If the millis below account for the
		// stall, the stack at the top is a bystander.
		java.util.List<GarbageCollectorMXBean> collectors =
			ManagementFactory.getGarbageCollectorMXBeans();
		long collectedMillis = totalCollectionMillis(collectors);
		// Written by the event thread, read here. Holds the time of the last
		// ping it actually got to; the gap between that and now is how long it
		// has been unavailable.
		AtomicLong lastSeen = new AtomicLong(System.currentTimeMillis());
		boolean reported = false;

		while (true)
		{
			try
			{
				Thread.sleep(PING_MILLIS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}

			SwingUtilities.invokeLater(() -> lastSeen.set(System.currentTimeMillis()));
			long collectedNow = totalCollectionMillis(collectors);
			long collectedDuring = collectedNow - collectedMillis;
			collectedMillis = collectedNow;

			long stalledFor = System.currentTimeMillis() - lastSeen.get();
			if (stalledFor < STALL_MILLIS)
			{
				reported = false;
				continue;
			}
			if (reported)
			{
				// One dump per stall, not one per sample. A four second stall
				// would otherwise print thirty near-identical stacks and bury
				// the one that started it.
				continue;
			}
			reported = true;

			long edtId = findEventThread(threads);
			if (edtId < 0)
			{
				log.info("questpath perf: EDT stalled {}ms, thread not found", stalledFor);
				continue;
			}
			ThreadInfo info = threads.getThreadInfo(edtId, 40);
			if (info == null)
			{
				continue;
			}
			StringBuilder out = new StringBuilder();
			out.append("questpath perf: EDT stalled ").append(stalledFor)
				.append("ms in state ").append(info.getThreadState())
				.append(", ").append(collectedDuring).append("ms of it collecting\n");
			for (StackTraceElement frame : info.getStackTrace())
			{
				out.append("    at ").append(frame).append('\n');
			}
			log.info("{}", out);
		}
	}

	private static long totalCollectionMillis(java.util.List<GarbageCollectorMXBean> collectors)
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

	private static long findEventThread(ThreadMXBean threads)
	{
		for (long id : threads.getAllThreadIds())
		{
			ThreadInfo info = threads.getThreadInfo(id);
			if (info != null && info.getThreadName().startsWith("AWT-EventQueue"))
			{
				return id;
			}
		}
		return -1;
	}
}
