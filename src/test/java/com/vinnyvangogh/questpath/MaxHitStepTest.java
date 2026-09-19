package com.vinnyvangogh.questpath;

import java.util.Collections;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link MaxHit#nextStep}: the level at which a locked loadout's max hit
 * next goes up.
 *
 * Every expectation here is derived from {@link MaxHit}'s own formulae
 * rather than written down separately -- the point of the walk is that
 * there is exactly one implementation of the arithmetic, so a test that
 * hardcoded a second one would be asserting against the thing it is meant
 * to protect.
 */
public class MaxHitStepTest
{
	private static GearBonuses gear(int strengthBonus, int rangedStrength)
	{
		return new GearBonuses(strengthBonus, rangedStrength, 0, Collections.emptySet(), null);
	}

	private static int meleeAt(int level, GearBonuses gear)
	{
		return MaxHit.melee(level, AttackStyle.AGGRESSIVE, StrengthPrayer.NONE, StrengthPotion.NONE, gear).value();
	}

	/**
	 * The reported level really is the first one that hits harder: it beats
	 * the current number, and every level between the two does not.
	 */
	@Test
	public void theStepIsTheFirstLevelThatChangesTheNumber()
	{
		GearBonuses g = gear(80, 0);
		int level = 70;
		int current = meleeAt(level, g);

		MaxHitStep step = MaxHit.nextStep(Skill.STRENGTH, level, current,
			l -> MaxHit.melee(l, AttackStyle.AGGRESSIVE, StrengthPrayer.NONE, StrengthPotion.NONE, g));

		assertNotNull("a level 70 melee loadout has somewhere to go before 99", step);
		assertEquals(Skill.STRENGTH, step.skill());
		assertTrue("a step is always upwards", step.level() > level);
		assertEquals("the step carries the max hit at its own level", meleeAt(step.level(), g), step.maxHit());
		assertTrue("and that is an increase", step.maxHit() > current);
		for (int between = level + 1; between < step.level(); between++)
		{
			assertEquals("level " + between + " must not already have raised it",
				current, meleeAt(between, g));
		}
	}

	/** At 99 there is no higher level to report, so there is no step. */
	@Test
	public void ninetyNineHasNoNextStep()
	{
		GearBonuses g = gear(80, 0);
		int current = meleeAt(99, g);

		assertNull(MaxHit.nextStep(Skill.STRENGTH, 99, current,
			l -> MaxHit.melee(l, AttackStyle.AGGRESSIVE, StrengthPrayer.NONE, StrengthPotion.NONE, g)));
	}

	/**
	 * A standard-spellbook spell's damage does not depend on Magic level at
	 * all, so no amount of Magic raises the number and the honest answer is
	 * no step rather than 99.
	 */
	@Test
	public void aStandardSpellNeverStepsOnMagicLevel()
	{
		GearBonuses g = new GearBonuses(0, 0, 0, Collections.emptySet(), null);
		int current = MaxHit.magic(50, Spell.FIRE_STRIKE, MagicPrayer.NONE, g).value();

		assertNull(MaxHit.nextStep(Skill.MAGIC, 50, current,
			l -> MaxHit.magic(l, Spell.FIRE_STRIKE, MagicPrayer.NONE, g)));
	}

	/** Ranged walks its own formula, and reports the Ranged skill. */
	@Test
	public void rangedStepsOnRangedLevel()
	{
		GearBonuses g = gear(0, 60);
		int level = 40;
		int current = MaxHit.ranged(level, RangedStyle.ACCURATE, RangedPrayer.NONE, g).value();

		MaxHitStep step = MaxHit.nextStep(Skill.RANGED, level, current,
			l -> MaxHit.ranged(l, RangedStyle.ACCURATE, RangedPrayer.NONE, g));

		assertNotNull(step);
		assertEquals(Skill.RANGED, step.skill());
		assertTrue(step.level() > level);
		assertTrue(step.maxHit() > current);
	}

	/**
	 * A loadout whose arithmetic throws further up the ladder yields no
	 * step, rather than letting the exception escape a once-per-tick
	 * refresh.
	 */
	@Test
	public void anExceptionFurtherUpMeansNoStep()
	{
		assertNull(MaxHit.nextStep(Skill.STRENGTH, 50, 10, l ->
		{
			throw new IllegalArgumentException("gear strength bonus too low");
		}));
	}

	/** A level whose result is not modelled ends the walk with no step. */
	@Test
	public void aNotModelledResultMeansNoStep()
	{
		assertNull(MaxHit.nextStep(Skill.STRENGTH, 50, 10,
			l -> MaxHitResult.notModelled("Twisted bow")));
	}

	/** The type refuses values it could not have computed. */
	@Test(expected = IllegalArgumentException.class)
	public void aStepAboveNinetyNineIsRejected()
	{
		MaxHitStep.at(Skill.STRENGTH, 100, 30);
	}
}
