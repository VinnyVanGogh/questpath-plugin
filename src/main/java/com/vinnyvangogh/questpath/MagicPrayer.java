package com.vinnyvangogh.questpath;

/**
 * The magic-book prayers, and the percentage bonus RESEARCH.md &sect;20.3.1
 * adds into Primary Magic Damage's multiplier sum (floored once, alongside
 * Void).
 *
 * {@link #MYSTIC_WILL} is included even though it does not affect damage:
 * it is a real prayer the client reports and a loadout can hold, but
 * &sect;20.3.1's damage table lists only Mystic Lore/Might/Vigour and
 * Augury &mdash; Mystic Will boosts magic defence, not damage, so its
 * multiplier here is a documented no-op rather than an omission.
 */
public enum MagicPrayer
{
	/** No prayer active. */
	NONE(0, "None"),
	/** Boosts magic defence only; RESEARCH.md &sect;20.3.1 gives it no damage bonus. */
	MYSTIC_WILL(0, "Mystic Will"),
	/** +1%. RESEARCH.md &sect;20.3.1. */
	MYSTIC_LORE(1, "Mystic Lore"),
	/** +2%. RESEARCH.md &sect;20.3.1. */
	MYSTIC_MIGHT(2, "Mystic Might"),
	/** +3%. RESEARCH.md &sect;20.3.1. */
	MYSTIC_VIGOUR(3, "Mystic Vigour"),
	/** +4%. RESEARCH.md &sect;20.3.1. */
	AUGURY(4, "Augury");

	private final int percentBonus;
	private final String label;

	MagicPrayer(int percentBonus, String label)
	{
		this.percentBonus = percentBonus;
		this.label = label;
	}

	/** The whole-percent bonus this prayer adds into Primary Magic Damage's multiplier sum. */
	public int percentBonus()
	{
		return percentBonus;
	}

	/** The human label used verbatim in the UI. */
	public String label()
	{
		return label;
	}
}
