package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
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
 * <p>{@code AudioPlayer} starts a clip and returns, and says nothing when it
 * ends. "One utterance at a time" needs to know, so the joined WAV's length
 * is computed ({@link VoiceAudio#durationMillis}) and the next line is
 * released from a timer on the same executor, through {@link VoiceQueue}.
 */
@Slf4j
final class VoicePlayer
{
	/** Slack after a line's computed length, so the next one does not clip its tail. */
	private static final long TAIL_MILLIS = 60;

	private final AudioPlayer audio;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice");
		thread.setDaemon(true);
		return thread;
	});
	private final VoiceQueue<Job> queue = new VoiceQueue<>(this::submit);
	private volatile boolean stopped;

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

	VoicePlayer(AudioPlayer audio)
	{
		this.audio = audio;
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
			audio.play(new ByteArrayInputStream(wav), VoiceAudio.gain(job.volume));
			executor.schedule(queue::finished, VoiceAudio.durationMillis(wav) + TAIL_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while this line was starting.
		}
		catch (Exception e)
		{
			// AudioPlayer declares javax.sound's checked exceptions, which the
			// Hub does not let a plugin name; any failure means "could not play".
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			queue.finished();
		}
	}
}
