package com.vinnyvangogh.questpath;

/**
 * How a recommended teleport should appear on the Grand Exchange list: as
 * the tablet, or as the runes to cast the spell.
 *
 * <p>Both get you to Camelot. Which one a main buys is a matter of price on
 * the day and whether the account can cast the spell at all, so it is the
 * player's call and never questpath's -- see {@link
 * TeleportEquivalence#decompose} for the one rule that overrides it: an
 * account below the spell's Magic level is shown the tablet whatever this
 * says, because runes it cannot cast are not a teleport.
 *
 * <p>Only the Grand Exchange list reads this. The walking shop route always
 * decomposes to runes, because no shop sells teleport tablets and a rune
 * shop is somewhere to walk to.
 */
public enum TeleportForm
{
	TABLET("Teleport tablets"),
	RUNES("Runes to cast it");

	private final String label;

	TeleportForm(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
