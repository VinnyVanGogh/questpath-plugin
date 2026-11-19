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
 * <p>Plugin Hub code may not use {@code javax.sound}, so the WAV is read and
 * written here by hand; playback goes through RuneLite's {@code AudioPlayer}
 * (see {@link VoicePlayer}). Only 16-bit PCM and G.711 mu-law clips are
 * understood, which is what {@code generate_voice.py} renders. Every part is
 * converted to 16-bit signed PCM, so a list rendered as mu-law ({@code
 * generate_voice.py --ulaw}) joins and plays the same as one rendered as PCM.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private static final int HEADER_BYTES = 44;
	private static final int TAG_PCM = 1;
	private static final int TAG_ULAW = 7;

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
				throw new IOException("cannot join " + part + ": " + clip.rate + " Hz x" + clip.channels
					+ " to " + rate + " Hz x" + channels);
			}
			pcm.write(clip.pcm16, 0, clip.pcm16.length);
		}
		if (rate == 0)
		{
			throw new IOException("no clips");
		}
		return wav(pcm.toByteArray(), rate, channels);
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

	/** How long a WAV made by this class plays, in milliseconds; 0 if it is not one. */
	static long durationMillis(byte[] wav)
	{
		if (wav.length <= HEADER_BYTES)
		{
			return 0;
		}
		final int channels = le16(wav, 22);
		final int rate = le32(wav, 24);
		if (channels <= 0 || rate <= 0)
		{
			return 0;
		}
		final long frames = (wav.length - HEADER_BYTES) / (2L * channels);
		return frames * 1000 / rate;
	}

	/** A canonical 44-byte-header WAV around 16-bit little-endian PCM. */
	private static byte[] wav(byte[] data, int rate, int channels)
	{
		final byte[] out = new byte[HEADER_BYTES + data.length];
		put(out, 0, "RIFF");
		putLe32(out, 4, 36 + data.length);
		put(out, 8, "WAVE");
		put(out, 12, "fmt ");
		putLe32(out, 16, 16);
		putLe16(out, 20, TAG_PCM);
		putLe16(out, 22, channels);
		putLe32(out, 24, rate);
		putLe32(out, 28, rate * channels * 2);
		putLe16(out, 32, channels * 2);
		putLe16(out, 34, 16);
		put(out, 36, "data");
		putLe32(out, 40, data.length);
		System.arraycopy(data, 0, out, HEADER_BYTES, data.length);
		return out;
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

	/** Reads a RIFF/WAVE file's format and samples as 16-bit PCM. */
	private static Clip parse(byte[] wav, String name) throws IOException
	{
		if (wav.length < 12 || !tag(wav, 0, "RIFF") || !tag(wav, 8, "WAVE"))
		{
			throw new IOException("not a WAV: " + name);
		}
		int format = -1;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int pos = 12;
		while (pos + 8 <= wav.length)
		{
			final long size = le32(wav, pos + 4) & 0xFFFFFFFFL;
			final int body = pos + 8;
			if (tag(wav, pos, "fmt ") && body + 16 <= wav.length)
			{
				format = le16(wav, body);
				channels = le16(wav, body + 2);
				rate = le32(wav, body + 4);
				bits = le16(wav, body + 14);
			}
			else if (tag(wav, pos, "data"))
			{
				// A streamed file can claim more than it holds; take what is there.
				final int length = (int) Math.min(size, wav.length - body);
				return new Clip(rate, channels, decode(wav, body, length, format, channels, rate, bits, name));
			}
			final long next = body + size + (size & 1);
			if (next > wav.length)
			{
				break;
			}
			pos = (int) next;
		}
		throw new IOException("no audio data in " + name);
	}

	private static byte[] decode(byte[] wav, int from, int length, int format, int channels, int rate, int bits,
		String name) throws IOException
	{
		if (channels < 1 || rate < 1)
		{
			throw new IOException("no format in " + name);
		}
		if (format == TAG_PCM && bits == 16)
		{
			final int usable = length - (length % (2 * channels));
			final byte[] out = new byte[usable];
			System.arraycopy(wav, from, out, 0, usable);
			return out;
		}
		if (format == TAG_ULAW && bits == 8)
		{
			final byte[] out = new byte[length * 2];
			for (int i = 0; i < length; i++)
			{
				final int sample = ulaw(wav[from + i]);
				out[2 * i] = (byte) sample;
				out[2 * i + 1] = (byte) (sample >> 8);
			}
			return out;
		}
		throw new IOException("unsupported WAV encoding (tag " + format + ", " + bits + " bit) in " + name);
	}

	/** G.711 mu-law byte to a 16-bit linear sample. */
	private static int ulaw(byte encoded)
	{
		final int u = ~encoded & 0xFF;
		final int exponent = (u >> 4) & 0x07;
		final int mantissa = u & 0x0F;
		final int magnitude = (((mantissa << 3) + 0x84) << exponent) - 0x84;
		return (u & 0x80) != 0 ? -magnitude : magnitude;
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

	private static int le16(byte[] b, int at)
	{
		return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
	}

	private static int le32(byte[] b, int at)
	{
		return le16(b, at) | (le16(b, at + 2) << 16);
	}

	private static void putLe16(byte[] b, int at, int v)
	{
		b[at] = (byte) v;
		b[at + 1] = (byte) (v >> 8);
	}

	private static void putLe32(byte[] b, int at, int v)
	{
		putLe16(b, at, v);
		putLe16(b, at + 2, v >> 16);
	}
}
