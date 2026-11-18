package com.vinnyvangogh.questpath;

import java.util.function.Consumer;

/**
 * One utterance at a time, and only the newest waiting: while a line plays,
 * a second one waits, and a third replaces the second. A shop reminder
 * queued behind "Bucket nearby" is worth hearing; five floor items queued
 * behind it are not, and only the latest still describes where you are.
 *
 * <p>Pure and thread-safe: {@link #offer} comes from the client thread,
 * {@link #finished} from the audio line's own event thread. Starting a
 * line is handed to {@code start}, which the player points at its single
 * executor.
 */
final class VoiceQueue<T>
{
	private final Consumer<T> start;
	private boolean playing;
	private T pending;

	VoiceQueue(Consumer<T> start)
	{
		this.start = start;
	}

	/** Plays now if idle, otherwise waits, replacing anything already waiting. */
	void offer(T item)
	{
		final T now;
		synchronized (this)
		{
			if (playing)
			{
				pending = item;
				return;
			}
			playing = true;
			now = item;
		}
		start.accept(now);
	}

	/** The playing line ended (or failed to start): the waiting one goes next. */
	void finished()
	{
		final T next;
		synchronized (this)
		{
			next = pending;
			pending = null;
			playing = next != null;
		}
		if (next != null)
		{
			start.accept(next);
		}
	}

	/** Drops whatever is waiting; the playing line, if any, still ends normally. */
	synchronized void clear()
	{
		pending = null;
	}

	synchronized boolean playing()
	{
		return playing;
	}
}
