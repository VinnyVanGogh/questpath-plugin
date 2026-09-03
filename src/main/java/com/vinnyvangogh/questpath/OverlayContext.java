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
	private final OverlayListLength length;

	OverlayContext(OverlayIcons icons, OverlayDisplayMode mode)
	{
		this(icons, mode, false, true, OverlayListLength.NORMAL);
	}

	OverlayContext(OverlayIcons icons, OverlayDisplayMode mode, boolean compact, boolean showOverflow,
		OverlayListLength length)
	{
		this.icons = icons;
		this.mode = mode == null ? OverlayDisplayMode.TEXT_AND_ICONS : mode;
		this.compact = compact;
		this.showOverflow = showOverflow;
		this.length = length == null ? OverlayListLength.NORMAL : length;
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
	 * How many entries a list of plain rows should show.
	 *
	 * The baseline is the overlay's own idea of a sensible length at the
	 * Normal setting, scaled by what the player chose. Scaling rather than
	 * replacing keeps the proportions the overlays were written with -- a
	 * route preview should stay shorter than a shopping list at every setting,
	 * not become the same length as it.
	 *
	 * Smaller text raises it in proportion to the row height it saves. Display
	 * mode does not, and must not: these rows are one line tall whatever is or
	 * is not drawn in them. Letting mode in here is what made "Next on your
	 * route" quietly grow four extra quests when the player switched to
	 * images, on a panel that has no item art in it at all.
	 */
	int listCap(int base)
	{
		if (length.unlimited())
		{
			return Integer.MAX_VALUE;
		}
		int cap = Math.max(1, base * length.baseline() / OverlayListLength.NORMAL.baseline());
		if (compact)
		{
			cap = cap * 3 / 2;
		}
		return cap;
	}

	/**
	 * How many entries a list of <em>items</em> should show.
	 *
	 * The same as {@link #listCap(int)} except in icon-only mode, where these
	 * rows really do collapse into a wrapping grid and several fit on the line
	 * one text row would have taken. Only lists that pack may ask for this
	 * bonus; a list of quest names must not, however it is displayed.
	 */
	int itemListCap(int base)
	{
		int cap = listCap(base);
		if (cap == Integer.MAX_VALUE)
		{
			return cap;
		}
		return mode == OverlayDisplayMode.ICONS_ONLY ? cap * 4 : cap;
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
