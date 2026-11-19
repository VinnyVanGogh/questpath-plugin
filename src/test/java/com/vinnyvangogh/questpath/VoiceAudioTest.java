package com.vinnyvangogh.questpath;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VoiceAudioTest
{
	private static byte[] wav(AudioFormat format, int frames) throws IOException
	{
		byte[] data = new byte[frames * format.getFrameSize()];
		Arrays.fill(data, (byte) 0x7f);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(data), format, frames),
			AudioFileFormat.Type.WAVE, out);
		return out.toByteArray();
	}

	private static AudioFormat decoded(byte[] wav) throws Exception
	{
		return AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav)).getFormat();
	}

	private static long frames(byte[] wav) throws Exception
	{
		return AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav)).getFrameLength();
	}

	@Test
	public void joinsClipsEndToEndAs16BitPcm() throws Exception
	{
		AudioFormat pcm = new AudioFormat(22050f, 16, 1, true, false);
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("a", wav(pcm, 100));
		clips.put("b", wav(pcm, 250));
		byte[] joined = VoiceAudio.join(Arrays.asList("a", "b", "a"),
			path -> new ByteArrayInputStream(clips.get(path)));
		assertEquals(450, frames(joined));
		assertEquals(16, decoded(joined).getSampleSizeInBits());
		assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded(joined).getEncoding());
	}

	@Test
	public void muLawClipsJoinTheSameAsPcm() throws Exception
	{
		AudioFormat ulaw = new AudioFormat(AudioFormat.Encoding.ULAW, 22050f, 8, 1, 1, 22050f, false);
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("u", wav(ulaw, 300));
		clips.put("p", wav(new AudioFormat(22050f, 16, 1, true, false), 200));
		byte[] joined = VoiceAudio.join(Arrays.asList("u", "p"), path -> new ByteArrayInputStream(clips.get(path)));
		assertEquals(500, frames(joined));
		assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded(joined).getEncoding());
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
	public void thePingIsAPlayableWavBuiltInCode() throws Exception
	{
		byte[] ping = VoiceAudio.ping();
		assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded(ping).getEncoding());
		assertTrue("ping has sound", frames(ping) > 1000);
	}

	@Test
	public void durationIsReadFromTheJoinedWav() throws Exception
	{
		AudioFormat pcm = new AudioFormat(22050f, 16, 1, true, false);
		byte[] clip = wav(pcm, 22050);
		byte[] joined = VoiceAudio.join(Collections.singletonList("a"), path -> new ByteArrayInputStream(clip));
		assertEquals(1000, VoiceAudio.durationMillis(joined));
	}

	@Test
	public void clipsAtDifferentRatesAreRefusedNotMangled() throws Exception
	{
		Map<String, byte[]> clips = new HashMap<>();
		clips.put("a", wav(new AudioFormat(22050f, 16, 1, true, false), 100));
		clips.put("b", wav(new AudioFormat(16000f, 16, 1, true, false), 100));
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
	public void volumeIsAGainInDecibels()
	{
		assertEquals(0f, VoiceAudio.gain(100), 0.001f);
		assertEquals(-6.02f, VoiceAudio.gain(50), 0.01f);
		assertEquals(-40f, VoiceAudio.gain(1), 0.01f);
		assertEquals("clamped", VoiceAudio.gain(1), VoiceAudio.gain(-5), 0.001f);
	}
}
