package com.vinnyvangogh.questpath;

/**
 * Every combat spell RESEARCH.md &sect;20.3 gives a base max hit for, plus
 * the {@link #POWERED_STAFF} sentinel for a weapon that computes its own
 * max hit from the caster's Magic level instead of casting a spell (see
 * &sect;20.4, resolved in {@link MaxHit#magic}).
 *
 * Magic Dart is deliberately absent: &sect;20.3 says its max hit scales
 * with Magic level like the elemental tiers, but does not print the
 * formula, and this project does not guess one.
 */
public enum Spell
{
	// Standard spellbook.
	/** Base max hit 8. RESEARCH.md &sect;20.3. */
	WIND_STRIKE(8, "Wind Strike"),
	/** Base max hit 8. RESEARCH.md &sect;20.3. */
	WATER_STRIKE(8, "Water Strike"),
	/** Base max hit 8. RESEARCH.md &sect;20.3. */
	EARTH_STRIKE(8, "Earth Strike"),
	/** Base max hit 8. RESEARCH.md &sect;20.3. */
	FIRE_STRIKE(8, "Fire Strike"),
	/** Base max hit 12. RESEARCH.md &sect;20.3. */
	WIND_BOLT(12, "Wind Bolt"),
	/** Base max hit 12. RESEARCH.md &sect;20.3. */
	WATER_BOLT(12, "Water Bolt"),
	/** Base max hit 12. RESEARCH.md &sect;20.3. */
	EARTH_BOLT(12, "Earth Bolt"),
	/** Base max hit 12. RESEARCH.md &sect;20.3. */
	FIRE_BOLT(12, "Fire Bolt"),
	/** Base max hit 15. RESEARCH.md &sect;20.3. */
	CRUMBLE_UNDEAD(15, "Crumble Undead"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	WIND_BLAST(16, "Wind Blast"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	WATER_BLAST(16, "Water Blast"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	EARTH_BLAST(16, "Earth Blast"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	FIRE_BLAST(16, "Fire Blast"),
	/** Base max hit 25. RESEARCH.md &sect;20.3. */
	IBAN_BLAST(25, "Iban Blast"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	SARADOMIN_STRIKE(20, "Saradomin Strike"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	CLAWS_OF_GUTHIX(20, "Claws of Guthix"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	FLAMES_OF_ZAMORAK(20, "Flames of Zamorak"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	WIND_WAVE(20, "Wind Wave"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	WATER_WAVE(20, "Water Wave"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	EARTH_WAVE(20, "Earth Wave"),
	/** Base max hit 20. RESEARCH.md &sect;20.3. */
	FIRE_WAVE(20, "Fire Wave"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	WIND_SURGE(24, "Wind Surge"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	WATER_SURGE(24, "Water Surge"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	EARTH_SURGE(24, "Earth Surge"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	FIRE_SURGE(24, "Fire Surge"),

	// Ancient Magicks.
	/** Base max hit 13. RESEARCH.md &sect;20.3. */
	SMOKE_RUSH(13, "Smoke Rush"),
	/** Base max hit 14. RESEARCH.md &sect;20.3. */
	SHADOW_RUSH(14, "Shadow Rush"),
	/** Base max hit 15. RESEARCH.md &sect;20.3. */
	BLOOD_RUSH(15, "Blood Rush"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	ICE_RUSH(16, "Ice Rush"),
	/** Base max hit 17. RESEARCH.md &sect;20.3. */
	SMOKE_BURST(17, "Smoke Burst"),
	/** Base max hit 18. RESEARCH.md &sect;20.3. */
	SHADOW_BURST(18, "Shadow Burst"),
	/** Base max hit 21. RESEARCH.md &sect;20.3. */
	BLOOD_BURST(21, "Blood Burst"),
	/** Base max hit 22. RESEARCH.md &sect;20.3. */
	ICE_BURST(22, "Ice Burst"),
	/** Base max hit 23. RESEARCH.md &sect;20.3. */
	SMOKE_BLITZ(23, "Smoke Blitz"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	SHADOW_BLITZ(24, "Shadow Blitz"),
	/** Base max hit 25. RESEARCH.md &sect;20.3. */
	BLOOD_BLITZ(25, "Blood Blitz"),
	/** Base max hit 26. RESEARCH.md &sect;20.3. */
	ICE_BLITZ(26, "Ice Blitz"),
	/** Base max hit 27. RESEARCH.md &sect;20.3. */
	SMOKE_BARRAGE(27, "Smoke Barrage"),
	/** Base max hit 28. RESEARCH.md &sect;20.3. */
	SHADOW_BARRAGE(28, "Shadow Barrage"),
	/** Base max hit 29. RESEARCH.md &sect;20.3. */
	BLOOD_BARRAGE(29, "Blood Barrage"),
	/** Base max hit 30. RESEARCH.md &sect;20.3. */
	ICE_BARRAGE(30, "Ice Barrage"),

	// Arceuus (Demonbane / Grasp).
	/** Base max hit 12. RESEARCH.md &sect;20.3. */
	GHOSTLY_GRASP(12, "Ghostly Grasp"),
	/** Base max hit 17. RESEARCH.md &sect;20.3. */
	SKELETAL_GRASP(17, "Skeletal Grasp"),
	/** Base max hit 24. RESEARCH.md &sect;20.3. */
	UNDEAD_GRASP(24, "Undead Grasp"),
	/** Base max hit 16. RESEARCH.md &sect;20.3. */
	INFERIOR_DEMONBANE(16, "Inferior Demonbane"),
	/** Base max hit 23. RESEARCH.md &sect;20.3. */
	SUPERIOR_DEMONBANE(23, "Superior Demonbane"),
	/** Base max hit 30. RESEARCH.md &sect;20.3. */
	DARK_DEMONBANE(30, "Dark Demonbane"),

	/**
	 * Sentinel: not a spell at all, but a powered staff whose max hit is a
	 * function of the caster's Magic level (RESEARCH.md &sect;20.4). {@link
	 * #baseMaxHit()} throws for this constant; {@link MaxHit#magic} resolves
	 * the actual formula from {@link GearBonuses#poweredStaffItemId()}.
	 */
	POWERED_STAFF(-1, "Powered staff");

	private final int baseMaxHit;
	private final String label;

	Spell(int baseMaxHit, String label)
	{
		this.baseMaxHit = baseMaxHit;
		this.label = label;
	}

	/**
	 * The base max hit RESEARCH.md &sect;20.3's table gives this spell,
	 * before Void/prayer.
	 *
	 * @throws IllegalStateException if called on {@link #POWERED_STAFF},
	 * which has no fixed base and must be resolved from the weapon instead.
	 */
	public int baseMaxHit()
	{
		if (this == POWERED_STAFF)
		{
			throw new IllegalStateException("POWERED_STAFF has no fixed base max hit; resolve it from the weapon item id");
		}
		return baseMaxHit;
	}

	/** True only for the {@link #POWERED_STAFF} sentinel. */
	public boolean isPoweredStaff()
	{
		return this == POWERED_STAFF;
	}

	/** The human label used verbatim in the UI. */
	public String label()
	{
		return label;
	}
}
