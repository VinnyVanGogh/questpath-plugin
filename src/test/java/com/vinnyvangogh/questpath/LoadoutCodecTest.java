package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The pure JSON (de)serialisation and replace/delete/rename edit logic
 * {@link LoadoutStore} delegates to.
 *
 * <p>This is the class that actually gets exercised: {@code
 * net.runelite.client.config.ConfigManager} has only a private constructor
 * (confirmed with {@code javap -p}), so it cannot be constructed, subclassed,
 * or faked here without Mockito, which this project does not depend on. See
 * {@link LoadoutStore}'s own class javadoc.
 */
public class LoadoutCodecTest
{
	private static final Gson GSON = new Gson();

	private static Loadout loadout(String name)
	{
		return new Loadout(name, Collections.singletonList(new LoadoutItem(0, 1163, 1, "Bronze full helm")),
			null, null, null, null, null, null, 1L);
	}

	@Test
	public void anAbsentKeyDecodesToAnEmptyList()
	{
		assertTrue(LoadoutCodec.decode(GSON, null).isEmpty());
		assertTrue(LoadoutCodec.decode(GSON, "").isEmpty());
		assertTrue(LoadoutCodec.decode(GSON, "   ").isEmpty());
	}

	@Test
	public void encodeThenDecodeRoundTrips()
	{
		List<Loadout> loadouts = Collections.singletonList(loadout("PK setup"));

		String json = LoadoutCodec.encode(GSON, loadouts);
		List<Loadout> decoded = LoadoutCodec.decode(GSON, json);

		assertEquals(loadouts, decoded);
	}

	@Test
	public void decodedListIsSortedByName()
	{
		String json = LoadoutCodec.encode(GSON, java.util.Arrays.asList(loadout("Zulrah"), loadout("Barrows")));

		List<Loadout> decoded = LoadoutCodec.decode(GSON, json);

		assertEquals("Barrows", decoded.get(0).name());
		assertEquals("Zulrah", decoded.get(1).name());
	}

	@Test
	public void saveAddsANewLoadout()
	{
		String updated = LoadoutCodec.withSaved(GSON, null, loadout("PK setup"));

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(1, decoded.size());
		assertEquals("PK setup", decoded.get(0).name());
	}

