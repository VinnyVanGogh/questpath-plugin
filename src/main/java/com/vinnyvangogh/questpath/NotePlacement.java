package com.vinnyvangogh.questpath;

/**
 * Where a {@link FloatingNotes} line appears: over you, over the shopkeeper
 * you are talking to, or both.
 *
 * <p>Over the shopkeeper is the one the plan actually asked for -- "the
 * shopkeeper could congratulate you" -- and it reads as that NPC speaking,
 * which is the intent rather than an accident. Plenty of RuneLite plugins
 * put words over an NPC's head; the voice is the plugin's and nobody is
 * confused about whose it is.
 *
 * <p><b>There is not always a shopkeeper.</b> Items get finished by chopping
 * a tree as often as by buying something, and there is no NPC in the picture
 * then. Rather than showing nothing at those moments -- which would make the
 * feature look broken exactly when it fires -- the shopkeeper options fall
 * back to over the player, and {@link #describesFallback} exists so the
 * setting's own description can say so instead of quietly differing from its
 * label.
 */
public enum NotePlacement
{
	/** Always over the player. */
	ABOVE_YOU("Above you"),

	/** Over the shopkeeper when there is one, over you when there is not. */
	ABOVE_SHOPKEEPER("Above the shopkeeper"),

	/** Both at once, when there is a shopkeeper to speak. */
	BOTH("Both");

	private final String label;

	NotePlacement(String label)
	{
		this.label = label;
	}

	/** Whether a note should be drawn over the player for this setting. */
	boolean overPlayer(boolean haveShopkeeper)
	{
		if (this == ABOVE_YOU || this == BOTH)
		{
			return true;
		}
		// ABOVE_SHOPKEEPER with nobody to speak: over the player is the only
		// place left, and showing nothing would read as the feature failing.
		return !haveShopkeeper;
	}

	/** Whether a note should be drawn over the shopkeeper for this setting. */
	boolean overShopkeeper(boolean haveShopkeeper)
	{
		return haveShopkeeper && (this == ABOVE_SHOPKEEPER || this == BOTH);
	}

	/** Whether this setting can silently land somewhere other than its label. */
	boolean describesFallback()
	{
		return this != ABOVE_YOU;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
