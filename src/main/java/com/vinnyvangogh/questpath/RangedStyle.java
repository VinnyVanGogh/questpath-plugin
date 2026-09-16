package com.vinnyvangogh.questpath;

/**
 * The three ranged attack styles, and the flat "Atk Style" bonus
 * RESEARCH.md &sect;20.2 adds to effective ranged strength before the
 * {@code +8} and the floor.
 */
public enum RangedStyle
{
	/** +3 Atk Style bonus. RESEARCH.md &sect;20.2. */
	ACCURATE(3, "Accurate"),
	/** No Atk Style bonus. RESEARCH.md &sect;20.2. */
	RAPID(0, "Rapid"),
	/** No Atk Style bonus. RESEARCH.md &sect;20.2. */
	LONGRANGE(0, "Longrange");

	private final int atkStyleBonus;
	private final String label;

	RangedStyle(int atkStyleBonus, String label)
	{
		this.atkStyleBonus = atkStyleBonus;
		this.label = label;
	}

	/** The flat "Atk Style" bonus added to effective ranged strength before the {@code +8} and the floor. */
	public int atkStyleBonus()
	{
		return atkStyleBonus;
	}

	/** The human label used verbatim in the UI. */
	public String label()
	{
		return label;
	}
}
