package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What "after more work" actually resolves to, over the real bundled graph.
 *
 * <p>The transitive part is the part worth testing. Reporting only the
 * immediate blocker is true and useless, and it is the failure this class
 * exists to avoid.
 */
public class QuestBlockersTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	private static PlayerState fresh()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
	}

	private static QuestGraph.Quest quest(QuestGraph graph, String name)
	{
		for (QuestGraph.Quest quest : graph.quests())
		{
			if (quest.name().equalsIgnoreCase(name))
			{
				return quest;
			}
		}
		return null;
	}

	/**
	 * The whole chain, not the first link. Desert Treasure I sits behind a
	 * long line of quests on a fresh account.
	 */
	@Test
	public void theChainIsWalkedAllTheWayDown() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Quest deep = quest(graph, "Desert Treasure I");
		assertNotNull("the bundle carries Desert Treasure I", deep);

		QuestBlockers blockers = QuestBlockers.of(graph, fresh(), deep);
		assertTrue("a deep quest names more than its immediate prerequisites",
			blockers.quests().size() > 3);
		assertFalse("and it does not name itself as its own cost",
			blockers.quests().contains(deep.name()));
	}

	/**
	 * A prerequisite always appears before the quest that needs it, so the
	 * list can be followed top to bottom.
	 */
	@Test
	public void prerequisitesComeBeforeWhatNeedsThem() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Quest deep = quest(graph, "Desert Treasure I");
		QuestBlockers blockers = QuestBlockers.of(graph, fresh(), deep);

		java.util.List<String> order = blockers.quests();
		for (int i = 0; i < order.size(); i++)
		{
			QuestGraph.Quest listed = quest(graph, order.get(i));
			assertNotNull(listed);
			for (long prerequisiteId : listed.prerequisites())
			{
				QuestGraph.Quest prerequisite = graph.get(prerequisiteId);
				if (prerequisite == null)
				{
					continue;
				}
				int at = order.indexOf(prerequisite.name());
				assertTrue(prerequisite.name() + " must be listed before " + listed.name(),
					at < 0 || at < i);
			}
		}
	}

	/** Completed prerequisites are not work, and are left out entirely. */
	@Test
	public void whatIsAlreadyDoneIsNotListed() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Quest deep = quest(graph, "Desert Treasure I");

		QuestBlockers before = QuestBlockers.of(graph, fresh(), deep);
		String first = before.quests().get(0);

		PlayerState done = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<>(Collections.singleton(first)), Collections.emptyList());
		QuestBlockers after = QuestBlockers.of(graph, done, deep);

		assertFalse(first + " was still listed after being completed",
			after.quests().contains(first));
		assertTrue(after.quests().size() < before.quests().size());
	}

	/**
	 * One row per skill, holding the highest level anything in the chain
	 * asks for. Two rows for the same skill would double-count work done once.
	 */
	@Test
	public void eachSkillIsNamedOnceAtItsHighestRequirement() throws Exception
	{
		QuestGraph graph = graph();
		QuestBlockers blockers = QuestBlockers.of(graph, fresh(), quest(graph, "Desert Treasure I"));

		java.util.Set<String> seen = new HashSet<>();
		for (QuestBlockers.Gap gap : blockers.skills())
		{
			assertTrue(gap.skill() + " was listed twice", seen.add(gap.skill().toLowerCase()));
			assertTrue("a gap is a shortfall, not a met requirement", gap.need() > gap.have());
		}
	}

	/** Biggest shortfall first: the level furthest away sets the timescale. */
	@Test
	public void theWorstShortfallLeads() throws Exception
	{
		QuestGraph graph = graph();
		QuestBlockers blockers = QuestBlockers.of(graph, fresh(), quest(graph, "Desert Treasure I"));

		java.util.List<QuestBlockers.Gap> gaps = blockers.skills();
		for (int i = 1; i < gaps.size(); i++)
		{
			assertTrue("gaps are ordered by shortfall, descending",
				gaps.get(i - 1).shortfall() >= gaps.get(i).shortfall());
		}
	}

	/** An account that has done everything has nothing standing in its way. */
	@Test
	public void nothingBlocksAQuestWhoseChainIsDone() throws Exception
	{
		QuestGraph graph = graph();
		QuestGraph.Quest cooksAssistant = quest(graph, "Cook's Assistant");
		assertNotNull(cooksAssistant);

		// No prerequisites and no level requirements, so a fresh account is
		// already clear.
		QuestBlockers blockers = QuestBlockers.of(graph, fresh(), cooksAssistant);
		assertEquals(Collections.emptyList(), blockers.quests());
		assertTrue(blockers.isEmpty());
	}
}
