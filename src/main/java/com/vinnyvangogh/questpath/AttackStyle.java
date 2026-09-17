package com.vinnyvangogh.questpath;

/**
 * The four melee attack styles, and the flat style bonus RESEARCH.md
 * &sect;20.1 adds to effective strength before the {@code +8} and the
 * floor.
 */
public enum AttackStyle
{
	/** No style bonus. RESEARCH.md &sect;20.1. */
	ACCURATE(0, "Accurate"),
	/** +3 style bonus. RESEARCH.md &sect;20.1. */
	AGGRESSIVE(3, "Aggressive"),
	/** +1 style bonus. RESEARCH.md &sect;20.1. */
	CONTROLLED(1, "Controlled"),
	/** No style bonus, same as Accurate. RESEARCH.md &sect;20.1. */
	DEFENSIVE(0, "Defensive");

	private final int styleBonus;
	private final String label;

	AttackStyle(int styleBonus, String label)
	{
		this.styleBonus = styleBonus;
		this.label = label;
	}

	/** The flat bonus added to effective strength before the {@code +8} and the floor. */
	public int styleBonus()
	{
		return styleBonus;
	}

	/** The human label used verbatim in the UI. */
	public String label()
	{
		return label;
	}
}
