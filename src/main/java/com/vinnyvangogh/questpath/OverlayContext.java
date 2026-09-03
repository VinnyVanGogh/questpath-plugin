package com.vinnyvangogh.questpath;

/**
 * What every row needs to draw itself: where pictures come from, and how much
 * of a row the player asked to see.
 *
 * <p>Passed down rather than reached for, so a row is a pure description of
 * what to draw and can be built off the client thread if it ever needs to be.
 */
final class OverlayContext
{
	private final OverlayIcons icons;
	private final OverlayDisplayMode mode;

	OverlayContext(OverlayIcons icons, OverlayDisplayMode mode)
	{
		this.icons = icons;
		this.mode = mode == null ? OverlayDisplayMode.TEXT_AND_ICONS : mode;
	}

	OverlayIcons icons()
	{
		return icons;
	}

	OverlayDisplayMode mode()
	{
		return mode;
	}
}