	@Test
	public void saveReplacesAnExistingLoadoutOfTheSameName()
	{
		String withOriginal = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));
		Loadout replacement = new Loadout("PK setup",
			Collections.singletonList(new LoadoutItem(1, 1163, 1, "Iron full helm")),
			"AGGRESSIVE", "PIETY", null, null, null, null, 2L);

		String updated = LoadoutCodec.withSaved(GSON, withOriginal, replacement);

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals("save replaces, it does not append", 1, decoded.size());
		assertEquals(replacement, decoded.get(0));
	}

	@Test
	public void saveLeavesOtherLoadoutsAlone()
	{
		String current = LoadoutCodec.encode(GSON,
			java.util.Arrays.asList(loadout("PK setup"), loadout("Bank setup")));

		String updated = LoadoutCodec.withSaved(GSON, current, loadout("Skilling setup"));

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(3, decoded.size());
	}

	@Test
	public void deleteRemovesTheNamedLoadout()
	{
		String current = LoadoutCodec.encode(GSON,
			java.util.Arrays.asList(loadout("PK setup"), loadout("Bank setup")));

		String updated = LoadoutCodec.withDeleted(GSON, current, "PK setup");

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(1, decoded.size());
		assertEquals("Bank setup", decoded.get(0).name());
	}

	@Test
	public void deletingANameThatDoesNotExistIsNotAnError()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		String updated = LoadoutCodec.withDeleted(GSON, current, "no such loadout");

		assertEquals(1, LoadoutCodec.decode(GSON, updated).size());
	}

	/**
	 * Every stored {@link Loadout#name()} is already trimmed (enforced by its
	 * constructor), so a caller passing a padded name -- e.g. straight out of
	 * a text field -- must still match the entry it plainly refers to.
	 */
	@Test
	public void deletingWithAPaddedNameStillMatches()
	{
		String current = LoadoutCodec.encode(GSON,
			java.util.Arrays.asList(loadout("PK setup"), loadout("Bank setup")));

		String updated = LoadoutCodec.withDeleted(GSON, current, "  PK setup  ");

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(1, decoded.size());
		assertEquals("Bank setup", decoded.get(0).name());
	}

	@Test
	public void renameChangesTheName()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		String updated = LoadoutCodec.withRenamed(GSON, current, "PK setup", "Wilderness setup");

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(1, decoded.size());
		assertEquals("Wilderness setup", decoded.get(0).name());
	}

	/** Same reasoning as {@link #deletingWithAPaddedNameStillMatches}, for {@code from}. */
	@Test
	public void renamingFromAPaddedNameStillMatches()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		String updated = LoadoutCodec.withRenamed(GSON, current, "  PK setup  ", "Wilderness setup");

		List<Loadout> decoded = LoadoutCodec.decode(GSON, updated);
		assertEquals(1, decoded.size());
		assertEquals("Wilderness setup", decoded.get(0).name());
	}

	@Test
	public void renameToAnExistingNameIsRejected()
	{
		String current = LoadoutCodec.encode(GSON,
			java.util.Arrays.asList(loadout("PK setup"), loadout("Bank setup")));

		try
		{
			LoadoutCodec.withRenamed(GSON, current, "PK setup", "Bank setup");
			fail("expected IllegalArgumentException for a rename collision");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}

		// The collision must not have partially applied.
		List<Loadout> decoded = LoadoutCodec.decode(GSON, current);
		assertEquals(2, decoded.size());
	}

	@Test
	public void renamingANonexistentLoadoutIsRejected()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		try
		{
			LoadoutCodec.withRenamed(GSON, current, "no such loadout", "New name");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void renamingToItsOwnNameIsAllowed()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		String updated = LoadoutCodec.withRenamed(GSON, current, "PK setup", "PK setup");

		assertEquals(1, LoadoutCodec.decode(GSON, updated).size());
	}

	@Test
	public void renamingToABlankNameIsRejected()
	{
		String current = LoadoutCodec.encode(GSON, Collections.singletonList(loadout("PK setup")));

		try
		{
			LoadoutCodec.withRenamed(GSON, current, "PK setup", "   ");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void corruptJsonSyntaxFailsRatherThanReturningAnEmptyList()
	{
		try
		{
			LoadoutCodec.decode(GSON, "{not valid json");
			fail("expected CorruptLoadoutsException");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected -- and specifically not an empty list, which a caller
			// could never tell apart from "no loadouts saved".
		}
	}

	@Test
	public void jsonThatIsNotAnArrayIsCorrupt()
	{
		try
		{
			LoadoutCodec.decode(GSON, "{\"name\":\"PK setup\"}");
			fail("expected CorruptLoadoutsException");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected
		}
	}

	@Test
	public void anEntryWithNoNameIsCorrupt()
	{
		// Gson deserialises this fine -- it is valid JSON -- but bypasses
		// Loadout's validating constructor, which is exactly the gap decode()
		// exists to close: a missing name must not silently produce a
		// Loadout whose name() breaks its own contract.
		try
		{
			LoadoutCodec.decode(GSON, "[{\"items\":[]}]");
			fail("expected CorruptLoadoutsException");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected
		}
	}

	@Test
	public void anItemWithAnOutOfRangeSlotIsCorrupt()
	{
		try
		{
			LoadoutCodec.decode(GSON, "[{\"name\":\"PK setup\",\"items\":[{\"slot\":99,\"itemId\":1163,"
				+ "\"quantity\":1,\"name\":\"x\"}]}]");
			fail("expected CorruptLoadoutsException");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected
		}
	}

	@Test
	public void aNullEntryInTheArrayIsCorrupt()
	{
		try
		{
			LoadoutCodec.decode(GSON, "[null]");
			fail("expected CorruptLoadoutsException");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected
		}
	}

	@Test
	public void savingOntoACorruptCurrentValueFailsRatherThanDiscardingIt()
	{
		try
		{
			LoadoutCodec.withSaved(GSON, "{not valid json", loadout("PK setup"));
			fail("expected CorruptLoadoutsException -- a save must not silently wipe the existing value");
		}
		catch (LoadoutCodec.CorruptLoadoutsException expected)
		{
			// expected
		}
	}
}
