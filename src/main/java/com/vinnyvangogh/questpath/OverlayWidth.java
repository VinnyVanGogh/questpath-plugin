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
	/**
	 * Take whatever the shared width setting says.
	 *
	 * The default for every individual overlay, so the single setting keeps
	 * working for anyone who does not care -- while the ones who want the
	 * route panel narrow and the shopping list enormous can say so per panel.
	 */
	MATCH_GLOBAL("Match the shared setting", 0),

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

	/** The outer width in pixels, or 0 for {@link #MATCH_GLOBAL}. */
	int pixels()
	{
		return pixels;
	}

	/**
	 * This width, or the shared one when this is MATCH_GLOBAL.
	 *
	 * A shared setting that is itself MATCH_GLOBAL would be a cycle, so it
	 * falls back to REGULAR -- the config cannot currently express that, but
	 * a saved value from a future build could, and a panel of width zero
	 * draws nothing at all.
	 */
	int resolve(OverlayWidth shared)
	{
		if (this != MATCH_GLOBAL)
		{
			return pixels;
		}
		if (shared == null || shared == MATCH_GLOBAL)
		{
			return REGULAR.pixels;
		}
		return shared.pixels;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
