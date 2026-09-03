package com.vinnyvangogh.questpath;

/**
 * One panel's list length: a length of its own, or whatever the shared setting
 * says.
 *
 * <p>Separate from {@link OverlayListLength} for the same reason
 * {@link PanelWidth} is separate from {@link OverlayWidth}: the shared setting
 * must not offer to match itself.
 */
public enum PanelListLength
{
	MATCH_SHARED("Match the shared setting", null),
	SHORT("Short", OverlayListLength.SHORT),
	NORMAL("Normal", OverlayListLength.NORMAL),
	LONG("Long", OverlayListLength.LONG),
	EVERYTHING("Everything", OverlayListLength.EVERYTHING);

	private final String label;
	private final OverlayListLength own;

	PanelListLength(String label, OverlayListLength own)
	{
		this.label = label;
		this.own = own;
	}

	/**
	 * This panel's length: its own choice, or the shared one.
	 *
	 * Falls back to NORMAL when no shared setting is supplied -- a baseline of
	 * zero would show nothing, which is not what any setting here asks for.
	 */
	OverlayListLength resolve(OverlayListLength shared)
	{
		if (own != null)
		{
			return own;
		}
		return shared == null ? OverlayListLength.NORMAL : shared;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
