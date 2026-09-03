package com.vinnyvangogh.questpath;

/**
 * One panel's width: a width of its own, or whatever the shared setting says.
 *
 * <p>Separate from {@link OverlayWidth} so the shared setting cannot offer to
 * match itself. Folding both roles into one enum put "Match the shared
 * setting" in the shared dropdown, which is not a thing that can be true --
 * the shared setting is what the others match.
 */
public enum PanelWidth
{
	MATCH_SHARED("Match the shared setting", null),
	COMPACT("Compact", OverlayWidth.COMPACT),
	REGULAR("Regular", OverlayWidth.REGULAR),
	WIDE("Wide", OverlayWidth.WIDE),
	WIDEST("Widest", OverlayWidth.WIDEST);

	private final String label;
	private final OverlayWidth own;

	PanelWidth(String label, OverlayWidth own)
	{
		this.label = label;
		this.own = own;
	}

	/**
	 * This panel's width in pixels: its own choice, or the shared one.
	 *
	 * Falls back to REGULAR when no shared setting is supplied, because a
	 * panel zero pixels wide draws nothing at all and an absent setting is a
	 * bug rather than an instruction.
	 */
	int resolve(OverlayWidth shared)
	{
		if (own != null)
		{
			return own.pixels();
		}
		return shared == null ? OverlayWidth.REGULAR.pixels() : shared.pixels();
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
