package com.vinnyvangogh.questpath;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The report: the route named shops with nothing on the shelf. Pins the four
 * behaviours the fix is for.
 */
public class ShopStockTest
{
	private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");
	private static final String ITEM = "Chisel";
	private static final String RUNES = "Aubury's Rune Shop";
	private static final String CRAFT = "Dommik's Crafting Store";

	private static QuestItems.Total chisel()
	{
		QuestItems.Total total = new QuestItems.Total(ITEM, 0, 0L, 0L, false,
			Collections.emptyList(), Arrays.asList(RUNES, CRAFT));
		total.add(new QuestItems.Line(ITEM, 1, 0L, false), "Some Quest");
		return total;
	}

	@Test
	public void zeroStockIsExcluded()
	{
		ShopStock stock = new ShopStock();
		stock.record(RUNES, ITEM, 0, T0);
		assertTrue(stock.excludes(RUNES, ITEM, T0.plusSeconds(10)));

		QuestItems.Total total = chisel();
		total.applyShopStock(stock, T0.plusSeconds(10));
		assertEquals(Collections.singletonList(CRAFT), total.shopNames());
	}

	@Test
	public void restockReincludesTheShop()
	{
		ShopStock stock = new ShopStock();
		stock.record(RUNES, ITEM, 0, T0);
		stock.record(RUNES, ITEM, 12, T0.plusSeconds(60));
		assertFalse(stock.excludes(RUNES, ITEM, T0.plusSeconds(61)));

		QuestItems.Total total = chisel();
		total.applyShopStock(stock, T0.plusSeconds(61));
		assertEquals(Arrays.asList(RUNES, CRAFT), total.shopNames());
	}

	@Test
	public void zeroAgesIntoMayBeOutAndStaysASource()
	{
		ShopStock stock = new ShopStock();
		stock.record(RUNES, ITEM, 0, T0);
		Instant later = T0.plus(ShopStock.EMPTY_TTL).plusSeconds(1);
		assertEquals(ShopStock.State.MAY_BE_OUT, stock.state(RUNES, ITEM, later));

		QuestItems.Total total = chisel();
		total.applyShopStock(stock, later);
		assertEquals(Arrays.asList(RUNES, CRAFT), total.shopNames());
		assertTrue(total.mayBeOutAt(RUNES));
		assertFalse(total.mayBeOutAt(CRAFT));
	}

	@Test
	public void unknownStockIsRoutedToUnflagged()
	{
		ShopStock stock = new ShopStock();
		assertEquals(ShopStock.State.UNKNOWN, stock.state(RUNES, ITEM, T0));

		QuestItems.Total total = chisel();
		total.applyShopStock(stock, T0);
		assertEquals(Arrays.asList(RUNES, CRAFT), total.shopNames());
		assertFalse(total.mayBeOutAt(RUNES));
	}

	@Test
	public void replanPicksTheNextSourceAndClearForgets()
	{
		ShopStock stock = new ShopStock();
		stock.record(RUNES, ITEM, 0, T0);
		QuestItems.Total total = chisel();
		total.applyShopStock(stock, T0);
		assertEquals(CRAFT, total.shopNames().get(0));

		stock.clear();
		assertEquals(ShopStock.State.UNKNOWN, stock.state(RUNES, ITEM, T0));
	}
}
