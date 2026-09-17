package com.vinnyvangogh.questpath;

/**
 * Every gear- or state-driven max-hit multiplier named anywhere in
 * RESEARCH.md &sect;20 (Maximum hit formulas), across all three combat
 * styles.
 *
 * v1's scope, per &sect;20.6, is base level + style bonus + offensive prayer
 * + Void/Elite Void only. Every other row on this list is real and verified
 * against the wiki, but its multiplier is never multiplied into a {@link
 * MaxHit} result &mdash; it exists so {@link MaxHitResult#notApplied()} can
 * name it instead of silently dropping it, e.g. "Dharok's set: not applied".
 * {@link #modelled()} is the switch between the two: {@code true} only for
 * the five void/elite-void entries &sect;20.1/20.2/20.3.1 give a settled
 * multiplier for (plain Void melee, plain Void ranged, Elite Void ranged,
 * Elite Void mage). Plain (non-elite) Void mage carries no separate constant
 * at all: &sect;20.3.1 states outright "no listed bonus for plain (non-elite)
 * void mage", so there is nothing to enumerate for it. Elite Void melee is
 * enumerated but deliberately {@code modelled() == false}: &sect;20.1 gives
 * no elite-melee multiplier and explicitly warns not to assume it equals the
 * ranged/magic elite value.
 *
 * {@link GearEffects#detect(java.util.Collection)} can only recognise a
 * fraction of these &mdash; only the ones whose item ids are verified in
 * {@code runelite-api.md} item 13. See that class's javadoc for exactly
 * which ones.
 */
