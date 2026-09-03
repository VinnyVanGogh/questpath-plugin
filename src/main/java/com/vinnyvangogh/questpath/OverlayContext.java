package com.vinnyvangogh.questpath;

import java.awt.Font;

/**
 * What every row needs to draw itself: where pictures come from, how much of a
 * row the player asked to see, and how big to set it.
 *
 * <p>Passed down rather than reached for, so a row stays a description of what
 * to draw. It also owns the fonts: a row that called
 * {@link OverlayTheme#font()} directly could not be made compact, and the
 * compact setting has to reach all five overlays at once or it is not a
 * setting, it is five inconsistencies.
 */
final class OverlayContext
{
	private final OverlayIcons icons;
	private final OverlayDisplayMode mode;
	private final boolean compact;
	private final boolean showOverflow;

	OverlayContext(OverlayIcons icons, OverlayDisplayMode mode)
	{
		this(icons, mode, false, true);
	}

	OverlayContext(OverlayIcons icons, OverlayDisplayMode mode, boolean compact, boolean showOverflow)
	{
		this.icons = icons;
		this.mode = mode == null ? OverlayDisplayMode.TEXT_AND_ICONS : mode;
		this.compact = compact;
		this.showOverflow = showOverflow;
	}

	OverlayIcons icons()
	{
		return icons;
	}

	OverlayDisplayMode mode()
	{
		return mode;
	}

	/**
	 * Whether the player asked for the smaller face.
	 *
	 * The game's small font is about two-thirds the height of the regular one,
	 * which is a third more rows in the same panel. It is a real trade rather
	 * than a free win -- the small face is harder to read over bright scenery --
	 * so it is a choice and not a default.
	 */
	boolean compact()
	{
		return compact;
	}

	/**
	 * Whether "...and 58 more" lines are drawn.
	 *
	 * They are honest -- a truncated list that does not say it is truncated is
	 * a list claiming to be complete -- but on a panel deliberately capped
	 * short they are also a line of chrome per section. Off, the caps still
	 * apply; the panel simply stops explaining itself, which is a reasonable
	 * thing to want once you already know.
	 */
	boolean showOverflow()
	{
		return showOverflow;
	}

	/**
	 * How many entries a capped list should show, given a baseline written for
	 * the roomiest case: regular text at the default width.
	 *
	 * The caps exist so a panel stays a readout rather than a wall, and "a
	 * wall" is measured in screen height, not entries. An icon-only grid at a
	 * wide setting fits nine items on the line a text row spends on one, so
	 * holding both to the same count would waste the mode the player switched
	 * to precisely to see more. Smaller text buys a more modest increase, in
	 * proportion to the row height it saves.
	 */
	int listCap(int base)
	{
		int cap = base;
		if (mode == OverlayDisplayMode.ICONS_ONLY)
		{
			cap *= 4;
		}
		if (compact)
		{
			cap = cap * 3 / 2;
		}
		return cap;
	}

	/** The body face. */
	Font font()
	{
		return compact ? OverlayTheme.smallFont() : OverlayTheme.font();
	}

	/** The face for secondary lines, already the smaller of the two. */
	Font smallFont()
	{
		return OverlayTheme.smallFont();
	}

	/** The face for a heading. */
	Font boldFont()
	{
		return OverlayTheme.boldFont();
	}
}
