package com.vinnyvangogh.questpath;

import java.util.List;

/**
 * Whether a place the game will send you to killing something is somewhere
 * this account agreed to go.
 *
 * <p>Every drop location carries a region -- "general", "wilderness", and so
 * on -- and every account carries its own declarations about them. Both
 * halves existed for a while and never met: the plugin routed a hardcore
 * ironman towards Wilderness spawns with nothing said, because constraints
 * were not sent to it at all.
 *
 * <p><b>Severity decides, not this class.</b> "wilderness" at FATAL means the
 * destination is not offered; at WARN it is offered and the region is named
 * so the player can decide. Hardcoding either would bake one account's answer
 * in for every account, which is the same fabrication as inventing the
 * declaration in the first place.
 *
 * <p>A region nobody has declared anything about is allowed silently. That is
 * not a fallback masking missing data -- it is the honest reading of "this
 * player has said nothing about this", and an account with no declarations at
 * all should notice no difference.
 */
final class DropRegionPolicy
{
	private DropRegionPolicy()
	{
	}

	/** What to do about a destination in a given region. */
	enum Verdict
	{
		/** Nothing declared, or nothing that applies. Route there quietly. */
		ALLOWED,

		/** Declared at WARN: route there, and say where it is. */
		WARNED,

		/** Declared at FATAL: do not offer it. */
		BLOCKED
	}

	/**
	 * Judges one region against the account's declarations.
	 *
	 * <p>Matched on {@link PlayerConstraint#kind()} against the region name,
	 * case-insensitively, and only for declarations with no {@code detail} --
	 * a whole-category declaration. A declaration naming one specific thing
	 * ("slayer_unlock: dragons") is about that thing rather than about
	 * everywhere it might live, and reading it as a region ban would block
	 * far more than the player asked to block.
	 *
	 * @param region the drop location's region, as the bundle spells it
	 * @param constraints what the account has declared; may be empty
	 */
	static Verdict of(String region, List<PlayerConstraint> constraints)
	{
		if (region == null || region.isEmpty() || constraints == null)
		{
			return Verdict.ALLOWED;
		}

		Verdict worst = Verdict.ALLOWED;
		for (PlayerConstraint constraint : constraints)
		{
			if (!constraint.enabled() || !constraint.detail().isEmpty())
			{
				continue;
			}
			if (!region.equalsIgnoreCase(constraint.kind()))
			{
				continue;
			}
			if (constraint.blocks())
			{
				// Nothing outranks a block, so there is no reason to keep
				// looking once one is found.
				return Verdict.BLOCKED;
			}
			worst = Verdict.WARNED;
		}
		return worst;
	}
}
