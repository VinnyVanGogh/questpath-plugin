package com.vinnyvangogh.questpath;

/**
 * How much of a row an overlay draws: the words, the picture, or both.
 *
 * <p>The three are genuinely different tools rather than a preference. Icons
 * alone are the densest possible readout and the fastest to scan once the art
 * is familiar, which is most of an hour into a quest. Text alone survives a
 * bank full of items whose sprites look alike -- four runes are four blue
 * squares -- and is the only mode that works for a skill cap or an xp figure.
 * Both together is the default because it is the only one that needs nothing
 * learned first.
 *
 * <p>A row with no icon available renders its text in every mode, ICONS_ONLY
 * included. The alternative is a blank line, and an overlay that silently
 * drops what it cannot draw a picture for would be hiding exactly the item
 * questpath could not identify.
 */
public enum OverlayDisplayMode
{
	TEXT_AND_ICONS("Text and images"),
	TEXT_ONLY("Just text"),
	ICONS_ONLY("Just images");

	private final String label;

	OverlayDisplayMode(String label)
	{
		this.label = label;
	}

	public String label()
	{
		return label;
	}

	boolean showsText()
	{
		return this != ICONS_ONLY;
	}

	boolean showsIcons()
	{
		return this != TEXT_ONLY;
	}

	/**
	 * What RuneLite's config dropdown shows. It renders an enum by its
	 * toString, so without this the options would read TEXT_AND_ICONS.
	 */
	@Override
	public String toString()
	{
		return label;
	}
}
