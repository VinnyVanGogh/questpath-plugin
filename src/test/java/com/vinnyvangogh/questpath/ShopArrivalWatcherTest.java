package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * When the "this shop sells something you need" line is allowed to fire, and
 * -- mostly -- when it is not.
 *
 * <p>The negative cases are the point, same as {@link
 * ItemCompletionWatcherTest}: a shop interface is re-observed every game
 * tick for as long as it stays open, and without the once-per-opening rule
 * this would print the same line dozens of times over one shopping trip.
 *
 * <p>Clay (item id 434) is Doric's Quest's own ingredient, same fixture
 * {@link ItemCompletionWatcherTest} uses, so both suites are pinned against
 * the same real bundled item rather than an invented one.
 */
public class ShopArrivalWatcherTest
{
	private static final Gson GSON = new Gson();
	private static final int CLAY_ITEM_ID = 434;

	private static PlayerState freshAccount()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
	}

	private static QuestItems items(Map<String, Long> held, boolean bankSeen) throws Exception
	{
		return QuestItems.of(QuestGraph.load(GSON), freshAccount(), held, bankSeen);
	}

	private static Map<String, Long> clay(long quantity)
	{
		Map<String, Long> out = new HashMap<>();
		out.put("clay", quantity);
		return out;
	}

	private static Set<Integer> sells(int... itemIds)
	{
		Set<Integer> out = new HashSet<>();
		for (int id : itemIds)
		{
			out.add(id);
		}
		return out;
	}

	/** The item Doric's Quest wants, still short. */
	private static QuestItems shortOnClay() throws Exception
	{
		return items(clay(0), true);
	}

	@Test
	public void nothingWhenTheShopNeverOpens() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		QuestItems items = shortOnClay();
		assertTrue(watcher.observe(false, sells(CLAY_ITEM_ID), items).isEmpty());
		assertTrue(watcher.observe(false, Collections.emptySet(), items).isEmpty());
	}

	/** The case this class exists for: a shop opens selling something still short. */
	@Test
	public void announcesTheMomentAMatchingShopOpens() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		List<QuestItems.Total> got = watcher.observe(true, sells(CLAY_ITEM_ID), shortOnClay());
		assertEquals(1, got.size());
		assertEquals("Clay", got.get(0).name());
	}

	/** Said once. The shop stays open and is re-observed every tick; the next tick has nothing new to say. */
	@Test
	public void announcesOnlyOncePerOpening() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		QuestItems items = shortOnClay();
		assertEquals(1, watcher.observe(true, sells(CLAY_ITEM_ID), items).size());
		assertTrue("still open, nothing new to say",
			watcher.observe(true, sells(CLAY_ITEM_ID), items).isEmpty());
		assertTrue("still open on a third tick, same story",
			watcher.observe(true, sells(CLAY_ITEM_ID), items).isEmpty());
	}

	/** Closing and reopening is a new arrival, worth saying again. */
	@Test
	public void reopeningAnnouncesAgain() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		QuestItems items = shortOnClay();
		assertEquals(1, watcher.observe(true, sells(CLAY_ITEM_ID), items).size());
		assertTrue(watcher.observe(false, Collections.emptySet(), items).isEmpty());
		assertEquals(1, watcher.observe(true, sells(CLAY_ITEM_ID), items).size());
	}

	/** A shop that sells none of what is outstanding says nothing, whatever else it stocks. */
	@Test
	public void noMatchWhenTheShopSellsNothingNeeded() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		assertTrue(watcher.observe(true, sells(9999999), shortOnClay()).isEmpty());
	}

	/** Already satisfied is not worth a prompt -- nothing left to buy here. */
	@Test
	public void noMatchWhenAlreadySatisfied() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		QuestItems satisfied = items(clay(6), true);
		assertTrue(watcher.observe(true, sells(CLAY_ITEM_ID), satisfied).isEmpty());
	}

	/** No item list loaded yet is "nothing to say", never a guess. */
	@Test
	public void nullItemsIsTreatedAsNothingToSay()
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		assertTrue(watcher.observe(true, sells(CLAY_ITEM_ID), null).isEmpty());
	}

	/** Logging out drops the open/closed baseline: the next login may find the player already inside a different shop. */
	@Test
	public void resetForgetsTheOpenState() throws Exception
	{
		ShopArrivalWatcher watcher = new ShopArrivalWatcher();
		QuestItems items = shortOnClay();
		assertEquals(1, watcher.observe(true, sells(CLAY_ITEM_ID), items).size());
		watcher.reset();
		assertEquals("a reset opening is a fresh arrival again",
			1, watcher.observe(true, sells(CLAY_ITEM_ID), items).size());
	}
}
