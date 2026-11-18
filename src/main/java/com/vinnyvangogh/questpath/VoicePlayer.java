package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays {@link Utterance}s through {@code javax.sound.sampled}, one at a time,
 * on one executor thread of its own -- never the client thread, which must
 * not wait on decoding or an audio device.
 *
 * <p>Combat Vitals hands its joined clip to RuneLite's {@code AudioPlayer} on
 * its executor (CombatVitalsPlugin#play); this opens the {@link Clip} itself
 * instead, because "one utterance at a time" needs to know when a line ends,
 * and only the line's own {@code STOP} event says so without sleeping a
 * thread. The next line starts from that event through {@link VoiceQueue}.
 */
@Slf4j
final class VoicePlayer
{
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
	{
		final Thread thread = new Thread(runnable, "quest-route-voice");
		thread.setDaemon(true);
		return thread;
	});
	private final VoiceQueue<Job> queue = new VoiceQueue<>(this::submit);
	private volatile Clip current;
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

	/** Says one line, or its ping if a clip is missing; volume 0 to 100. */
	void say(Utterance utterance, int volume)
	{
		if (stopped || utterance == null || volume <= 0)
		{
			return;
		}
		queue.offer(new Job(utterance, volume));
	}

	/** Stops the playing line, drops the waiting one, and ends the thread. Never blocks. */
	void shutDown()
	{
		stopped = true;
		queue.clear();
		final Clip clip = current;
		if (clip != null)
		{
			clip.close();
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
			final AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
			final Clip clip = AudioSystem.getClip();
			final AtomicBoolean done = new AtomicBoolean();
			clip.addLineListener(event ->
			{
				if ((event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE)
					&& done.compareAndSet(false, true))
				{
					clip.close();
					queue.finished();
				}
			});
			clip.open(in);
			setGain(clip, job.volume);
			current = clip;
			clip.start();
		}
		catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException e)
		{
			log.debug("Quest Route could not play \"{}\"", job.utterance.text, e);
			queue.finished();
		}
	}

	private static void setGain(Clip clip, int volume)
	{
		if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			return;
		}
		final FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
		control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), VoiceAudio.gain(volume))));
	}
}
