package com.vinnyvangogh.questpath;

/**
 * How much of a capped list an overlay draws.
 *
 * <p>The caps exist so a panel stays a readout rather than a wall, and for
 * "Next on your route" that is exactly right -- nobody needs the ninetieth
 * quest of a plan while standing in the first. For "Everything you need" it is
 * often wrong: a shopping list you can only see the top of is a list you
 * still have to open a browser to finish, which is the entire thing the
 * overlay was meant to save.
 *
 * <p>So it is the player's call, including the answer that costs the most
 * screen. EVERYTHING really is everything: no cap, however tall that gets.
 */
public enum OverlayListLength
{
	/**
	 * Take whatever the shared length setting says.
	 *
	 * The default per panel, so the shared control keeps working for anyone
	 * who does not care. One length for all five was the wrong shape and had
	 * to go: the setting that makes the shopping list usable ("Everything")
	 * makes the route preview ninety quests long, and the one that makes the
	 * route preview right leaves the shopping list showing five of sixty-five.
	 * There is no single value that is correct for both.
	 */
	MATCH_SHARED("Match the shared setting", 0),

	SHORT("Short", 5),
	NORMAL("Normal", 10),
	LONG("Long", 25),
	EVERYTHING("Everything", Integer.MAX_VALUE);

	private final String label;
	private final int baseline;

	OverlayListLength(String label, int baseline)
	{
		this.label = label;
		this.baseline = baseline;
	}

	/**
	 * The cap, before the density bonuses in
	 * {@link OverlayContext#listCap(int)} are applied.
	 *
	 * EVERYTHING short-circuits those rather than multiplying: there is no
	 * number larger than no limit, and multiplying MAX_VALUE overflows to a
	 * negative cap, which shows nothing at all.
	 */
	int baseline()
	{
		return baseline;
	}

	boolean unlimited()
	{
		return this == EVERYTHING;
	}

	/**
	 * This setting, or the shared one when this is MATCH_SHARED.
	 *
	 * A shared setting that is itself MATCH_SHARED would be a cycle, so it
	 * falls back to NORMAL -- the config cannot express that today, but a
	 * value saved by a future build could, and a baseline of zero shows
	 * nothing at all.
	 */
	OverlayListLength resolve(OverlayListLength shared)
	{
		if (this != MATCH_SHARED)
		{
			return this;
		}
		if (shared == null || shared == MATCH_SHARED)
		{
			return NORMAL;
		}
		return shared;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
