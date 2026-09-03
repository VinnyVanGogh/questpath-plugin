package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
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
		QuestGraph graph = GSON.fromJson("{\"version\":2,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
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
		QuestGraph graph = GSON.fromJson("{\"version\":2,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Rope\"}]}]}", QuestGraph.class);

		assertTrue(graph.quests().get(0).items().get(0).sources().isEmpty());
	}

	/**
	 * Pins the bump: a graph shape change (Item.sources()) must raise the
	 * version, and this constant is what load() refuses a mismatch against.
	 * A regression here would silently start parsing an old bundle's missing
	 * field as "no sources" instead of refusing it outright.
	 */
	@Test
	public void supportedVersionWasBumpedForSources()
	{
		assertEquals(2, QuestGraph.SUPPORTED_VERSION);
	}
}
