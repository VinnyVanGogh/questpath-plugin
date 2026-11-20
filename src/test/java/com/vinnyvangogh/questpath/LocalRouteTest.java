package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The route built with no server, from the bundled quest graph. */
public class LocalRouteTest
{
	private QuestGraph graph;

	@Before
	public void load() throws IOException
	{
		graph = QuestGraph.load(new Gson());
	}

	private PlayerState fresh(Set<String> completed)
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), completed, Collections.emptyList());
	}

	private Map<String, QuestGraph.Quest> byName()
	{
		Map<String, QuestGraph.Quest> map = new HashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			map.put(quest.name(), quest);
		}
		return map;
	}

	@Test
	public void aFreshAccountGetsFreeQuestsOnlyWhenThatIsAllItChose()
	{
		List<String> route = LocalRoute.of(graph, fresh(Collections.emptySet()),
			EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY));

		assertTrue(route.contains("Cook's Assistant"));
		Map<String, QuestGraph.Quest> byName = byName();
		for (String name : route)
		{
			assertFalse(name + " is members-only", byName.get(name).members());
		}
	}

	@Test
	public void everyQuestComesAfterItsPrerequisites()
	{
		Set<String> done = new HashSet<>();
		List<String> route = LocalRoute.of(graph, fresh(done), EnumSet.allOf(LocalProfile.QuestType.class));
		assertFalse(route.isEmpty());

		Map<Long, QuestGraph.Quest> byId = new HashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			byId.put(quest.id(), quest);
		}
		Set<String> seen = new HashSet<>();
		Map<String, QuestGraph.Quest> byName = byName();
		for (String name : route)
		{
			for (long id : byName.get(name).prerequisites())
			{
				assertTrue(name + " needs " + byId.get(id).name() + " first", seen.contains(byId.get(id).name()));
			}
			seen.add(name);
		}
	}

	@Test
	public void aFinishedQuestIsNotOnTheRoute()
	{
		List<String> route = LocalRoute.of(graph, fresh(Set.of("Cook's Assistant")),
			EnumSet.allOf(LocalProfile.QuestType.class));

		assertFalse(route.contains("Cook's Assistant"));
	}

	@Test
	public void theOrderIsTheSameEveryTime()
	{
		Set<LocalProfile.QuestType> all = EnumSet.allOf(LocalProfile.QuestType.class);

		assertEquals(LocalRoute.of(graph, fresh(Collections.emptySet()), all),
			LocalRoute.of(graph, fresh(Collections.emptySet()), all));
	}

	@Test
	public void noChosenTypesOrNoGraphMeansNoRoute()
	{
		assertTrue(LocalRoute.of(graph, fresh(Collections.emptySet()), EnumSet.noneOf(LocalProfile.QuestType.class)).isEmpty());
		assertTrue(LocalRoute.of(null, fresh(Collections.emptySet()), EnumSet.allOf(LocalProfile.QuestType.class)).isEmpty());
		assertTrue(LocalRoute.of(graph, null, EnumSet.allOf(LocalProfile.QuestType.class)).isEmpty());
	}

	private static LocalProfile marks(LocalProfile.Mark mark, String... quests)
	{
		LocalProfile profile = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class), null);
		for (String quest : quests)
		{
			profile = profile.toggled(mark, quest);
		}
		return profile;
	}

	@Test
	public void aRefusedQuestAndWhatNeedsItAreLeftOff()
	{
		Set<LocalProfile.QuestType> all = EnumSet.allOf(LocalProfile.QuestType.class);
		List<String> plain = LocalRoute.of(graph, fresh(Collections.emptySet()), all);
		assertTrue(plain.contains("Cook's Assistant"));

		List<String> route = LocalRoute.of(graph, fresh(Collections.emptySet()), all,
			marks(LocalProfile.Mark.REFUSED, "Cook's Assistant"));
		assertFalse(route.contains("Cook's Assistant"));
		assertFalse("Recipe for Disaster needs Cook's Assistant", route.contains("Recipe for Disaster"));
	}

	@Test
	public void pinnedQuestsLeadInPinOrderAndDeferredOnesTrail()
	{
		Set<LocalProfile.QuestType> all = EnumSet.allOf(LocalProfile.QuestType.class);
		List<String> route = LocalRoute.of(graph, fresh(Collections.emptySet()), all,
			marks(LocalProfile.Mark.PINNED, "Sheep Shearer", "Cook's Assistant")
				.toggled(LocalProfile.Mark.DEFERRED, "Rune Mysteries"));

		assertEquals("Sheep Shearer", route.get(0));
		assertEquals("Cook's Assistant", route.get(1));

		// Deferred means "after everything that does not need it": only its
		// own dependants (Enter the Abyss) may follow it.
		List<String> plain = LocalRoute.of(graph, fresh(Collections.emptySet()), all);
		int at = route.indexOf("Rune Mysteries");
		assertTrue(at > plain.indexOf("Rune Mysteries"));
		long runeMysteries = byName().get("Rune Mysteries").id();
		for (String later : route.subList(at + 1, route.size()))
		{
			assertTrue(later + " does not need Rune Mysteries", byName().get(later).prerequisites().contains(runeMysteries));
		}
	}

	@Test
	public void refusingClearsAPinAndPinAndDeferExcludeEachOther()
	{
		LocalProfile profile = marks(LocalProfile.Mark.PINNED, "Sheep Shearer");
		assertTrue(profile.has(LocalProfile.Mark.PINNED, "Sheep Shearer"));
		profile = profile.toggled(LocalProfile.Mark.DEFERRED, "Sheep Shearer");
		assertFalse(profile.has(LocalProfile.Mark.PINNED, "Sheep Shearer"));
		assertTrue(profile.has(LocalProfile.Mark.DEFERRED, "Sheep Shearer"));
		profile = profile.toggled(LocalProfile.Mark.REFUSED, "Sheep Shearer");
		assertFalse(profile.has(LocalProfile.Mark.DEFERRED, "Sheep Shearer"));
		assertTrue(profile.has(LocalProfile.Mark.REFUSED, "Sheep Shearer"));
		assertFalse(profile.hideBlocked());
		assertTrue(profile.toggled(LocalProfile.Mark.HIDE_BLOCKED, "").hideBlocked());
		assertTrue("marks survive a route write",
			profile.withRoute(1, Collections.emptyList(), Collections.emptyList()).refused().contains("Sheep Shearer"));
	}

	@Test
	public void aBuildRuleKeepsAForbiddenQuestOffTheRoute()
	{
		// Capping Attack and Prayer at 1 makes Land of the Goblins' chain
		// permanently blocked; see QuestClassifier's own doc.
		PlayerState capped = new PlayerState(new HashMap<>(), new HashMap<>(), Collections.emptySet(),
			java.util.Arrays.asList(BuildRule.local("attack", "max_level", 1, ""),
				BuildRule.local("prayer", "max_level", 1, "")));

		List<String> route = LocalRoute.of(graph, capped, EnumSet.allOf(LocalProfile.QuestType.class));

		assertFalse(route.contains("Land of the Goblins"));
	}
}
