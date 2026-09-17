package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * {@link LoadoutCapture#classifyWeaponStyles}: the pure category-to-style
 * bucketing this feature's "which style blocks should this loadout show"
 * behaviour is built on. Deliberately does not test {@link
 * LoadoutCapture#fromWorn} itself -- that needs a live {@code Client} and
 * {@code ItemManager}, which this project cannot fake without Mockito (see
 * {@code LoadoutCodecTest}'s own class javadoc for the same reasoning about
 * {@code ConfigManager}). {@code classifyWeaponStyles} is package-private and
 * free of every RuneLite type specifically so this logic is testable without
 * either.
 */
public class LoadoutCaptureTest
{
	@Test
	public void aBowIsRangedOnly()
	{
		// e.g. a Magic shortbow: arange positive, everything else zero.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 49, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_RANGED), styles);
	}

	@Test
	public void aCrossbowIsRangedOnly()
	{
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 90, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_RANGED), styles);
	}

	@Test
	public void aThrownWeaponIsRangedOnly()
	{
		// Thrown knives/javelins/chinchompas sit in the weapon slot but carry
		// an arange bonus, not a melee one -- they train Ranged in-game.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 20, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_RANGED), styles);
	}

	@Test
	public void aScimitarIsMeleeOnly()
	{
		// aslash positive, arange and amagic zero.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 67, 0, 0, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_MELEE), styles);
	}

	@Test
	public void anUnarmedFistIsMeleeOnly()
	{
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 0, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_MELEE), styles);
	}

	@Test
	public void aWeaponSlotItemWithNoOffensiveBonusFallsBackToMelee()
	{
		// Same bucket as unarmed -- "everything else" per the task brief,
		// not left empty or guessed from the item's name.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 0, 0);

		assertEquals(Collections.singletonList(Loadout.STYLE_MELEE), styles);
	}

	@Test
	public void aPoweredStaffIsMagicOnly()
	{
		// e.g. Trident of the seas: amagic positive, melee/ranged bonuses zero.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 0, 15);

		assertEquals(Collections.singletonList(Loadout.STYLE_MAGIC), styles);
	}

	@Test
	public void anElementalStaffIsMagicOnly()
	{
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 0, 10);

		assertEquals(Collections.singletonList(Loadout.STYLE_MAGIC), styles);
	}

	@Test
	public void aSalamanderIsMeleeAndRangedAmbiguous()
	{
		// Salamanders carry both a melee attack bonus and a ranged one --
		// genuinely ambiguous, per the task brief, not forced into one bucket.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(40, 40, 40, 40, 0);

		assertEquals(Arrays.asList(Loadout.STYLE_MELEE, Loadout.STYLE_RANGED), styles);
	}

	@Test
	public void aWeaponWithBonusesInAllThreeShowsAllThree()
	{
		List<String> styles = LoadoutCapture.classifyWeaponStyles(10, 0, 0, 10, 10);

		assertEquals(Arrays.asList(Loadout.STYLE_MELEE, Loadout.STYLE_RANGED, Loadout.STYLE_MAGIC), styles);
	}

	@Test
	public void negativeBonusesDoNotCountAsOffensive()
	{
		// A negative bonus (e.g. some defensive-only weapon-slot items can
		// carry a negative attack bonus) must not be mistaken for a positive
		// one and wrongly mark a style as available.
		List<String> styles = LoadoutCapture.classifyWeaponStyles(-5, -5, -5, -5, -5);

		assertEquals(Collections.singletonList(Loadout.STYLE_MELEE), styles);
	}

	@Test
	public void theResultIsNeverEmptyAndAlwaysUnmodifiable()
	{
		List<String> styles = LoadoutCapture.classifyWeaponStyles(0, 0, 0, 0, 0);

		try
		{
			styles.add(Loadout.STYLE_RANGED);
			org.junit.Assert.fail("expected the result to be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}
}
