package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Pure max-hit arithmetic for melee, ranged, and magic, straight from
 * RESEARCH.md &sect;20. No client dependency: levels are the caller's
 * (live) values, gear is the frozen loadout's {@link GearBonuses}.
 *
 * v1's scope is exactly &sect;20.6's ruling: base level + style bonus +
 * offensive prayer + Void/Elite Void. Every other multiplier &sect;20 names
 * is a real {@link GearEffect}, but this class never multiplies it in
 * &mdash; it only reports it via {@link MaxHitResult#notApplied()} when
 * {@link GearBonuses#effects()} contains it. All arithmetic below is done
 * in integers on purpose: every prayer, style, and void bonus &sect;20
 * lists is a whole percent or an exact eighth (1.125 = 9/8), so integer
 * multiply-then-divide reproduces the spec's floors exactly with no
 * floating-point rounding risk.
 */
public final class MaxHit
{
	// Melee: every RESEARCH.md §20.1.1 multiplier this class knows about,
	// other than VOID_MELEE (applied, not reported) and ELITE_VOID_MELEE's
	// modelled()==false handling (the general filter already excludes any
	// modelled() effect from notApplied, so it does not need special-casing
	// here — it is listed for completeness of the "what melee cares about"
	// set).
	private static final Set<GearEffect> MELEE_EFFECTS = EnumSet.of(
		GearEffect.ELITE_VOID_MELEE, GearEffect.OBSIDIAN_ARMOUR_SET, GearEffect.DHAROKS_EQUIPMENT,
		GearEffect.CASTLE_WARS_BRACELET, GearEffect.BLISTERWOOD_FLAIL, GearEffect.IVANDIS_FLAIL,
		GearEffect.WILDERNESS_CHAINMACE, GearEffect.SLAYER_HELMET, GearEffect.SALVE_AMULET,
		GearEffect.SALVE_AMULET_E, GearEffect.INQUISITORS_ARMOUR, GearEffect.COLOSSAL_BLADE,
		GearEffect.OSMUMTENS_FANG, GearEffect.KERIS_PARTISAN,
		GearEffect.ARMADYL_GODSWORD_SPECIAL, GearEffect.BANDOS_GODSWORD_SPECIAL,
		GearEffect.ANCIENT_SARADOMIN_ZAMORAK_GODSWORD_SPECIAL, GearEffect.DRAGON_DAGGER_SPECIAL,
		GearEffect.DRAGON_HALBERD_SPECIAL, GearEffect.DRAGON_HASTA_SPECIAL, GearEffect.DRAGON_LONGSWORD_SPECIAL,
		GearEffect.DRAGON_MACE_SPECIAL, GearEffect.DRAGON_WARHAMMER_SPECIAL, GearEffect.RUNE_CLAWS_SPECIAL,
		GearEffect.ABYSSAL_DAGGER_SPECIAL, GearEffect.ABYSSAL_BLUDGEON_SPECIAL, GearEffect.SARADOMIN_SWORD_SPECIAL);

	// Ranged: every RESEARCH.md §20.2/§20.2.1 multiplier this class knows
	// about, other than VOID_RANGED/ELITE_VOID_RANGED (applied).
	private static final Set<GearEffect> RANGED_EFFECTS = EnumSet.of(
		GearEffect.SLAYER_HELMET_IMBUED, GearEffect.SALVE_AMULET_I, GearEffect.SALVE_AMULET_EI,
		GearEffect.WILDERNESS_BOW, GearEffect.TWISTED_BOW, GearEffect.CHINCHOMPA,
		GearEffect.DARK_BOW_DRAGON_ARROWS, GearEffect.DARK_BOW_OTHER_ARROWS,
		GearEffect.ZARYTE_CROSSBOW_ENCHANTED_BOLTS, GearEffect.DIAMOND_BOLTS_E,
		GearEffect.DRAGONSTONE_BOLTS_E, GearEffect.ONYX_BOLTS_E, GearEffect.OPAL_BOLTS_E);

	// Magic: every RESEARCH.md §20.3.1 term this class knows about, other
	// than ELITE_VOID_MAGE (applied).
	private static final Set<GearEffect> MAGIC_EFFECTS = EnumSet.of(
		GearEffect.CASTLE_WARS_BRACELET, GearEffect.SLAYER_HELMET_IMBUED, GearEffect.SALVE_AMULET_I,
		GearEffect.SALVE_AMULET_EI, GearEffect.CHAOS_GAUNTLETS, GearEffect.CHARGE_GOD_CAPE,
		GearEffect.SHADOW_BONUS, GearEffect.AVARICE, GearEffect.SMOKE_BATTLESTAFF,
		GearEffect.TWINFLAME_STAFF_SECOND_HIT, GearEffect.VIRTUS_ROBES, GearEffect.SCEPTRE_OF_THE_WILDERNESS,
		GearEffect.ACCURSED_SCEPTRE_SPECIAL, GearEffect.TOME_OF_FIRE, GearEffect.TOME_OF_WATER,
		GearEffect.TOME_OF_EARTH, GearEffect.MARK_OF_DARKNESS_DEMONBANE, GearEffect.AHRIMS_DAMNED);

	// Powered staff item ids RESEARCH.md §20.4 gives a usable formula for,
	// and whose gameval ItemID constant was verified by a direct
	// `javap -constants -p net.runelite.api.gameval.ItemID` check against
	// runelite-api-1.12.37.jar (re-run in fix round 1 — see task-2-report.md
	// for the full transcript). Trident of the Seas/Swamp, Sanguinesti
	// staff, and Tumeken's shadow were already verified in the initial
	// pass. Accursed sceptre, Thammaron's sceptre, and Bone staff were
	// missed in that pass — none of gameval's cache-derived names are
	// guessable from the wiki's item names — but a fix-round review found
	// them by cross-referencing the deprecated net.runelite.api.ItemID
	// class's human-readable names (ACCURSED_SCEPTRE = 27665,
	// THAMMARONS_SCEPTRE = 22555, BONE_STAFF = 28796) against gameval by
	// numeric id, the same technique already used above for Void's
	// trouver-locked variants. Re-verified independently here, not taken on
	// the reviewer's word:
	//
	//   javap -constants -p net/runelite/api/gameval/ItemID.class | grep -n "= 27665;\|= 22555;\|= 28796;"
	//     public static final int WILD_CAVE_ACCURSED_CHARGED = 27665;
	//     public static final int WILD_CAVE_SCEPTRE_CHARGED = 22555;
	//     public static final int RAT_BONE_STAFF = 28796;
	//
	// Each charged/uncharged pair (Accursed and Thammaron's only — Bone
	// staff has no uncharged variant in gameval) was checked too, so the
	// uncharged item id is deliberately never wired in below and falls
	// through to notModelled: an uncharged staff cannot cast at all, so it
	// must never produce a max hit.
	//
	//   javap -constants -p net/runelite/api/gameval/ItemID.class | grep -n "ACCURSED\|WILD_CAVE_SCEPTRE\|BONE_STAFF"
	//     public static final int WILD_CAVE_SCEPTRE_UNCHARGED = 22552;
	//     public static final int WILD_CAVE_SCEPTRE_CHARGED = 22555;
	//     public static final int WILD_CAVE_ACCURSED_UNCHARGED = 27662;
	//     public static final int WILD_CAVE_ACCURSED_CHARGED = 27665;
	//     public static final int RAT_BONE_STAFF = 28796;
	private static final int TRIDENT_OF_THE_SEAS = ItemID.TOTS_CHARGED;
	private static final int TRIDENT_OF_THE_SWAMP = ItemID.TOXIC_TOTS_CHARGED;
	private static final int SANGUINESTI_STAFF = ItemID.SANGUINESTI_STAFF;
	private static final int TUMEKENS_SHADOW = ItemID.TUMEKENS_SHADOW;
	private static final int ACCURSED_SCEPTRE = ItemID.WILD_CAVE_ACCURSED_CHARGED;
	private static final int THAMMARONS_SCEPTRE = ItemID.WILD_CAVE_SCEPTRE_CHARGED;
	private static final int BONE_STAFF = ItemID.RAT_BONE_STAFF;

	private MaxHit()
	{
	}

	/**
	 * Max melee hit: base Strength level + style + prayer + Void melee only.
	 * Every other &sect;20.1.1 multiplier (Dharok's, Obsidian, Salve,
	 * Osmumten's fang, every weapon special, ...) is reported via {@link
	 * MaxHitResult#notApplied()} instead of applied, per &sect;20.6.
	 *
	 * @param strengthLevel the caller's live Strength level.
	 * @param style the melee attack style in use.
	 * @param prayer the active strength prayer, or {@link StrengthPrayer#NONE}.
	 * @param gear the frozen loadout's gear bonuses.
	 */
	public static MaxHitResult melee(int strengthLevel, AttackStyle style, StrengthPrayer prayer, GearBonuses gear)
	{
		Set<GearEffect> effects = gear.effects();

		// RESEARCH.md §20.1: floor(floor(Str + Potion) × Prayer). Potion
		// Bonus is always 0 in this project, so floor(Str + 0) = Str
		// trivially; only the × Prayer floor is real.
		int stage1 = (strengthLevel * (100 + prayer.percentBonus())) / 100;

		// §20.1: + Style Bonus + 8.
		int stage2 = stage1 + style.styleBonus() + 8;

		// §20.1: × Void Bonus, floored. 1.1 = 11/10 exactly; no void is a
		// no-op multiply, so it is skipped rather than computed as ×1.
		int effStr = effects.contains(GearEffect.VOID_MELEE) ? (stage2 * 11) / 10 : stage2;

		// §20.1: Max Hit = floor(0.5 + EffStr × (StrBonus + 64) / 640).
		// Rewritten to exact integer division: 0.5 + n/640 == (320 + n)/640,
		// and Java's int division truncates toward zero, which is floor()
		// for the non-negative values this formula always produces.
		int maxHit = (320 + effStr * (gear.strengthBonus() + 64)) / 640;

		return MaxHitResult.known(maxHit, notApplied(effects, MELEE_EFFECTS));
	}

	/**
	 * Max ranged hit: base Ranged level + style + prayer + Void/Elite Void
	 * ranged only. Every other &sect;20.2.1 multiplier (Twisted bow, Craw's
	 * bow, Salve, bolt specials, chinchompas, ...) is reported via {@link
	 * MaxHitResult#notApplied()} instead of applied, per &sect;20.6.
	 *
	 * @param rangedLevel the caller's live Ranged level.
	 * @param style the ranged attack style in use.
	 * @param prayer the active ranged prayer, or {@link RangedPrayer#NONE}.
	 * @param gear the frozen loadout's gear bonuses.
	 */
	public static MaxHitResult ranged(int rangedLevel, RangedStyle style, RangedPrayer prayer, GearBonuses gear)
	{
		Set<GearEffect> effects = gear.effects();

		// §20.2: floor((Rng + Boost) × Prayer). Boost is always 0.
		int stage1 = (rangedLevel * (100 + prayer.percentBonus())) / 100;

		// §20.2: + Atk Style + 8.
		int stage2 = stage1 + style.atkStyleBonus() + 8;

		// §20.2: × Void Modifier, floored. Elite ranged void is 1.125 =
		// 9/8 exactly; plain ranged void is 1.1 = 11/10 exactly.
		int effRng;
		if (effects.contains(GearEffect.ELITE_VOID_RANGED))
		{
			effRng = (stage2 * 9) / 8;
		}
		else if (effects.contains(GearEffect.VOID_RANGED))
		{
			effRng = (stage2 * 11) / 10;
		}
		else
		{
			effRng = stage2;
		}

		// §20.2: inner floor(0.5 + EffRng × (EquipRangedStr + 64) / 640),
		// same (320 + n)/640 rewrite as melee. The formula's outer "× Gear
		// Bonus, floored" step is a structural no-op in v1: every step-three
		// ranged multiplier (Black mask (i), Salve (i)/(ei), Craw's bow,
		// Twisted bow, ...) is unmodelled, so Gear Bonus is always 1 and the
		// outer floor never changes the inner value. It is omitted rather
		// than coded as a literal ×1 no-op.
		int maxHit = (320 + effRng * (gear.rangedStrength() + 64)) / 640;

		return MaxHitResult.known(maxHit, notApplied(effects, RANGED_EFFECTS));
	}

	/**
	 * Max magic hit: base spell damage (or a powered staff's level-based
	 * formula) + Elite Void mage + prayer only. Every other &sect;20.3.1
	 * term (Chaos Gauntlets, Charge, Shadow Bonus, Salve, Avarice, Smoke
	 * battlestaff, Virtus, both sceptres, all three Tomes, Mark of
	 * Darkness, Ahrim's Damned, Castle wars bracelet, Twinflame staff's
	 * second hit) is reported via {@link MaxHitResult#notApplied()} instead
	 * of applied, per &sect;20.6. {@link GearBonuses#magicDamagePercent()}
	 * feeds the "Visible Bonuses" term of &sect;20.3.1's full pipeline,
	 * which &sect;20.6 places outside v1's scope entirely (it is only ever
	 * relevant multiplied by Tumeken's shadow's Shadow Bonus, itself
	 * unmodelled) — it is stored on {@link GearBonuses} for callers that
	 * need the raw stat, but this method does not read it.
	 *
	 * @param magicLevel the caller's live Magic level.
	 * @param spell the spell being cast, or {@link Spell#POWERED_STAFF} to
	 * use the wielded weapon's own level-based formula.
	 * @param prayer the active magic prayer, or {@link MagicPrayer#NONE}.
	 * @param gear the frozen loadout's gear bonuses.
	 */
	public static MaxHitResult magic(int magicLevel, Spell spell, MagicPrayer prayer, GearBonuses gear)
	{
		Set<GearEffect> effects = gear.effects();

		int base;
		if (spell.isPoweredStaff())
		{
			Integer staffItemId = gear.poweredStaffItemId();
			if (staffItemId == null)
			{
				return MaxHitResult.notModelled("Spell.POWERED_STAFF selected but this loadout has no powered staff item id set");
			}
			Integer resolved = poweredStaffBase(staffItemId, magicLevel);
			if (resolved == null)
			{
				return MaxHitResult.notModelled(
					"Powered staff item id " + staffItemId + " has no verified §20.4 formula/item id in this build");
			}
			base = resolved;
		}
		else
		{
			base = spell.baseMaxHit();
		}

		// §20.3.1 Void row: +5%, Elite void mage + Void mage helm only.
		int voidPercent = effects.contains(GearEffect.ELITE_VOID_MAGE) ? 5 : 0;

		// §20.3.1: PrimaryMagicDamage = floor(BaseDamageModifier × (1 +
		// ... + Void + ... + Prayer)), with every other additive term in
		// v1's scope forced to zero per §20.6.
		int maxHit = (base * (100 + voidPercent + prayer.percentBonus())) / 100;

		return MaxHitResult.known(maxHit, notApplied(effects, MAGIC_EFFECTS));
	}

	/**
	 * Resolves a powered staff's RESEARCH.md &sect;20.4 level-based base,
	 * or {@code null} when {@code itemId} is not one of the staves this
	 * class has a verified formula and item id for.
	 *
	 * Trident of the Seas/Swamp and Sanguinesti staff each carry a stated
	 * minimum hit that the raw formula alone does not reproduce (e.g.
	 * {@code floor(0/3) − 5 = −5} at Magic 0, but the page states the real
	 * minimum is 1) &mdash; {@code Math.max} enforces that floor. Tumeken's
	 * shadow needs no such clamp: {@code floor(Magic/3 + 1)} already gives
	 * 1 at Magic 0, matching the page's own worked example. Accursed
	 * sceptre carries a stated minimum too (0 at Magic 0); Thammaron's
	 * sceptre and Bone staff state no minimum at all in §20.4, so neither
	 * is clamped &mdash; inventing a floor for either would be exactly the
	 * kind of guessed constant this project does not allow.
	 */
	private static Integer poweredStaffBase(int itemId, int magicLevel)
	{
		if (itemId == TRIDENT_OF_THE_SEAS)
		{
			return Math.max(1, magicLevel / 3 - 5);
		}
		if (itemId == TRIDENT_OF_THE_SWAMP)
		{
			return Math.max(4, magicLevel / 3 - 2);
		}
		if (itemId == SANGUINESTI_STAFF)
		{
			return Math.max(6, magicLevel / 3);
		}
		if (itemId == TUMEKENS_SHADOW)
		{
			return magicLevel / 3 + 1;
		}
		if (itemId == ACCURSED_SCEPTRE)
		{
			// §20.4: "Magic/3 − 6". The page's own <math> block does not
			// render an explicit floor() around this expression — only the
			// surrounding prose ("increasing by one at 72 and every three
			// Magic levels thereafter") establishes that it behaves as a
			// floor. This is an inferred-from-prose reading of an ambiguous
			// math block, not a directly quoted floor; integer division
			// reproduces that inferred floor exactly for non-negative
			// inputs. The page's stated minimum (0 at Magic 0) is enforced
			// with Math.max, same as the other three clamped staves above.
			return Math.max(0, magicLevel / 3 - 6);
		}
		if (itemId == THAMMARONS_SCEPTRE)
		{
			// §20.4: "floor(Magic/3) − 8". No minimum is stated on the page
			// for this weapon, so none is invented here.
			return magicLevel / 3 - 8;
		}
		if (itemId == BONE_STAFF)
		{
			// §20.4: "floor(Magic/3) + 5". No minimum is stated on the page
			// for this weapon, so none is invented here.
			return magicLevel / 3 + 5;
		}
		return null;
	}

	/**
	 * Every effect in {@code detected} that also belongs to {@code
	 * relevantToStyle} and is not {@link GearEffect#modelled()}, in {@link
	 * GearEffect} declaration order.
	 */
	private static List<GearEffect> notApplied(Set<GearEffect> detected, Set<GearEffect> relevantToStyle)
	{
		List<GearEffect> out = new ArrayList<>();
		for (GearEffect effect : GearEffect.values())
		{
			if (relevantToStyle.contains(effect) && detected.contains(effect) && !effect.modelled())
			{
				out.add(effect);
			}
		}
		return out;
	}
}
