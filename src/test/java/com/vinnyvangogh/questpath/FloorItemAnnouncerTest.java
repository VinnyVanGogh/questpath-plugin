package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FloorItemAnnouncerTest
{
	private final FloorItemAnnouncer announcer = new FloorItemAnnouncer();

	private static FloorItemAnnouncer.Sighting at(String spawn, String name, int distance)
	{
		return new FloorItemAnnouncer.Sighting(spawn, name, distance);
	}

	@Test
	public void saysASpawnOnceWhileItStaysInRange()
	{
		FloorItemAnnouncer.Sighting bucket = at("bucket@1", "Bucket", 4);
		assertEquals("Bucket", announcer.observe(Collections.singletonList(bucket), 0));
		for (long t = 600; t < 60_000; t += 600)
		{
			assertNull("no repeat at " + t, announcer.observe(Collections.singletonList(bucket), t));
		}
	}

	@Test
	public void walkingAwayAndBackDoesNotRepeat()
	{
		FloorItemAnnouncer.Sighting bucket = at("bucket@1", "Bucket", 4);
		announcer.observe(Collections.singletonList(bucket), 0);
		assertNull(announcer.observe(Collections.emptyList(), 10_000));
		assertNull(announcer.observe(Collections.singletonList(bucket), 20_000));
	}

	@Test
	public void aDespawnedSpawnIsNewsAgain()
	{
		FloorItemAnnouncer.Sighting bucket = at("bucket@1", "Bucket", 4);
		announcer.observe(Collections.singletonList(bucket), 0);
		announcer.forget("bucket@1");
		assertEquals("Bucket", announcer.observe(Collections.singletonList(bucket), 10_000));
	}

	@Test
	public void manySpawnsAreRateLimitedNearestFirst()
	{
		FloorItemAnnouncer.Sighting far = at("egg@1", "Egg", 9);
		FloorItemAnnouncer.Sighting near = at("bucket@1", "Bucket", 2);
		FloorItemAnnouncer.Sighting mid = at("pot@1", "Pot", 5);
		assertEquals("Bucket", announcer.observe(Arrays.asList(far, near, mid), 0));
		assertNull("held inside the gap", announcer.observe(Arrays.asList(far, near, mid), 1000));
		assertNull(announcer.observe(Arrays.asList(far, near, mid), FloorItemAnnouncer.MIN_GAP_MILLIS - 1));
		assertEquals("Pot", announcer.observe(Arrays.asList(far, near, mid), FloorItemAnnouncer.MIN_GAP_MILLIS));
		assertEquals("Egg", announcer.observe(Arrays.asList(far, near, mid), 2 * FloorItemAnnouncer.MIN_GAP_MILLIS));
		assertNull(announcer.observe(Arrays.asList(far, near, mid), 10 * FloorItemAnnouncer.MIN_GAP_MILLIS));
	}

	@Test
	public void aPileOfTheSameItemIsOneLine()
	{
		assertEquals("Bones", announcer.observe(Arrays.asList(
			at("bones@1", "Bones", 3), at("bones@2", "Bones", 4), at("bones@3", "Bones", 5)), 0));
		assertNull(announcer.observe(Arrays.asList(
			at("bones@1", "Bones", 3), at("bones@2", "Bones", 4), at("bones@3", "Bones", 5)), 60_000));
	}

	@Test
	public void clearStartsOver()
	{
		FloorItemAnnouncer.Sighting bucket = at("bucket@1", "Bucket", 4);
		announcer.observe(Collections.singletonList(bucket), 0);
		announcer.clear();
		assertEquals("Bucket", announcer.observe(Collections.singletonList(bucket), 1));
	}
}
