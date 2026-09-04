package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	 * 5 added boosts(), 6 added prerequisites_started_only.
	 *
	 * <p>5 and 6 are the two that most need refusing rather than tolerating,
	 * and for the same reason: both carry information whose *absence* reads
	 * as a confident answer rather than as a gap. A requirement's boostable
	 * flag permits a boost and never says how large one can be, so a bundle
	 * without the boost table cannot tell a one-level shortfall from a
	 * fifty-level one. A bundle without prerequisites_started_only cannot
	 * tell "needs Desert Treasure I finished" from "needs it merely begun",
	 * so it demands completion of a quest that only has to be started, and
	 * blocks something the account can genuinely do.
	 */
	@Test
	public void supportedVersionWasBumpedForStartedOnlyPrerequisites()
	{
		assertEquals(6, QuestGraph.SUPPORTED_VERSION);
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
}
