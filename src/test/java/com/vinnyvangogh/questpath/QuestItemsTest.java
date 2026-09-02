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
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap());

		assertFalse("a fresh account can start something with an item list", items.isEmpty());
		assertTrue("no more than a screenful of quests", items.quests().size() <= 3);
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
		QuestItems empty = QuestItems.of(graph(), freshAccount(), Collections.emptyMap());
		QuestItems.QuestNeeds first = empty.quests().get(0);
		assertEquals("an empty bank satisfies nothing", 0, first.have());
		assertFalse(first.ready());

		Map<String, Long> bank = new HashMap<>();
		for (QuestItems.Line line : first.lines())
		{
			bank.put(line.name().toLowerCase(), 100L);
		}

		QuestItems stocked = QuestItems.of(graph(), freshAccount(), bank);
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
}
