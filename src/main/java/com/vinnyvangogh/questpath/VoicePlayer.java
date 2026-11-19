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
 * <p>{@code AudioPlayer.play} returns as soon as the sound starts, so "one
 * utterance at a time" cannot wait on a line event. The joined WAV's length
 * is known, though ({@link VoiceAudio#durationMillis}), so the next line is
 * started from a scheduled task that fires when this one should have ended,
 * through {@link VoiceQueue}.
 */
@Slf4j
final class VoicePlayer
{
	private final AudioPlayer audioPlayer;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice");
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
		long millis = 0;
		try
		{
			final byte[] wav = job.utterance.fallback()
				? VoiceAudio.ping()
				: VoiceAudio.join(job.utterance.clips, VoiceLines.class::getResourceAsStream);
			millis = VoiceAudio.durationMillis(wav);
			audioPlayer.play(new ByteArrayInputStream(wav), VoiceAudio.gain(job.volume));
		}
		catch (Exception e)
		{
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			millis = 0;
		}
		try
		{
			executor.schedule(queue::finished, millis, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while playing: the queue is not needed any more.
		}
	}
}
