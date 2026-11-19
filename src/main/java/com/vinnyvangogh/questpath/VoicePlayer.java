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
 * <p>{@code AudioPlayer} reports no end-of-line event, so the line's length is
 * read from the WAV this class joined itself ({@link VoiceAudio#durationMillis})
 * and the next line is released from a timer, through {@link VoiceQueue}.
 */
@Slf4j
final class VoicePlayer
{
	/** Slack after a line's length before the next may start, for the device's own buffering. */
	private static final long TAIL_MILLIS = 60;

	private final AudioPlayer audioPlayer;
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

	VoicePlayer(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
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
			audioPlayer.play(new ByteArrayInputStream(wav), VoiceAudio.gain(job.volume));
			executor.schedule(queue::finished, VoiceAudio.durationMillis(wav) + TAIL_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e)
		{
			// Shut down while this line was starting.
		}
		catch (Exception e)
		{
			// AudioPlayer declares javax.sound's checked exceptions; none is worth telling the player.
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			queue.finished();
		}
	}
}