public enum GearEffect
{
	/** Full Void melee armour (top, robe, gloves, melee helm): &times;1.1 on effective strength. RESEARCH.md &sect;20.1. */
	VOID_MELEE(true, "Void melee armour"),
	/** Elite Void top/robe + gloves + melee helm: &sect;20.1 gives no elite-melee multiplier at all, so this is left unmodelled rather than assumed. */
	ELITE_VOID_MELEE(false, "Elite void melee armour"),
	/** Full Void ranged armour (top, robe, gloves, ranger helm): &times;1.1 on effective ranged strength. RESEARCH.md &sect;20.2. */
	VOID_RANGED(true, "Void ranged armour"),
	/** Elite Void top/robe + gloves + ranger helm: &times;1.125 on effective ranged strength. RESEARCH.md &sect;20.2. */
	ELITE_VOID_RANGED(true, "Elite void ranged armour"),
	/** Elite Void top/robe + gloves + mage helm: +5% primary magic damage. RESEARCH.md &sect;20.3.1. */
	ELITE_VOID_MAGE(true, "Elite void mage armour"),
	/** Obsidian armour (helmet/platebody/platelegs) + obsidian weapon + Berserker necklace, &times;1.3; the wiki does not settle which subset suffices. RESEARCH.md &sect;20.1.1. */
	OBSIDIAN_ARMOUR_SET(false, "Obsidian armour set"),
	/** Full Dharok the Wretched's equipment: 1 + (MaxHP-CurrentHP)/100 &times; MaxHP/100. RESEARCH.md &sect;20.1.1. */
	DHAROKS_EQUIPMENT(false, "Dharok's set"),
	/** &times;1.2 melee vs. the Castle Wars flag bearer, or +20% magic post-hit-roll vs. the same target. RESEARCH.md &sect;20.1.1, &sect;20.3.1. */
	CASTLE_WARS_BRACELET(false, "Castle wars bracelet"),
	/** &times;1.25 vs. vampyres. RESEARCH.md &sect;20.1.1. */
	BLISTERWOOD_FLAIL(false, "Blisterwood flail"),
	/** &times;1.20 vs. vampyres. RESEARCH.md &sect;20.1.1. */
	IVANDIS_FLAIL(false, "Ivandis flail"),
	/** Viggora's chainmace / Ursine chainmace, charged, in the Wilderness: &times;1.5. RESEARCH.md &sect;20.1.1. */
	WILDERNESS_CHAINMACE(false, "Viggora's chainmace / Ursine chainmace"),
	/** Black mask / Slayer helmet (unimbued), on the matching slayer task: &times;7/6 melee. RESEARCH.md &sect;20.1.1. */
	SLAYER_HELMET(false, "Slayer helmet"),
	/** Vs. undead: &times;7/6 melee. RESEARCH.md &sect;20.1.1. Ids not detected: see {@link GearEffects}. */
	SALVE_AMULET(false, "Salve amulet"),
	/** Vs. undead: &times;1.2 melee. RESEARCH.md &sect;20.1.1. Ids not detected: see {@link GearEffects}. */
	SALVE_AMULET_E(false, "Salve amulet (e)"),
	/** Crush attack style only: &times;1.025. RESEARCH.md &sect;20.1.1. */
	INQUISITORS_ARMOUR(false, "Inquisitor's armour"),
	/** +(2 &times; target size), additive not multiplicative. RESEARCH.md &sect;20.1.1. */
	COLOSSAL_BLADE(false, "Colossal blade"),
	/** &times;0.85 with a raised minimum hit (0.15&times; base) to compensate. RESEARCH.md &sect;20.1.1. */
	OSMUMTENS_FANG(false, "Osmumten's fang"),
	/** Vs. Kalphites/Scarabites: &times;1.33 normally, &times;1.33&times;3 on a critical roll. RESEARCH.md &sect;20.1.1. */
	KERIS_PARTISAN(false, "Keris / Keris partisan"),
	/** Special attack, &times;1.375, while active. RESEARCH.md &sect;20.1.1. Specials are never applied in v1. */
	ARMADYL_GODSWORD_SPECIAL(false, "Armadyl godsword special"),
	/** Special attack, &times;1.21, while active. RESEARCH.md &sect;20.1.1. */
	BANDOS_GODSWORD_SPECIAL(false, "Bandos godsword special"),
	/** Special attack, &times;1.1, while active. RESEARCH.md &sect;20.1.1. */
	ANCIENT_SARADOMIN_ZAMORAK_GODSWORD_SPECIAL(false, "Ancient/Saradomin/Zamorak godsword special"),
	/** Special attack, &times;1.15 as two separate hits, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_DAGGER_SPECIAL(false, "Dragon dagger special"),
	/** Special attack, &times;1.1, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_HALBERD_SPECIAL(false, "Dragon halberd / Crystal halberd special"),
	/** Special attack, 1.0 + 0.5 &times; special-bar%, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_HASTA_SPECIAL(false, "Dragon hasta special"),
	/** Special attack, &times;1.25, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_LONGSWORD_SPECIAL(false, "Dragon longsword special"),
	/** Special attack, &times;1.5, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_MACE_SPECIAL(false, "Dragon mace special"),
	/** Special attack, &times;1.5, while active. RESEARCH.md &sect;20.1.1. */
	DRAGON_WARHAMMER_SPECIAL(false, "Dragon warhammer special"),
	/** Special attack, &times;1.1, while active. RESEARCH.md &sect;20.1.1. */
	RUNE_CLAWS_SPECIAL(false, "Rune claws special"),
	/** Special attack, &times;0.85, while active. RESEARCH.md &sect;20.1.1. */
	ABYSSAL_DAGGER_SPECIAL(false, "Abyssal dagger special"),
	/** Special attack, 1.0 + 0.005 &times; prayer points missing, while active. RESEARCH.md &sect;20.1.1. */
	ABYSSAL_BLUDGEON_SPECIAL(false, "Abyssal bludgeon special"),
	/** Special attack, &times;1.1 on the main hit plus a flat +16 magic-based component, while active. RESEARCH.md &sect;20.1.1. */
	SARADOMIN_SWORD_SPECIAL(false, "Saradomin sword special"),
	/** In the Wilderness: &times;1.5; stacks additively with Slayer helm(i) and multiplicatively with Salve (i)/(ei). RESEARCH.md &sect;20.2.1. */
	WILDERNESS_BOW(false, "Craw's bow / Webweaver bow"),
	/** Opponent-dependent, not a constant multiplier; the wiki explicitly special-cases this rather than giving a plain scalar. RESEARCH.md &sect;20.2.1. */
	TWISTED_BOW(false, "Twisted bow"),
	/** Not covered by the fetched `Maximum ranged hit` page's mechanics at all. RESEARCH.md &sect;20.2. */
	CHINCHOMPA(false, "Chinchompa"),
	/** Dark bow with dragon arrows: &times;1.5, step-three bonus damage. RESEARCH.md &sect;20.2.1. */
	DARK_BOW_DRAGON_ARROWS(false, "Dark bow (dragon arrows)"),
	/** Dark bow with any other arrows: &times;1.3, step-three bonus damage. RESEARCH.md &sect;20.2.1. */
	DARK_BOW_OTHER_ARROWS(false, "Dark bow (other ammo)"),
	/** Zaryte crossbow with enchanted bolts: &times;1.1. RESEARCH.md &sect;20.2.1. */
	ZARYTE_CROSSBOW_ENCHANTED_BOLTS(false, "Zaryte crossbow (enchanted bolts)"),
	/** Bolt special effect, &times;1.15. RESEARCH.md &sect;20.2.1. */
	DIAMOND_BOLTS_E(false, "Diamond bolts (e)"),
	/** Bolt special effect, &times;1.45. RESEARCH.md &sect;20.2.1. */
	DRAGONSTONE_BOLTS_E(false, "Dragonstone bolts (e)"),
	/** Bolt special effect, &times;1.15. RESEARCH.md &sect;20.2.1. */
	ONYX_BOLTS_E(false, "Onyx bolts (e)"),
	/** Bolt special effect, &times;1.25. RESEARCH.md &sect;20.2.1. */
	OPAL_BOLTS_E(false, "Opal bolts (e)"),
	/** Black mask (i) / Slayer helm (i), on the matching slayer task: &times;1.15 ranged, +15% magic pre-hit-roll. RESEARCH.md &sect;20.2.1, &sect;20.3.1. */
	SLAYER_HELMET_IMBUED(false, "Slayer helmet (i)"),
	/** Vs. undead: &times;7/6 ranged, +15% magic. RESEARCH.md &sect;20.2.1, &sect;20.3.1. Ids not detected: see {@link GearEffects}. */
	SALVE_AMULET_I(false, "Salve amulet (i)"),
	/** Vs. undead: &times;1.2 ranged, +20% magic. RESEARCH.md &sect;20.2.1, &sect;20.3.1. Ids not detected: see {@link GearEffects}. */
	SALVE_AMULET_EI(false, "Salve amulet (ei)"),
	/** +3 base damage modifier, only while casting a bolt spell. RESEARCH.md &sect;20.3.1. */
	CHAOS_GAUNTLETS(false, "Chaos gauntlets"),
	/** God spell cast with the matching god cape: +10 base damage modifier. RESEARCH.md &sect;20.3.1. */
	CHARGE_GOD_CAPE(false, "Charge (god spell + god cape)"),
	/** Tumeken's shadow's own passive: &times;4 inside ToA, &times;3 outside, capped at +100% of visible bonuses. RESEARCH.md &sect;20.3.1. */
	SHADOW_BONUS(false, "Tumeken's shadow passive"),
	/** Vs. revenants: +20% (+35% with Forinthry surge). RESEARCH.md &sect;20.3.1. */
	AVARICE(false, "Amulet of avarice"),
	/** Standard spellbook only: +10% primary magic damage. RESEARCH.md &sect;20.3.1. */
	SMOKE_BATTLESTAFF(false, "Smoke battlestaff / Mystic smoke staff / Twinflame staff"),
	/** Bolt/blast/wave spells: +40% as a separate second hit, post-hit-roll. RESEARCH.md &sect;20.3.1. */
	TWINFLAME_STAFF_SECOND_HIT(false, "Twinflame staff second hit"),
	/** Ancient Magicks only: +3% per piece. RESEARCH.md &sect;20.3.1. */
	VIRTUS_ROBES(false, "Virtus robes"),
	/** Thammaron's / Accursed sceptre, charged, autocast included, in the Wilderness: +50% pre-hit-roll. RESEARCH.md &sect;20.3.1. */
	SCEPTRE_OF_THE_WILDERNESS(false, "Thammaron's / Accursed sceptre"),
	/** Accursed sceptre special attack: +50% pre-hit-roll. RESEARCH.md &sect;20.3.1. */
	ACCURSED_SCEPTRE_SPECIAL(false, "Accursed sceptre special attack"),
	/** Fire spell, standard spellbook: +10% (PvP +50%) pre-hit-roll. RESEARCH.md &sect;20.3.1. */
	TOME_OF_FIRE(false, "Tome of fire"),
	/** Water spell, standard spellbook: +10% (PvP +20%) pre-hit-roll. RESEARCH.md &sect;20.3.1. */
	TOME_OF_WATER(false, "Tome of water"),
	/** Earth spell, standard spellbook: +10% pre-hit-roll. RESEARCH.md &sect;20.3.1. */
	TOME_OF_EARTH(false, "Tome of earth"),
	/** +25% (Purging staff +50%) post-hit-roll, scaled again by the target's demonbane effectiveness. RESEARCH.md &sect;20.3.1. */
	MARK_OF_DARKNESS_DEMONBANE(false, "Mark of Darkness demonbane"),
	/** Ahrim's set + amulet of the damned: 25% chance of +30% post-hit-roll. RESEARCH.md &sect;20.3.1. */
	AHRIMS_DAMNED(false, "Ahrim's Damned");

	private final boolean modelled;
	private final String label;

	GearEffect(boolean modelled, String label)
	{
		this.modelled = modelled;
		this.label = label;
	}

	/**
	 * Whether {@link MaxHit} actually multiplies this effect into its
	 * result. {@code false} for every entry except the settled void/elite
	 * void multipliers &mdash; see the class javadoc for exactly which ones
	 * and why.
	 */
	public boolean modelled()
	{
		return modelled;
	}

	/** The human label used verbatim in the UI, e.g. "Dharok's set". */
	public String label()
	{
		return label;
	}
}
