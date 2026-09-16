package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;

/**
 * The result of a max-hit calculation, in exactly one of two shapes: a
 * known number (with whichever detected gear effects were not applied to
 * it), or an honest "not modelled" with a reason naming the thing.
 *
 * No shape carries a number when the answer is unknown &mdash; there is no
 * default, no zero, and no upper bound standing in for a real value. A
 * fabricated number is worse than an explicit "not modelled", per this
 * project's own no-fabricated-data rule.
 */
public final class MaxHitResult
{
	private final boolean known;
	private final int value;
	private final List<GearEffect> notApplied;
	private final String reason;

	private MaxHitResult(boolean known, int value, List<GearEffect> notApplied, String reason)
	{
		this.known = known;
		this.value = value;
		this.notApplied = notApplied;
		this.reason = reason;
	}

	/**
	 * A known max hit, plus every detected {@link GearEffect} that was not
	 * applied to it (in {@link GearEffect} enum order).
	 */
	public static MaxHitResult known(int value, List<GearEffect> notApplied)
	{
		List<GearEffect> copy = notApplied == null || notApplied.isEmpty()
			? Collections.emptyList()
			: Collections.unmodifiableList(notApplied);
		return new MaxHitResult(true, value, copy, null);
	}

	/**
	 * No number at all: the calculation could not be performed, e.g. a
	 * Twisted bow (opponent-dependent), a chinchompa (ungoverned by the
	 * fetched page), or a spell RESEARCH.md &sect;20 has no base for.
	 *
	 * @param reason names the specific thing that could not be modelled.
	 */
	public static MaxHitResult notModelled(String reason)
	{
		return new MaxHitResult(false, 0, null, reason);
	}

	/** Whether this result carries a real number. */
	public boolean isKnown()
	{
		return known;
	}

	/**
	 * The computed max hit.
	 *
	 * @throws IllegalStateException if {@link #isKnown()} is false.
	 */
	public int value()
	{
		if (!known)
		{
			throw new IllegalStateException("not modelled: " + reason);
		}
		return value;
	}

	/**
	 * Every detected {@link GearEffect} whose multiplier was not applied to
	 * {@link #value()}, in {@link GearEffect} enum order.
	 *
	 * @throws IllegalStateException if {@link #isKnown()} is false.
	 */
	public List<GearEffect> notApplied()
	{
		if (!known)
		{
			throw new IllegalStateException("not modelled: " + reason);
		}
		return notApplied;
	}

	/**
	 * The reason no number could be computed.
	 *
	 * @throws IllegalStateException if {@link #isKnown()} is true.
	 */
	public String reason()
	{
		if (known)
		{
			throw new IllegalStateException("result is known: " + value);
		}
		return reason;
	}

	@Override
	public String toString()
	{
		return known
			? "MaxHitResult{known=" + value + " notApplied=" + notApplied + "}"
			: "MaxHitResult{notModelled=\"" + reason + "\"}";
	}
}
