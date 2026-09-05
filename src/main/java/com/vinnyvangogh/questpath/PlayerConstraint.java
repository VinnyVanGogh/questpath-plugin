package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;

/**
 * One thing this account has declared it will not do.
 *
 * <p>Declared on the questpath website and sent down with the push response.
 * The plugin does not own these and never writes them -- the same
 * relationship it has with {@link BuildRule}.
 *
 * <p>They matter here because the shop trip routes to monsters for items no
 * shop sells, and some of those are in the Wilderness. An account that said
 * "warn me about the Wilderness" and one that said "never send me there" want
 * different behaviour from the same route, and until this arrived the plugin
 * could not tell them apart -- or know either had been said.
 */
class PlayerConstraint
{
	@SerializedName("kind")
	private String kind;

	@SerializedName("detail")
	private String detail;

	@SerializedName("severity")
	private String severity;

	@SerializedName("enabled")
	private boolean enabled;

	/**
	 * What kind of thing is declared -- "wilderness", "unsafe_combat",
	 * "slayer_unlock". Lowercase, as questpath stores it.
	 */
	String kind()
	{
		return kind == null ? "" : kind;
	}

	/**
	 * The specific thing, or empty for the whole category.
	 *
	 * <p>Empty is meaningful rather than missing: "wilderness" with no detail
	 * is "no Wilderness at all", which is the broadest form of the
	 * declaration and not an incomplete one.
	 */
	String detail()
	{
		return detail == null ? "" : detail;
	}

	/** "FATAL" or "WARN", as questpath spells it. */
	String severity()
	{
		return severity == null ? "" : severity;
	}

	/**
	 * Whether the declaration is switched on.
	 *
	 * <p>Switched-off rows are sent deliberately, so this carries
	 * information. A row arriving with this false is a declaration the player
	 * made and then turned off, which is different from never having made it.
	 */
	boolean enabled()
	{
		return enabled;
	}

	/**
	 * Whether this forbids the thing outright, as opposed to wanting to be
	 * told about it.
	 *
	 * <p>Only FATAL blocks. Anything else -- WARN, or a severity this build
	 * does not recognise -- is treated as "say so and let me decide", because
	 * silently refusing to route somewhere on the strength of a word this
	 * build has never seen is a worse failure than mentioning it.
	 */
	boolean blocks()
	{
		return enabled && "FATAL".equalsIgnoreCase(severity());
	}

	/** Whether this wants to be mentioned but not obeyed. */
	boolean warns()
	{
		return enabled && !blocks();
	}
}
