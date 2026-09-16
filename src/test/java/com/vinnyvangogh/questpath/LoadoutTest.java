package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link Loadout} and {@link LoadoutItem} as plain values: JSON round-trip,
 * the copy-on-write update methods, and the name validation every stored
 * loadout must satisfy.
 */
public class LoadoutTest
{
	private static final Gson GSON = new Gson();

	private static List<LoadoutItem> threeItems()
	{
		return Arrays.asList(
			new LoadoutItem(0, 1163, 1, "Bronze full helm"),
			new LoadoutItem(3, 1277, 1, "Bronze sword"),
			new LoadoutItem(5, 1173, 1, "Bronze square shield"));
	}

	private static Loadout withThreeItems()
	{
		return new Loadout("PK setup", threeItems(), "AGGRESSIVE", "PIETY", null, 1_726_000_000_000L);
	}

	@Test
	public void jsonRoundTripsThroughGson()
	{
		Loadout original = withThreeItems();

		String json = GSON.toJson(original);
		Loadout parsed = GSON.fromJson(json, Loadout.class);

		assertEquals(original, parsed);
		assertEquals(3, parsed.items().size());
		assertEquals("AGGRESSIVE", parsed.attackStyle());
		assertEquals("PIETY", parsed.prayer());
		assertNull("spell was never chosen", parsed.spell());
		assertEquals(1_726_000_000_000L, parsed.savedAtEpochMillis());
	}

	@Test
	public void jsonRoundTripPreservesEveryItem()
	{
		Loadout original = withThreeItems();
		Loadout parsed = GSON.fromJson(GSON.toJson(original), Loadout.class);

		for (LoadoutItem item : original.items())
		{
			assertTrue("round trip lost " + item, parsed.items().contains(item));
		}
	}

	@Test
	public void withSelectionsReturnsACopyLeavingTheOriginalUnchanged()
	{
		Loadout noSelections = new Loadout("Bank setup", threeItems(), null, null, null, 1L);

		Loadout withPicks = noSelections.withSelections("DEFENSIVE", "RIGOUR", "ICE_BARRAGE");

		assertFalse("the original never gained selections", noSelections.hasSelections());
		assertNull(noSelections.attackStyle());
		assertTrue("the copy has both required selections", withPicks.hasSelections());
		assertEquals("DEFENSIVE", withPicks.attackStyle());
		assertEquals("RIGOUR", withPicks.prayer());
		assertEquals("ICE_BARRAGE", withPicks.spell());
		assertEquals("everything else carried over", noSelections.name(), withPicks.name());
		assertEquals(noSelections.items(), withPicks.items());
	}

	@Test
	public void hasSelectionsRequiresBothStyleAndPrayerNotSpell()
	{
		Loadout neither = new Loadout("A", threeItems(), null, null, null, 1L);
		Loadout styleOnly = new Loadout("A", threeItems(), "AGGRESSIVE", null, null, 1L);
		Loadout prayerOnly = new Loadout("A", threeItems(), null, "PIETY", null, 1L);
		Loadout both = new Loadout("A", threeItems(), "AGGRESSIVE", "PIETY", null, 1L);

		assertFalse(neither.hasSelections());
		assertFalse(styleOnly.hasSelections());
		assertFalse(prayerOnly.hasSelections());
		assertTrue("spell is not required", both.hasSelections());
	}

	@Test
	public void nameIsTrimmedOnConstruction()
	{
		Loadout padded = new Loadout("  PK setup  ", threeItems(), null, null, null, 1L);

		assertEquals("PK setup", padded.name());
	}

	@Test
	public void emptyNameIsRejected()
	{
		try
		{
			new Loadout("", threeItems(), null, null, null, 1L);
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void blankNameIsRejected()
	{
		try
		{
			new Loadout("   ", threeItems(), null, null, null, 1L);
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void nullNameIsRejected()
	{
		try
		{
			new Loadout(null, threeItems(), null, null, null, 1L);
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void itemIdsAreReturnedInSlotOrderRegardlessOfInputOrder()
	{
		List<LoadoutItem> outOfOrder = Arrays.asList(
			new LoadoutItem(5, 1173, 1, "Bronze square shield"),
			new LoadoutItem(0, 1163, 1, "Bronze full helm"),
			new LoadoutItem(3, 1277, 1, "Bronze sword"));
		Loadout loadout = new Loadout("A", outOfOrder, null, null, null, 1L);

		assertEquals(Arrays.asList(1163, 1277, 1173), loadout.itemIds());
	}

	@Test
	public void nullItemsBecomesAnEmptyImmutableList()
	{
		Loadout loadout = new Loadout("A", null, null, null, null, 1L);

		assertTrue(loadout.items().isEmpty());
		try
		{
			loadout.items().add(new LoadoutItem(0, 1163, 1, "x"));
			fail("expected the items list to be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test
	public void loadoutItemRejectsAnOutOfRangeSlot()
	{
		try
		{
			new LoadoutItem(-1, 1163, 1, "x");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
		try
		{
			new LoadoutItem(14, 1163, 1, "x");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}

	@Test
	public void loadoutItemRejectsZeroOrNegativeQuantity()
	{
		try
		{
			new LoadoutItem(0, 1163, 0, "x");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}
}
