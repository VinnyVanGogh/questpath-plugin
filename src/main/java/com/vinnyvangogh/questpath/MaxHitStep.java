package com.vinnyvangogh.questpath;

import net.runelite.api.Skill;

/**
 * The next level at which a locked loadout's max hit goes up, and what it
 * goes up to.
 *
 * <p>A max hit is a floored function of a single level, so between two
 * breakpoints a great many levels buy nothing at all. Showing only the
 * current number therefore answers "how hard do I hit" but not "what is
 * the next level worth training to", which is the question a frozen
 * loadout is actually being consulted about.
 *
 * <p>This type only ever describes a real, computed breakpoint: it is
 * constructed from a {@link MaxHitResult} that {@link
 * MaxHitResult#isKnown() was known} at the higher level, never from an
 * extrapolation. Where no higher level below 99 raises the number --
 * a standard-spellbook magic line, whose damage does not scale with Magic
 * at all, or a style already at its ceiling -- there is no {@code
 * MaxHitStep} rather than a guessed one.
 */
public final class MaxHitStep
{
	private final Skill skill;
	private final int level;
	private final int maxHit;

	private MaxHitStep(Skill skill, int level, int maxHit)
	{
		this.skill = skill;
		this.level = level;
		this.maxHit = maxHit;
	}

	/**
	 * A breakpoint.
	 *
	 * @param skill the skill whose level drives this style's max hit.
	 * @param level the level at which the max hit next increases.
	 * @param maxHit the max hit at that level.
	 */
	public static MaxHitStep at(Skill skill, int level, int maxHit)
	{
		if (skill == null)
		{
			throw new IllegalArgumentException("MaxHitStep.at() skill must not be null");
		}
		if (level < 1 || level > MaxHit.MAX_SKILL_LEVEL)
		{
			throw new IllegalArgumentException("MaxHitStep.at() level out of range: " + level);
		}
		if (maxHit < 0)
		{
			throw new IllegalArgumentException("MaxHitStep.at() maxHit must not be negative: " + maxHit);
		}
		return new MaxHitStep(skill, level, maxHit);
	}

	/** The skill whose level drives this style's max hit. */
	public Skill skill()
	{
		return skill;
	}

	/** The level at which the max hit next increases. */
	public int level()
	{
		return level;
	}

	/** The max hit at {@link #level()}. */
	public int maxHit()
	{
		return maxHit;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof MaxHitStep))
		{
			return false;
		}
		MaxHitStep that = (MaxHitStep) o;
		return skill == that.skill && level == that.level && maxHit == that.maxHit;
	}

	@Override
	public int hashCode()
	{
		int result = skill.hashCode();
		result = 31 * result + level;
		result = 31 * result + maxHit;
		return result;
	}

	@Override
	public String toString()
	{
		return "MaxHitStep{" + skill + " " + level + " -> " + maxHit + "}";
	}
}
