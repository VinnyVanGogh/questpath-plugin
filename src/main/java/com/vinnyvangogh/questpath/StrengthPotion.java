package com.vinnyvangogh.questpath;

/**
 * The strength-boosting potions RESEARCH.md &sect;20.1's melee table lists,
 * and the bonus each adds to effective strength before the prayer multiplier
 * is applied.
 *
 * <p>Unlike {@link StrengthPrayer}'s flat percentage, a potion's bonus is
 * itself level-dependent -- {@code floor(Str &times; percent) + flat} -- so
 * each constant carries both halves of that formula rather than a single
 * number, and {@link #bonus(int)} takes the level it applies against.
 *
 * <p>Only the four potions RESEARCH.md's fetch actually recorded. Divine
 * variants and Overload were not on that page's table and are not modelled
 * here rather than guessed at -- see this project's standing rule against a
 * game formula sourced from memory.
 */
public enum StrengthPotion
{
	/** No potion active. */
	NONE(0, 0, "None"),
	/** {@code floor(Str &times; 10%) + 3}. RESEARCH.md &sect;20.1. */
	STRENGTH_POTION(10, 3, "Strength potion"),
	/** {@code floor(Str &times; 12%) + 2}. RESEARCH.md &sect;20.1. */
	ZAMORAK_BREW(12, 2, "Zamorak brew"),
	/** {@code floor(Str &times; 15%) + 4}. RESEARCH.md &sect;20.1. */
	BLACK_WARLOCK(15, 4, "Black warlock"),
	/** {@code floor(Str &times; 15%) + 5}. RESEARCH.md &sect;20.1. */
	SUPER_STRENGTH_POTION(15, 5, "Super strength potion");

	private final int percent;
	private final int flat;
	private final String label;

	StrengthPotion(int percent, int flat, String label)
	{
		this.percent = percent;
		this.flat = flat;
		this.label = label;
	}

	/**
	 * The bonus this potion adds to the given strength level, per
	 * RESEARCH.md &sect;20.1's {@code floor(Str &times; percent) + flat}.
	 * Zero for {@link #NONE} at any level, by construction.
	 */
	public int bonus(int strengthLevel)
	{
		return (strengthLevel * percent) / 100 + flat;
	}

	/** The human label used verbatim in the UI. */
	public String label()
	{
		return label;
	}
}
