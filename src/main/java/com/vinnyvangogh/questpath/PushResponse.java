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

	List<BuildRule> buildRules()
	{
		return buildRules == null ? Collections.emptyList() : buildRules;
	}
}
