package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a level costs, over the real bundled quest graph.
 *
 * <p>The arithmetic is the point. Every band has to add up against the
 * account's actual experience, and the one number that must never be
 * generous is what is left to train -- a plan that counts experience the
 * account can never earn finishes short, and finishes short silently.
 */
public class SkillPlanTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	private static PlayerState account(Map<String, Integer> levels, Map<String, Long> xp)
	{
		return new PlayerState(levels, xp, new HashSet<String>(), Collections.emptyList());
	}

	private static PlayerState fresh()
	{
		return account(new HashMap<>(), new HashMap<>());
	}

	private static SkillPlan plan(PlayerState account, String skill, int target) throws Exception
	{
		QuestGraph graph = graph();
		return SkillPlan.of(graph, account, new QuestClassifier(graph, account), skill, target);
	}

	/** A fresh account can start quests that grant Cooking, and the plan finds them. */
	@Test
	public void questsThatGrantTheSkillAreFound() throws Exception
	{
		SkillPlan cooking = plan(fresh(), "Cooking", 30);
		assertFalse("a fresh account can start something that grants Cooking",
			cooking.ready().isEmpty());
		assertTrue("and the ready band is real experience", cooking.readyXp() > 0);
		for (SkillPlan.Entry entry : cooking.ready())
		{
			assertTrue(entry.questName() + " was listed with no experience", entry.xp() > 0);
		}
	}

	/** Biggest payout first, in every band. Name order buries what matters. */
	@Test
	public void everyBandLeadsWithItsBiggestPayout() throws Exception
	{
		SkillPlan plan = plan(fresh(), "Cooking", 50);
		assertOrdered(plan.ready());
		assertOrdered(plan.later());
		assertOrdered(plan.lost());
	}

	private static void assertOrdered(java.util.List<SkillPlan.Entry> entries)
	{
		for (int i = 1; i < entries.size(); i++)
		{
			assertTrue("entries are ordered by experience, descending",
				entries.get(i - 1).xp() >= entries.get(i).xp());
		}
	}

	/**
	 * The headline number. Quests cannot be counted twice and cannot be
	 * counted when they are off the table.
	 */
	@Test
	public void whatIsLeftToTrainExcludesWhatCanNeverBeEarned() throws Exception
	{
		// A pure: attack and strength capped hard, which closes a pile of
		// quests permanently and takes their experience with them.
		BuildRule cap = GSON.fromJson(
			"{\"skill\":\"Attack\",\"type\":\"HARD_CAP\",\"value\":1,\"blocking\":true}",
			BuildRule.class);
		PlayerState pure = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<String>(), Arrays.asList(cap));

		SkillPlan plan = plan(pure, "Cooking", 40);
		assertEquals("train = needed - ready - later, floored at zero",
			Math.max(0L, plan.xpNeeded() - plan.readyXp() - plan.laterXp()),
			plan.xpToTrain());
		assertTrue("what is lost is never subtracted from what is left to train",
			plan.xpToTrain() >= plan.xpNeeded() - plan.readyXp() - plan.laterXp());
	}

	/**
	 * Partial progress into the current level counts. Without it the plan
	 * asks for a whole extra level the moment you are one point into one.
	 */
	@Test
	public void experienceAlreadyEarnedInsideTheCurrentLevelIsCredited() throws Exception
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> xp = new HashMap<>();
		levels.put("Cooking", 40);
		// Level 40 starts at 37,224; level 41 at 41,171.
		xp.put("Cooking", 41_000L);

		SkillPlan plan = plan(account(levels, xp), "Cooking", 41);
		assertEquals("only the last 171 experience is outstanding",
			XpTable.xpFor(41) - 41_000L, plan.xpNeeded());
	}

	/** A target already met asks for nothing. */
	@Test
	public void aTargetAlreadyReachedCostsNothing() throws Exception
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> xp = new HashMap<>();
		levels.put("Cooking", 50);
		xp.put("Cooking", XpTable.xpFor(50));

		SkillPlan plan = plan(account(levels, xp), "Cooking", 30);
		assertTrue(plan.reached());
		assertEquals(0L, plan.xpNeeded());
		assertEquals(0L, plan.xpToTrain());
	}

	/** A target above the account's own hard cap is named as a build break. */
	@Test
	public void aTargetAboveTheBuildsCapIsFlagged() throws Exception
	{
		BuildRule cap = GSON.fromJson(
			"{\"skill\":\"Ranged\",\"type\":\"HARD_CAP\",\"value\":55,\"blocking\":true}",
			BuildRule.class);
		PlayerState capped = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<String>(), Arrays.asList(cap));

		assertEquals(55, plan(capped, "Ranged", 70).cappedAt());
		assertTrue(plan(capped, "Ranged", 70).targetBreaksBuild());
		assertFalse("at the cap is not over it", plan(capped, "Ranged", 55).targetBreaksBuild());
	}

	/**
	 * A soft cap carries no level. Reading its zero as a ceiling would call
	 * every target a build break for that skill.
	 */
	@Test
	public void aSoftCapIsNotACeiling() throws Exception
	{
		BuildRule soft = GSON.fromJson(
			"{\"skill\":\"Ranged\",\"type\":\"SOFT_CAP\",\"blocking\":false}",
			BuildRule.class);
		PlayerState account = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<String>(), Arrays.asList(soft));

		SkillPlan plan = plan(account, "Ranged", 70);
		assertEquals(-1, plan.cappedAt());
		assertFalse(plan.targetBreaksBuild());
	}

	/** Completed quests are already in your experience and must not be offered again. */
	@Test
	public void completedQuestsAreNotOfferedAsFutureExperience() throws Exception
	{
		SkillPlan before = plan(fresh(), "Cooking", 50);
		String firstReady = before.ready().get(0).questName();

		PlayerState done = new PlayerState(new HashMap<>(), new HashMap<>(),
			new HashSet<>(Collections.singleton(firstReady)), Collections.emptyList());
		SkillPlan after = plan(done, "Cooking", 50);

		for (SkillPlan.Entry entry : after.ready())
		{
			assertFalse(firstReady + " was counted after being completed",
				entry.questName().equals(firstReady));
		}
		assertTrue("and the ready total drops by its experience",
			after.readyXp() < before.readyXp());
	}
}
