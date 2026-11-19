package com.vinnyvangogh.questpath;

import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * The audio half of the voice, kept free of any line or device so it can be
 * tested: joining clips into one WAV, the fallback ping, and the volume
 * slider as a gain.
 *
 * <p>WAV files are read and written by hand so the plugin never touches the
 * Java sound API: the Plugin Hub requires RuneLite's {@code AudioPlayer} for
 * all audio. Clips may be 8-bit mu-law ({@code generate_voice.py --ulaw}) or
 * PCM; every part is decoded to 16-bit signed little-endian PCM, so both
 * join and play the same.
 */
final class VoiceAudio
{
	/** The ping's sample rate and length. */
	private static final int PING_RATE = 22050;
	private static final double PING_SECONDS = 0.18;
	private static final double PING_HZ = 880;

	private static final int ENCODING_PCM = 1;
	private static final int ENCODING_ULAW = 7;
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
	 * sound with no gaps between parts. Every part must share a sample rate
	 * and channel count.
	 *
	 * @param open opens a clip's bytes by its resource path; null if missing
	 */
	static byte[] join(List<String> parts, Function<String, InputStream> open) throws IOException
	{
		int rate = -1;
		int channels = -1;
		final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
		for (String part : parts)
		{
			try (InputStream raw = open.apply(part))
			{
				if (raw == null)
				{
					throw new IOException("missing clip " + part);
				}
				final Clip clip = read(raw);
				if (rate < 0)
				{
					rate = clip.rate;
					channels = clip.channels;
				}
				else if (rate != clip.rate || channels != clip.channels)
				{
					throw new IOException("cannot join " + part + ": " + clip.rate + " Hz " + clip.channels
						+ " ch, not " + rate + " Hz " + channels + " ch");
				}
				pcm.write(clip.pcm16);
			}
		}
		if (rate < 0)
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

	/** How long a WAV made by {@link #join} or {@link #ping} plays, in milliseconds. */
	static long durationMillis(byte[] wav)
	{
		if (wav.length < HEADER)
		{
			return 0;
		}
		final long byteRate = le32(wav, 28);
		final long dataLength = wav.length - HEADER;
		return byteRate <= 0 ? 0 : dataLength * 1000 / byteRate;
	}

	private static long le32(byte[] b, int at)
	{
		return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
	}

	private static void put32(byte[] b, int at, long v)
	{
		for (int i = 0; i < 4; i++)
		{
			b[at + i] = (byte) (v >> (8 * i));
		}
	}

	private static void put16(byte[] b, int at, int v)
	{
		b[at] = (byte) v;
		b[at + 1] = (byte) (v >> 8);
	}

	private static void tag(byte[] b, int at, String s)
	{
		System.arraycopy(s.getBytes(Charsets.US_ASCII), 0, b, at, 4);
	}

	/** A canonical 44-byte-header 16-bit PCM WAV. */
	private static byte[] wav(byte[] pcm, int rate, int channels)
	{
		final byte[] out = new byte[HEADER + pcm.length];
		final int frame = channels * 2;
		tag(out, 0, "RIFF");
		put32(out, 4, 36L + pcm.length);
		tag(out, 8, "WAVE");
		tag(out, 12, "fmt ");
		put32(out, 16, 16);
		put16(out, 20, ENCODING_PCM);
		put16(out, 22, channels);
		put32(out, 24, rate);
		put32(out, 28, (long) rate * frame);
		put16(out, 32, frame);
		put16(out, 34, 16);
		tag(out, 36, "data");
		put32(out, 40, pcm.length);
		System.arraycopy(pcm, 0, out, HEADER, pcm.length);
		return out;
	}

	/** One clip decoded to 16-bit little-endian PCM. */
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

	private static Clip read(InputStream raw) throws IOException
	{
		final DataInputStream in = new DataInputStream(raw);
		if (!"RIFF".equals(readTag(in)))
		{
			throw new IOException("not a RIFF file");
		}
		in.readInt();
		if (!"WAVE".equals(readTag(in)))
		{
			throw new IOException("not a WAVE file");
		}
		int encoding = 0;
		int channels = 0;
		int rate = 0;
		int bits = 0;
		boolean haveFormat = false;
		while (true)
		{
			final String id = readTag(in);
			final long size = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
			if ("fmt ".equals(id))
			{
				if (size < 16)
				{
					throw new IOException("short fmt chunk");
				}
				encoding = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
				channels = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
				rate = Integer.reverseBytes(in.readInt());
				in.readInt(); // byte rate
				in.readShort(); // block align
				bits = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
				skip(in, size - 16 + (size & 1));
				haveFormat = true;
			}
			else if ("data".equals(id))
			{
				if (!haveFormat)
				{
					throw new IOException("data before fmt");
				}
				if (size > Integer.MAX_VALUE)
				{
					throw new IOException("data chunk too large");
				}
				final byte[] data = new byte[(int) size];
				in.readFully(data);
				return new Clip(rate, channels, decode(encoding, bits, data));
			}
			else
			{
				skip(in, size + (size & 1));
			}
		}
	}

	private static byte[] decode(int encoding, int bits, byte[] data) throws IOException
	{
		if (encoding == ENCODING_PCM && bits == 16)
		{
			// Whole samples only, so the joined line never shifts by a byte.
			return data.length % 2 == 0 ? data : java.util.Arrays.copyOf(data, data.length - 1);
		}
		if (encoding == ENCODING_ULAW && bits == 8)
		{
			final byte[] out = new byte[data.length * 2];
			for (int i = 0; i < data.length; i++)
			{
				put16(out, 2 * i, ulaw(data[i]));
			}
			return out;
		}
		throw new IOException("unsupported WAV: encoding " + encoding + ", " + bits + "-bit");
	}

	/** ITU-T G.711 mu-law byte to a 16-bit linear sample. */
	private static int ulaw(byte value)
	{
		final int u = ~value & 0xff;
		final int t = (((u & 0x0f) << 3) + 0x84) << ((u & 0x70) >> 4);
		return (u & 0x80) != 0 ? 0x84 - t : t - 0x84;
	}

	private static String readTag(DataInputStream in) throws IOException
	{
		final byte[] id = new byte[4];
		in.readFully(id);
		return new String(id, Charsets.US_ASCII);
	}

	private static void skip(DataInputStream in, long n) throws IOException
	{
		for (long left = n; left > 0; left--)
		{
			if (in.read() < 0)
			{
				throw new EOFException("truncated chunk");
			}
		}
	}
}
