package com.vinnyvangogh.questpath;

/**
 * How wide an overlay panel may grow.
 *
 * <p>Width is the setting that decides whether these are readouts or columns.
 * At the narrowest, a list of items is one item per line and twenty of them is
 * a stripe down the screen; at the widest, icon mode packs the same twenty into
 * two rows of a grid and the panel is shorter than the chatbox. Every mode gets
 * denser as this grows, so it is one control rather than one per overlay.
 *
 * <p>The pixel figures are the panel's outer width, not its content width.
 */
public enum OverlayWidth
{
	COMPACT("Compact", 168),
	REGULAR("Regular", 224),
	WIDE("Wide", 300),
	WIDEST("Widest", 380);

	private final String label;
	private final int pixels;

	OverlayWidth(String label, int pixels)
	{
		this.label = label;
		this.pixels = pixels;
	}

	int pixels()
	{
		return pixels;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
