package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The wire contract for the bundled quest graph, and the version gate that
 * keeps a shape this build does not understand from being parsed as if it
 * did.
 */
public class QuestGraphTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void parsesAnItemsObtainSources()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Pot\",\"sources\":[\"Shop: Leenz's General Supplies (1 Coins)\","
			+ "\"Shop: Little Munty's Little Shop (1 Coins)\"]}]}]}", QuestGraph.class);

		List<String> sources = graph.quests().get(0).items().get(0).sources();
		assertEquals(2, sources.size());
		assertEquals("Shop: Leenz's General Supplies (1 Coins)", sources.get(0));
	}

	/**
	 * An item line with no sources key at all -- an older export, or an item
	 * questpath knows no source for -- must parse to an empty list, never
	 * null, so the panel can render "no tooltip" without a null check at
	 * every call site.
	 */
	@Test
	public void anItemWithNoSourcesKeyParsesToAnEmptyList()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Rope\"}]}]}", QuestGraph.class);

		assertTrue(graph.quests().get(0).items().get(0).sources().isEmpty());
	}

	/**
	 * The item id the overlays draw the game's own sprite from.
	 *
	 * Sent by questpath rather than searched for by name in the client,
	 * because the name alone is ambiguous: "Bucket" matches a Meiyerditch
	 * bucket and an animation-only bucket as readily as the real one, and
	 * questpath's catalogue has already settled which is meant.
	 */
	@Test
	public void parsesAnItemsGameItemId()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Bucket\",\"item_id\":1925}]}]}", QuestGraph.class);

		assertEquals(1925, graph.quests().get(0).items().get(0).itemId());
	}

	/**
	 * An item questpath could not identify carries no id, and must arrive as
	 * 0 rather than as some other item.
	 *
	 * Zero is the "draw nothing" signal every icon caller checks. It is not
	 * item 0, which is a real item -- which is exactly why questpath omits
	 * the field instead of sending a zero, and why nothing downstream may
	 * treat 0 as an id to look up.
	 */
	@Test
	public void anItemWithNoIdParsesToZeroSoNothingIsDrawn()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Rope\"}]}]}", QuestGraph.class);

		assertEquals(0, graph.quests().get(0).items().get(0).itemId());
	}

	/**
	 * Pins the bump: a graph shape change must raise the version, and this
	 * constant is what load() refuses a mismatch against. A regression here
	 * would silently start parsing an old bundle's missing field as an
	 * absence rather than refusing the bundle outright -- 2 added
	 * Item.sources(), 3 added Item.itemId(), 4 added Item.unresolved(),
	 * 5 added boosts(), 6 added prerequisites_started_only, 7 added shops
	 * and Item.shop_names, 8 added Quest.start_x/start_y.
	 *
	 * <p>5, 6, 7 and 8 are the four that most need refusing rather than
	 * tolerating, and for the same reason: each carries information whose
	 * *absence* reads as a confident answer rather than as a gap. A
	 * requirement's boostable flag permits a boost and never says how large
	 * one can be, so a bundle without the boost table cannot tell a
	 * one-level shortfall from a fifty-level one. A bundle without
	 * prerequisites_started_only cannot tell "needs Desert Treasure I
	 * finished" from "needs it merely begun", so it demands completion of a
	 * quest that only has to be started, and blocks something the account
	 * can genuinely do. A bundle without shops and shop_names would parse an
	 * item's outstanding shops as "cannot be bought anywhere" rather than
	 * "this build does not carry that field yet" -- and the first of those
	 * is exactly the wrong answer to feed a shortest-path button. A bundle
	 * without start_x/start_y has nothing for {@link QuestGraph.Quest#hasStart()}
	 * to read but absence either way, which happens to degrade safely here --
	 * the arrival prompt simply never fires -- but only because {@code
	 * hasStart()} exists to be checked at all; refusing the version outright
	 * is what stops a future field from being added the same way and quietly
	 * misread.
	 */
	@Test
	public void supportedVersionWasBumpedForShopReachabilityAndDropLocations()
	{
		// 9 adds shop reachability and monster drop locations. Both are the
		// same kind of hazard the comment above describes: a bundle without
		// reachability parses every shop as open, which is precisely the
		// wrong answer to hand a shortest-path button -- it is how the
		// Fishing Guild Shop was offered to an account nine levels short of
		// entering. Refusing the version is what stops that being read as
		// "nothing stands in the way".
		assertEquals(9, QuestGraph.SUPPORTED_VERSION);
	}

	/**
	 * A wiki line the catalogue could not place arrives marked, with the
	 * wiki's own words as its name and no id.
	 *
	 * 354 of the wiki's 1,301 item lines are like this, and every one of them
	 * used to be dropped from the bundle without trace -- so a quest whose
	 * wiki page lists five items presented four and called it the list. "Any
	 * fruit blast excluding a premade fr' blast" is one of them: the item
	 * exists, the catalogue holds it as 2084, and the phrasing defeats the
	 * resolver.
	 */
	@Test
	public void anUnresolvedLineArrivesMarkedAndWithoutAnId()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Any fruit blast excluding a premade fr' blast\","
			+ "\"unresolved\":true}]}]}", QuestGraph.class);

		QuestGraph.Item item = graph.quests().get(0).items().get(0);
		assertTrue(item.unresolved());
		assertEquals(0, item.itemId());
		assertEquals("Any fruit blast excluding a premade fr' blast", item.name());
	}

	/** An ordinary item is not marked unresolved. */
	@Test
	public void aResolvedLineIsNotMarked()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":4,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Ashes\",\"item_id\":592}]}]}", QuestGraph.class);

		assertFalse(graph.quests().get(0).items().get(0).unresolved());
	}

	/**
	 * The top-level shop table version 7 introduced: a name to match against
	 * an item's shop_names and a tile a pathfinder can actually walk to.
	 */
	@Test
	public void parsesTheShopTable()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":7,\"quests\":[],"
			+ "\"shops\":[{\"name\":\"Tynan's Fishing Supplies\",\"x\":3053,\"y\":3237,\"town\":\"Catherby\"}]}",
			QuestGraph.class);

		QuestGraph.Shop shop = graph.shops().get(0);
		assertEquals("Tynan's Fishing Supplies", shop.name());
		assertEquals(3053, shop.x());
		assertEquals(3237, shop.y());
		assertEquals("Catherby", shop.town());
		assertEquals(0, shop.plane());
	}

	/** A graph with no shops key at all -- an older export -- parses to an empty list, never null. */
	@Test
	public void aGraphWithNoShopsKeyParsesToAnEmptyList()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":7,\"quests\":[]}", QuestGraph.class);

		assertTrue(graph.shops().isEmpty());
	}

	/**
	 * An item's shop_names names shops in the graph's own shop table by exact
	 * string, which {@link QuestGraph#shop} resolves back to a tile.
	 */
	@Test
	public void anItemsShopNamesResolveAgainstTheShopTable()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":7,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Bucket\",\"shop_names\":[\"Tynan's Fishing Supplies\"]}]}],"
			+ "\"shops\":[{\"name\":\"Tynan's Fishing Supplies\",\"x\":3053,\"y\":3237,\"town\":\"Catherby\"}]}",
			QuestGraph.class);

		List<String> shopNames = graph.quests().get(0).items().get(0).shopNames();
		assertEquals(1, shopNames.size());
		QuestGraph.Shop shop = graph.shop(shopNames.get(0));
		assertEquals(3053, shop.x());
		assertEquals(3237, shop.y());
	}

	/** An item with no shop_names key parses to an empty list, never null. */
	@Test
	public void anItemWithNoShopNamesKeyParsesToAnEmptyList()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":7,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Rope\"}]}]}", QuestGraph.class);

		assertTrue(graph.quests().get(0).items().get(0).shopNames().isEmpty());
	}

	/** A shop name the graph does not carry resolves to null, never a fabricated tile. */
	@Test
	public void anUnknownShopNameResolvesToNull()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":7,\"quests\":[]}", QuestGraph.class);

		assertNull(graph.shop("Nobody's Shop"));
	}

	/**
	 * The quest start point version 8 introduced: where the quest begins,
	 * not any per-step location -- see {@link QuestGraph#SUPPORTED_VERSION}'s
	 * own doc for why that distinction matters.
	 */
	@Test
	public void parsesAQuestsStartPoint()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":8,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"start_x\":3208,\"start_y\":3214}]}", QuestGraph.class);

		QuestGraph.Quest quest = graph.quests().get(0);
		assertTrue(quest.hasStart());
		assertEquals(3208, quest.startX());
		assertEquals(3214, quest.startY());
	}

	/**
	 * A quest with neither start_x nor start_y -- 13 of 218 in the real
	 * bundle -- must report hasStart() false rather than a fabricated (0, 0),
	 * which is itself a real, walkable tile.
	 */
	@Test
	public void aQuestWithNoStartKeysReportsNoStart()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":8,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\"}]}",
			QuestGraph.class);

		QuestGraph.Quest quest = graph.quests().get(0);
		assertFalse(quest.hasStart());
	}

	/** One quest by its exact name, the lookup the arrival trigger reads from. */
	@Test
	public void resolvesAQuestByExactName()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":8,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"Cook's Assistant\","
			+ "\"start_x\":3208,\"start_y\":3214}]}", QuestGraph.class);

		QuestGraph.Quest quest = graph.byName("Cook's Assistant");
		assertEquals(3208, quest.startX());
	}

	/** A quest name the graph does not carry resolves to null, never a fabricated quest. */
	@Test
	public void anUnknownQuestNameResolvesToNull()
	{
		QuestGraph graph = GSON.fromJson("{\"version\":8,\"quests\":[]}", QuestGraph.class);

		assertNull(graph.byName("Nobody's Quest"));
	}
}
