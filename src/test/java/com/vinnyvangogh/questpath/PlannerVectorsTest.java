package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import static org.junit.Assert.assertTrue;

/**
 * The port against the original.
 *
 * <p>questpath's {@code -export-planner-vectors} runs its Go planner over the
 * real account and writes a verdict for all 218 quests. This runs the Java
 * port over the same account and the same graph, and demands the same answers.
 *
 * <p>This is the only thing standing between "the two agree" and "the two
 * appear to agree". Both implementations answer confidently, so a
 * disagreement is invisible until the game and the app tell you different
 * things about the same quest. Regenerating these files is part of changing
 * the Go planner:
 *
 * <pre>
 * questpath -export-quest-graph      src/main/resources/com/vinnyvangogh/questpath/quests.json
 * questpath -export-planner-vectors  src/test/resources/com/vinnyvangogh/questpath/planner-vectors.json
 * </pre>
 */
public class PlannerVectorsTest
{
	private static final Gson GSON = new Gson();

	private QuestGraph graph;
	private Vectors vectors;

	static class Vectors
	{
		@SerializedName("version")
		int version;
		@SerializedName("account")
		Account account;
		@SerializedName("expected")
		List<Verdict> expected;
	}

	static class Account
	{
		@SerializedName("skills")
		List<SkillState> skills;
		@SerializedName("completed")
		List<Long> completed;
		@SerializedName("rules")
		List<BuildRule> rules;
	}

	static class SkillState
	{
		@SerializedName("skill")
		String skill;
		@SerializedName("level")
		int level;
		@SerializedName("xp")
		long xp;
	}

	static class Verdict
	{
		@SerializedName("quest_id")
		long questId;
		@SerializedName("slug")
		String slug;
		@SerializedName("status")
		String status;
	}

