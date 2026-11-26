package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every item and shop the shipped bundle can name has a row in the bundled
 * word list -- and, once clips are rendered, a clip.
 *
 * <p>The names come from {@code quests.json} through {@link QuestGraph}, the
 * same loader the plugin uses: every item line with an item id or a shop,
 * every member of an "X or Y" group, every shop. When the bundle grows, re-run
 * {@code tools/voice/build_lines.py} and this passes again.
 *
 * <p>The clip check is skipped, loudly, only while no clip at all is bundled
 * (the list is committed before the Board session renders it). The moment one
 * WAV exists, every row must have one.
 */
public class VoiceLinesCoverageTest
{
	private static QuestGraph graph;
	private static VoiceLines voice;

	@BeforeClass
	public static void load() throws IOException
	{
		try (InputStream in = QuestGraph.class.getResourceAsStream("quests.json"))
		{
			graph = QuestGraph.parse(new Gson(), new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		voice = VoiceLines.load();
	}

	/** Every item name a quest line can carry into a shop or floor line. */
	private static Set<String> spokenItems(boolean soldOnly)
	{
		Set<String> names = new TreeSet<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			List<QuestGraph.Item> lines = new ArrayList<>(quest.items());
			lines.addAll(quest.recommendedItems());
			for (QuestGraph.Item item : lines)
			{
				boolean sells = !item.shopNames().isEmpty();
				if (soldOnly ? sells : (item.itemId() > 0 || sells))
				{
					names.add(item.name());
				}
				if (!soldOnly || sells)
				{
					for (QuestGraph.Alternative alternative : item.alternatives())
					{
						names.add(alternative.name());
					}
				}
			}
		}
		return names;
	}

	@Test
	public void theWordListIsBundled()
	{
		assertFalse("voice/lines.tsv is missing or empty", voice.clips().isEmpty());
	}

	@Test
	public void everyShippableItemHasARow()
	{
		List<String> missing = new ArrayList<>();
		for (String name : spokenItems(false))
		{
			if (!voice.has("item", name))
			{
				missing.add(name);
			}
		}
		assertTrue("items with no voice row (re-run tools/voice/build_lines.py): " + missing, missing.isEmpty());
	}

	@Test
	public void everyItemAShopSellsHasAPlural()
	{
		List<String> missing = new ArrayList<>();
		for (String name : spokenItems(true))
		{
			if (!voice.has("plural", name))
			{
				missing.add(name);
			}
		}
		assertTrue("sold items with no plural row: " + missing, missing.isEmpty());
	}

	@Test
	public void everyShopHasARow()
	{
		List<String> missing = new ArrayList<>();
		for (QuestGraph.Shop shop : graph.shops())
		{
			if (!voice.has("shop", shop.name()))
			{
				missing.add(shop.name());
			}
		}
		assertTrue("shops with no voice row: " + missing, missing.isEmpty());
	}

	@Test
	public void everyFixedPhraseAndNumberIsListed()
	{
		Map<String, String> clips = voice.clips();
		for (String phrase : new String[]{VoiceLines.LOOKING_FOR, VoiceLines.AND, VoiceLines.AT, VoiceLines.NEARBY,
			VoiceLines.A, VoiceLines.AN, VoiceLines.HUNDRED, VoiceLines.THOUSAND})
		{
			assertTrue("phrase " + phrase, clips.containsKey(phrase));
		}
		for (int n = 1; n <= 100; n++)
		{
			assertEquals(Integer.toString(n), clips.get("num-" + n));
		}
	}

	@Test
	public void everyRowHasItsClipOnceAnyIsRendered()
	{
		List<String> missing = new ArrayList<>();
		int present = 0;
		for (String clip : voice.clips().keySet())
		{
			if (VoiceLines.bundled(VoiceLines.path(clip)))
			{
				present++;
			}
			else
			{
				missing.add(clip);
			}
		}
		Assume.assumeTrue("NO VOICE CLIPS RENDERED YET: " + missing.size() + " rows wait for"
			+ " tools/voice/generate_voice.py; every line falls back to ping + text until then", present > 0);
		assertTrue(missing.size() + " clips missing: " + missing, missing.isEmpty());
	}
}
