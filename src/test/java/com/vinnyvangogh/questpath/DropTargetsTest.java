package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DropTargetsTest
{
	private static final List<String> IMP = List.of("Drop: Imp (5/128)");

	private static QuestItems.Total total(String name, long owned, int wanted, String quest,
		List<String> sources)
	{
		QuestItems.Total total = new QuestItems.Total(name, 1, owned, sources);
		total.add(new QuestItems.Line(name, 1, wanted, owned, false, sources), quest);
		return total;
	}

	@Test
	public void dropOnlyItemNamesItsMonsterWithoutRateOrAnchor()
	{
		assertEquals(List.of("Imp"), DropTargets.dropOnlyNames(IMP));
		assertEquals(List.of("Yama", "Imp"),
			DropTargets.dropOnlyNames(List.of("Drop: Yama#Junk (3/11)", "Drop: Imp (5/128)")));
		assertEquals(List.of("Cadavaberry bush"),
			DropTargets.dropOnlyNames(List.of("Drop: Cadavaberry bush (Always)")));
	}

	@Test
	public void anItemAShopSellsIsNeverADropTarget()
	{
		assertTrue(DropTargets.dropOnlyNames(
			List.of("Shop: Tynan's Fishing Supplies (2 Coins)", "Drop: Imp (5/128)")).isEmpty());
		assertTrue(DropTargets.dropOnlyNames(Collections.emptyList()).isEmpty());
		assertTrue(DropTargets.dropOnlyNames(null).isEmpty());
		assertNull(DropTargets.dropsFrom(List.of("Shop: Somewhere (1 Coins)")));
	}

	@Test
	public void duplicateNamesCollapse()
	{
		assertEquals(List.of("Imp"),
			DropTargets.dropOnlyNames(List.of("Drop: Imp (5/128)", "Drop: imp#Other (1/9)")));
	}

	@Test
	public void needsListSaysWhereItDropsFrom()
	{
		assertEquals("drops from Imps", DropTargets.dropsFrom(IMP));
		assertEquals("drops from Yamas or Imps",
			DropTargets.dropsFrom(List.of("Drop: Yama (1/2)", "Drop: Imp (5/128)")));
		assertEquals("drops from Goblins, Imps or Rats and more", DropTargets.dropsFrom(
			Arrays.asList("Drop: Goblin (1/2)", "Drop: Imp (1/2)", "Drop: Rat (1/2)",
				"Drop: Cow (1/2)")));
	}

	@Test
	public void stillNeededDropItemIsATarget()
	{
		List<QuestItems.Total> items = List.of(total("White bead", 0, 1, "Imp Catcher", IMP));
		List<DropTargets.Target> targets = DropTargets.targets(items, null);
		assertEquals(1, targets.size());
		assertEquals("Imp", targets.get(0).sourceName());
		assertEquals("drops White bead", targets.get(0).label());
		assertEquals(1L, targets.get(0).outstanding());
	}

	@Test
	public void stopsOnceYouHaveEnough()
	{
		List<QuestItems.Total> items = List.of(total("White bead", 1, 1, "Imp Catcher", IMP));
		assertTrue(DropTargets.targets(items, null).isEmpty());
	}

	@Test
	public void nothingForAShopItemOrWithoutAList()
	{
		List<QuestItems.Total> items = List.of(
			total("Bucket", 0, 1, "Cook's Assistant", List.of("Shop: Shop (2 Coins)")));
		assertTrue(DropTargets.targets(items, null).isEmpty());
		assertTrue(DropTargets.targets(null, null).isEmpty());
	}

	@Test
	public void currentQuestScopeFiltersOtherQuests()
	{
		List<QuestItems.Total> items = List.of(
			total("White bead", 0, 1, "Imp Catcher", IMP),
			total("Bones", 0, 1, "Other Quest", List.of("Drop: Goblin (Always)")));
		assertEquals(1, DropTargets.targets(items, "imp catcher").size());
		assertEquals("Imp", DropTargets.targets(items, "Imp Catcher").get(0).sourceName());
		assertEquals(2, DropTargets.targets(items, null).size());
		assertTrue(DropTargets.targets(items, "Unrelated").isEmpty());
	}
}
