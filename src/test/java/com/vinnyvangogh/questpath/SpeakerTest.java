package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeakerTest
{
	private final List<String> played = new ArrayList<>();
	private final List<String> chatted = new ArrayList<>();
	private final Speaker.Out out = new Speaker.Out()
	{
		@Override
		public void play(Utterance line, int volume)
		{
			played.add((line.fallback() ? "ping:" : "voice:") + line.text + "@" + volume);
		}

		@Override
		public void chat(String text)
		{
			chatted.add(text);
		}
	};

	private static final Utterance SPOKEN = new Utterance("Bucket nearby",
		Collections.singletonList(VoiceLines.path("item-bucket")));
	private static final Utterance MISSING = Utterance.textOnly("Bucket nearby");

	@Test
	public void voiceOffIsSilent()
	{
		Speaker.speak(false, 70, SPOKEN, true, out);
		Speaker.speak(false, 70, MISSING, true, out);
		assertTrue(played.isEmpty());
		assertTrue(chatted.isEmpty());
	}

	@Test
	public void aWholeLineIsSpokenWithNoChat()
	{
		Speaker.speak(true, 70, SPOKEN, true, out);
		assertEquals(Collections.singletonList("voice:Bucket nearby@70"), played);
		assertTrue(chatted.isEmpty());
	}

	@Test
	public void aMissingClipPingsAndPutsTheLineInChat()
	{
		Speaker.speak(true, 40, MISSING, true, out);
		assertEquals(Collections.singletonList("ping:Bucket nearby@40"), played);
		assertEquals(Collections.singletonList("Quest Route: Bucket nearby"), chatted);
	}

	@Test
	public void aMissingClipStillPingsWhenAnotherChatLineAlreadySaidIt()
	{
		Speaker.speak(true, 40, MISSING, false, out);
		assertEquals(Collections.singletonList("ping:Bucket nearby@40"), played);
		assertTrue(chatted.isEmpty());
	}
}
