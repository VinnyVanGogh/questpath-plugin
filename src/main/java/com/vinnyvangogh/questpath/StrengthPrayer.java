package com.vinnyvangogh.questpath;

/**
 * The offensive strength prayers, and the percentage bonus RESEARCH.md
 * &sect;20.1 multiplies into effective strength (floored) before the style
 * bonus and {@code +8} are added.
 */
public enum StrengthPrayer
{
	/** No prayer active. */
	NONE(0, "None"),
	/** +5%. RESEARCH.md &sect;20.1. */
	BURST_OF_STRENGTH(5, "Burst of Strength"),
	/** +10%. RESEARCH.md &sect;20.1. */
	SUPERHUMAN_STRENGTH(10, "Superhuman Strength"),
	/** +15%. RESEARCH.md &sect;20.1. */
	ULTIMATE_STRENGTH(15, "Ultimate Strength"),
	/** +18%. RESEARCH.md &sect;20.1. */
	CHIVALRY(18, "Chivalry"),
	/** +23%. RESEARCH.md &sect;20.1. */
	PIETY(23, "Piety");

	private final int percentBonus;
	private final String label;

	StrengthPrayer(int percentBonus, String label)
	{
		this.percentBonus = percentBonus;
		this.label = label;
	}

	/** The whole-percent bonus this prayer multiplies into strength level, before flooring. */
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
