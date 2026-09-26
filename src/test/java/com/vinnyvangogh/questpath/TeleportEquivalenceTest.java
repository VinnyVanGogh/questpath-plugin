package com.vinnyvangogh.questpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TeleportEquivalenceTest
{
	@Test
	public void standardRecipesAreDefinedCorrectly()
	{
		assertEquals(6, TeleportEquivalence.recipes().size());

		TeleportEquivalence.Recipe camelot = TeleportEquivalence.CAMELOT;
		assertEquals("Camelot", camelot.name());
		assertEquals(8010, camelot.tabletItemId());
		assertEquals(45, camelot.magicLevel());
		assertEquals(2, camelot.runes().size());
		assertEquals("Air rune", camelot.runes().get(0).name());
		assertEquals(5, camelot.runes().get(0).quantity());
		assertEquals("Law rune", camelot.runes().get(1).name());
		assertEquals(1, camelot.runes().get(1).quantity());

		TeleportEquivalence.Recipe varrock = TeleportEquivalence.VARROCK;
		assertEquals(8007, varrock.tabletItemId());
		assertEquals(25, varrock.magicLevel());
		assertEquals(3, varrock.runes().size());

		TeleportEquivalence.Recipe lumbridge = TeleportEquivalence.LUMBRIDGE;
		assertEquals(8008, lumbridge.tabletItemId());
		assertEquals(31, lumbridge.magicLevel());

		TeleportEquivalence.Recipe falador = TeleportEquivalence.FALADOR;
		assertEquals(8009, falador.tabletItemId());
		assertEquals(37, falador.magicLevel());

		TeleportEquivalence.Recipe ardougne = TeleportEquivalence.ARDOUGNE;
		assertEquals(8011, ardougne.tabletItemId());
		assertEquals(51, ardougne.magicLevel());
		assertEquals(2, ardougne.runes().size());
		assertEquals("Water rune", ardougne.runes().get(0).name());
		assertEquals(2, ardougne.runes().get(0).quantity());
		assertEquals("Law rune", ardougne.runes().get(1).name());
		assertEquals(2, ardougne.runes().get(1).quantity());

		TeleportEquivalence.Recipe draynor = TeleportEquivalence.DRAYNOR;
		assertEquals(8013, draynor.tabletItemId());
		assertEquals(0, draynor.magicLevel());
		assertTrue(draynor.runes().isEmpty());
	}

	@Test
	public void findRecipeMatchesByIdAndName()
	{
		assertNotNull(TeleportEquivalence.findRecipe(8010, null));
		assertEquals("Camelot", TeleportEquivalence.findRecipe(8010, null).name());
		assertEquals("Camelot", TeleportEquivalence.findRecipe(0, "Camelot teleport").name());
		assertEquals("Camelot", TeleportEquivalence.findRecipe(0, "Camelot teleport tablet").name());
		assertEquals("Varrock", TeleportEquivalence.findRecipe(8007, null).name());
		assertEquals("Lumbridge", TeleportEquivalence.findRecipe(8008, null).name());
		assertEquals("Falador", TeleportEquivalence.findRecipe(8009, null).name());
		assertEquals("Ardougne", TeleportEquivalence.findRecipe(8011, null).name());
		assertEquals("Draynor", TeleportEquivalence.findRecipe(8013, null).name());
		assertEquals("Draynor", TeleportEquivalence.findRecipe(19615, null).name());
		assertEquals("Draynor", TeleportEquivalence.findRecipe(0, "Draynor manor teleport").name());
		assertEquals("Draynor", TeleportEquivalence.findRecipe(0, "Draynor teleport tablet").name());
	}

	@Test
	public void tabletInHeldItemsSatisfiesRequirement()
	{
		Map<String, Long> held = new HashMap<>();
		held.put("camelot teleport", 2L);

		assertTrue(TeleportEquivalence.isCovered(8010, "Camelot teleport", 1, held, 1));
		assertTrue(TeleportEquivalence.isCovered(8010, "Camelot teleport", 2, held, 1));
		assertFalse(TeleportEquivalence.isCovered(8010, "Camelot teleport", 3, held, 1));
	}

	@Test
	public void runesInHeldItemsSatisfyRequirementIfMagicLevelMet()
	{
		Map<String, Long> held = new HashMap<>();
		held.put("air rune", 10L);
		held.put("law rune", 2L);

		// Camelot requires 45 Magic, 5 Air + 1 Law per cast
		assertTrue("45 Magic and 10 air + 2 law covers 2 casts",
			TeleportEquivalence.isCovered(8010, "Camelot teleport", 2, held, 45));

		assertFalse("45 Magic and 10 air + 2 law does not cover 3 casts",
			TeleportEquivalence.isCovered(8010, "Camelot teleport", 3, held, 45));

		assertFalse("44 Magic cannot cast Camelot teleport with runes",
			TeleportEquivalence.isCovered(8010, "Camelot teleport", 1, held, 44));
	}

	@Test
	public void tabletsAndRunesCombineToSatisfyRequirement()
	{
		Map<String, Long> held = new HashMap<>();
		held.put("camelot teleport", 1L);
		held.put("air rune", 5L);
		held.put("law rune", 1L);

		// 1 tablet + 1 rune cast = 2 teleports
		assertTrue(TeleportEquivalence.isCovered(8010, "Camelot teleport", 2, held, 45));
		assertFalse(TeleportEquivalence.isCovered(8010, "Camelot teleport", 3, held, 45));
		// With low magic, only the 1 tablet counts
		assertFalse(TeleportEquivalence.isCovered(8010, "Camelot teleport", 2, held, 40));
		assertTrue(TeleportEquivalence.isCovered(8010, "Camelot teleport", 1, held, 40));
	}

	@Test
	public void draynorManorOnlyCoveredByTablet()
	{
		Map<String, Long> held = new HashMap<>();
		held.put("air rune", 1000L);
		held.put("law rune", 1000L);

		assertFalse("Draynor has no rune recipe",
			TeleportEquivalence.isCovered(8013, "Draynor manor teleport", 1, held, 99));

		held.put("draynor manor teleport", 1L);
		assertTrue("Draynor covered by tablet",
			TeleportEquivalence.isCovered(8013, "Draynor manor teleport", 1, held, 1));
	}

	@Test
	public void decomposeToRunesProducesCorrectIngredients()
	{
		List<TeleportEquivalence.RuneIngredient> runes =
			TeleportEquivalence.decomposeToRunes(8010, "Camelot teleport", 2);
		assertEquals(2, runes.size());
		assertEquals("Air rune", runes.get(0).name());
		assertEquals(10, runes.get(0).quantity());
		assertEquals("Law rune", runes.get(1).name());
		assertEquals(2, runes.get(1).quantity());

		List<TeleportEquivalence.RuneIngredient> draynorRunes =
			TeleportEquivalence.decomposeToRunes(8013, "Draynor manor teleport", 1);
		assertTrue(draynorRunes.isEmpty());
	}
}
