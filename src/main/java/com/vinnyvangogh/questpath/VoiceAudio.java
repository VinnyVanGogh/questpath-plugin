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
 * <p>The Plugin Hub does not allow {@code javax.sound}; playback goes through
 * RuneLite's {@code AudioPlayer}. So the WAV container is read and written
 * here by hand: 8-bit mu-law ({@code generate_voice.py --ulaw}) and 16-bit PCM
 * are the two encodings accepted, and the joined line is always 16-bit signed
 * little-endian PCM.
 *
 * <p>{@link #join} and {@link #gain} descend from Combat Vitals'
 * {@code AlertSounds}.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private static final int FORMAT_PCM = 1;
	private static final int FORMAT_ULAW = 7;
	private static final int FORMAT_EXTENSIBLE = 0xFFFE;
	private static final int HEADER_BYTES = 44;
	private static final int MAX_CLIP_BYTES = 32 * 1024 * 1024;

	private VoiceAudio()
	{
	}

	/** A cleanly-failing WAV that is not one this class can read. */
	static final class UnsupportedWavException extends IOException
	{
		UnsupportedWavException(String message)
		{
			super(message);
		}
	}

	/** One decoded clip: 16-bit signed little-endian PCM. */
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
	 */
	static byte[] join(List<String> parts, Function<String, InputStream> open) throws IOException
	{
		Pcm first = null;
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
				clip = decode(readAll(in));
			}
			if (first == null)
			{
				first = clip;
			}
			else if (clip.rate != first.rate || clip.channels != first.channels)
			{
				throw new UnsupportedWavException("cannot join " + part + ": " + clip.rate + " Hz x"
					+ clip.channels + " to " + first.rate + " Hz x" + first.channels);
			}
			pcm.write(clip.data, 0, clip.data.length);
		}
		if (first == null)
		{
			throw new IOException("no clips");
		}
		return wav(pcm.toByteArray(), first.rate, first.channels);
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

	/** How long a WAV built by {@link #join} or {@link #ping} plays, in milliseconds. */
	static long durationMillis(byte[] wav)
	{
		if (wav.length < HEADER_BYTES)
		{
			return 0;
		}
		final long byteRate = le32(wav, 28);
		return byteRate <= 0 ? 0 : (wav.length - HEADER_BYTES) * 1000L / byteRate;
	}

	/** A canonical 44-byte-header 16-bit PCM WAV. */
	private static byte[] wav(byte[] data, int rate, int channels)
	{
		final byte[] out = new byte[HEADER_BYTES + data.length];
		put(out, 0, "RIFF");
		putLe32(out, 4, 36 + data.length);
		put(out, 8, "WAVE");
		put(out, 12, "fmt ");
		putLe32(out, 16, 16);
		putLe16(out, 20, FORMAT_PCM);
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

	/** Reads a RIFF/WAVE file: mu-law or 16-bit PCM in, 16-bit PCM out. */
	private static Pcm decode(byte[] wav) throws IOException
	{
		if (wav.length < 12 || !tag(wav, 0, "RIFF") || !tag(wav, 8, "WAVE"))
		{
			throw new UnsupportedWavException("not a RIFF/WAVE file");
		}
		int format = -1;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int pos = 12;
		while (pos + 8 <= wav.length)
		{
			final long size = le32(wav, pos + 4);
			final int body = pos + 8;
			if (tag(wav, pos, "fmt ") && size >= 16 && body + 16 <= wav.length)
			{
				format = le16(wav, body);
				channels = le16(wav, body + 2);
				rate = (int) le32(wav, body + 4);
				bits = le16(wav, body + 14);
				if (format == FORMAT_EXTENSIBLE && size >= 26 && body + 26 <= wav.length)
				{
					format = le16(wav, body + 24);
				}
			}
			else if (tag(wav, pos, "data"))
			{
				if (format < 0 || channels < 1 || rate < 1)
				{
					throw new UnsupportedWavException("data chunk before fmt chunk");
				}
				// A data size that overruns the file (a streamed header) is clipped to what is there.
				final int length = (int) Math.min(size, wav.length - body);
				if (length > MAX_CLIP_BYTES)
				{
					throw new UnsupportedWavException("clip too large");
				}
				return new Pcm(rate, channels, toPcm16(wav, body, length, format, bits));
			}
			// Chunks are word-aligned.
			final long next = body + size + (size & 1);
			if (next > wav.length)
			{
				break;
			}
			pos = (int) next;
		}
		throw new UnsupportedWavException("no data chunk");
	}

	private static byte[] toPcm16(byte[] src, int offset, int length, int format, int bits) throws IOException
	{
		if (format == FORMAT_PCM && bits == 16)
		{
			final byte[] out = new byte[length & ~1];
			System.arraycopy(src, offset, out, 0, out.length);
			return out;
		}
		if (format == FORMAT_ULAW && bits == 8)
		{
			final byte[] out = new byte[length * 2];
			for (int i = 0; i < length; i++)
			{
				final int sample = ulaw(src[offset + i]);
				out[2 * i] = (byte) sample;
				out[2 * i + 1] = (byte) (sample >> 8);
			}
			return out;
		}
		throw new UnsupportedWavException("unsupported WAV encoding " + format + " at " + bits + " bits");
	}

	/** ITU-T G.711 mu-law to 16-bit linear. */
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
			if (out.size() > MAX_CLIP_BYTES)
			{
				throw new UnsupportedWavException("clip too large");
			}
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

	private static long le32(byte[] b, int at)
	{
		return (b[at] & 0xFFL) | ((b[at + 1] & 0xFFL) << 8) | ((b[at + 2] & 0xFFL) << 16)
			| ((b[at + 3] & 0xFFL) << 24);
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
