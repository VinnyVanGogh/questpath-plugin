package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Pins {@link GrandExchangeButton#resultIds}: what the client's own search
 * result list gets handed when questpath's button is pressed.
 *
 * <p>This is the whole payload of the feature. Nothing is drawn -- the game
 * renders these ids itself, with its own rows and its own "Select &lt;item&gt;"
 * option -- so an id in this array is an item questpath is telling the player
 * to buy, and every rule about what may be suggested has to hold here or
 * nowhere.
 *
 * <p>No client is needed: {@code resultIds} reads only the supplied list.
 */
public class GrandExchangeButtonTest
{
	private static QuestItems.Total total(String name, int itemId, int wanted, long owned)
	{
		QuestItems.Total total = new QuestItems.Total(name, itemId, owned, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		total.add(new QuestItems.Line(name, wanted, owned, false), "Sheep Herder");
		return total;
	}

	private static GrandExchangeButton buttonOver(List<GrandExchangeShoppingList.Line> lines)
	{
		return new GrandExchangeButton(null, Runnable::run, () -> lines);
	}

	/** Alphabetical, ids only, in the order the list itself is in. */
	@Test
	public void resultsAreTheBuyListInItsOwnOrder()
	{
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Arrays.asList(
			total("Rope", 954, 1, 0),
			total("Bucket", 1925, 1, 0)));

		assertArrayEquals(new short[]{1925, 954}, buttonOver(lines).resultIds());
	}

	/**
	 * An item already on offer is not offered again.
	 *
	 * <p>Same rule as the overlay's Next row and for the same reason: a slot
	 * already spent on something is the one thing the list must not tell you
	 * to go and buy.
	 */
	@Test
	public void itemsAlreadyOnOfferAreNotListed()
	{
		GrandExchangeOffers offers = new GrandExchangeOffers();
		offers.update(0, GrandExchangeOfferState.BUYING, 1925, "Bucket", 1, 0);

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Arrays.asList(
			total("Rope", 954, 1, 0),
			total("Bucket", 1925, 1, 0)), offers);

		assertArrayEquals(new short[]{954}, buttonOver(lines).resultIds());
	}

	/**
	 * Karamjan rum never reaches the client's result list.
	 *
	 * <p>It cannot be bought at a Grand Exchange, and these rows carry a real
	 * "Select" option -- so listing it would be handing the player a working
	 * control for something that cannot happen.
	 */
	@Test
	public void untradeableItemsAreNotListed()
	{
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			Arrays.asList(
				total("Karamjan rum", 431, 1, 0),
				total("Rope", 954, 1, 0)),
			null,
			(itemId, name) -> "Karamjan rum".equals(name)
				? GrandExchangeShoppingList.Availability.NOT_ON_GE
				: GrandExchangeShoppingList.Availability.BUYABLE);

		assertArrayEquals(new short[]{954}, buttonOver(lines).resultIds());
	}

	/**
	 * Nothing to buy yields an empty array, not a null and not a stale one.
	 *
	 * <p>The caller hands this straight to {@code setGeSearchResultCount} and
	 * {@code setGeSearchResultIds}, so an empty array is what makes the panel
	 * honestly empty. Returning null there would leave the previous search's
	 * results on screen under questpath's own title.
	 */
	@Test
	public void anEmptyListYieldsAnEmptyArray()
	{
		assertEquals(0, buttonOver(Collections.emptyList()).resultIds().length);
		assertEquals(0, buttonOver(null).resultIds().length);
	}

	/**
	 * Capped at what the client itself will render.
	 *
	 * <p>Both RuneLite's own Grand Exchange plugin and Quest Helper limit
	 * their id arrays to the same figure before handing them over.
	 */
	@Test
	public void resultsAreCappedAtTheClientsOwnLimit()
	{
		List<QuestItems.Total> totals = new java.util.ArrayList<>();
		for (int i = 0; i < GrandExchangeButton.MAX_RESULTS + 20; i++)
		{
			totals.add(total(String.format("Item %04d", i), 1000 + i, 1, 0));
		}

		assertEquals(GrandExchangeButton.MAX_RESULTS,
			buttonOver(GrandExchangeShoppingList.of(totals)).resultIds().length);
	}

	/**
	 * A sidebar row click answers with that one id, through the sentinel --
	 * the narrow form of the single-item search. What is written into the
	 * chatbox input is the sentinel and nothing else: never the item's name,
	 * which is the Hub's autotyping rule.
	 */
	@Test
	public void showOneAnswersWithThatIdThroughTheSentinelOnly()
	{
		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(Arrays.asList(
			total("Rope", 954, 1, 0),
			total("Bucket", 1925, 1, 0)));
		List<String> written = new java.util.ArrayList<>();
		GrandExchangeButton button = new GrandExchangeButton(recordingClient(written), Runnable::run, () -> lines);

		button.showOne(954);

		assertArrayEquals(new short[]{954}, button.resultIds());
		assertEquals(List.of(GrandExchangeButton.SEARCH_SENTINEL), written);
	}

	/** A test-only Client that records varc string writes and answers null to everything else. */
	private static net.runelite.api.Client recordingClient(List<String> written)
	{
		return (net.runelite.api.Client) java.lang.reflect.Proxy.newProxyInstance(
			GrandExchangeButtonTest.class.getClassLoader(),
			new Class<?>[]{net.runelite.api.Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("setVarcStrValue"))
				{
					written.add((String) args[1]);
				}
				return null;
			});
	}
}
