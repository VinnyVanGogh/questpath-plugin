package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Plays {@link Utterance}s through RuneLite's {@link AudioPlayer}, one at a
 * time, on one executor thread of its own -- never the client thread, which
 * must not wait on decoding or an audio device.
 *
 * <p>{@code AudioPlayer.play} returns once the clip has started, so the end
 * of a line is not reported by the device. The joined clip's length is known
 * (see {@link VoiceAudio.Joined}), so the next line is released by a
 * scheduled task after that long, through {@link VoiceQueue}. Nothing sleeps.
 */
@Slf4j
final class VoicePlayer
{
	/** Slack after a line's computed length before the next may start. */
	private static final long TAIL_MILLIS = 60;

	private final AudioPlayer audioPlayer;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice");
		thread.setDaemon(true);
		return thread;
	});
	private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice-timer");
		thread.setDaemon(true);
		return thread;
	});
	private final VoiceQueue<Job> queue = new VoiceQueue<>(this::submit);
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

	/** Drops the waiting line and ends both threads. Never blocks. */
	void shutDown()
	{
		stopped = true;
		queue.clear();
		executor.shutdownNow();
		timer.shutdownNow();
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
			final VoiceAudio.Joined line = job.utterance.fallback()
				? VoiceAudio.ping()
				: VoiceAudio.joinTimed(job.utterance.clips, VoiceLines.class::getResourceAsStream);
			audioPlayer.play(new ByteArrayInputStream(line.wav), VoiceAudio.gain(job.volume));
			release(line.millis + TAIL_MILLIS);
		}
		catch (Exception e)
		{
			// Decode failure, no audio device, or a missing clip: the line is lost, the queue must not be.
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			queue.finished();
		}
	}

	private void release(long millis)
	{
		try
		{
			timer.schedule(queue::finished, millis, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while the line played: nothing more will.
		}
	}
}
