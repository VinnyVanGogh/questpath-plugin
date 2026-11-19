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
 * <p>The Plugin Hub does not accept {@code javax.sound}; playback goes
 * through RuneLite's {@code AudioPlayer}, which takes a finished WAV. So the
 * WAV is read and written here by hand: a RIFF header, then PCM, mu-law or
 * A-law samples decoded to 16-bit little-endian PCM. That keeps a list
 * rendered as mu-law ({@code generate_voice.py --ulaw}) joining and playing
 * the same as one rendered as PCM.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private static final int HEADER_BYTES = 44;
	private static final int FORMAT_PCM = 1;
	private static final int FORMAT_ALAW = 6;
	private static final int FORMAT_ULAW = 7;
	private static final int FORMAT_EXTENSIBLE = 0xFFFE;

	private VoiceAudio()
	{
	}

	/** A decoded clip: 16-bit little-endian PCM and the format it came in. */
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
	 * @throws IOException a clip is missing, is not a WAV this can read, or
	 *                     differs in rate or channels from the first
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

	/** How long a WAV written by {@link #join} or {@link #ping} plays, in milliseconds. */
	static long durationMillis(byte[] wav)
	{
		if (wav.length <= HEADER_BYTES)
		{
			return 0;
		}
		final long rate = le32(wav, 24);
		final long channels = le16(wav, 22);
		final long bytesPerSecond = rate * channels * 2;
		return bytesPerSecond == 0 ? 0 : (wav.length - HEADER_BYTES) * 1000 / bytesPerSecond;
	}

	/** A canonical 44-byte-header PCM WAV around 16-bit little-endian samples. */
	private static byte[] wav(byte[] data, int rate, int channels)
	{
		final byte[] out = new byte[HEADER_BYTES + data.length];
		putAscii(out, 0, "RIFF");
		putLe32(out, 4, 36 + data.length);
		putAscii(out, 8, "WAVE");
		putAscii(out, 12, "fmt ");
		putLe32(out, 16, 16);
		putLe16(out, 20, FORMAT_PCM);
		putLe16(out, 22, channels);
		putLe32(out, 24, rate);
		putLe32(out, 28, rate * channels * 2);
		putLe16(out, 32, channels * 2);
		putLe16(out, 34, 16);
		putAscii(out, 36, "data");
		putLe32(out, 40, data.length);
		System.arraycopy(data, 0, out, HEADER_BYTES, data.length);
		return out;
	}

	/** Reads a RIFF/WAVE file's fmt and data chunks and returns its samples as 16-bit PCM. */
	private static Pcm decode(byte[] file, String name) throws IOException
	{
		if (file.length < 12 || !ascii(file, 0, "RIFF") || !ascii(file, 8, "WAVE"))
		{
			throw new IOException("not a WAV: " + name);
		}
		int format = -1;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int pos = 12;
		while (pos + 8 <= file.length)
		{
			final long size = le32(file, pos + 4);
			final int body = pos + 8;
			if (ascii(file, pos, "fmt "))
			{
				if (size < 16 || body + 16 > file.length)
				{
					throw new IOException("short fmt chunk: " + name);
				}
				format = le16(file, body);
				channels = le16(file, body + 2);
				rate = (int) le32(file, body + 4);
				bits = le16(file, body + 14);
				if (format == FORMAT_EXTENSIBLE && size >= 26 && body + 26 <= file.length)
				{
					format = le16(file, body + 24);
				}
			}
			else if (ascii(file, pos, "data"))
			{
				if (format < 0)
				{
					throw new IOException("data before fmt: " + name);
				}
				// A streamed WAV can claim more data than the file holds; take what is there.
				final int length = (int) Math.min(size, file.length - body);
				return new Pcm(rate, channels, toPcm16(file, body, length, format, bits, name));
			}
			pos = (int) Math.min(Integer.MAX_VALUE, body + size + (size & 1));
		}
		throw new IOException("no data chunk: " + name);
	}

	private static byte[] toPcm16(byte[] file, int from, int length, int format, int bits, String name)
		throws IOException
	{
		if (format == FORMAT_PCM && bits == 16)
		{
			final byte[] out = new byte[length - (length & 1)];
			System.arraycopy(file, from, out, 0, out.length);
			return out;
		}
		if ((format == FORMAT_ULAW || format == FORMAT_ALAW) && bits == 8)
		{
			final byte[] out = new byte[length * 2];
			for (int i = 0; i < length; i++)
			{
				final int sample = format == FORMAT_ULAW ? ulaw(file[from + i]) : alaw(file[from + i]);
				out[2 * i] = (byte) sample;
				out[2 * i + 1] = (byte) (sample >> 8);
			}
			return out;
		}
		throw new IOException("unsupported WAV encoding (format " + format + ", " + bits + " bit): " + name);
	}

	/** G.711 mu-law byte to a 16-bit sample. */
	private static int ulaw(byte value)
	{
		final int u = ~value & 0xFF;
		final int magnitude = (((u & 0x0F) << 3) + 0x84) << ((u & 0x70) >> 4);
		return (u & 0x80) != 0 ? 0x84 - magnitude : magnitude - 0x84;
	}

	/** G.711 A-law byte to a 16-bit sample. */
	private static int alaw(byte value)
	{
		final int a = (value & 0xFF) ^ 0x55;
		final int exponent = (a & 0x70) >> 4;
		int magnitude = (a & 0x0F) << 4;
		magnitude += 8;
		if (exponent != 0)
		{
			magnitude += 0x100;
			magnitude <<= exponent - 1;
		}
		return (a & 0x80) != 0 ? magnitude : -magnitude;
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

	private static boolean ascii(byte[] b, int at, String tag)
	{
		if (at + tag.length() > b.length)
		{
			return false;
		}
		for (int i = 0; i < tag.length(); i++)
		{
			if (b[at + i] != (byte) tag.charAt(i))
			{
				return false;
			}
		}
		return true;
	}

	private static void putAscii(byte[] b, int at, String tag)
	{
		for (int i = 0; i < tag.length(); i++)
		{
			b[at + i] = (byte) tag.charAt(i);
		}
	}

	private static int le16(byte[] b, int at)
	{
		return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
	}

	private static long le32(byte[] b, int at)
	{
		return (le16(b, at) | ((long) le16(b, at + 2) << 16)) & 0xFFFFFFFFL;
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
