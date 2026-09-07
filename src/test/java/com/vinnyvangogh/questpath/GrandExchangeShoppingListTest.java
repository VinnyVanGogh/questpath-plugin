package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link GrandExchangeShoppingList#of}: satisfied totals never become a
 * line, an outstanding total's quantity and item id pass through unchanged,
 * and the order is plain alphabetical -- deliberately not {@link
 * QuestItems.Total}'s own in-progress/ran-out priority, which exists to
 * sequence a walking trip this list has no need to sequence. See {@link
 * GrandExchangeShoppingList}'s own class doc for why.
 */
public class GrandExchangeShoppingListTest
{
	private static QuestItems.Total total(String name, int itemId, int wanted, long owned)
	{
		QuestItems.Total total = new QuestItems.Total(name, itemId, owned, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		total.add(new QuestItems.Line(name, wanted, owned, false), "Sheep Herder");
		return total;
	}

	@Test
	public void nullOrEmptyTotalsProduceAnEmptyList()
	{
		assertTrue(GrandExchangeShoppingList.of(null).isEmpty());
		assertTrue(GrandExchangeShoppingList.of(Collections.emptyList()).isEmpty());
	}

	@Test
	public void satisfiedTotalsAreNeverLines()
	{
		List<QuestItems.Total> totals = Arrays.asList(
			total("Bucket", 100, 2, 2),
			total("Rope", 101, 1, 0));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals);

		assertEquals(1, lines.size());
		assertEquals("Rope", lines.get(0).name());
	}

	/** Quantity and item id pass through untouched -- this class only reorders and filters. */
	@Test
	public void outstandingCountAndItemIdPassThrough()
	{
		List<QuestItems.Total> totals = Collections.singletonList(total("Ball of wool", 42, 5, 2));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(totals);

		assertEquals(1, lines.size());
		GrandExchangeShoppingList.Line line = lines.get(0);
		assertEquals("Ball of wool", line.name());
		assertEquals(42, line.itemId());
		assertEquals(3L, line.outstanding());
	}

	/**
	 * Plain alphabetical, case-insensitively -- not the in-progress/ran-out
	 * priority {@code QuestItems#totals()} sorts by for a walking tour. Built
	 * out of alphabetical order, and out of the id order they were added in,
	 * so a sort that silently fell back to either would be caught.
	 */
	@Test
	public void orderIsPlainAlphabeticalRegardlessOfProgress()
	{
		QuestItems.Total zebra = total("Zebra head", 1, 5, 0);
		QuestItems.Total apple = total("apple", 2, 5, 0);
		QuestItems.Total mango = total("Mango", 3, 5, 0);

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			Arrays.asList(zebra, apple, mango));

		assertEquals(Arrays.asList("apple", "Mango", "Zebra head"),
			Arrays.asList(lines.get(0).name(), lines.get(1).name(), lines.get(2).name()));
	}
}
