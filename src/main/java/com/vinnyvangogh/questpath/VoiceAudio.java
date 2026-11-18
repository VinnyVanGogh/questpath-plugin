package com.vinnyvangogh.questpath;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * The audio half of the voice, kept free of any line or device so it can be
 * tested: joining clips into one WAV, the fallback ping, and the volume
 * slider as a gain.
 *
 * <p>{@link #join} and {@link #gain} are ported from Combat Vitals'
 * {@code AlertSounds} (combat-vitals-plugin/src/main/java/com/vinnyvangogh/combatvitals/AlertSounds.java),
 * with one change: every part is converted to 16-bit signed PCM, so a list
 * rendered as mu-law ({@code generate_voice.py --ulaw}) joins and plays the
 * same as one rendered as PCM.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final float PING_RATE = 22050f;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private VoiceAudio()
	{
	}

	/** Volume 0 to 100 as a gain in dB, as RuneLite's {@code Notifier} converts it. */
	static float gain(int volume)
	{
		final int clamped = Math.max(1, Math.min(100, volume));
		return (float) (Math.log10(clamped / 100.0) * 20);
	}

	/**
	 * Joins several WAV clips into one WAV, so a line plays as one sound with
	 * no gaps between parts.
	 *
	 * @param open opens a clip's bytes by its resource path; null if missing
	 */
	static byte[] join(List<String> parts, Function<String, InputStream> open)
		throws IOException, UnsupportedAudioFileException
	{
		AudioFormat format = null;
		final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
		for (String part : parts)
		{
			final InputStream raw = open.apply(part);
			if (raw == null)
			{
				throw new IOException("missing clip " + part);
			}
			try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw)))
			{
				if (format == null)
				{
					format = pcm16(in.getFormat());
				}
				copy(inFormat(in, format), pcm);
			}
		}
		if (format == null)
		{
			throw new IOException("no clips");
		}
		return wav(pcm.toByteArray(), format);
	}

	/** A short soft sine ping, built in code so it can never be the missing file. */
	static byte[] ping() throws IOException
	{
		final AudioFormat format = new AudioFormat(PING_RATE, 16, 1, true, false);
		final int frames = (int) (PING_RATE * PING_SECONDS);
		final byte[] data = new byte[frames * 2];
		for (int i = 0; i < frames; i++)
		{
			final double t = i / (double) PING_RATE;
			// Fast attack, exponential decay: a bell, not a beep.
			final double envelope = Math.min(1.0, i / 200.0) * Math.exp(-t * 18);
			final int sample = (int) (Math.sin(2 * Math.PI * PING_HZ * t) * envelope * 0.5 * Short.MAX_VALUE);
			data[2 * i] = (byte) sample;
			data[2 * i + 1] = (byte) (sample >> 8);
		}
		return wav(data, format);
	}

	private static byte[] wav(byte[] data, AudioFormat format) throws IOException
	{
		final AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format,
			data.length / format.getFrameSize());
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
		return out.toByteArray();
	}

	/** The same rate and channels, as 16-bit little-endian signed PCM. */
	private static AudioFormat pcm16(AudioFormat source)
	{
		return new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
	}

	/** The clip in the joined line's format; a clip that cannot be converted is a checked error, not a crash. */
	private static AudioInputStream inFormat(AudioInputStream in, AudioFormat format) throws UnsupportedAudioFileException
	{
		if (in.getFormat().matches(format))
		{
			return in;
		}
		if (!AudioSystem.isConversionSupported(format, in.getFormat()))
		{
			throw new UnsupportedAudioFileException("cannot join " + in.getFormat() + " to " + format);
		}
		return AudioSystem.getAudioInputStream(format, in);
	}

	private static void copy(InputStream in, ByteArrayOutputStream out) throws IOException
	{
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) > 0)
		{
			out.write(buffer, 0, read);
		}
	}
}
