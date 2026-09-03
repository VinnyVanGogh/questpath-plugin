package com.vinnyvangogh.questpath;

/**
 * Which halves of the build-rules overlay to draw.
 *
 * <p>They answer different questions. A blocking rule is a wall -- cross it
 * and the account this plugin exists to protect is over. An informational one
 * is a goal or a soft cap: worth tracking, never urgent. Someone watching a
 * single Defence cap does not want six goals underneath it taking up the
 * screen, and someone tracking goals mid-grind does not need the cap they have
 * been nowhere near for a year.
 */
public enum BuildRuleSections
{
	BOTH("Blocking and informational"),
	BLOCKING("Blocking only"),
	INFORMATIONAL("Informational only");

	private final String label;

	BuildRuleSections(String label)
	{
		this.label = label;
	}

	boolean showsBlocking()
	{
		return this != INFORMATIONAL;
	}

	boolean showsInformational()
	{
		return this != BLOCKING;
	}

	/** RuneLite renders an enum config item by its toString. */
	@Override
	public String toString()
	{
		return label;
	}
}
