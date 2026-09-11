package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

/**
 * What questpath answers a push with.
 *
 * Only the parts the plugin acts on are modelled. Gson ignores the rest, which
 * means an older plugin keeps working against a newer questpath — the counts
 * it does not know about simply do not arrive.
 */
class PushResponse
{
	@SerializedName("player")
	private String player;

	@SerializedName("unmatched")
	private List<String> unmatched;

	@SerializedName("build_rules")
	private List<BuildRule> buildRules;

	@SerializedName("constraints")
	private List<PlayerConstraint> constraints;

	@SerializedName("route_order")
	private List<String> routeOrder;

	@SerializedName("partial_runs")
	private List<PartialRun> partialRuns;

	@SerializedName("known_items")
	private java.util.Map<String, Long> knownItems;

	String player()
	{
		return player == null ? "" : player;
	}

	/**
	 * Quest names the plugin sent that questpath could not resolve.
	 *
	 * Worth surfacing rather than counting: a name the client uses and
	 * questpath does not is a real disagreement between the two, and naming
	 * them is what makes it fixable.
	 */
	List<String> unmatched()
	{
		return unmatched == null ? Collections.emptyList() : unmatched;
	}

	/**
	 * What this account has declared it will not do -- empty from a questpath
	 * older than the constraints field, which is the same thing as having
	 * declared nothing and is handled identically.
	 */
	List<PlayerConstraint> constraints()
	{
		return constraints == null ? Collections.emptyList() : constraints;
	}

	List<BuildRule> buildRules()
	{
		return buildRules == null ? Collections.emptyList() : buildRules;
	}

	/**
	 * The account's saved route from questpath, as quest names in route order.
	 *
	 * questpath always sends this field, even empty, so an empty list here
	 * means "no route saved" -- a null would only happen against a questpath
	 * old enough to predate this field, and is treated the same way rather
	 * than as a crash.
	 */
	/**
	 * What questpath already holds for this account, by lowercased item name.
	 *
	 * The client cannot see a bank until the player opens one, so until then
	 * every "do you have this" answer is about a pocket rather than an
	 * account. This is the fallback, and it turns that from "I can only see
	 * what you are carrying" into "as of your last sync" -- stale being a far
	 * smaller problem than blind, provided the panel says which it is.
	 *
	 * Empty means questpath holds nothing on record, which is different from
	 * the field not arriving; the server sends `{}` rather than dropping it
	 * precisely so the two stay distinguishable.
	 */
	java.util.Map<String, Long> knownItems()
	{
		return knownItems == null ? Collections.emptyMap() : knownItems;
	}

	List<String> routeOrder()
	{
		return routeOrder == null ? Collections.emptyList() : routeOrder;
	}

	/**
	 * The quests this account's build says to start and never finish.
	 *
	 * <p>Absent from the response for an account that has applied no build,
	 * and for a build with no such quests. Those are the same answer to the
	 * plugin -- there is no section to draw -- so questpath drops the field
	 * for both rather than distinguishing them on the wire, and both arrive
	 * here as an empty list.
	 *
	 * <p>That is the opposite of {@link #routeOrder()}, where `[]` and absent
	 * are genuinely different facts and questpath always sends the field.
	 */
	List<PartialRun> partialRuns()
	{
		return partialRuns == null ? Collections.emptyList() : partialRuns;
	}
}