	@Before
	public void load() throws Exception
	{
		graph = QuestGraph.load(GSON);

		try (InputStream stream = PlannerVectorsTest.class.getResourceAsStream("planner-vectors.json"))
		{
			assertNotNull("planner-vectors.json is missing; regenerate it with -export-planner-vectors", stream);
			vectors = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Vectors.class);
		}
		assertNotNull(vectors);
	}

	private PlayerState accountUnderTest()
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (SkillState skill : vectors.account.skills)
		{
			levels.put(skill.skill, skill.level);
			experience.put(skill.skill, skill.xp);
		}
		// The vectors carry questpath's own quest ids; the engine matches by
		// name, because the client's ids are the game's and comparing the two
		// spaces is the bug this indirection exists to prevent. Resolving
		// through the graph is what the plugin does with the client's names.
		Set<String> completed = new HashSet<>();
		if (vectors.account.completed != null)
		{
			for (Long id : vectors.account.completed)
			{
				QuestGraph.Quest quest = graph.get(id);
				assertNotNull("the vectors mark quest " + id + " complete but the graph lacks it", quest);
				completed.add(quest.name());
			}
		}
		List<BuildRule> rules = vectors.account.rules == null ? Collections.emptyList() : vectors.account.rules;
		return new PlayerState(levels, experience, completed, rules);
	}

	/**
	 * Quests where this port is deliberately ahead of the Go planner.
	 *
	 * <p>The port now checks that a boost is large enough to close a
	 * shortfall, not merely that the wiki permits one. Go still checks only
	 * permission -- {@code if !req.Boostable { boostRescues = false }} in
	 * internal/planner/classify.go, with no magnitude anywhere -- so it calls
	 * a thirty-level gap boostable. Lost City is the clearest: it wants
	 * Crafting 31 and Woodcutting 36, and the best boosts available are +4
	 * from a mushroom pie and +7 from the Woodcutting Guild.
	 *
	 * <p>Listed rather than regenerated, deliberately. Re-exporting the
	 * vectors would make the suite green by writing the bug down as the
	 * expected answer, and the whole purpose of these vectors is to be the
	 * thing that notices. Listing keeps every other quest under test: a new
	 * divergence still fails.
	 *
	 * <p><b>This list must go to empty.</b> The Go planner needs the same
	 * check, which needs the boost table stored rather than fetched at export
	 * time -- see the note in cmd/questpath/export.go. Delete this set and
	 * re-export the vectors in the same change.
	 */
	private static final Set<String> KNOWN_AHEAD_OF_GO = new HashSet<>(Arrays.asList(
		"lost_city",
		"sea_slug",
		"one_small_favour",
		"tears_of_guthix",
		"forgettable_tale",
		"shadow_of_the_storm",
		"enlightened_journey",
		"eagles_peak",
		"recipe_for_disaster_freeing_pirate_pete",
		"recipe_for_disaster_freeing_skrach_uglogwee"));

	/**
	 * The whole point. Every quest, the same verdict.
	 *
	 * Failures are collected rather than thrown on the first one: a port that
	 * is wrong is usually wrong about a whole class of quest, and seeing three
	 * of them names the class where seeing one names a quest.
	 */
	@Test
	public void everyVerdictMatchesTheGoPlanner()
	{
		QuestClassifier classifier = new QuestClassifier(graph, accountUnderTest());

		List<String> mismatches = new ArrayList<>();
		for (Verdict expected : vectors.expected)
		{
			QuestStatus actual = classifier.classify(expected.questId);
			String actualName = actual == null ? "NOT_IN_GRAPH" : actual.name();
			if (!expected.status.equals(actualName) && !KNOWN_AHEAD_OF_GO.contains(expected.slug))
			{
				mismatches.add(expected.slug + ": expected " + expected.status + ", got " + actualName);
			}
		}

		assertEquals(mismatches.size() + " of " + vectors.expected.size()
			+ " verdicts disagree with the Go planner:\n  " + String.join("\n  ", mismatches),
			Collections.emptyList(), mismatches);
	}

	/**
	 * The exemptions above have to stay real.
	 *
	 * A slug that stops diverging is a slug someone fixed, and leaving it
	 * listed would quietly excuse a future regression on that quest. This
	 * fails when the list is stale, which is what makes it safe to have one.
	 */
	@Test
	public void everyKnownDivergenceStillDiverges()
	{
		QuestClassifier classifier = new QuestClassifier(graph, accountUnderTest());

		List<String> agreeing = new ArrayList<>();
		for (Verdict expected : vectors.expected)
		{
			if (!KNOWN_AHEAD_OF_GO.contains(expected.slug))
			{
				continue;
			}
			QuestStatus actual = classifier.classify(expected.questId);
			String actualName = actual == null ? "NOT_IN_GRAPH" : actual.name();
			if (expected.status.equals(actualName))
			{
				agreeing.add(expected.slug);
			}
		}

		assertEquals("these no longer diverge and must come off KNOWN_AHEAD_OF_GO: "
			+ String.join(", ", agreeing), Collections.emptyList(), agreeing);
	}

	/**
	 * The vectors must actually exercise the engine. A set that was all
	 * ELIGIBLE would pass a classifier that returned ELIGIBLE unconditionally,
	 * and the suite would be worthless while looking green.
	 */
	@Test
	public void theVectorsCoverEveryStatusTheEngineCanProduce()
	{
		Set<String> seen = new HashSet<>();
		for (Verdict verdict : vectors.expected)
		{
			seen.add(verdict.status);
		}

		for (String required : new String[]{
			"COMPLETE", "ELIGIBLE", "BOOSTABLE", "QUEST_BLOCKED",
			"SKILL_BLOCKED", "BUILD_BLOCKED", "UNREACHABLE"})
		{
			assertTrue("the vectors never exercise " + required
				+ "; regenerate them from an account that does", seen.contains(required));
		}
	}

	@Test
	public void theBundledGraphCoversEveryQuestTheVectorsJudge()
	{
		for (Verdict verdict : vectors.expected)
		{
			assertNotNull("the bundled graph is missing " + verdict.slug
				+ "; the two files were exported from different databases",
				graph.get(verdict.questId));
		}
		assertEquals("the graph and the vectors describe different quest sets",
			graph.quests().size(), vectors.expected.size());
	}

	/**
	 * The account the vectors were generated against must actually have rules.
	 * Without them nothing can be BUILD_BLOCKED, and the build guard -- the
	 * reason this whole engine exists -- would be untested while every other
	 * assertion still passed.
	 */
	@Test
	public void theAccountUnderTestActuallyHasBuildRules()
	{
		assertFalse("regenerate the vectors from an account with build rules",
			accountUnderTest().hasRules() == false);
	}
}
