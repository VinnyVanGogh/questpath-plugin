package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceQueueTest
{
	private final List<String> started = new ArrayList<>();
	private final VoiceQueue<String> queue = new VoiceQueue<>(started::add);

	@Test
	public void idlePlaysAtOnce()
	{
		queue.offer("one");
		assertEquals(Arrays.asList("one"), started);
		assertTrue(queue.playing());
	}

	@Test
	public void oneAtATimeAndTheNewestWaitingReplacesTheOlder()
	{
		queue.offer("one");
		queue.offer("two");
		queue.offer("three");
		assertEquals("nothing overlaps the playing line", Arrays.asList("one"), started);
		queue.finished();
		assertEquals("only the newest waiting line plays next", Arrays.asList("one", "three"), started);
		queue.finished();
		assertFalse(queue.playing());
		assertEquals(2, started.size());
	}

	@Test
	public void afterIdleTheNextOfferPlaysAgain()
	{
		queue.offer("one");
		queue.finished();
		queue.offer("two");
		assertEquals(Arrays.asList("one", "two"), started);
	}

	@Test
	public void clearDropsTheWaitingLine()
	{
		queue.offer("one");
		queue.offer("two");
		queue.clear();
		queue.finished();
		assertEquals(Arrays.asList("one"), started);
		assertFalse(queue.playing());
	}
}
