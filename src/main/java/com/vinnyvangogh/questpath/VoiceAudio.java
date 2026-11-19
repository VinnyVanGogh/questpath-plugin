package com.vinnyvangogh.questpath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * The audio half of the voice, kept free of any line or device so it can be
 * tested: joining clips into one WAV, the fallback ping, and the volume
 * slider as a gain.
 *
 * <p>The Plugin Hub forbids {@code javax.sound}, so WAV files are read and
 * written by hand here and playback goes through RuneLite's own
 * {@code AudioPlayer}. Supported inputs are RIFF/WAVE, mono or stereo, as
 * 16-bit signed PCM or 8-bit mu-law ({@code generate_voice.py --ulaw});
 * every part is converted to 16-bit little-endian PCM, so both join and play
 * the same.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private static final int FORMAT_PCM = 1;
	private static final int FORMAT_ULAW = 7;

	private VoiceAudio()
	{
	}

	/** One decoded clip: 16-bit little-endian PCM plus its shape. */
	private static final class Pcm
	{
		final int rate;
		final int channels;
		final byte[] data;

		Pcm(int rate, int channels, byte[] data)
		{
			this.rate = rate;
			this.channels = channels;
			this.data = data;
		}
	}

	/** A joined line: a playable WAV and how long it lasts. */
	static final class Joined
	{
		final byte[] wav;
		final long millis;

		Joined(byte[] wav, long millis)
		{
			this.wav = wav;
			this.millis = millis;
		}
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
	static byte[] join(List<String> parts, Function<String, InputStream> open) throws IOException
	{
		return joinTimed(parts, open).wav;
	}

	/** {@link #join}, also reporting the line's length so the caller knows when it ends. */
	static Joined joinTimed(List<String> parts, Function<String, InputStream> open) throws IOException
	{
		int rate = 0;
		int channels = 0;
		final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
		for (String part : parts)
		{
			final InputStream raw = open.apply(part);
			if (raw == null)
			{
				throw new IOException("missing clip " + part);
			}
			final Pcm clip;
			try (InputStream in = raw)
			{
				clip = decode(readAll(in), part);
			}
			if (rate == 0)
			{
				rate = clip.rate;
				channels = clip.channels;
			}
			else if (clip.rate != rate || clip.channels != channels)
			{
				throw new IOException("cannot join " + part + ": " + clip.rate + " Hz x" + clip.channels
					+ " to " + rate + " Hz x" + channels);
			}
			pcm.write(clip.data, 0, clip.data.length);
		}
		if (rate == 0)
		{
			throw new IOException("no clips");
		}
		final byte[] data = pcm.toByteArray();
		return new Joined(wav(data, rate, channels), millis(data.length, rate, channels));
	}

	/** A short soft sine ping, built in code so it can never be the missing file. */
	static Joined ping()
	{
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
		return new Joined(wav(data, PING_RATE, 1), millis(data.length, PING_RATE, 1));
	}

	private static long millis(int pcmBytes, int rate, int channels)
	{
		return pcmBytes * 1000L / ((long) rate * channels * 2);
	}

	/** Reads a RIFF/WAVE file's fmt and data chunks into 16-bit little-endian PCM. */
	private static Pcm decode(byte[] file, String name) throws IOException
	{
		if (file.length < 12 || !tag(file, 0, "RIFF") || !tag(file, 8, "WAVE"))
		{
			throw new IOException(name + " is not a WAV file");
		}
		int format = 0;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int pos = 12;
		while (pos + 8 <= file.length)
		{
			final long size = le32(file, pos + 4);
			final int body = pos + 8;
			if (tag(file, pos, "fmt ") && size >= 16 && body + 16 <= file.length)
			{
				format = le16(file, body);
				channels = le16(file, body + 2);
				rate = (int) le32(file, body + 4);
				bits = le16(file, body + 14);
			}
			else if (tag(file, pos, "data"))
			{
				if (rate <= 0 || channels < 1 || channels > 2)
				{
					throw new IOException(name + " has no usable fmt chunk before its data");
				}
				// A streamed WAV can declare a data size past the end of the file.
				final int length = (int) Math.min(size, file.length - body);
				return new Pcm(rate, channels, toPcm16(file, body, length, format, bits, name));
			}
			// Chunks are word-aligned.
			pos = (int) Math.min((long) body + size + (size & 1), file.length);
		}
		throw new IOException(name + " has no data chunk");
	}

	private static byte[] toPcm16(byte[] file, int from, int length, int format, int bits, String name)
		throws IOException
	{
		if (format == FORMAT_PCM && bits == 16)
		{
			final byte[] out = new byte[length & ~1];
			System.arraycopy(file, from, out, 0, out.length);
			return out;
		}
		if (format == FORMAT_ULAW && bits == 8)
		{
			final byte[] out = new byte[length * 2];
			for (int i = 0; i < length; i++)
			{
				final int sample = ulaw(file[from + i]);
				out[2 * i] = (byte) sample;
				out[2 * i + 1] = (byte) (sample >> 8);
			}
			return out;
		}
		throw new IOException(name + " is format " + format + " at " + bits + " bits; only 16-bit PCM and mu-law play");
	}

	/** ITU G.711 mu-law byte to a 16-bit linear sample. */
	private static int ulaw(byte value)
	{
		final int u = ~value & 0xff;
		final int exponent = (u >> 4) & 0x07;
		final int mantissa = u & 0x0f;
		final int magnitude = (((mantissa << 3) + 0x84) << exponent) - 0x84;
		return (u & 0x80) != 0 ? -magnitude : magnitude;
	}

	private static byte[] wav(byte[] data, int rate, int channels)
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream(44 + data.length);
		writeTag(out, "RIFF");
		writeLe32(out, 36 + data.length);
		writeTag(out, "WAVE");
		writeTag(out, "fmt ");
		writeLe32(out, 16);
		writeLe16(out, FORMAT_PCM);
		writeLe16(out, channels);
		writeLe32(out, rate);
		writeLe32(out, rate * channels * 2);
		writeLe16(out, channels * 2);
		writeLe16(out, 16);
		writeTag(out, "data");
		writeLe32(out, data.length);
		out.write(data, 0, data.length);
		return out.toByteArray();
	}

	private static byte[] readAll(InputStream in) throws IOException
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) > 0)
		{
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static boolean tag(byte[] b, int at, String tag)
	{
		if (at + 4 > b.length)
		{
			return false;
		}
		for (int i = 0; i < 4; i++)
		{
			if (b[at + i] != (byte) tag.charAt(i))
			{
				return false;
			}
		}
		return true;
	}

	private static int le16(byte[] b, int at)
	{
		return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8);
	}

	private static long le32(byte[] b, int at)
	{
		return (le16(b, at) | ((long) le16(b, at + 2) << 16)) & 0xffffffffL;
	}

	private static void writeTag(ByteArrayOutputStream out, String tag)
	{
		for (int i = 0; i < 4; i++)
		{
			out.write(tag.charAt(i));
		}
	}

	private static void writeLe16(ByteArrayOutputStream out, int value)
	{
		out.write(value & 0xff);
		out.write((value >> 8) & 0xff);
	}

	private static void writeLe32(ByteArrayOutputStream out, int value)
	{
		writeLe16(out, value & 0xffff);
		writeLe16(out, (value >>> 16) & 0xffff);
	}
}
