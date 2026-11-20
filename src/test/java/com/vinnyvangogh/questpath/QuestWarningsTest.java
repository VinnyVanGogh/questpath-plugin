package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Quest warnings over the real bundled graph: what triggers one, what never
 * does, and that each trigger says it once.
 */
public class QuestWarningsTest
{
	private static final String DS1 = "Dragon Slayer I";

	private QuestGraph graph;
	private boolean enabled;
	private Set<String> muted;
	private List<QuestWarning> shown;
	private QuestWarnings warnings;

	@Before
	public void setUp() throws Exception
	{
		graph = QuestGraph.load(new Gson());
		enabled = true;
		muted = new HashSet<>();
		shown = new ArrayList<>();
		warnings = new QuestWarnings(() -> enabled, muted::contains, shown::add);
	}

	private PlayerState account(int level, int questPoints, Set<String> completed)
	{
		Map<String, Integer> levels = new HashMap<>();
		for (String skill : new String[]{"Attack", "Strength", "Defence", "Hitpoints", "Ranged",
			"Prayer", "Magic", "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking",
			"Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer",
			"Farming", "Runecraft", "Hunter", "Construction"})
		{
			levels.put(skill, level);
		}
		return new PlayerState(levels, new HashMap<>(), completed, Collections.emptyList())
			.withQuestPoints(questPoints);
	}

	/** A maxed account that has finished every quest except the named one. */
	private PlayerState maxedExcept(String quest)
	{
		Set<String> done = new HashSet<>();
		for (QuestGraph.Quest q : graph.quests())
		{
			if (!q.name().equals(quest))
			{
				done.add(q.name());
			}
		}
		return account(99, 300, done);
	}

	private PlayerState fresh()
	{
		return account(1, 0, new HashSet<>());
	}

	@Test
	public void everyQuestNameResolvesToItself()
	{
		for (QuestGraph.Quest quest : graph.quests())
		{
			assertEquals(quest.name(), quest.id(), QuestWarning.resolve(graph, quest.name()).id());
			assertNotNull(quest.name(), QuestWarning.resolve(graph, quest.name().toUpperCase()));
			assertNotNull(quest.name(), QuestWarning.resolve(graph, quest.name().replace('\'', '’')));
		}
	}

	@Test
	public void unknownAndBlankNamesResolveToNothing()
	{
		assertNull(QuestWarning.resolve(graph, "Not A Real Quest"));
		assertNull(QuestWarning.resolve(graph, ""));
		assertNull(QuestWarning.resolve(graph, null));
	}

	@Test
	public void selectingAQuestYouCannotDoWarnsWithWhatIsMissing()
	{
		warnings.onSelected(DS1, graph, fresh());
		assertEquals(1, shown.size());
		QuestWarning warning = shown.get(0);
		assertTrue(warning.lines().get(0).startsWith("32 quest points (you have 0)"));
	}

	@Test
	public void metRequirementsNeverWarn()
	{
		warnings.onSelected(DS1, graph, maxedExcept(DS1));
		assertTrue(shown.isEmpty());
	}

	@Test
	public void aFinishedQuestNeverWarns()
	{
		Set<String> done = new HashSet<>();
		done.add(DS1);
		warnings.onSelected(DS1, graph, account(1, 0, done));
		assertTrue(shown.isEmpty());
	}

	@Test
	public void anUnknownSelectedNameIsSilent()
	{
		warnings.onSelected("Quest Helper Only Thing", graph, fresh());
		assertTrue(shown.isEmpty());
	}

	@Test
	public void switchedOffIsSilentOnBothSignals()
	{
		enabled = false;
		warnings.onSelected(DS1, graph, fresh());
		warnings.onQuestStates(Collections.emptySet(), graph, fresh());
		warnings.onQuestStates(Collections.singleton(DS1), graph, fresh());
		assertTrue(shown.isEmpty());
	}

	@Test
	public void aMutedQuestIsSilent()
	{
		muted.add(DS1);
		warnings.onSelected(DS1, graph, fresh());
		assertTrue(shown.isEmpty());
	}

	@Test
	public void aSelectionWarnsOncePerSelection()
	{
		warnings.onSelected(DS1, graph, fresh());
		warnings.onSelected(DS1, graph, fresh());
		assertEquals(1, shown.size());

		warnings.onSelected(null, graph, fresh());
		warnings.onSelected(DS1, graph, fresh());
		assertEquals("clearing then picking again is a new selection", 2, shown.size());
	}

	@Test
	public void startingAQuestWarnsOnTheTransitionOnly()
	{
		warnings.onQuestStates(Collections.emptySet(), graph, fresh());
		assertTrue(shown.isEmpty());
		warnings.onQuestStates(Collections.singleton(DS1), graph, fresh());
		assertEquals(1, shown.size());
		warnings.onQuestStates(Collections.singleton(DS1), graph, fresh());
		assertEquals("still in progress is not a new start", 1, shown.size());
	}

	@Test
	public void questsAlreadyUnderWayAtLoginNeverWarn()
	{
		warnings.onQuestStates(Collections.singleton(DS1), graph, fresh());
		assertTrue(shown.isEmpty());
	}

	@Test
	public void startingWhatYouAlreadyWereWarnedAboutSelectingIsNotRepeated()
	{
		warnings.onQuestStates(Collections.emptySet(), graph, fresh());
		warnings.onSelected(DS1, graph, fresh());
		warnings.onQuestStates(Collections.singleton(DS1), graph, fresh());
		assertEquals(1, shown.size());
	}

	@Test
	public void startingAQuestYouCanDoIsSilent()
	{
		warnings.onQuestStates(Collections.emptySet(), graph, maxedExcept(DS1));
		warnings.onQuestStates(Collections.singleton(DS1), graph, maxedExcept(DS1));
		assertTrue(shown.isEmpty());
	}

	@Test
	public void resetForgetsTheSelectionSoItWarnsAgain()
	{
		warnings.onSelected(DS1, graph, fresh());
		warnings.reset();
		warnings.onSelected(DS1, graph, fresh());
		assertEquals(2, shown.size());
	}

	@Test
	public void theOverlayRowsNameEveryMissingThing()
	{
		warnings.onSelected(DS1, graph, fresh());
		QuestWarning warning = shown.get(0);
		assertEquals(1 + warning.lines().size(), QuestWarningOverlay.rowsFor(warning).size());
		assertTrue(QuestWarningOverlay.rowsFor(null).isEmpty());
	}
}
