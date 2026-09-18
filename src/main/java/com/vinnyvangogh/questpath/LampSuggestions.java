package com.vinnyvangogh.questpath;

/**
 * How many lamp targets the overlay boxes.
 *
 * <p>Three settings rather than a boolean, because the useful amount of advice
 * genuinely differs by account. An account with one obvious answer wants one
 * box; an account weighing three near-equal skills wants to see all three; and
 * a player who has decided for themselves wants the greying — which is the
 * part that stops a mistake — without a recommendation on top of it.
 *
 * <p><b>OFF turns off suggestions, not the whole advisor.</b> Greying blocked
 * skills has its own setting, and it defaults on. Folding the two together
 * would mean someone who merely found the boxes noisy switching off the
 * warnings as well, without meaning to.
 */
enum LampSuggestions
{
	/** The best target and the next two. */
	TOP_THREE("Top three", 3),
	/** The best target only. */
	TOP_ONE("Top one", 1),
	/** No boxes. Greying is unaffected. */
	OFF("Off", 0);

	private final String label;
	private final int count;

	LampSuggestions(String label, int count)
	{
		this.label = label;
		this.count = count;
	}

	/** How many ranks to box. */
	int count()
	{
		return count;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
