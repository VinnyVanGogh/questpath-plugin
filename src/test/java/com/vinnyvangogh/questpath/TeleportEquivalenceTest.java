package com.vinnyvangogh.questpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
		// TELETAB_DRAYNOR. 8013 is the Teleport to house tablet, which the
		// first cut of this table had here.
		assertEquals(19615, draynor.tabletItemId());
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
		assertEquals("Draynor", TeleportEquivalence.findRecipe(19615, null).name());
		assertNull("8013 is Teleport to house, not Draynor", TeleportEquivalence.findRecipe(8013, null));
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
			TeleportEquivalence.decomposeToRunes(19615, "Draynor manor teleport", 1);
		assertTrue(draynorRunes.isEmpty());
	}

	// decompose() -- the list-level rewrite the walking route and the Grand
	// Exchange list both go through.

	private static QuestItems.Total teleport(String name, int itemId, int casts, boolean recommended, String quest)
	{
		QuestItems.Total total = new QuestItems.Total(name, itemId, 0L, 0L, false,
			java.util.Collections.emptyList(), java.util.Collections.emptyList());
		total.add(new QuestItems.Line(name, itemId, casts, 0L, false, false,
			java.util.Collections.emptyList(), java.util.Collections.emptyList(),
			java.util.Collections.emptyList(), "", java.util.Collections.emptyList(), recommended), quest);
		return total;
	}

	private static QuestItems.Total named(List<QuestItems.Total> totals, String name)
	{
		for (QuestItems.Total total : totals)
		{
			if (total.name().equalsIgnoreCase(name))
			{
				return total;
			}
		}
		return null;
	}

	/**
	 * The "runes, not tablets" choice is about the recommended extras only:
	 * a required Camelot teleport on the same list is left exactly as it
	 * came, so the choice never rewrites the required path.
	 */
	@Test
	public void recommendedOnlyLeavesRequiredTeleportsAlone()
	{
		QuestItems.Total required = teleport("Camelot teleport", 8010, 1, false, "Merlin's Crystal");
		QuestItems.Total recommended = teleport("Varrock teleport", 8007, 2, true, "Demon Slayer");

		List<QuestItems.Total> out = TeleportEquivalence.decompose(
			List.of(required, recommended), true, 99);

		assertNotNull("required tablet stays", named(out, "Camelot teleport"));
		assertNull("recommended tablet is gone", named(out, "Varrock teleport"));
		assertEquals(6, named(out, "Air rune").required());
		assertEquals(2, named(out, "Fire rune").required());
		assertEquals(2, named(out, "Law rune").required());
		assertTrue(named(out, "Law rune").recommended());
		assertEquals(List.of("Demon Slayer"), named(out, "Law rune").questNames());
	}

	/**
	 * An account that cannot cast the spell is shown the tablet whatever
	 * form it asked for -- law runes it cannot use are not a way to Camelot.
	 */
	@Test
	public void belowTheMagicLevelTheTabletStays()
	{
		QuestItems.Total camelot = teleport("Camelot teleport", 8010, 1, true, "Merlin's Crystal");

		List<QuestItems.Total> low = TeleportEquivalence.decompose(List.of(camelot), true, 44);
		assertNotNull(named(low, "Camelot teleport"));
		assertNull(named(low, "Law rune"));

		List<QuestItems.Total> high = TeleportEquivalence.decompose(List.of(camelot), true, 45);
		assertNull(named(high, "Camelot teleport"));
		assertEquals(1, named(high, "Law rune").required());
	}

	/**
	 * Runes two teleports share become one row for the sum, and a rune the
	 * list already wanted outright is folded in rather than duplicated. The
	 * caller's own totals are never touched: this runs every overlay frame,
	 * and the first cut grew the shared law-rune count by a cast a frame.
	 */
	@Test
	public void sharedRunesMergeWithoutMutatingTheInputs()
	{
		QuestItems.Total camelot = teleport("Camelot teleport", 8010, 1, true, "Merlin's Crystal");
		QuestItems.Total varrock = teleport("Varrock teleport", 8007, 1, true, "Demon Slayer");
		QuestItems.Total laws = teleport("Law rune", 563, 3, false, "Rune Mysteries");
		List<QuestItems.Total> in = List.of(camelot, varrock, laws);

		List<QuestItems.Total> once = TeleportEquivalence.decompose(in, false, 99);
		List<QuestItems.Total> twice = TeleportEquivalence.decompose(in, false, 99);

		assertEquals(8, named(once, "Air rune").required());
		assertEquals("3 wanted outright + 1 + 1 for the casts", 5, named(once, "Law rune").required());
		assertFalse("required + recommended is required", named(once, "Law rune").recommended());
		assertEquals(List.of("Rune Mysteries", "Merlin's Crystal", "Demon Slayer"),
			named(once, "Law rune").questNames());
		assertEquals("a second pass gives the same answer", 5, named(twice, "Law rune").required());
		assertEquals("the caller's total is unchanged", 3, laws.required());
		assertEquals(List.of("Rune Mysteries"), laws.questNames());
	}

	/** A teleport already covered -- tablets in the bank, or runes counted as casts -- is not decomposed. */
	@Test
	public void coveredTeleportsAreNotDecomposed()
	{
		QuestItems.Total camelot = teleport("Camelot teleport", 8010, 1, true, "Merlin's Crystal");
		camelot.setEquivalentOwned(1L);

		List<QuestItems.Total> out = TeleportEquivalence.decompose(List.of(camelot), true, 99);
		assertNotNull(named(out, "Camelot teleport"));
		assertNull(named(out, "Law rune"));
	}
}
