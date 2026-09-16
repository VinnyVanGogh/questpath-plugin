package com.vinnyvangogh.questpath;

/**
 * The offensive ranged prayers, and the percentage bonus RESEARCH.md
 * &sect;20.2 multiplies into effective ranged strength (floored) before the
 * Atk Style bonus and {@code +8} are added.
 */
public enum RangedPrayer
{
	/** No prayer active. */
	NONE(0, "None"),
	/** +5%. RESEARCH.md &sect;20.2. */
	SHARP_EYE(5, "Sharp Eye"),
	/** +10%. RESEARCH.md &sect;20.2. */
	HAWK_EYE(10, "Hawk Eye"),
	/** +15%. RESEARCH.md &sect;20.2. */
	EAGLE_EYE(15, "Eagle Eye"),
	/** +18%. RESEARCH.md &sect;20.2. */
	DEADEYE(18, "Deadeye"),
	/** +23%. RESEARCH.md &sect;20.2. */
	RIGOUR(23, "Rigour");

	private final int percentBonus;
	private final String label;

	RangedPrayer(int percentBonus, String label)
	{
		this.percentBonus = percentBonus;
		this.label = label;
	}

	/** The whole-percent bonus this prayer multiplies into ranged level, before flooring. */
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
