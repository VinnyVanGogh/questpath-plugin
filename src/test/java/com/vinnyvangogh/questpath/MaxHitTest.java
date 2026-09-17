package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Table-driven vectors for {@link MaxHit}, {@link GearEffects}, and {@link
 * MaxHitResult}.
 *
 * The 18 rows in {@link #meleeVectors()}, {@link #rangedVectors()}, and
 * {@link #magicVectors()} cover every worked vector from RESEARCH.md
 * &sect;20.5 (M1-M6, R1-R6, Mg1-Mg6). <b>17 of the 18 are verbatim; one
 * &mdash; Mg6 &mdash; is deliberately scoped down and is NOT the &sect;20.5
 * value.</b> &sect;20.5 states Mg6's full in-game answer as 35 (base 30
 * &times; Augury 1.04 &times; on-task Slayer helm(i) 1.15), but the Slayer
 * bonus is a &sect;20.3.1 Pre-Hit-Roll term v1 does not model (&sect;20.6).
 * The row below asserts the value v1 can actually compute from that same
 * input &mdash; 31, base &times; Augury only, i.e. &sect;20.5's own numbers
 * minus the unmodelled Slayer helm term &mdash; with the Slayer helm
 * surfaced via {@link MaxHitResult#notApplied()} instead of silently
 * applied or silently dropped. Its row name and assertion messages say so
 * explicitly rather than presenting 31 as if it were &sect;20.5's own
 * figure. That also makes Mg6 double as the "unmodelled effect does not
 * change value()" proof for magic.
 */
public class MaxHitTest
{
	private static GearBonuses gear(int strengthBonus, int rangedStrength, int magicDamagePercent, GearEffect... effects)
	{
		Set<GearEffect> set = effects.length == 0
			? Collections.<GearEffect>emptySet()
			: EnumSet.copyOf(Arrays.asList(effects));
		return new GearBonuses(strengthBonus, rangedStrength, magicDamagePercent, set, null);
	}

	private static GearBonuses poweredStaffGear(int itemId)
	{
		return new GearBonuses(0, 0, 0, Collections.<GearEffect>emptySet(), itemId);
	}

	// ------------------------------------------------------------------
	// §20.5.1 Melee (6 vectors, verbatim)
	// ------------------------------------------------------------------

	@Test
	public void meleeVectors()
	{
		assertEquals("M1: Str 99, no gear, no prayer, no style bonus",
			11, MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, gear(0, 0, 0)).value());

		assertEquals("M2: Str 99, no gear, Piety, aggressive (+3)",
			13, MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY, gear(0, 0, 0)).value());

		assertEquals("M3: Str 99, no gear, Void melee (1.1), controlled (+1)",
			12, MaxHit.melee(99, AttackStyle.CONTROLLED, StrengthPrayer.NONE, gear(0, 0, 0, GearEffect.VOID_MELEE)).value());

		assertEquals("M4: Str 99, Abyssal whip (+82 str), Piety, aggressive (+3), no void",
			30, MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY, gear(82, 0, 0)).value());

		assertEquals("M5: Str 75, no gear, no prayer, no style bonus",
			8, MaxHit.melee(75, AttackStyle.ACCURATE, StrengthPrayer.NONE, gear(0, 0, 0)).value());

		assertEquals("M6: Str 99, Rune scimitar (+44 str), Superhuman Strength, controlled (+1)",
			20, MaxHit.melee(99, AttackStyle.CONTROLLED, StrengthPrayer.SUPERHUMAN_STRENGTH, gear(44, 0, 0)).value());
	}

	// ------------------------------------------------------------------
	// §20.5.2 Ranged (6 vectors, verbatim)
	// ------------------------------------------------------------------

	@Test
	public void rangedVectors()
	{
		assertEquals("R1: Rng 99, no gear, no prayer, no style bonus",
			11, MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.NONE, gear(0, 0, 0)).value());

		assertEquals("R2: Rng 99, no gear, Rigour, no style bonus",
			13, MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.RIGOUR, gear(0, 0, 0)).value());

		assertEquals("R3: Rng 99, no gear, Void ranged (1.1), accurate (+3)",
			12, MaxHit.ranged(99, RangedStyle.ACCURATE, RangedPrayer.NONE, gear(0, 0, 0, GearEffect.VOID_RANGED)).value());

		assertEquals("R4: Rng 81, Rune crossbow + Ruby bolts (rstr +103), Rigour, accurate (+3), no void",
			29, MaxHit.ranged(81, RangedStyle.ACCURATE, RangedPrayer.RIGOUR, gear(0, 103, 0)).value());

		assertEquals("R5: Rng 70, no gear, no prayer, no style bonus",
			8, MaxHit.ranged(70, RangedStyle.RAPID, RangedPrayer.NONE, gear(0, 0, 0)).value());

		assertEquals("R6: Rng 99, Rune crossbow + Ruby bolts (rstr +103), Elite ranged void (1.125), accurate (+3), no prayer",
			32, MaxHit.ranged(99, RangedStyle.ACCURATE, RangedPrayer.NONE, gear(0, 103, 0, GearEffect.ELITE_VOID_RANGED)).value());
	}

	// ------------------------------------------------------------------
	// §20.5.3 Magic (5 verbatim vectors + 1 scoped-down row — see class javadoc)
	// ------------------------------------------------------------------

	@Test
	public void magicVectors()
	{
		assertEquals("Mg1: Fire Surge (base 24), no gear, no prayer",
			24, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE, gear(0, 0, 0)).value());

		assertEquals("Mg2: Ice Barrage (base 30), Augury (+4%), no gear",
			31, MaxHit.magic(99, Spell.ICE_BARRAGE, MagicPrayer.AUGURY, gear(0, 0, 0)).value());

		assertEquals("Mg3: Fire Surge (base 24), Elite void mage + Void mage helm (+5%)",
			25, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE, gear(0, 0, 0, GearEffect.ELITE_VOID_MAGE)).value());

		assertEquals("Mg4: Trident of the Swamp, Magic 99, no damage bonus",
			31, MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE, poweredStaffGear(ItemID.TOXIC_TOTS_CHARGED)).value());

		assertEquals("Mg5: Tumeken's shadow, Magic 85, no damage bonus",
			29, MaxHit.magic(85, Spell.POWERED_STAFF, MagicPrayer.NONE, poweredStaffGear(ItemID.TUMEKENS_SHADOW)).value());

		// Mg6 (§20.5 minus Slayer helm, not modelled in v1; full answer 35):
		// this is NOT §20.5's own Mg6 value. §20.5's Mg6 is Ice Barrage
		// (base 30) × Augury (1.04) × on-task Slayer helm(i) (1.15) = 35,
		// but the Slayer helm's Pre-Hit-Roll bonus is unmodelled in v1
		// (§20.6). This row uses the same inputs and asserts only what v1
		// actually computes — base × Augury, 31 — with the Slayer helm
		// required to appear in notApplied() rather than silently applied
		// or silently dropped.
		MaxHitResult mg6 = MaxHit.magic(99, Spell.ICE_BARRAGE, MagicPrayer.AUGURY,
			gear(0, 0, 0, GearEffect.SLAYER_HELMET_IMBUED));
		assertEquals("Mg6 (§20.5 minus Slayer helm, not modelled in v1; full §20.5 answer is 35): v1 computes base × Augury only",
			31, mg6.value());
		assertEquals("Mg6 (§20.5 minus Slayer helm, not modelled in v1; full §20.5 answer is 35): SLAYER_HELMET_IMBUED must appear in notApplied()",
			Collections.singletonList(GearEffect.SLAYER_HELMET_IMBUED), mg6.notApplied());
	}

	// ------------------------------------------------------------------
	// Powered staves added in fix round 1: Accursed sceptre, Thammaron's
	// sceptre, Bone staff. §20.5 has no worked vector for any of these
	// three (they were not resolvable to an item id in the initial pass),
	// so each expected value here is derived directly from §20.4's formula
	// rather than cited to §20.5.
	// ------------------------------------------------------------------

	@Test
	public void accursedSceptreVector()
	{
		// §20.4: Magic/3 − 6, clamped to a stated minimum of 0. At Magic 99:
		// floor(99/3) − 6 = 33 − 6 = 27.
		assertEquals("Accursed sceptre, Magic 99 (§20.4: Magic/3 − 6)",
			27, MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
				poweredStaffGear(ItemID.WILD_CAVE_ACCURSED_CHARGED)).value());
	}

	@Test
	public void accursedSceptreClampsToItsStatedMinimumAtLowMagic()
	{
		// §20.4 states the real minimum is 0 at Magic 0 ("quoted"); the raw
		// formula alone would give floor(10/3) − 6 = 3 − 6 = −3.
		assertEquals("Accursed sceptre, Magic 10, clamped to the stated minimum of 0",
			0, MaxHit.magic(10, Spell.POWERED_STAFF, MagicPrayer.NONE,
				poweredStaffGear(ItemID.WILD_CAVE_ACCURSED_CHARGED)).value());
	}

	@Test
	public void thammaronsSceptreVector()
	{
		// §20.4: floor(Magic/3) − 8. At Magic 99: floor(99/3) − 8 = 33 − 8 = 25.
		assertEquals("Thammaron's sceptre, Magic 99 (§20.4: floor(Magic/3) − 8)",
			25, MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
				poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_CHARGED)).value());
	}

	@Test
	public void boneStaffVector()
	{
		// §20.4: floor(Magic/3) + 5. At Magic 99: floor(99/3) + 5 = 33 + 5 = 38.
		assertEquals("Bone staff, Magic 99 (§20.4: floor(Magic/3) + 5)",
			38, MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
				poweredStaffGear(ItemID.RAT_BONE_STAFF)).value());
	}

	@Test
	public void unchargedAccursedSceptreIsNotModelled()
	{
		// An uncharged staff cannot cast at all, so it must never produce a
		// max hit — the uncharged item id is deliberately not wired into
		// poweredStaffBase().
		MaxHitResult result = MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_ACCURSED_UNCHARGED));
		assertTrue("uncharged Accursed sceptre must not resolve to a value", !result.isKnown());
	}

	@Test
	public void unchargedThammaronsSceptreIsNotModelled()
	{
		MaxHitResult result = MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_UNCHARGED));
		assertTrue("uncharged Thammaron's sceptre must not resolve to a value", !result.isKnown());
	}

	// ------------------------------------------------------------------
	// Unmodelled effects: reported, never change value().
	// ------------------------------------------------------------------

	@Test
	public void meleeUnmodelledEffectIsReportedNotApplied()
	{
		MaxHitResult result = MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE,
			gear(0, 0, 0, GearEffect.DHAROKS_EQUIPMENT));

		assertEquals("Dharok's is not applied: value must match the no-gear M1 vector", 11, result.value());
		assertEquals(Collections.singletonList(GearEffect.DHAROKS_EQUIPMENT), result.notApplied());
	}

	@Test
	public void rangedUnmodelledEffectIsReportedNotApplied()
	{
		MaxHitResult result = MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.NONE,
			gear(0, 0, 0, GearEffect.TWISTED_BOW));

		assertEquals("Twisted bow is not applied: value must match the no-gear R1 vector", 11, result.value());
		assertEquals(Collections.singletonList(GearEffect.TWISTED_BOW), result.notApplied());
	}

	@Test
	public void magicUnmodelledEffectIsReportedNotApplied()
	{
		MaxHitResult result = MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE,
			gear(0, 0, 0, GearEffect.CHAOS_GAUNTLETS));

		assertEquals("Chaos gauntlets is not applied: value must match the no-gear Mg1 vector", 24, result.value());
		assertEquals(Collections.singletonList(GearEffect.CHAOS_GAUNTLETS), result.notApplied());
	}

	@Test
	public void notAppliedEffectsIrrelevantToTheStyleAreNotReported()
	{
		// A ranged-only effect (Twisted bow) must not leak into melee's
		// notApplied list, and vice versa.
		MaxHitResult melee = MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE,
			gear(0, 0, 0, GearEffect.TWISTED_BOW));
		assertTrue("Twisted bow is a ranged effect and must not appear in melee's notApplied",
			melee.notApplied().isEmpty());

		MaxHitResult ranged = MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.NONE,
			gear(0, 0, 0, GearEffect.DHAROKS_EQUIPMENT));
		assertTrue("Dharok's is a melee effect and must not appear in ranged's notApplied",
			ranged.notApplied().isEmpty());
	}

	// ------------------------------------------------------------------
	// Void detection needs the whole set.
	// ------------------------------------------------------------------

	@Test
	public void voidDetectionNeedsTheWholeSet()
	{
		int top = ItemID.PEST_VOID_KNIGHT_TOP;
		int robe = ItemID.PEST_VOID_KNIGHT_ROBES;
		int gloves = ItemID.PEST_VOID_KNIGHT_GLOVES;
		int meleeHelm = ItemID.GAME_PEST_MELEE_HELM;

		assertTrue("top + robe + helm, missing gloves, detects nothing",
			GearEffects.detect(Arrays.asList(top, robe, meleeHelm)).isEmpty());

		assertTrue("top + robe + gloves, missing a helm, detects nothing",
			GearEffects.detect(Arrays.asList(top, robe, gloves)).isEmpty());

		assertEquals("the full set (top + robe + gloves + melee helm) detects Void melee",
			Collections.singleton(GearEffect.VOID_MELEE),
			GearEffects.detect(Arrays.asList(top, robe, gloves, meleeHelm)));
	}

	@Test
	public void voidDetectionDoesNotMixBaseAndEliteTiers()
	{
		int baseTop = ItemID.PEST_VOID_KNIGHT_TOP;
		int eliteRobe = ItemID.ELITE_VOID_KNIGHT_ROBES;
		int gloves = ItemID.PEST_VOID_KNIGHT_GLOVES;
		int meleeHelm = ItemID.GAME_PEST_MELEE_HELM;

		assertTrue("a base top with an elite robe is not a complete set of either tier",
			GearEffects.detect(Arrays.asList(baseTop, eliteRobe, gloves, meleeHelm)).isEmpty());
	}

	@Test
	public void eliteVoidRangedIsDetectedAndModelled()
	{
		int eliteTop = ItemID.ELITE_VOID_KNIGHT_TOP;
		int eliteRobe = ItemID.ELITE_VOID_KNIGHT_ROBES;
		int gloves = ItemID.PEST_VOID_KNIGHT_GLOVES;
		int rangerHelm = ItemID.GAME_PEST_ARCHER_HELM;

		Set<GearEffect> detected = GearEffects.detect(Arrays.asList(eliteTop, eliteRobe, gloves, rangerHelm));
		assertEquals(Collections.singleton(GearEffect.ELITE_VOID_RANGED), detected);
		assertTrue(GearEffect.ELITE_VOID_RANGED.modelled());
	}

	@Test
	public void eliteVoidMeleeIsDetectedButNotModelled()
	{
		int eliteTop = ItemID.ELITE_VOID_KNIGHT_TOP;
		int eliteRobe = ItemID.ELITE_VOID_KNIGHT_ROBES;
		int gloves = ItemID.PEST_VOID_KNIGHT_GLOVES;
		int meleeHelm = ItemID.GAME_PEST_MELEE_HELM;

		Set<GearEffect> detected = GearEffects.detect(Arrays.asList(eliteTop, eliteRobe, gloves, meleeHelm));
		assertEquals(Collections.singleton(GearEffect.ELITE_VOID_MELEE), detected);
		assertTrue("§20.1 gives no elite-melee multiplier, so this must stay unmodelled",
			!GearEffect.ELITE_VOID_MELEE.modelled());
	}

	@Test
	public void plainVoidMageHasNoListedBonusAndIsNotDetected()
	{
		int top = ItemID.PEST_VOID_KNIGHT_TOP;
		int robe = ItemID.PEST_VOID_KNIGHT_ROBES;
		int gloves = ItemID.PEST_VOID_KNIGHT_GLOVES;
		int mageHelm = ItemID.GAME_PEST_MAGE_HELM;

		assertTrue("§20.3.1: no listed bonus for plain (non-elite) void mage",
			GearEffects.detect(Arrays.asList(top, robe, gloves, mageHelm)).isEmpty());
	}

	// ------------------------------------------------------------------
	// MaxHitResult shape rules.
	// ------------------------------------------------------------------

	@Test(expected = IllegalStateException.class)
	public void notModelledValueThrows()
	{
		MaxHitResult.notModelled("Twisted bow scales with target Magic; not modelled").value();
	}

	@Test(expected = IllegalStateException.class)
	public void notModelledNotAppliedThrows()
	{
		MaxHitResult.notModelled("no base for this spell").notApplied();
	}

	@Test(expected = IllegalStateException.class)
	public void knownReasonThrows()
	{
		MaxHitResult.known(11, Collections.<GearEffect>emptyList()).reason();
	}

	@Test
	public void unresolvedPoweredStaffIsNotModelled()
	{
		// No formula/id in this build resolves an arbitrary, unrecognized
		// item id. -1 stands in for "some weapon this class does not
		// recognize" without asserting a specific real item's id — every
		// staff §20.4 gives a formula for now has a wired-in id (see
		// task-2-report.md, fix round 1).
		MaxHitResult result = MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE, poweredStaffGear(-1));
		assertTrue(!result.isKnown());
	}

	@Test
	public void poweredStaffWithoutSpellSelectedIsNotModelled()
	{
		MaxHitResult result = MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE, gear(0, 0, 0));
		assertTrue(!result.isKnown());
	}

	// ------------------------------------------------------------------
	// Prayer NONE and the floor boundary cases §20 calls out.
	// ------------------------------------------------------------------

	@Test
	public void strengthPrayerNoneAndDefensiveStyleContributeNothing()
	{
		// Accurate and Defensive both carry a 0 style bonus (§20.1); this
		// asserts Defensive really does match Accurate's M1 vector rather
		// than assuming it from the table.
		assertEquals(11, MaxHit.melee(99, AttackStyle.DEFENSIVE, StrengthPrayer.NONE, gear(0, 0, 0)).value());
	}

	@Test
	public void meleePrayerFlooringIsNotRounding()
	{
		// Str 85 × Superhuman Strength (1.10) = 93.5, which floors to 93,
		// not rounds to 94 — the exact floor case §20.1's floor #2 calls
		// for.
		MaxHitResult result = MaxHit.melee(85, AttackStyle.ACCURATE, StrengthPrayer.SUPERHUMAN_STRENGTH, gear(0, 0, 0));
		// effStr = floor(85*1.10) + 0 + 8 = 93 + 8 = 101; maxHit = floor(0.5 + 101*64/640) = floor(10.6) = 10.
		assertEquals(10, result.value());
	}

	@Test
	public void rangedPrayerFlooringIsNotRounding()
	{
		// Rng 87 × Rigour (1.23) = 107.01, floors to 107 (not 108).
		MaxHitResult result = MaxHit.ranged(87, RangedStyle.RAPID, RangedPrayer.RIGOUR, gear(0, 0, 0));
		// effRng = floor(87*1.23) + 0 + 8 = 107 + 8 = 115; maxHit = floor(0.5 + 115*64/640) = floor(12.0) = 12.
		assertEquals(12, result.value());
	}

	@Test
	public void magicPrayerNoneLeavesBaseUnchanged()
	{
		assertEquals(20, MaxHit.magic(99, Spell.SARADOMIN_STRIKE, MagicPrayer.NONE, gear(0, 0, 0)).value());
	}

	@Test
	public void mysticWillDoesNotAffectDamage()
	{
		// §20.3.1's damage table omits Mystic Will entirely; its multiplier
		// here must be a true no-op, identical to NONE.
		assertEquals(
			MaxHit.magic(99, Spell.SARADOMIN_STRIKE, MagicPrayer.NONE, gear(0, 0, 0)).value(),
			MaxHit.magic(99, Spell.SARADOMIN_STRIKE, MagicPrayer.MYSTIC_WILL, gear(0, 0, 0)).value());
	}

	@Test(expected = IllegalStateException.class)
	public void poweredStaffSentinelHasNoFixedBase()
	{
		Spell.POWERED_STAFF.baseMaxHit();
	}
}
