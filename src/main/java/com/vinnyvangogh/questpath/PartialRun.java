package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;

/**
 * One quest the account's build says to start and deliberately never finish.
 *
 * <p>Declared by the build preset in questpath and sent down with the push
 * response. The plugin does not own these and never writes them -- the same
 * relationship it has with {@link BuildRule} and {@link PlayerConstraint}.
 *
 * <p>These are the quests no route can carry. A route target is something the
 * plan intends to <em>finish</em>, and finishing one of these is precisely
 * what breaks the build: Dragon Slayer I hands out 18,650 Defence experience
 * at the end, so an account holding Defence at 1 plays it as far as the
 * anti-dragon shield and walks away. The planner drops it, correctly, and
 * without this field the preset's own written advice appears nowhere in the
 * plan that advice produced.
 *
 * <p>Three sentences travel rather than one, because a player looking at the
 * row is asking three things at once: what am I getting, how far do I play,
 * and why can I not simply finish it. A single note answers the first and
 * leaves the other two to be guessed, which is the state this replaces.
 */
class PartialRun
{
	@SerializedName("slug")
	private String slug;

	@SerializedName("name")
	private String name;

	@SerializedName("up_to")
	private String upTo;

	@SerializedName("for")
	private String forWhat;

	@SerializedName("why")
	private String why;

	/** questpath's slug for the quest, e.g. "dragon_slayer_i". */
	String slug()
	{
		return slug == null ? "" : slug;
	}

	/** The quest's name as the game spells it. */
	String name()
	{
		return name == null ? "" : name;
	}

	/**
	 * How far to play, in terms a player can act on.
	 *
	 * <p>"Up to receiving the anti-dragon shield from Duke Horacio", not
	 * "partially". A stopping point you cannot recognise while playing is not
	 * a stopping point.
	 */
	String upTo()
	{
		return upTo == null ? "" : upTo;
	}

	/** The unlock being chased -- the whole reason to do this at all. */
	String forWhat()
	{
		return forWhat == null ? "" : forWhat;
	}

	/**
	 * Why finishing is not an option, concretely enough to check: which
	 * skill, how much experience, and which rule it breaks.
	 */
	String why()
	{
		return why == null ? "" : why;
	}

	/**
	 * Whether this row says enough to be worth drawing.
	 *
	 * <p>A name with no explanation is the note this field exists to replace.
	 * Rather than render a quest with an empty tooltip -- which reads as
	 * "questpath has nothing to say about this" when the truth is "a field did
	 * not arrive" -- an incomplete row is left out and the count of what was
	 * drawn stays honest.
	 */
	boolean usable()
	{
		return !name().isEmpty() && !upTo().isEmpty() && !forWhat().isEmpty() && !why().isEmpty();
	}
}
