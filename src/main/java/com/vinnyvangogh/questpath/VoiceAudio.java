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
 * <p>Clips are parsed and written by hand rather than through
 * {@code javax.sound}, which Plugin Hub plugins may not use; the joined
 * bytes go to RuneLite's {@code AudioPlayer}. Every part is converted to
 * 16-bit signed PCM, so a list rendered as mu-law
 * ({@code generate_voice.py --ulaw}) joins and plays the same as one
 * rendered as PCM.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	/** A canonical 44-byte RIFF/WAVE header, which is all {@link #wav} writes. */
	private static final int HEADER = 44;

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
	 * Joins several WAV clips into one 16-bit PCM WAV, so a line plays as one
	 * sound with no gaps between parts.
	 *
	 * @param open opens a clip's bytes by its resource path; null if missing
	 */
	static byte[] join(List<String> parts, Function<String, InputStream> open) throws IOException
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
			final byte[] bytes;
			try (InputStream in = raw)
			{
				bytes = readAll(in);
			}
			final Clip clip = parse(bytes, part);
			if (rate == 0)
			{
				rate = clip.rate;
				channels = clip.channels;
			}
			else if (rate != clip.rate || channels != clip.channels)
			{
				throw new IOException("cannot join " + part + ": format differs from the first clip");
			}
			pcm.write(clip.pcm16, 0, clip.pcm16.length);
		}
		if (rate == 0)
		{
			throw new IOException("no clips");
		}
		return wav(pcm.toByteArray(), rate, channels);
	}

	/** How long a WAV built by this class plays, in milliseconds. */
	static long durationMillis(byte[] wav)
	{
		if (wav.length <= HEADER)
		{
			return 0;
		}
		final long byteRate = le32(wav, 28);
		return byteRate <= 0 ? 0 : (wav.length - HEADER) * 1000L / byteRate;
	}

	/** A short soft sine ping, built in code so it can never be the missing file. */
	static byte[] ping()
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
		return wav(data, PING_RATE, 1);
	}

	private static final class Clip
	{
		final int rate;
		final int channels;
		final byte[] pcm16;

		Clip(int rate, int channels, byte[] pcm16)
		{
			this.rate = rate;
			this.channels = channels;
			this.pcm16 = pcm16;
		}
	}

	/** Reads a RIFF/WAVE clip in 16-bit PCM or 8-bit mu-law, as 16-bit little-endian PCM. */
	private static Clip parse(byte[] b, String name) throws IOException
	{
		if (b.length < 12 || !tag(b, 0, "RIFF") || !tag(b, 8, "WAVE"))
		{
			throw new IOException("not a WAV: " + name);
		}
		int format = -1;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		long pos = 12;
		while (pos + 8 <= b.length)
		{
			final int at = (int) pos;
			final long size = le32(b, at + 4);
			final int body = at + 8;
			if (tag(b, at, "fmt ") && body + 16 <= b.length)
			{
				format = le16(b, body);
				channels = le16(b, body + 2);
				rate = (int) le32(b, body + 4);
				bits = le16(b, body + 14);
			}
			else if (tag(b, at, "data"))
			{
				final int len = (int) Math.min(size, b.length - body);
				return new Clip(rate, channels, toPcm16(b, body, len, format, bits, name));
			}
			pos = body + size + (size & 1);
		}
		throw new IOException("no data chunk: " + name);
	}

	private static byte[] toPcm16(byte[] b, int off, int len, int format, int bits, String name) throws IOException
	{
		if (format == 1 && bits == 16)
		{
			final byte[] out = new byte[len & ~1];
			System.arraycopy(b, off, out, 0, out.length);
			return out;
		}
		if (format == 7 && bits == 8)
		{
			final byte[] out = new byte[len * 2];
			for (int i = 0; i < len; i++)
			{
				final int sample = ulaw(b[off + i]);
				out[2 * i] = (byte) sample;
				out[2 * i + 1] = (byte) (sample >> 8);
			}
			return out;
		}
		throw new IOException("unsupported WAV encoding (format " + format + ", " + bits + " bit): " + name);
	}

	/** ITU-T G.711 mu-law byte to a 16-bit linear sample. */
	private static int ulaw(byte value)
	{
		final int u = ~value & 0xff;
		final int magnitude = (((u & 0x0f) << 3) + 0x84) << ((u & 0x70) >> 4);
		return (u & 0x80) != 0 ? 0x84 - magnitude : magnitude - 0x84;
	}

	private static byte[] wav(byte[] data, int rate, int channels)
	{
		final byte[] out = new byte[HEADER + data.length];
		final int blockAlign = channels * 2;
		put(out, 0, "RIFF");
		put32(out, 4, 36 + data.length);
		put(out, 8, "WAVE");
		put(out, 12, "fmt ");
		put32(out, 16, 16);
		put16(out, 20, 1);
		put16(out, 22, channels);
		put32(out, 24, rate);
		put32(out, 28, rate * blockAlign);
		put16(out, 32, blockAlign);
		put16(out, 34, 16);
		put(out, 36, "data");
		put32(out, 40, data.length);
		System.arraycopy(data, 0, out, HEADER, data.length);
		return out;
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

	private static void put(byte[] b, int at, String tag)
	{
		for (int i = 0; i < 4; i++)
		{
			b[at + i] = (byte) tag.charAt(i);
		}
	}

	private static void put16(byte[] b, int at, int v)
	{
		b[at] = (byte) v;
		b[at + 1] = (byte) (v >> 8);
	}

	private static void put32(byte[] b, int at, int v)
	{
		put16(b, at, v);
		put16(b, at + 2, v >> 16);
	}

	private static int le16(byte[] b, int at)
	{
		return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8);
	}

	private static long le32(byte[] b, int at)
	{
		return (le16(b, at) & 0xffffL) | ((long) le16(b, at + 2) << 16);
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
}
