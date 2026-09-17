package com.vinnyvangogh.questpath;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Detects {@link GearEffect}s from a set of worn item ids.
 *
 * A pure table lookup: no client, no {@code ItemManager}, no I/O. It is
 * only as complete as {@code runelite-api.md} item 13's verified {@code
 * net.runelite.api.gameval.ItemID} constant list, cross-checked directly
 * against {@code runelite-api-1.12.37.jar} (via {@code javap -constants})
 * in the course of building this class. That direct check found more Void
 * variants than item 13's own write-up named (the trouver-locked ids below),
 * so those are included too; everything else stops exactly where item 13
 * stopped verifying.
 *
 * <b>Detected:</b>
 * <ul>
 * <li>{@link GearEffect#VOID_MELEE}, {@link GearEffect#VOID_RANGED},
 * {@link GearEffect#ELITE_VOID_RANGED}, {@link GearEffect#ELITE_VOID_MAGE},
 * {@link GearEffect#ELITE_VOID_MELEE} &mdash; base, elite, and trouver-locked
 * Void Knight equipment ids (top, robe, gloves, and the three style helms).
 * Plain (non-elite) Void mage is deliberately never emitted: RESEARCH.md
 * &sect;20.3.1 says it carries no bonus at all, so there is nothing to
 * detect.</li>
 * <li>{@link GearEffect#DHAROKS_EQUIPMENT} &mdash; all four
 * {@code BARROWS_DHAROK_*} pieces (head, weapon, body, legs) worn together.
 * Degraded (100/75/50/25/broken) variants exist in gameval but are not
 * checked here; item 13 did not verify them and this project does not guess
 * ids.</li>
 * <li>{@link GearEffect#OBSIDIAN_ARMOUR_SET} &mdash; the full three-piece
 * obsidian armour (helmet + platebody + platelegs) OR the Berserker
 * necklace (base or ornament kit) alone. RESEARCH.md &sect;20.1.1 flags the
 * wiki's own table row as ambiguous about which subset is required, so this
 * detects either reading rather than picking one arbitrarily; since the
 * effect is never applied to a max hit (only surfaced as "not applied"),
 * over-detecting costs nothing.</li>
 * <li>{@link GearEffect#SLAYER_HELMET} ({@code SLAYER_HELM}, unimbued) and
 * {@link GearEffect#SLAYER_HELMET_IMBUED} ({@code SLAYER_HELM_I}).</li>
 * <li>{@link GearEffect#CHAOS_GAUNTLETS} ({@code GAUNTLETS_OF_CHAOS}).</li>
 * <li>{@link GearEffect#TOME_OF_FIRE} (charged {@code TOME_OF_FIRE} only;
 * the uncharged variant is not treated as carrying the effect).</li>
 * <li>{@link GearEffect#TOXIC_BLOWPIPE} ({@code TOXIC_BLOWPIPE},
 * {@code TOXIC_BLOWPIPE_LOADED}) &mdash; final whole-branch review fix. An
 * ornament-kit-recoloured variant exists in gameval ({@code
 * TOXIC_BLOWPIPE_ORNAMENT}/{@code TOXIC_BLOWPIPE_LOADED_ORNAMENT}) but is
 * deliberately not wired in here: the controller's ruling scoped this fix
 * to exactly the two named ids, cross-verified against the real jar, and
 * this project does not widen a scoped fix on its own initiative.</li>
 * </ul>
 *
 * <b>Deliberately NOT detected:</b> Salve amulet, Salve amulet (e), Salve
 * amulet (i), and Salve amulet (ei) are never detected at all, at any id.
 * {@code runelite-api.md} item 13 flags every candidate id for these as an
 * unresolved collision with unrelated gameval items (a Nightmare Zone
 * reward-shop copy, a crystal shard necklace, etc.) &mdash; cross-checking
 * those same ids against the real jar in this session confirmed the
 * collision. Detecting a wrong item as Salve would be worse than not
 * detecting it, so {@link GearEffect#SALVE_AMULET}, {@link
 * GearEffect#SALVE_AMULET_E}, {@link GearEffect#SALVE_AMULET_I}, and {@link
 * GearEffect#SALVE_AMULET_EI} exist on the {@link GearEffect} enum (§20 does
 * name a multiplier for them) but this method can never return them.
 *
 * Every other {@link GearEffect} constant (Obsidian weapon specifically,
 * Blisterwood/Ivandis flail, Viggora's/Ursine chainmace, Inquisitor's
 * armour, Colossal blade, Osmumten's fang, Keris, every weapon special,
 * Craw's/Webweaver bow, Twisted bow, chinchompas, dark bow, Zaryte
 * crossbow, bolt specials, Castle wars bracelet, Charge, Shadow Bonus,
 * Avarice, Smoke battlestaff/Twinflame staff, Virtus robes, Sceptre of the
 * Wilderness, Accursed sceptre special, Tome of water/earth, Mark of
 * Darkness, Ahrim's Damned) has no id verified in item 13 or found in the
 * direct jar check, so none of them can be detected here. A caller that
 * knows one of these is worn (e.g. from the loadout's own item list) may
 * still construct a {@link GearBonuses} with that effect included directly.
 * {@link GearEffect#TUMEKENS_SHADOW_TOA_BONUS} is a different kind of
 * exclusion: it is not an item at all, but ToA-location state, so no item
 * id could ever detect it &mdash; {@link MaxHit#magic} surfaces it directly
 * from the wielded weapon, not from this class.
 */
public final class GearEffects
{
	// Void Knight equipment: base, elite, and trouver-locked variants.
	// Base top/robe/gloves.
	private static final int VOID_TOP = ItemID.PEST_VOID_KNIGHT_TOP;
	private static final int VOID_TOP_TROUVER = ItemID.PEST_VOID_KNIGHT_TOP_TROUVER;
	private static final int VOID_ROBE = ItemID.PEST_VOID_KNIGHT_ROBES;
	private static final int VOID_ROBE_TROUVER = ItemID.PEST_VOID_KNIGHT_ROBES_TROUVER;
	private static final int VOID_GLOVES = ItemID.PEST_VOID_KNIGHT_GLOVES;
	private static final int VOID_GLOVES_TROUVER = ItemID.PEST_VOID_KNIGHT_GLOVES_TROUVER;
	// Elite top/robe.
	private static final int ELITE_TOP = ItemID.ELITE_VOID_KNIGHT_TOP;
	private static final int ELITE_TOP_TROUVER = ItemID.ELITE_VOID_KNIGHT_TOP_TROUVER;
	private static final int ELITE_ROBE = ItemID.ELITE_VOID_KNIGHT_ROBES;
	private static final int ELITE_ROBE_TROUVER = ItemID.ELITE_VOID_KNIGHT_ROBES_TROUVER;
	// Style helms, shared by base and elite sets.
	private static final int MAGE_HELM = ItemID.GAME_PEST_MAGE_HELM;
	private static final int MAGE_HELM_TROUVER = ItemID.GAME_PEST_MAGE_HELM_TROUVER;
	private static final int RANGER_HELM = ItemID.GAME_PEST_ARCHER_HELM;
	private static final int RANGER_HELM_TROUVER = ItemID.GAME_PEST_ARCHER_HELM_TROUVER;
	private static final int MELEE_HELM = ItemID.GAME_PEST_MELEE_HELM;
	private static final int MELEE_HELM_TROUVER = ItemID.GAME_PEST_MELEE_HELM_TROUVER;

	// Dharok's the Wretched's equipment, full set.
	private static final int DHAROK_HEAD = ItemID.BARROWS_DHAROK_HEAD;
	private static final int DHAROK_WEAPON = ItemID.BARROWS_DHAROK_WEAPON;
	private static final int DHAROK_BODY = ItemID.BARROWS_DHAROK_BODY;
	private static final int DHAROK_LEGS = ItemID.BARROWS_DHAROK_LEGS;

	// Obsidian armour set + Berserker necklace (ambiguous which subset counts).
	private static final int OBSIDIAN_HELMET = ItemID.OBSIDIAN_HELMET;
	private static final int OBSIDIAN_PLATEBODY = ItemID.OBSIDIAN_PLATEBODY;
	private static final int OBSIDIAN_PLATELEGS = ItemID.OBSIDIAN_PLATELEGS;
	private static final int BERSERKER_NECKLACE = ItemID.JEWL_BESERKER_NECKLACE;
	private static final int BERSERKER_NECKLACE_ORNAMENT = ItemID.JEWL_BESERKER_NECKLACE_ORNAMENT;

	// Slayer helmet, unimbued and imbued.
	private static final int SLAYER_HELM = ItemID.SLAYER_HELM;
	private static final int SLAYER_HELM_IMBUED = ItemID.SLAYER_HELM_I;

	// Single-item effects.
	private static final int CHAOS_GAUNTLETS_ID = ItemID.GAUNTLETS_OF_CHAOS;
	private static final int TOME_OF_FIRE_ID = ItemID.TOME_OF_FIRE;

	// Toxic blowpipe, unloaded and loaded. Both ids verified by
	// `javap -constants -p net.runelite.api.gameval.ItemID` against
	// runelite-api-1.12.37.jar (final whole-branch review fix, I-1):
	//   public static final int TOXIC_BLOWPIPE = 12924;
	//   public static final int TOXIC_BLOWPIPE_LOADED = 12926;
	private static final int TOXIC_BLOWPIPE_ID = ItemID.TOXIC_BLOWPIPE;
	private static final int TOXIC_BLOWPIPE_LOADED_ID = ItemID.TOXIC_BLOWPIPE_LOADED;

	private GearEffects()
	{
	}

	/**
	 * Detects every {@link GearEffect} this class knows how to recognise
	 * from a collection of worn item ids (typically the equipment slots'
	 * item ids, in any order, duplicates ignored).
	 *
	 * Void requires the full set: top, robe, gloves, and the matching
	 * style's helm, all at once (mixing a base top with an elite robe, or
	 * missing the gloves, detects nothing). Elite top/robe count as the set
	 * for the elite variants; base and elite pieces do not mix.
	 */
	public static Set<GearEffect> detect(Collection<Integer> wornItemIds)
	{
		Set<Integer> ids = new HashSet<>(wornItemIds);
		Set<GearEffect> effects = EnumSet.noneOf(GearEffect.class);

		boolean hasBaseTop = ids.contains(VOID_TOP) || ids.contains(VOID_TOP_TROUVER);
		boolean hasBaseRobe = ids.contains(VOID_ROBE) || ids.contains(VOID_ROBE_TROUVER);
		boolean hasEliteTop = ids.contains(ELITE_TOP) || ids.contains(ELITE_TOP_TROUVER);
		boolean hasEliteRobe = ids.contains(ELITE_ROBE) || ids.contains(ELITE_ROBE_TROUVER);
		boolean hasGloves = ids.contains(VOID_GLOVES) || ids.contains(VOID_GLOVES_TROUVER);
		boolean hasMeleeHelm = ids.contains(MELEE_HELM) || ids.contains(MELEE_HELM_TROUVER);
		boolean hasRangerHelm = ids.contains(RANGER_HELM) || ids.contains(RANGER_HELM_TROUVER);
		boolean hasMageHelm = ids.contains(MAGE_HELM) || ids.contains(MAGE_HELM_TROUVER);

		if (hasBaseTop && hasBaseRobe && hasGloves)
		{
			if (hasMeleeHelm)
			{
				effects.add(GearEffect.VOID_MELEE);
			}
			if (hasRangerHelm)
			{
				effects.add(GearEffect.VOID_RANGED);
			}
			// A plain void mage helm carries no listed bonus (RESEARCH.md
			// §20.3.1) — deliberately not added here.
		}

		if (hasEliteTop && hasEliteRobe && hasGloves)
		{
			if (hasMeleeHelm)
			{
				// §20.1 gives no elite-melee multiplier: flagged unmodelled.
				effects.add(GearEffect.ELITE_VOID_MELEE);
			}
			if (hasRangerHelm)
			{
				effects.add(GearEffect.ELITE_VOID_RANGED);
			}
			if (hasMageHelm)
			{
				effects.add(GearEffect.ELITE_VOID_MAGE);
			}
		}

		if (ids.contains(DHAROK_HEAD) && ids.contains(DHAROK_WEAPON)
			&& ids.contains(DHAROK_BODY) && ids.contains(DHAROK_LEGS))
		{
			effects.add(GearEffect.DHAROKS_EQUIPMENT);
		}

		boolean fullObsidianArmour = ids.contains(OBSIDIAN_HELMET) && ids.contains(OBSIDIAN_PLATEBODY)
			&& ids.contains(OBSIDIAN_PLATELEGS);
		boolean berserkerNecklace = ids.contains(BERSERKER_NECKLACE) || ids.contains(BERSERKER_NECKLACE_ORNAMENT);
		if (fullObsidianArmour || berserkerNecklace)
		{
			effects.add(GearEffect.OBSIDIAN_ARMOUR_SET);
		}

		if (ids.contains(SLAYER_HELM))
		{
			effects.add(GearEffect.SLAYER_HELMET);
		}
		if (ids.contains(SLAYER_HELM_IMBUED))
		{
			effects.add(GearEffect.SLAYER_HELMET_IMBUED);
		}

		if (ids.contains(CHAOS_GAUNTLETS_ID))
		{
			effects.add(GearEffect.CHAOS_GAUNTLETS);
		}

		if (ids.contains(TOME_OF_FIRE_ID))
		{
			effects.add(GearEffect.TOME_OF_FIRE);
		}

		if (ids.contains(TOXIC_BLOWPIPE_ID) || ids.contains(TOXIC_BLOWPIPE_LOADED_ID))
		{
			effects.add(GearEffect.TOXIC_BLOWPIPE);
		}

		return effects;
	}
}
