package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every shape {@link LockedGear#resolve} can produce, plus the acceptance
 * test from the Task 4b brief: a loadout whose items are nothing like
 * whatever is currently worn still produces a correct max hit, because
 * {@link LockedGear#resolve} has no "worn gear" input to read in the first
 * place.
 */
public class LockedGearTest
{
	private static final CombatStats LEVELS =
		new CombatStats(99, 99, 99, 99, 99, 99, 99);

	/** A stats table a test wires up by hand, standing in for {@code ItemManager}. */
	private static LockedGear.ItemStatsView known(int str, int rstr, int mdmg)
	{
		return new LockedGear.ItemStatsView()
		{
			@Override
			public boolean known()
			{
				return true;
			}

			@Override
			public int str()
			{
				return str;
			}

			@Override
			public int rstr()
			{
				return rstr;
			}

			@Override
			public int mdmg()
			{
				return mdmg;
			}
		};
	}

	private static final LockedGear.ItemStatsView UNKNOWN = new LockedGear.ItemStatsView()
	{
		@Override
		public boolean known()
		{
			return false;
		}

		@Override
		public int str()
		{
			return 0;
		}

		@Override
		public int rstr()
		{
			return 0;
		}

		@Override
		public int mdmg()
		{
			return 0;
		}
	};

	private static Loadout loadout(String name, List<LoadoutItem> items, String meleeStyle, String strengthPrayer,
		String rangedStyle, String rangedPrayer, String spell, String magicPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell, magicPrayer,
			1_000L);
	}

	private static Loadout loadoutWithMeleePotion(String name, List<LoadoutItem> items, String meleeStyle,
		String strengthPrayer, String meleePotion)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, null, null, null, null, 1_000L, null, meleePotion);
	}

	@Test
	public void notLoggedInShape()
	{
		LoadoutStore.Result stored = LoadoutStore.Result.notLoggedIn();
		LockedGear gear = LockedGear.resolve(stored, null, LEVELS, id -> UNKNOWN);
		assertEquals(LockedGear.Shape.NOT_LOGGED_IN, gear.shape());
	}

	@Test
	public void corruptShape()
	{
		LoadoutStore.Result stored = LoadoutStore.Result.corrupt("not a JSON array");
		LockedGear gear = LockedGear.resolve(stored, null, LEVELS, id -> UNKNOWN);
		assertEquals(LockedGear.Shape.CORRUPT, gear.shape());
		assertEquals("not a JSON array", gear.corruptDetail());
	}

	@Test
	public void noLoadoutsShape()
	{
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.emptyList());
		LockedGear gear = LockedGear.resolve(stored, null, LEVELS, id -> UNKNOWN);
		assertEquals(LockedGear.Shape.NO_LOADOUTS, gear.shape());
	}

	@Test
	public void fallsBackToFirstByNameWhenSelectedNameDoesNotMatch()
	{
		Loadout a = loadout("Alpha", Collections.emptyList(), null, null, null, null, null, null);
		Loadout b = loadout("Beta", Collections.emptyList(), null, null, null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Arrays.asList(a, b));

		LockedGear gear = LockedGear.resolve(stored, "Nonexistent", LEVELS, id -> UNKNOWN);
		assertEquals(LockedGear.Shape.READY, gear.shape());
		assertEquals("Alpha", gear.selectedLoadout().name());
		assertEquals(Arrays.asList("Alpha", "Beta"), gear.loadoutNames());
	}

	/**
	 * The acceptance test: a ranged max hit computed purely from a saved
	 * loadout and live levels, with no worn-gear input anywhere in the call.
	 *
	 * {@link LockedGear#resolve}'s signature carries {@code
	 * LoadoutStore.Result}, a name, {@code CombatStats}, and a stats lookup
	 * -- nothing that could read what is currently equipped. The gear here
	 * (a Rune crossbow slot standing in for ranged strength +103, matching
	 * {@code MaxHitTest}'s R4 vector) has no relationship at all to whatever
	 * the test "account" might be wearing, because there is no such account
	 * in this test and no way for this method to ask one.
	 */
	@Test
	public void rangedMaxHitMatchesMaxHitForSameLevelsAndBonuses()
	{
		LoadoutItem crossbow = new LoadoutItem(EquipmentInventorySlot.WEAPON.getSlotIdx(), 9999, 1, "Rune crossbow");
		Loadout saved = loadout("PK gear", Collections.singletonList(crossbow),
			null, null, "ACCURATE", "RIGOUR", null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		CombatStats levels = new CombatStats(1, 1, 1, 10, 81, 1, 1);
		LockedGear gear = LockedGear.resolve(stored, "PK gear", levels, id -> id == 9999 ? known(0, 103, 0) : UNKNOWN);

		assertEquals(LockedGear.Shape.READY, gear.shape());
		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.ranged().kind());
		int expected = MaxHit.ranged(81, RangedStyle.ACCURATE, RangedPrayer.RIGOUR,
			new GearBonuses(0, 103, 0, Collections.emptySet(), null)).value();
		assertEquals(29, expected);
		assertEquals(expected, gear.ranged().result().value());

		// Melee/magic were never given a style+prayer / spell+prayer, so
		// they stay unresolved rather than defaulting to something.
		assertEquals(LockedGear.StyleLine.Kind.NEEDS_SELECTION, gear.melee().kind());
		assertEquals(LockedGear.StyleLine.Kind.NEEDS_SELECTION, gear.magic().kind());
	}

	@Test
	public void unknownStatsOnOneItemSuppressesAllThreeNumbers()
	{
		LoadoutItem weapon = new LoadoutItem(EquipmentInventorySlot.WEAPON.getSlotIdx(), 111, 1, "Known weapon");
		LoadoutItem mystery = new LoadoutItem(EquipmentInventorySlot.RING.getSlotIdx(), 222, 1, "Mystery ring");
		Loadout saved = loadout("Full kit", Arrays.asList(weapon, mystery),
			"ACCURATE", "NONE", "ACCURATE", "NONE", "FIRE_STRIKE", "NONE");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Full kit", LEVELS,
			id -> id == 111 ? known(10, 10, 10) : UNKNOWN);

		assertEquals(LockedGear.Shape.READY, gear.shape());
		assertEquals(Collections.singletonList("Mystery ring"), gear.unknownStatsItemNames());
		assertEquals(LockedGear.StyleLine.Kind.UNKNOWN_STATS, gear.melee().kind());
		assertEquals(LockedGear.StyleLine.Kind.UNKNOWN_STATS, gear.ranged().kind());
		assertEquals(LockedGear.StyleLine.Kind.UNKNOWN_STATS, gear.magic().kind());
		assertEquals(Collections.singletonList("Mystery ring"), gear.melee().unknownItemNames());
		assertEquals(Collections.singletonList("Mystery ring"), gear.ranged().unknownItemNames());
		assertEquals(Collections.singletonList("Mystery ring"), gear.magic().unknownItemNames());
	}

	@Test
	public void unmodelledEffectAppearsInNotAppliedWithoutChangingTheValue()
	{
		// Slayer helmet (unimbued): a real GearEffects#detect hit, and a
		// real melee-relevant GearEffect, but GearEffect#modelled() is
		// false for it -- MaxHit never multiplies it in.
		LoadoutItem helm = new LoadoutItem(EquipmentInventorySlot.HEAD.getSlotIdx(), ItemID.SLAYER_HELM, 1, "Slayer helmet");
		Loadout saved = loadout("Slayer task", Collections.singletonList(helm),
			"ACCURATE", "NONE", null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Slayer task", LEVELS, id -> known(0, 0, 0));

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		MaxHitResult result = gear.melee().result();
		assertTrue(result.isKnown());
		// M1 from MaxHitTest: Str 99, no gear, no prayer, Accurate (+0) = 11.
		// The Slayer helmet contributes nothing to the number...
		assertEquals(11, result.value());
		// ...but is still named, not silently dropped.
		assertTrue(result.notApplied().contains(GearEffect.SLAYER_HELMET));
	}

	@Test
	public void unrecognisedSelectionNameFallsBackToNeedsSelection()
	{
		Loadout saved = loadout("Renamed enum", Collections.emptyList(),
			"NOT_A_REAL_STYLE", "NONE", null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Renamed enum", LEVELS, id -> UNKNOWN);

		assertEquals(LockedGear.StyleLine.Kind.NEEDS_SELECTION, gear.melee().kind());
	}

	@Test
	public void negativeMeleeValueBecomesNotModelledInsteadOfThrowing()
	{
		LoadoutItem cursed = new LoadoutItem(EquipmentInventorySlot.WEAPON.getSlotIdx(), 333, 1, "Cursed weapon");
		Loadout saved = loadout("Cursed", Collections.singletonList(cursed),
			"ACCURATE", "NONE", "ACCURATE", "NONE", null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		// Summed strength bonus of -200 -- large enough that MaxHit.melee's
		// own arithmetic goes negative and MaxHitResult.known() would throw.
		LockedGear gear = LockedGear.resolve(stored, "Cursed", LEVELS, id -> known(-200, -200, 0));

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		assertFalse(gear.melee().result().isKnown());
		assertEquals("Gear strength bonus too low to model", gear.melee().result().reason());

		// The ranged style must not be taken down by melee's failure --
		// it hits the exact same guard from its own summed rstr of -200.
		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.ranged().kind());
		assertFalse(gear.ranged().result().isKnown());
		assertEquals("Gear ranged strength bonus too low to model", gear.ranged().result().reason());
	}

	/**
	 * The variant the review asked for: only melee's summed bonus is
	 * negative here (str -200, rstr 0). Where {@link
	 * #negativeMeleeValueBecomesNotModelledInsteadOfThrowing} drives melee
	 * and ranged negative together -- which cannot tell "each fails
	 * independently" apart from a hypothetical bug that let one style's
	 * failure blank the others -- this proves ranged (and magic, unrelated
	 * to either bonus) still produce real, correct numbers while melee alone
	 * reports not-modelled.
	 */
	@Test
	public void onlyMeleeFailsWhileRangedAndMagicStayCorrect()
	{
		LoadoutItem cursed = new LoadoutItem(EquipmentInventorySlot.WEAPON.getSlotIdx(), 333, 1, "Cursed weapon");
		Loadout saved = loadout("Half cursed", Collections.singletonList(cursed),
			"ACCURATE", "NONE", "ACCURATE", "NONE", "FIRE_STRIKE", "NONE");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		// str -200 (melee-only failure), rstr 0 (ranged unaffected), mdmg 0.
		LockedGear gear = LockedGear.resolve(stored, "Half cursed", LEVELS, id -> known(-200, 0, 0));

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		assertFalse("melee alone should fail its own guard", gear.melee().result().isKnown());
		assertEquals("Gear strength bonus too low to model", gear.melee().result().reason());

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.ranged().kind());
		assertTrue("ranged must not be taken down by melee's failure", gear.ranged().result().isKnown());
		int expectedRanged = MaxHit.ranged(99, RangedStyle.ACCURATE, RangedPrayer.NONE,
			new GearBonuses(0, 0, 0, Collections.emptySet(), null)).value();
		assertEquals(expectedRanged, gear.ranged().result().value());

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.magic().kind());
		assertTrue("magic must not be taken down by melee's failure either", gear.magic().result().isKnown());
		int expectedMagic = MaxHit.magic(99, Spell.FIRE_STRIKE, MagicPrayer.NONE,
			new GearBonuses(0, 0, 0, Collections.emptySet(), null)).value();
		assertEquals(expectedMagic, gear.magic().result().value());
	}

	/**
	 * A loadout saved before {@link Loadout#meleePotion()} existed -- {@code
	 * meleePotion} is null -- keeps computing exactly the number it always
	 * did. Potion is the one selection {@link Loadout#hasMeleeSelections()}
	 * does not gate on, precisely so this stays true without forcing a
	 * re-selection on upgrade; see {@link Loadout}'s own class javadoc.
	 */
	@Test
	public void noMeleePotionSelectedDefaultsToNoPotionRatherThanBlockingTheLine()
	{
		Loadout saved = loadout("Unpotted", Collections.emptyList(), "ACCURATE", "NONE", null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Unpotted", LEVELS, id -> UNKNOWN);

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		// M1 from MaxHitTest: Str 99, no gear, no prayer, no potion, Accurate (+0) = 11.
		assertEquals(11, gear.melee().result().value());
	}

	/**
	 * A real potion selection changes the number -- the wiring end to end,
	 * not just {@link StrengthPotion#bonus} in isolation. Matches P1 from
	 * {@link MaxHitTest#meleePotionVectors()}.
	 */
	@Test
	public void meleePotionSelectionRaisesTheNumber()
	{
		Loadout saved = loadoutWithMeleePotion("Potted", Collections.emptyList(),
			"ACCURATE", "NONE", "SUPER_STRENGTH_POTION");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Potted", LEVELS, id -> UNKNOWN);

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		assertEquals("P1 from MaxHitTest#meleePotionVectors", 13, gear.melee().result().value());
	}

	/**
	 * Neither buff active: nothing to compare against, so both comparisons
	 * are absent rather than repeating the number already shown above them.
	 */
	@Test
	public void withoutComparisonsAreAbsentWhenNeitherBuffIsActive()
	{
		Loadout saved = loadout("Bare", Collections.emptyList(), "ACCURATE", "NONE", null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear.StyleLine melee = LockedGear.resolve(stored, "Bare", LEVELS, id -> UNKNOWN).melee();

		assertEquals(LockedGear.StyleLine.Kind.RESULT, melee.kind());
		assertNull(melee.withoutPrayerValue());
		assertNull(melee.withoutPotionValue());
	}

	/** Only prayer is active: only the prayer comparison appears. */
	@Test
	public void onlyWithoutPrayerAppearsWhenOnlyPrayerIsActive()
	{
		Loadout saved = loadout("Prayer only", Collections.emptyList(), "ACCURATE", "PIETY", null, null, null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear.StyleLine melee = LockedGear.resolve(stored, "Prayer only", LEVELS, id -> UNKNOWN).melee();

		assertEquals(LockedGear.StyleLine.Kind.RESULT, melee.kind());
		assertNotNull("removing the active prayer must be comparable", melee.withoutPrayerValue());
		assertNull(melee.withoutPotionValue());
		// M1: Accurate, no prayer, no potion, no gear = 11.
		assertEquals(Integer.valueOf(11), melee.withoutPrayerValue());
	}

	/** Only potion is active: only the potion comparison appears. */
	@Test
	public void onlyWithoutPotionAppearsWhenOnlyPotionIsActive()
	{
		Loadout saved = loadoutWithMeleePotion("Potion only", Collections.emptyList(),
			"ACCURATE", "NONE", "SUPER_STRENGTH_POTION");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear.StyleLine melee = LockedGear.resolve(stored, "Potion only", LEVELS, id -> UNKNOWN).melee();

		assertEquals(LockedGear.StyleLine.Kind.RESULT, melee.kind());
		assertNull(melee.withoutPrayerValue());
		assertEquals(Integer.valueOf(11), melee.withoutPotionValue());
	}

	/**
	 * Both buffs active: each comparison independently drops exactly one of
	 * them, verified against {@link MaxHit#melee} called directly with the
	 * same gear rather than against hand arithmetic -- the same "trust the
	 * one implementation, don't restate it" reasoning {@link MaxHitTest}'s
	 * own class doc states.
	 */
	@Test
	public void withoutValuesEachDropExactlyOneBuff()
	{
		Loadout saved = loadoutWithMeleePotion("Fully buffed", Collections.emptyList(),
			"AGGRESSIVE", "PIETY", "SUPER_STRENGTH_POTION");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear.StyleLine melee = LockedGear.resolve(stored, "Fully buffed", LEVELS, id -> UNKNOWN).melee();
		assertEquals(LockedGear.StyleLine.Kind.RESULT, melee.kind());

		GearBonuses noGear = new GearBonuses(0, 0, 0, Collections.emptySet(), null);
		int withBoth = MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY,
			StrengthPotion.SUPER_STRENGTH_POTION, noGear).value();
		int withoutPrayerExpected = MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.NONE,
			StrengthPotion.SUPER_STRENGTH_POTION, noGear).value();
		int withoutPotionExpected = MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY,
			StrengthPotion.NONE, noGear).value();

		assertEquals(withBoth, melee.result().value());
		assertEquals(Integer.valueOf(withoutPrayerExpected), melee.withoutPrayerValue());
		assertEquals(Integer.valueOf(withoutPotionExpected), melee.withoutPotionValue());
	}

	/**
	 * The asymmetry with {@link #unrecognisedSelectionNameFallsBackToNeedsSelection}:
	 * an unrecognised (e.g. since-renamed) potion enum name still resolves
	 * the melee line, using no potion, rather than withholding it. Style and
	 * prayer have no honest "unselected" value to fall back to; a potion
	 * does.
	 */
	@Test
	public void unrecognisedMeleePotionNameFallsBackToNoPotionRatherThanNeedsSelection()
	{
		Loadout saved = loadoutWithMeleePotion("Renamed potion", Collections.emptyList(),
			"ACCURATE", "NONE", "NOT_A_REAL_POTION");
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));

		LockedGear gear = LockedGear.resolve(stored, "Renamed potion", LEVELS, id -> UNKNOWN);

		assertEquals(LockedGear.StyleLine.Kind.RESULT, gear.melee().kind());
		assertEquals(11, gear.melee().result().value());
	}

	@Test
	public void equalsAndHashCodeAreStructural()
	{
		LoadoutItem item = new LoadoutItem(EquipmentInventorySlot.WEAPON.getSlotIdx(), 9999, 1, "Rune crossbow");
		Loadout saved = loadout("PK gear", Collections.singletonList(item),
			null, null, "ACCURATE", "RIGOUR", null, null);
		LoadoutStore.Result stored = LoadoutStore.Result.ok(Collections.singletonList(saved));
		CombatStats levels = new CombatStats(1, 1, 1, 10, 81, 1, 1);

		LockedGear first = LockedGear.resolve(stored, "PK gear", levels, id -> id == 9999 ? known(0, 103, 0) : UNKNOWN);
		LockedGear second = LockedGear.resolve(stored, "PK gear", levels, id -> id == 9999 ? known(0, 103, 0) : UNKNOWN);

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}
}
