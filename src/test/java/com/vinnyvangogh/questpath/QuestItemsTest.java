package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Quest item lists ticked against a bank, over the real bundled graph.
 */
public class QuestItemsTest
{
	private static final Gson GSON = new Gson();

	private static PlayerState freshAccount()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(), Collections.emptyList());
	}

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	@Test
	public void listsOnlyQuestsTheAccountCanActuallyStart() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		assertFalse("a fresh account can start something with an item list", items.isEmpty());
		assertTrue("every doable quest with an item list is shown, not the first twelve",
			items.quests().size() > 12);
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			assertFalse(needs.questName().isEmpty());
			assertFalse(needs.questName() + " was listed with no items", needs.lines().isEmpty());
		}
	}

	/**
	 * The point of the whole plugin: an empty bank and a full one give
	 * different answers for the same quest.
	 */
	@Test
	public void holdingTheItemsChangesTheAnswer() throws Exception
	{
		QuestItems empty = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);
		QuestItems.QuestNeeds first = empty.quests().get(0);
		assertEquals("an empty bank satisfies nothing", 0, first.have());
		assertFalse(first.ready());

		Map<String, Long> bank = new HashMap<>();
		for (QuestItems.Line line : first.lines())
		{
			bank.put(line.name().toLowerCase(), 100L);
		}

		QuestItems stocked = QuestItems.of(graph(), freshAccount(), bank, true);
		QuestItems.QuestNeeds sameQuest = stocked.quests().get(0);
		assertEquals(first.questName(), sameQuest.questName());
		assertEquals("a stocked bank satisfies every required line",
			sameQuest.needed(), sameQuest.have());
		assertTrue(sameQuest.ready());
	}

	/**
	 * A line with no stated count is satisfied by holding any at all. The wiki
	 * did not say how many, and inventing a threshold would fail a line for
	 * missing a number nobody set.
	 */
	/**
	 * A line the wiki gave no count for needs one, and says so. "none" told
	 * you nothing about whether you were short.
	 */
	@Test
	public void aLineWithNoStatedCountNeedsOnlyOne()
	{
		QuestItems.Line unstated = new QuestItems.Line("Bucket", 0, 1, false);
		assertTrue(unstated.held());
		assertEquals("1 / 1", unstated.describe());

		QuestItems.Line none = new QuestItems.Line("Bucket", 0, 0, false);
		assertFalse(none.held());
		assertEquals("0 / 1", none.describe());
	}

	@Test
	public void aStatedCountMustActuallyBeMet()
	{
		assertFalse(new QuestItems.Line("Egg", 3, 2, false).held());
		assertTrue(new QuestItems.Line("Egg", 3, 3, false).held());
		assertTrue("more than enough is enough", new QuestItems.Line("Egg", 3, 9, false).held());
		assertEquals("2 / 3", new QuestItems.Line("Egg", 3, 2, false).describe());
	}

	/**
	 * Optional lines are shown but do not count towards readiness. A quest is
	 * not held up by something the wiki called optional.
	 */
	/**
	 * A bank nobody has opened is unknown, not empty. The client does not hold
	 * the bank until it has been opened once, so counts taken before that are
	 * inventory only -- and a row reading "none" would claim you do not own
	 * something sitting in a bank nobody looked in.
	 */
	@Test
	public void anUnreadBankIsFlaggedRatherThanReportedAsEmpty() throws Exception
	{
		QuestItems unread = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), false);
		assertFalse(unread.bankSeen());

		QuestItems read = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);
		assertTrue(read.bankSeen());
	}

	@Test
	public void optionalLinesDoNotDecideReadiness()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Cook's Assistant");
		needs.lines().add(new QuestItems.Line("Bucket of milk", 0, 1, false));
		needs.lines().add(new QuestItems.Line("Bucket", 0, 0, true));

		assertEquals(1, needs.needed());
		assertEquals(1, needs.have());
		assertTrue("an unheld optional item must not block readiness", needs.ready());
	}

	/**
	 * One shopping list across every quest. Coins wanted by two quests is one
	 * trip to the bank for the sum, not two lines to add up in your head.
	 */
	@Test
	public void totalsSumTheSameItemAcrossQuests() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		QuestItems.Total coins = null;
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equalsIgnoreCase("Coins"))
			{
				coins = total;
			}
		}

		assertTrue("several doable quests want coins", coins != null && coins.quests() > 1);

		int perQuest = 0;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equalsIgnoreCase("Coins") && !line.optional())
				{
					perQuest += line.wanted();
				}
			}
		}
		assertTrue("the total must be the sum of the lines, not one of them",
			coins.describe().startsWith("0 / " + perQuest));
		assertTrue(coins.sharedAcrossQuests());
	}

	/**
	 * Optional lines are advice, not shopping. Folding them into the totals
	 * would inflate every one with things nobody has to bring.
	 */
	@Test
	public void totalsExcludeOptionalLines() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		for (QuestItems.Total total : items.totals())
		{
			int required = 0;
			for (QuestItems.QuestNeeds needs : items.quests())
			{
				for (QuestItems.Line line : needs.lines())
				{
					if (line.name().equals(total.name()) && !line.optional())
					{
						required++;
					}
				}
			}
			assertEquals(total.name() + " counts a quest that only wants it optionally",
				required, total.quests());
		}
	}

	/**
	 * A total whose lines did not all state a count is a floor, not a guess.
	 * "61+" means at least 61; rounding an unstated line up to 1 would invent
	 * a number the wiki never gave.
	 */
	/**
	 * The bug that produced "Ghostspeak amulet x3": three quests want one
	 * amulet, and one is all anybody needs. What the wiki leaves uncounted is
	 * almost always a tool you keep rather than something you spend, so
	 * unstated lines contribute one no matter how many quests want them.
	 */
	@Test
	public void anUnstatedItemCountsOnceHoweverManyQuestsWantIt()
	{
		QuestItems.Total tool = new QuestItems.Total("Ghostspeak amulet", 0);
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));

		assertEquals("three quests wanting one amulet is one amulet", 1, tool.required());
		assertEquals("0 / 1", tool.describe());
		assertTrue(tool.sharedAcrossQuests());
	}

	/**
	 * Stated counts do add up. The wiki states a number for what a quest
	 * consumes, and two quests each spending coins really do want the sum.
	 */
	@Test
	public void statedCountsAddUpAcrossQuests()
	{
		QuestItems.Total coins = new QuestItems.Total("Coins", 500);
		coins.add(new QuestItems.Line("Coins", 60, 500, false));
		coins.add(new QuestItems.Line("Coins", 1, 500, false));

		assertEquals(61, coins.required());
		assertEquals("500 / 61", coins.describe());
		assertTrue(coins.satisfied());
	}

	/** A mix adds the stated counts and one for the uncounted tool. */
	@Test
	public void aMixOfStatedAndUnstatedAddsOneForTheTool()
	{
		QuestItems.Total mixed = new QuestItems.Total("Logs", 0);
		mixed.add(new QuestItems.Line("Logs", 10, 0, false));
		mixed.add(new QuestItems.Line("Logs", 0, 0, false));

		assertEquals(11, mixed.required());
		assertEquals("0 / 11", mixed.describe());
	}

	/** Totals put what you are missing first; a long tail of things you have would bury it. */
	@Test
	public void totalsPutWhatIsMissingFirst() throws Exception
	{
		Map<String, Long> bank = new HashMap<>();
		bank.put("coins", 1_000_000L);

		QuestItems items = QuestItems.of(graph(), freshAccount(), bank, true);
		boolean seenSatisfied = false;
		for (QuestItems.Total total : items.totals())
		{
			if (total.satisfied())
			{
				seenSatisfied = true;
			}
			else
			{
				assertFalse("an unsatisfied total must not sit below a satisfied one", seenSatisfied);
			}
		}
	}

	/**
	 * The pickup list is only worth anything while you are inside the quest,
	 * so it is driven by what the client calls in progress -- not by Quest
	 * Helper's selection, which lives in another plugin's memory with no
	 * config key, no event and no shared class to reach it through.
	 */
	@Test
	public void nothingToGrabWhenNoQuestIsUnderWay() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true,
			Collections.emptySet());
		assertTrue(items.pickups().isEmpty());
	}

	/**
	 * Something you can pick up inside a quest you are doing, that another
	 * quest on the list also wants, is the one thing worth interrupting the
	 * shopping list for.
	 */
	@Test
	public void aQuestUnderWaySurfacesWhatItCanSupply() throws Exception
	{
		QuestGraph graph = graph();

		// Find a quest with an obtainable item that something else wants.
		String questName = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (item.obtainable())
				{
					questName = quest.name();
					break;
				}
			}
			if (questName != null)
			{
				break;
			}
		}
		assertNotNull("the bundled graph carries no obtainable items at all", questName);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true,
			Collections.singleton(questName));

		for (QuestItems.Pickup pickup : items.pickups())
		{
			assertEquals(questName, pickup.questName());
			assertFalse(pickup.itemName().isEmpty());
			assertTrue("a pickup nobody wants is noise", pickup.alsoWantedBy() >= 1);
		}
	}

	/** An item already held is not worth a detour. */
	@Test
	public void nothingSatisfiedIsSuggestedAsAPickup() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Long> stocked = new HashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				stocked.put(item.name().toLowerCase(), 10_000L);
			}
		}

		java.util.Set<String> everything = new java.util.HashSet<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			everything.add(quest.name());
		}

		QuestItems items = QuestItems.of(graph, freshAccount(), stocked, true, everything);
		assertTrue("with a full bank there is nothing to detour for", items.pickups().isEmpty());
	}

	/**
	 * The bundled graph carries obtain sources for most quest items (questpath
	 * only omits one when it genuinely knows none), and QuestItems must carry
	 * them through onto both the per-quest line and the merged total -- that
	 * is the whole path a hover in the Items tab reads from.
	 */
	@Test
	public void obtainSourcesReachBothTheLineAndItsTotal() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithSources = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.sources().isEmpty())
				{
					itemWithSources = item.name();
					break;
				}
			}
			if (itemWithSources != null)
			{
				break;
			}
		}
		assertNotNull("the bundled graph carries no item with a known source at all", itemWithSources);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);

		boolean lineHadSources = false;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithSources))
				{
					assertFalse(line.sources().isEmpty());
					for (String source : line.sources())
					{
						assertTrue(source.startsWith("Shop: ") || source.startsWith("Drop: "));
					}
					lineHadSources = true;
				}
			}
		}
		assertTrue("the item was in the graph with sources but no line carried them", lineHadSources);

		boolean totalHadSources = false;
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equals(itemWithSources))
			{
				assertFalse(total.sources().isEmpty());
				totalHadSources = true;
			}
		}
		assertTrue("the item's total did not carry its sources", totalHadSources);
	}

	/**
	 * An item the wiki gives no obtain source for must carry none through --
	 * never an empty placeholder mistaken for "not loaded" and never an
	 * invented one.
	 */
	@Test
	public void anItemWithNoKnownSourceCarriesAnEmptyListNotNull() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithoutSources = null;
		outer:
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (item.sources().isEmpty())
				{
					itemWithoutSources = item.name();
					break outer;
				}
			}
		}
		assertNotNull("the bundled graph has no item lacking a source; nothing to pin here", itemWithoutSources);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithoutSources))
				{
					assertNotNull(line.sources());
					assertTrue(line.sources().isEmpty());
				}
			}
		}
	}

	/**
	 * A line questpath could not match to an item is listed but not counted.
	 *
	 * It cannot be ticked -- there is nothing to tick it against -- so
	 * counting it as missing would make "4 of 5" a ratio that never reaches
	 * 5 however much you buy. It still appears, and unresolvedCount says how
	 * many there are, because dropping it is what made these lists quietly
	 * short in the first place.
	 */
	@Test
	public void anUnresolvedLineIsListedButNotCounted()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Recipe for Disaster/Another Cook's Quest");
		needs.lines().add(new QuestItems.Line("Ashes", 592, 1, 1, false, false, null));
		needs.lines().add(new QuestItems.Line("Eye of newt", 221, 1, 0, false, false, null));
		needs.lines().add(new QuestItems.Line("Any fruit blast excluding a premade fr' blast",
			0, 0, 0, false, true, null));

		assertEquals(3, needs.lines().size());
		assertEquals("the unresolved line is neither held nor missing", 2, needs.needed());
		assertEquals(1, needs.have());
		assertEquals(1, needs.unresolvedCount());
	}
}
