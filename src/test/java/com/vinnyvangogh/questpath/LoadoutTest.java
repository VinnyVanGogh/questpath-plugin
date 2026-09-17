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

	private static Loadout noSelections(String name)
	{
		return new Loadout(name, threeItems(), null, null, null, null, null, null, 1L);
	}

	private static Loadout withAllSixSelections()
	{
		return new Loadout("PK setup", threeItems(), "AGGRESSIVE", "PIETY", "RAPID", "RIGOUR",
			"ICE_BARRAGE", "AUGURY", 1_726_000_000_000L);
	}

	@Test
	public void jsonRoundTripsThroughGsonWithAllSixSelectionsSet()
	{
		Loadout original = withAllSixSelections();

		String json = GSON.toJson(original);
		Loadout parsed = GSON.fromJson(json, Loadout.class);

		assertEquals(original, parsed);
		assertEquals(3, parsed.items().size());
		assertEquals("AGGRESSIVE", parsed.meleeStyle());
		assertEquals("PIETY", parsed.strengthPrayer());
		assertEquals("RAPID", parsed.rangedStyle());
		assertEquals("RIGOUR", parsed.rangedPrayer());
		assertEquals("ICE_BARRAGE", parsed.spell());
		assertEquals("AUGURY", parsed.magicPrayer());
		assertEquals(1_726_000_000_000L, parsed.savedAtEpochMillis());
		assertTrue(parsed.hasMeleeSelections());
		assertTrue(parsed.hasRangedSelections());
		assertTrue(parsed.hasMagicSelections());
	}

	@Test
	public void jsonRoundTripsThroughGsonWithOnlyRangedSet()
	{
		Loadout original = new Loadout("Ranging setup", threeItems(), null, null, "ACCURATE", "RIGOUR",
			null, null, 2L);

		Loadout parsed = GSON.fromJson(GSON.toJson(original), Loadout.class);

		assertEquals(original, parsed);
		assertNull("melee was never chosen", parsed.meleeStyle());
		assertNull("melee prayer was never chosen", parsed.strengthPrayer());
		assertEquals("ACCURATE", parsed.rangedStyle());
		assertEquals("RIGOUR", parsed.rangedPrayer());
		assertNull("spell was never chosen", parsed.spell());
		assertNull("magic prayer was never chosen", parsed.magicPrayer());
		assertFalse(parsed.hasMeleeSelections());
		assertTrue(parsed.hasRangedSelections());
		assertFalse(parsed.hasMagicSelections());
	}

	@Test
	public void jsonRoundTripPreservesEveryItem()
	{
		Loadout original = withAllSixSelections();
		Loadout parsed = GSON.fromJson(GSON.toJson(original), Loadout.class);

		for (LoadoutItem item : original.items())
		{
			assertTrue("round trip lost " + item, parsed.items().contains(item));
		}
	}

	@Test
	public void withMeleeReturnsACopyLeavingEverythingElseUnchanged()
	{
		Loadout base = noSelections("Bank setup");

		Loadout withMelee = base.withMelee("DEFENSIVE", "PIETY");

		assertFalse("the original never gained selections", base.hasMeleeSelections());
		assertNull(base.meleeStyle());
		assertTrue("the copy has both required melee selections", withMelee.hasMeleeSelections());
		assertEquals("DEFENSIVE", withMelee.meleeStyle());
		assertEquals("PIETY", withMelee.strengthPrayer());
		assertFalse("ranged and magic are untouched", withMelee.hasRangedSelections());
		assertFalse(withMelee.hasMagicSelections());
		assertEquals("everything else carried over", base.name(), withMelee.name());
		assertEquals(base.items(), withMelee.items());
	}

	@Test
	public void withRangedReturnsACopyLeavingEverythingElseUnchanged()
	{
		Loadout base = noSelections("Bank setup");

		Loadout withRanged = base.withRanged("RAPID", "RIGOUR");

		assertFalse(base.hasRangedSelections());
		assertTrue("the copy has both required ranged selections", withRanged.hasRangedSelections());
		assertEquals("RAPID", withRanged.rangedStyle());
		assertEquals("RIGOUR", withRanged.rangedPrayer());
		assertFalse("melee and magic are untouched", withRanged.hasMeleeSelections());
		assertFalse(withRanged.hasMagicSelections());
	}

	@Test
	public void withMagicReturnsACopyLeavingEverythingElseUnchanged()
	{
		Loadout base = noSelections("Bank setup");

		Loadout withMagic = base.withMagic("ICE_BARRAGE", "AUGURY");

		assertFalse(base.hasMagicSelections());
		assertTrue("the copy has both required magic selections", withMagic.hasMagicSelections());
		assertEquals("ICE_BARRAGE", withMagic.spell());
		assertEquals("AUGURY", withMagic.magicPrayer());
		assertFalse("melee and ranged are untouched", withMagic.hasMeleeSelections());
		assertFalse(withMagic.hasRangedSelections());
	}

	@Test
	public void theThreeStylesAreIndependent()
	{
		Loadout allThree = noSelections("A")
			.withMelee("AGGRESSIVE", "PIETY")
			.withRanged("RAPID", "RIGOUR")
			.withMagic("ICE_BARRAGE", "AUGURY");

		assertTrue(allThree.hasMeleeSelections());
		assertTrue(allThree.hasRangedSelections());
		assertTrue(allThree.hasMagicSelections());
		assertEquals("AGGRESSIVE", allThree.meleeStyle());
		assertEquals("RAPID", allThree.rangedStyle());
		assertEquals("ICE_BARRAGE", allThree.spell());
	}

	@Test
	public void hasMeleeSelectionsRequiresBothStyleAndPrayer()
	{
		Loadout neither = noSelections("A");
		Loadout styleOnly = neither.withMelee("AGGRESSIVE", null);
		Loadout prayerOnly = neither.withMelee(null, "PIETY");
		Loadout both = neither.withMelee("AGGRESSIVE", "PIETY");

		assertFalse(neither.hasMeleeSelections());
		assertFalse(styleOnly.hasMeleeSelections());
		assertFalse(prayerOnly.hasMeleeSelections());
		assertTrue(both.hasMeleeSelections());
	}

	@Test
	public void hasRangedSelectionsRequiresBothStyleAndPrayer()
	{
		Loadout neither = noSelections("A");
		Loadout styleOnly = neither.withRanged("RAPID", null);
		Loadout prayerOnly = neither.withRanged(null, "RIGOUR");
		Loadout both = neither.withRanged("RAPID", "RIGOUR");

		assertFalse(neither.hasRangedSelections());
		assertFalse(styleOnly.hasRangedSelections());
		assertFalse(prayerOnly.hasRangedSelections());
		assertTrue(both.hasRangedSelections());
	}

	@Test
	public void hasMagicSelectionsRequiresBothSpellAndPrayer()
	{
		Loadout neither = noSelections("A");
		Loadout spellOnly = neither.withMagic("ICE_BARRAGE", null);
		Loadout prayerOnly = neither.withMagic(null, "AUGURY");
		Loadout both = neither.withMagic("ICE_BARRAGE", "AUGURY");

		assertFalse(neither.hasMagicSelections());
		assertFalse(spellOnly.hasMagicSelections());
		assertFalse(prayerOnly.hasMagicSelections());
		assertTrue(both.hasMagicSelections());
	}

	@Test
	public void nameIsTrimmedOnConstruction()
	{
		Loadout padded = noSelections("  PK setup  ");

		assertEquals("PK setup", padded.name());
	}

	@Test
	public void emptyNameIsRejected()
	{
		try
		{
			noSelections("");
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
			noSelections("   ");
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
			noSelections(null);
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
		Loadout loadout = new Loadout("A", outOfOrder, null, null, null, null, null, null, 1L);

		assertEquals(Arrays.asList(1163, 1277, 1173), loadout.itemIds());
	}

	@Test
	public void nullItemsBecomesAnEmptyImmutableList()
	{
		Loadout loadout = new Loadout("A", null, null, null, null, null, null, null, 1L);

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
