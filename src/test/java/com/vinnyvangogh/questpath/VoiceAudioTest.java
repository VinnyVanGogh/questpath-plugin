package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VoiceAudioTest
{
	private static void le(byte[] b, int at, long v, int bytes)
	{
		for (int i = 0; i < bytes; i++)
		{
			b[at + i] = (byte) (v >> (8 * i));
		}
	}

	private static void tag(byte[] b, int at, String s)
	{
		for (int i = 0; i < s.length(); i++)
		{
			b[at + i] = (byte) s.charAt(i);
		}
	}

	/** A WAV with the given format tag and bits per sample, every data byte 0x7f. */
	private static byte[] wav(int formatTag, int bits, int rate, int channels, int frames)
	{
		int frameBytes = channels * bits / 8;
		int dataBytes = frames * frameBytes;
		byte[] b = new byte[44 + dataBytes];
		tag(b, 0, "RIFF");
		le(b, 4, 36 + dataBytes, 4);
		tag(b, 8, "WAVE");
		tag(b, 12, "fmt ");
		le(b, 16, 16, 4);
		le(b, 20, formatTag, 2);
		le(b, 22, channels, 2);
		le(b, 24, rate, 4);
		le(b, 28, (long) rate * frameBytes, 4);
		le(b, 32, frameBytes, 2);
		le(b, 34, bits, 2);
		tag(b, 36, "data");
		le(b, 40, dataBytes, 4);
		Arrays.fill(b, 44, b.length, (byte) 0x7f);
		return b;
	}

	private static int field(byte[] wav, int at, int bytes)
	{
		int v = 0;
		for (int i = 0; i < bytes; i++)
		{
			v |= (wav[at + i] & 0xFF) << (8 * i);
		}
		return v;
	}

	private static int frames(byte[] wav)
	{
		return (wav.length - 44) / 2;
	}

	private static short sample(byte[] wav, int frame)
	{
		return (short) field(wav, 44 + 2 * frame, 2);
	}

	@Test
	public void joinsClipsEndToEndAs16BitPcm() throws Exception
	{
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("a", wav(1, 16, 22050, 1, 100));
		clips.put("b", wav(1, 16, 22050, 1, 250));
		byte[] joined = VoiceAudio.join(Arrays.asList("a", "b", "a"),
			path -> new ByteArrayInputStream(clips.get(path)));
		assertEquals(450, frames(joined));
		assertEquals(1, field(joined, 20, 2));
		assertEquals(16, field(joined, 34, 2));
		assertEquals(22050, field(joined, 24, 4));
	}

	@Test
	public void muLawClipsJoinTheSameAsPcm() throws Exception
	{
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("u", wav(7, 8, 22050, 1, 300));
		clips.put("p", wav(1, 16, 22050, 1, 200));
		byte[] joined = VoiceAudio.join(Arrays.asList("u", "p"), path -> new ByteArrayInputStream(clips.get(path)));
		assertEquals(500, frames(joined));
		assertEquals(16, field(joined, 34, 2));
	}

	@Test
	public void muLawDecodesTheG711Endpoints() throws Exception
	{
		byte[] ulaw = wav(7, 8, 22050, 1, 3);
		ulaw[44] = (byte) 0xff; // +0
		ulaw[45] = (byte) 0x00; // most negative
		ulaw[46] = (byte) 0x80; // most positive
		byte[] out = VoiceAudio.join(Collections.singletonList("u"), path -> new ByteArrayInputStream(ulaw));
		assertEquals(0, sample(out, 0));
		assertEquals(-32124, sample(out, 1));
		assertEquals(32124, sample(out, 2));
	}

	@Test
	public void aMissingClipIsAnErrorNotACrash() throws Exception
	{
		try
		{
			VoiceAudio.join(Collections.singletonList("gone"), path -> (InputStream) null);
			fail("expected IOException");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("gone"));
		}
	}

	@Test
	public void clipsOfDifferentRatesCannotJoin() throws Exception
	{
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("a", wav(1, 16, 22050, 1, 10));
		clips.put("b", wav(1, 16, 44100, 1, 10));
		try
		{
			VoiceAudio.join(Arrays.asList("a", "b"), path -> new ByteArrayInputStream(clips.get(path)));
			fail("expected IOException");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("cannot join"));
		}
	}

	@Test
	public void somethingThatIsNotAWavIsAnError() throws Exception
	{
		try
		{
			VoiceAudio.join(Collections.singletonList("x"), path -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
			fail("expected IOException");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("not a WAV"));
		}
	}

	@Test
	public void thePingIsAPlayableWavBuiltInCode()
	{
		byte[] ping = VoiceAudio.ping();
		assertEquals(1, field(ping, 20, 2));
		assertTrue("ping has sound", frames(ping) > 1000);
		assertEquals(180, VoiceAudio.durationMillis(ping), 2);
	}

	@Test
	public void durationFollowsTheJoinedLength() throws Exception
	{
		byte[] clip = wav(1, 16, 22050, 1, 22050);
		byte[] joined = VoiceAudio.join(Arrays.asList("a", "a"), path -> new ByteArrayInputStream(clip));
		assertEquals(2000, VoiceAudio.durationMillis(joined));
	}

	@Test
	public void volumeIsAGainInDecibels()
	{
		assertEquals(0f, VoiceAudio.gain(100), 0.001f);
		assertEquals(-6.02f, VoiceAudio.gain(50), 0.01f);
		assertEquals(-40f, VoiceAudio.gain(1), 0.01f);
		assertEquals("clamped", VoiceAudio.gain(1), VoiceAudio.gain(-5), 0.001f);
	}
}
