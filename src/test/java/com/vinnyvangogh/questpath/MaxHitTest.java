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

	private static GearBonuses poweredStaffGear(int itemId, int magicDamagePercent)
	{
		return new GearBonuses(0, 0, magicDamagePercent, Collections.<GearEffect>emptySet(), itemId);
	}

	// ------------------------------------------------------------------
	// §20.5.1 Melee (6 vectors, verbatim)
	// ------------------------------------------------------------------

	@Test
	public void meleeVectors()
	{
		assertEquals("M1: Str 99, no gear, no prayer, no style bonus",
			11, MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.NONE, gear(0, 0, 0)).value());

		assertEquals("M2: Str 99, no gear, Piety, aggressive (+3)",
			13, MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY, StrengthPotion.NONE, gear(0, 0, 0)).value());

		assertEquals("M3: Str 99, no gear, Void melee (1.1), controlled (+1)",
			12, MaxHit.melee(99, AttackStyle.CONTROLLED, StrengthPrayer.NONE, StrengthPotion.NONE,
				gear(0, 0, 0, GearEffect.VOID_MELEE)).value());

		assertEquals("M4: Str 99, Abyssal whip (+82 str), Piety, aggressive (+3), no void",
			30, MaxHit.melee(99, AttackStyle.AGGRESSIVE, StrengthPrayer.PIETY, StrengthPotion.NONE, gear(82, 0, 0)).value());

		assertEquals("M5: Str 75, no gear, no prayer, no style bonus",
			8, MaxHit.melee(75, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.NONE, gear(0, 0, 0)).value());

		assertEquals("M6: Str 99, Rune scimitar (+44 str), Superhuman Strength, controlled (+1)",
			20, MaxHit.melee(99, AttackStyle.CONTROLLED, StrengthPrayer.SUPERHUMAN_STRENGTH, StrengthPotion.NONE,
				gear(44, 0, 0)).value());
	}

	/**
	 * {@link StrengthPotion}, hand-derived from &sect;20.1's own formula and
	 * potion table rather than a &sect;20.5 worked vector -- none of M1-M6
	 * carries a potion, so there is nothing to quote verbatim here. Each row
	 * still traces back to a sourced number: the potion bonus formula, and
	 * either a no-potion vector already pinned above (P1) or independent
	 * arithmetic through the same &sect;20.1 stages (P2).
	 */
	@Test
	public void meleePotionVectors()
	{
		assertEquals("StrengthPotion.NONE contributes nothing at any level",
			0, StrengthPotion.NONE.bonus(1));
		assertEquals(0, StrengthPotion.NONE.bonus(99));

		assertEquals("Super strength potion at 99: floor(99*15%) + 5 = 14 + 5 = 19",
			19, StrengthPotion.SUPER_STRENGTH_POTION.bonus(99));

		assertEquals("P1: Str 99, Super strength potion, no prayer, Accurate (+0), no gear -- "
				+ "stage1 = floor((99+19)*100/100) = 118, stage2 = 118+0+8 = 126, "
				+ "maxHit = floor((320 + 126*64)/640) = floor(13.1) = 13, "
				+ "strictly above M1's un-potted 11",
			13, MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.SUPER_STRENGTH_POTION,
				gear(0, 0, 0)).value());

		assertEquals("P2: Str 99, Super strength potion, Piety, Accurate (+0), no gear -- "
				+ "stage1 = floor(118*123/100) = 145, stage2 = 145+0+8 = 153, "
				+ "maxHit = floor((320 + 153*64)/640) = floor(15.8) = 15 -- "
				+ "the potion bonus is added before the prayer floor, per "
				+ "RESEARCH.md's floor(floor(Str + Potion) x Prayer)",
			15, MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.PIETY, StrengthPotion.SUPER_STRENGTH_POTION,
				gear(0, 0, 0)).value());
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
	// Final whole-branch review fix (I-2): gear magic damage % (§20.3.1's
	// Visible Bonuses, scaled by Shadow Bonus) is now applied. None of
	// these five cite §20.5 — that section has no vector with a nonzero
	// mdmg at all — each is derived and hand-computed straight from
	// §20.3.1's PrimaryMagicDamage formula.
	// ------------------------------------------------------------------

	@Test
	public void standardSpellWithGearMagicDamageBonus()
	{
		// §20.3.1: no Tumeken's shadow -> ShadowBonus = ×1 (identity), so
		// VisibleBonuses applies directly. Fire Surge base 24, mdmg 10, no
		// prayer, no void:
		//   shadowTerm = min(1, (0.10 − 0) × 1) = 0.10
		//   PrimaryMagicDamage = floor(24 × (1 + 0.10 + 0 + 0)) = floor(26.4) = 26
		assertEquals("Fire Surge, mdmg 10, no prayer (§20.3.1: floor(24 × 1.10))",
			26, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE, gear(0, 0, 10)).value());
	}

	@Test
	public void standardSpellWithGearMagicDamageBonusAndAugury()
	{
		// Same as above, plus Augury (+4%):
		//   PrimaryMagicDamage = floor(24 × (1 + 0.10 + 0 + 0.04)) = floor(24 × 1.14) = floor(27.36) = 27
		assertEquals("Fire Surge, mdmg 10, Augury (§20.3.1: floor(24 × 1.14))",
			27, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.AUGURY, gear(0, 0, 10)).value());
	}

	@Test
	public void gearMagicDamageBonusIsCappedAtOneHundredPercent()
	{
		// §20.3.1: "min(1, ...)" caps the term at +100% regardless of how
		// high VisibleBonuses × ShadowBonus goes. mdmg 150, no Tumeken's
		// (ShadowBonus ×1), no prayer, no void:
		//   uncapped: (1.50 − 0) × 1 = 1.50, which min(1, ...) truncates to 1.0
		//   PrimaryMagicDamage = floor(24 × (1 + 1.0 + 0 + 0)) = floor(24 × 2.0) = 48
		// (an uncapped reading would give floor(24 × 2.5) = 60 instead — the
		// cap is what makes 48, not 60, the answer here.)
		assertEquals("Fire Surge, mdmg 150, capped at +100% (§20.3.1: floor(24 × 2.0), not floor(24 × 2.5))",
			48, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE, gear(0, 0, 150)).value());
	}

	@Test
	public void poweredStaffWithGearMagicDamageBonus()
	{
		// Trident of the Swamp, Magic 99: base = floor(99/3) − 2 = 31 (as
		// Mg4). mdmg 20, no Tumeken's, no prayer, no void:
		//   shadowTerm = min(1, (0.20 − 0) × 1) = 0.20
		//   PrimaryMagicDamage = floor(31 × (1 + 0.20 + 0 + 0)) = floor(31 × 1.20) = floor(37.2) = 37
		assertEquals("Trident of the Swamp, Magic 99, mdmg 20 (§20.3.1: floor(31 × 1.20))",
			37, MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
				poweredStaffGear(ItemID.TOXIC_TOTS_CHARGED, 20)).value());
	}

	@Test
	public void tumekensShadowWithGearMagicDamageBonus()
	{
		// Tumeken's shadow, Magic 85: base = floor(85/3) + 1 = 29 (as Mg5).
		// Wielding Tumeken's shadow makes ShadowBonus = ×3 (outside ToA).
		// mdmg 20, no prayer, no void:
		//   shadowTerm = min(1, (0.20 − 0) × 3) = min(1, 0.60) = 0.60
		//   PrimaryMagicDamage = floor(29 × (1 + 0.60 + 0 + 0)) = floor(29 × 1.60) = floor(46.4) = 46
		MaxHitResult result = MaxHit.magic(85, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.TUMEKENS_SHADOW, 20));
		assertEquals("Tumeken's shadow, Magic 85, mdmg 20 (§20.3.1: floor(29 × 1.60), ShadowBonus ×3)",
			46, result.value());
		assertEquals("Tumeken's shadow: the inside-ToA ×4 case must be surfaced as not applied",
			Collections.singletonList(GearEffect.TUMEKENS_SHADOW_TOA_BONUS), result.notApplied());
	}

	@Test
	public void tumekensShadowToaBonusIsNotSurfacedForOtherStaves()
	{
		// TUMEKENS_SHADOW_TOA_BONUS is specific to actually wielding
		// Tumeken's shadow — a different powered staff must not report it.
		MaxHitResult result = MaxHit.magic(99, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.TOXIC_TOTS_CHARGED, 20));
		assertTrue("Trident of the Swamp must not surface Tumeken's shadow's ToA note",
			!result.notApplied().contains(GearEffect.TUMEKENS_SHADOW_TOA_BONUS));
	}

	// ------------------------------------------------------------------
	// Fix 2 (final-fix-rereview.md): the first pass of the I-2 fix
	// subtracted Void's 5% from gear.magicDamagePercent() before scaling by
	// ShadowBonus, on the theory that RESEARCH.md §20.3.1's "(VisibleBonuses
	// − Void)" required it. That subtraction was wrong: magicDamagePercent
	// is a pure item-stat sum (LockedGear#resolve sums each item's own
	// ItemEquipmentStats#getMdmg()) that never included Void's 5% in the
	// first place — Void is a set effect, not any single item's own
	// displayed stat — so subtracting it again silently discarded Void's
	// contribution whenever real item mdmg was also present. A standalone
	// driver against the compiled class demonstrated this directly: Elite
	// void mage + 10 item mdmg, Fire Surge, no prayer produced 26 instead
	// of the correct 27. These two tests are that exact case, plus its
	// mdmg-0 counterpart re-derived by hand rather than assumed from the
	// pre-existing Mg3 vector.
	// ------------------------------------------------------------------

	@Test
	public void eliteVoidMageWithGearMagicDamageBonus()
	{
		// The exact case final-fix-rereview.md verified by direct
		// execution. Elite void mage, no Tumeken's (ShadowBonus ×1), mdmg
		// 10, Fire Surge (base 24), no prayer:
		//   shadowTerm = min(100, 10 × 1) = 10        (no Void subtraction — fix 2)
		//   PrimaryMagicDamage = floor(24 × (1 + 0.10 + 0.05 + 0)) = floor(24 × 1.15) = floor(27.6) = 27
		assertEquals("Elite void mage + mdmg 10, Fire Surge, no prayer (§20.3.1: floor(24 × 1.15), NOT 26)",
			27, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE,
				gear(0, 0, 10, GearEffect.ELITE_VOID_MAGE)).value());
	}

	@Test
	public void eliteVoidMageWithNoGearMagicDamageBonus()
	{
		// Same setup with mdmg 0, re-derived independently of the
		// pre-existing Mg3 §20.5 vector (which happens to share this exact
		// input/output, since Mg3 also uses mdmg 0): with no item mdmg
		// there is nothing for ShadowBonus to scale, so removing the Void
		// subtraction changes nothing at this data point specifically —
		// this is the case the original bug's own Math.max(0, ...) patch
		// could pass without actually being correct.
		//   shadowTerm = min(100, 0 × 1) = 0
		//   PrimaryMagicDamage = floor(24 × (1 + 0 + 0.05 + 0)) = floor(24 × 1.05) = floor(25.2) = 25
		assertEquals("Elite void mage + mdmg 0, Fire Surge, no prayer (§20.3.1: floor(24 × 1.05))",
			25, MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE,
				gear(0, 0, 0, GearEffect.ELITE_VOID_MAGE)).value());
	}

	// ------------------------------------------------------------------
	// Final whole-branch review fix (I-1): Toxic blowpipe. Ranged max hit
	// must be entirely notModelled -- GearBonuses#rangedStrength()'s
	// summed-stat approach cannot capture the loaded dart's real ranged
	// strength for this weapon at all, so no partial number is shown
	// either. Melee and magic must be completely unaffected.
	// ------------------------------------------------------------------

	@Test
	public void toxicBlowpipeUnloadedIsNotModelled()
	{
		Set<GearEffect> effects = GearEffects.detect(Collections.singletonList(ItemID.TOXIC_BLOWPIPE));
		assertEquals(Collections.singleton(GearEffect.TOXIC_BLOWPIPE), effects);

		GearBonuses gearWithBlowpipe = new GearBonuses(0, 200, 0, effects, null);
		MaxHitResult result = MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.NONE, gearWithBlowpipe);
		assertTrue("unloaded Toxic blowpipe (id " + ItemID.TOXIC_BLOWPIPE + ") must not resolve to a value",
			!result.isKnown());
		assertEquals("Toxic blowpipe uses dart strength; not modelled", result.reason());
	}

	@Test
	public void toxicBlowpipeLoadedIsNotModelled()
	{
		Set<GearEffect> effects = GearEffects.detect(Collections.singletonList(ItemID.TOXIC_BLOWPIPE_LOADED));
		assertEquals(Collections.singleton(GearEffect.TOXIC_BLOWPIPE), effects);

		GearBonuses gearWithBlowpipe = new GearBonuses(0, 200, 0, effects, null);
		MaxHitResult result = MaxHit.ranged(99, RangedStyle.RAPID, RangedPrayer.NONE, gearWithBlowpipe);
		assertTrue("loaded Toxic blowpipe (id " + ItemID.TOXIC_BLOWPIPE_LOADED + ") must not resolve to a value",
			!result.isKnown());
		assertEquals("Toxic blowpipe uses dart strength; not modelled", result.reason());
	}

	@Test
	public void toxicBlowpipeDoesNotAffectMeleeOrMagicLines()
	{
		// The same GearBonuses (blowpipe detected, plus a non-zero strength
		// bonus and magic damage % so a real number would be produced if
		// the blowpipe check leaked into the other two styles) must still
		// resolve melee and magic normally.
		Set<GearEffect> effects = GearEffects.detect(Collections.singletonList(ItemID.TOXIC_BLOWPIPE_LOADED));
		GearBonuses gearWithBlowpipe = new GearBonuses(82, 200, 10, effects, null);

		MaxHitResult melee = MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.NONE, gearWithBlowpipe);
		assertTrue("melee must be unaffected by a detected Toxic blowpipe", melee.isKnown());

		MaxHitResult magic = MaxHit.magic(99, Spell.FIRE_SURGE, MagicPrayer.NONE, gearWithBlowpipe);
		assertTrue("magic must be unaffected by a detected Toxic blowpipe", magic.isKnown());
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

	/**
	 * Fix round 2: Thammaron's sceptre's formula ({@code floor(Magic/3) − 8})
	 * has no §20.4-stated minimum and goes to zero or negative below Magic
	 * 27. Before this fix that raw int flowed straight into {@code
	 * MaxHitResult.known(...)}, so a negative "max hit" could be reported as
	 * fact. It must now report notModelled instead, at every level where the
	 * formula is below 1 — recomputed by hand for each level asserted here,
	 * not assumed from a single boundary.
	 */
	@Test
	public void thammaronsSceptreIsNotModelledBelowItsUsableRange()
	{
		// Magic 1: floor(1/3) − 8 = 0 − 8 = −8 (negative).
		MaxHitResult atOne = MaxHit.magic(1, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_CHARGED));
		assertTrue("Thammaron's sceptre, Magic 1: formula gives −8, must be notModelled", !atOne.isKnown());

		// Magic 23: floor(23/3) − 8 = 7 − 8 = −1 (negative).
		MaxHitResult at23 = MaxHit.magic(23, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_CHARGED));
		assertTrue("Thammaron's sceptre, Magic 23: formula gives −1, must be notModelled", !at23.isKnown());

		// Magic 24: floor(24/3) − 8 = 8 − 8 = 0. §20.4 states no minimum for
		// this weapon (unlike Accursed sceptre's own documented 0), so this
		// zero is not a verified answer either — still notModelled.
		MaxHitResult at24 = MaxHit.magic(24, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_CHARGED));
		assertTrue("Thammaron's sceptre, Magic 24: formula gives 0, must be notModelled", !at24.isKnown());
	}

	@Test
	public void thammaronsSceptreBecomesKnownAtItsTrueBoundary()
	{
		// Magic 27 is the first level where floor(Magic/3) − 8 reaches 1:
		// floor(27/3) − 8 = 9 − 8 = 1.
		MaxHitResult result = MaxHit.magic(27, Spell.POWERED_STAFF, MagicPrayer.NONE,
			poweredStaffGear(ItemID.WILD_CAVE_SCEPTRE_CHARGED));
		assertEquals("Thammaron's sceptre, Magic 27 (§20.4: floor(Magic/3) − 8 = 1, the first usable level)",
			1, result.value());
	}

	// ------------------------------------------------------------------
	// Unmodelled effects: reported, never change value().
	// ------------------------------------------------------------------

	@Test
	public void meleeUnmodelledEffectIsReportedNotApplied()
	{
		MaxHitResult result = MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.NONE,
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
		MaxHitResult melee = MaxHit.melee(99, AttackStyle.ACCURATE, StrengthPrayer.NONE, StrengthPotion.NONE,
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

	@Test(expected = IllegalArgumentException.class)
	public void knownRejectsANegativeValue()
	{
		// Fix round 2: this is the defence-in-depth half of the Thammaron's
		// sceptre fix — MaxHit.magic() now catches the negative case before
		// it ever reaches here, but MaxHitResult itself must refuse to
		// construct a negative "known" result regardless of caller.
		MaxHitResult.known(-1, Collections.<GearEffect>emptyList());
	}

	@Test
	public void knownAcceptsZero()
	{
		// Zero is a legitimate known result — RESEARCH.md §20 does not state
		// that every max hit is at least 1, and Accursed sceptre's own
		// §20.4 row states its real minimum is 0 at Magic 0. Only negative
		// values are rejected.
		MaxHitResult result = MaxHitResult.known(0, Collections.<GearEffect>emptyList());
		assertTrue(result.isKnown());
		assertEquals(0, result.value());
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
		assertEquals(11, MaxHit.melee(99, AttackStyle.DEFENSIVE, StrengthPrayer.NONE, StrengthPotion.NONE, gear(0, 0, 0)).value());
	}

	@Test
	public void meleePrayerFlooringIsNotRounding()
	{
		// Str 85 × Superhuman Strength (1.10) = 93.5, which floors to 93,
		// not rounds to 94 — the exact floor case §20.1's floor #2 calls
		// for.
		MaxHitResult result = MaxHit.melee(85, AttackStyle.ACCURATE, StrengthPrayer.SUPERHUMAN_STRENGTH,
			StrengthPotion.NONE, gear(0, 0, 0));
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
