package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays {@link Utterance}s through RuneLite's {@link AudioPlayer}, one at a
 * time, on one executor thread of its own -- never the client thread, which
 * must not wait on decoding or an audio device.
 *
 * <p>{@code AudioPlayer} does not say when a line ends, and "one utterance at
 * a time" needs to know. The joined WAV's length is known, so the next line
 * is released by a timer set to that length rather than by sleeping a thread.
 */
@Slf4j
final class VoicePlayer
{
	/** Slack after a line's length before the next one starts. */
	private static final long GAP_MILLIS = 60;

	private final AudioPlayer audioPlayer;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice");
		thread.setDaemon(true);
		return thread;
	});
	private final VoiceQueue<Job> queue = new VoiceQueue<>(this::submit);
	private volatile ScheduledFuture<?> pending;
	private volatile boolean stopped;

	VoicePlayer(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
	}

	private static final class Job
	{
		final Utterance utterance;
		final int volume;

		Job(Utterance utterance, int volume)
		{
			this.utterance = utterance;
			this.volume = volume;
		}
	}

	/** Says one line, or its ping if a clip is missing; volume 0 to 100. */
	void say(Utterance utterance, int volume)
	{
		if (stopped || utterance == null || volume <= 0)
		{
			return;
		}
		queue.offer(new Job(utterance, volume));
	}

	/** Drops the waiting line and ends the thread. Never blocks. */
	void shutDown()
	{
		stopped = true;
		queue.clear();
		final ScheduledFuture<?> future = pending;
		if (future != null)
		{
			future.cancel(false);
		}
		executor.shutdownNow();
	}

	private void submit(Job job)
	{
		try
		{
			executor.execute(() -> play(job));
		}
		catch (RejectedExecutionException e)
		{
			// Shut down between the offer and here: nothing more will play.
		}
	}

	private void play(Job job)
	{
		if (stopped)
		{
			return;
		}
		try
		{
			final byte[] wav = job.utterance.fallback()
				? VoiceAudio.ping()
				: VoiceAudio.join(job.utterance.clips, VoiceLines.class::getResourceAsStream);
			audioPlayer.play(new ByteArrayInputStream(wav), VoiceAudio.gain(job.volume));
			pending = executor.schedule(queue::finished, VoiceAudio.durationMillis(wav) + GAP_MILLIS,
				TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down mid-line.
		}
		catch (Exception e)
		{
			// AudioPlayer throws checked javax.sound exceptions; any failure just skips the line.
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			queue.finished();
		}
	}
}
