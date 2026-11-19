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
