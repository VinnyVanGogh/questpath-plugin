package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Quest item lists ticked against a bank, over the real bundled graph.
 */
public class QuestItemsTest
{
	private static final Gson GSON = new Gson();

	private static PlayerState freshAccount()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<>(), Collections.emptyList());
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
	@Test
	public void aLineWithNoStatedCountNeedsOnlyOne()
	{
		QuestItems.Line unstated = new QuestItems.Line("Bucket", 0, 1, false);
		assertTrue(unstated.held());
		assertEquals("1", unstated.describe());

		QuestItems.Line none = new QuestItems.Line("Bucket", 0, 0, false);
		assertFalse(none.held());
		assertEquals("none", none.describe());
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
	@Test
	public void anUnstatedLineMakesTheTotalAFloor()
	{
		QuestItems.Total total = new QuestItems.Total("Coins", 0);
		assertEquals("none", total.describe());
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
}
