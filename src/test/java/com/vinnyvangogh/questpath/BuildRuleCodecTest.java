package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rules the player writes here, and how they meet the ones questpath
 * pushes.
 *
 * <p>All the logic worth testing lives in {@link BuildRuleCodec} for the
 * reason {@link LoadoutCodec} does: {@code ConfigManager}'s only constructor
 * is private, so the adapter around it cannot be exercised without Mockito,
 * which this project does not depend on. The adapter stays thin and this
 * carries the rules.
 */
public class BuildRuleCodecTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void emptyStorageIsNoRulesRatherThanAFailure()
	{
		assertTrue(BuildRuleCodec.decode(GSON, null).isEmpty());
		assertTrue(BuildRuleCodec.decode(GSON, "").isEmpty());
		assertTrue(BuildRuleCodec.decode(GSON, "   ").isEmpty());
	}

	/**
	 * A corrupt config value must not take the plugin down.
	 *
	 * <p>Empty is a defined, visible outcome: the editor shows no rules and
	 * the player can see and fix it. Throwing from a config read on startup
	 * is a plugin that will not load and says nothing about why.
	 */
	@Test
	public void unparseableStorageDecodesToEmpty()
	{
		assertTrue(BuildRuleCodec.decode(GSON, "{not json").isEmpty());
		assertTrue(BuildRuleCodec.decode(GSON, "\"a bare string\"").isEmpty());
	}

	@Test
	public void aSavedRuleSurvivesTheRoundTrip()
	{
		List<BuildRule> saved = BuildRuleCodec.set(
			Collections.emptyList(), "Attack", BuildRule.NO_XP, 0, "");
		List<BuildRule> back = BuildRuleCodec.decode(GSON, BuildRuleCodec.encode(GSON, saved));

		assertEquals(1, back.size());
		assertEquals("Attack", back.get(0).skillName());
		assertEquals(BuildRule.NO_XP, back.get(0).type());
		assertTrue("NO_XP has to survive as blocking", back.get(0).blocking());
	}

	/**
	 * Two rules on one skill is an ambiguity, not a richer statement: "no XP
	 * at all" and "cap at 40" cannot both be obeyed, and whichever the
	 * advisor happened to iterate first would decide silently.
	 */
	@Test
	public void settingTheSameSkillTwiceReplacesRatherThanAppends()
	{
		List<BuildRule> rules = BuildRuleCodec.set(
			Collections.emptyList(), "Attack", BuildRule.NO_XP, 0, "");
		rules = BuildRuleCodec.set(rules, "Attack", BuildRule.HARD_CAP, 40, "");

		assertEquals(1, rules.size());
		assertEquals(BuildRule.HARD_CAP, rules.get(0).type());
		assertEquals(40, rules.get(0).value());
	}

	@Test
	public void decodingCollapsesADuplicateSkillToTheLastOneSeen()
	{
		String json = "[{\"skill\":\"Attack\",\"type\":\"NO_XP\"},"
			+ "{\"skill\":\"attack\",\"type\":\"HARD_CAP\",\"value\":40}]";
		List<BuildRule> rules = BuildRuleCodec.decode(GSON, json);

		assertEquals(1, rules.size());
		assertEquals(BuildRule.HARD_CAP, rules.get(0).type());
	}

	/**
	 * A rule type this build does not know is dropped, not kept.
	 *
	 * <p>Everything downstream treats an unknown type as non-blocking, so
	 * keeping it turns a rule the player believes is protecting them into one
	 * that quietly does nothing. Dropping it at least makes the absence
	 * visible in the editor.
	 */
	@Test
	public void anUnknownRuleTypeIsDroppedRatherThanKeptAsNonBlocking()
	{
		String json = "[{\"skill\":\"Attack\",\"type\":\"SOMETHING_NEW\",\"value\":40}]";
		assertTrue(BuildRuleCodec.decode(GSON, json).isEmpty());
	}

	/**
	 * A level is stored only for the types that carry one.
	 *
	 * <p>A stored-but-ignored number is a setting the player believes they
	 * have made.
	 */
	@Test
	public void aLevelIsOnlyKeptForTheTypesThatUseOne()
	{
		assertEquals(0, BuildRuleCodec.set(
			Collections.emptyList(), "Attack", BuildRule.NO_XP, 40, "").get(0).value());
		assertEquals(0, BuildRuleCodec.set(
			Collections.emptyList(), "Prayer", BuildRule.SOFT_CAP, 40, "").get(0).value());
		assertEquals(40, BuildRuleCodec.set(
			Collections.emptyList(), "Attack", BuildRule.HARD_CAP, 40, "").get(0).value());
		assertEquals(60, BuildRuleCodec.set(
			Collections.emptyList(), "Cooking", BuildRule.GOAL, 60, "").get(0).value());
	}

	@Test
	public void clearingRemovesOnlyTheNamedSkill()
	{
		List<BuildRule> rules = BuildRuleCodec.set(
			Collections.emptyList(), "Attack", BuildRule.NO_XP, 0, "");
		rules = BuildRuleCodec.set(rules, "Defence", BuildRule.NO_XP, 0, "");
		rules = BuildRuleCodec.clear(rules, "attack");

		assertEquals(1, rules.size());
		assertEquals("Defence", rules.get(0).skillName());
	}

	/**
	 * The merge direction, which is the decision this class exists to settle.
	 *
	 * <p>A pushed rule can be minutes stale and arrives without the player
	 * doing anything. A local rule is something they typed. On a skill both
	 * name, the typed one is the more recent statement of intent and the one
	 * they would be surprised to find ignored.
	 */
	@Test
	public void aLocalRuleWinsOverAPushedOneOnTheSameSkill()
	{
		List<BuildRule> pushed = Arrays.asList(
			BuildRule.local("Attack", BuildRule.HARD_CAP, 40, "from questpath"),
			BuildRule.local("Prayer", BuildRule.SOFT_CAP, 0, "from questpath"));
		List<BuildRule> local = Collections.singletonList(
			BuildRule.local("Attack", BuildRule.NO_XP, 0, "typed here"));

		List<BuildRule> merged = BuildRuleCodec.merge(pushed, local);

		assertEquals("both skills survive", 2, merged.size());
		BuildRule attack = merged.stream()
			.filter(r -> "Attack".equals(r.skillName())).findFirst().orElse(null);
		assertEquals(BuildRule.NO_XP, attack.type());
		assertEquals("typed here", attack.note());
	}

	/**
	 * A skill only questpath knows about keeps its rule, which is what lets
	 * a synced account be useful without re-entering twenty-four rules by
	 * hand.
	 */
	@Test
	public void mergingKeepsAPushedRuleTheLocalSetSaysNothingAbout()
	{
		List<BuildRule> pushed = Collections.singletonList(
			BuildRule.local("Defence", BuildRule.NO_XP, 0, ""));
		List<BuildRule> merged = BuildRuleCodec.merge(
			pushed, Collections.singletonList(BuildRule.local("Attack", BuildRule.NO_XP, 0, "")));

		assertEquals(2, merged.size());
	}

	@Test
	public void mergingHandlesEitherSideBeingAbsent()
	{
		List<BuildRule> one = Collections.singletonList(
			BuildRule.local("Attack", BuildRule.NO_XP, 0, ""));

		assertEquals(1, BuildRuleCodec.merge(one, null).size());
		assertEquals(1, BuildRuleCodec.merge(null, one).size());
		assertTrue(BuildRuleCodec.merge(null, null).isEmpty());
	}

	/**
	 * A locally written rule has to be indistinguishable from a pushed one
	 * everywhere downstream.
	 *
	 * <p>{@link BuildRule#blocking()} is the field that matters: the wire
	 * flag is absent on a rule questpath generated into the planner vectors,
	 * which is why the accessor ORs it with the type. A local rule must come
	 * out blocking for the same two types, by the same route.
	 */
	@Test
	public void aLocalRuleBlocksExactlyWhereAPushedOneWould()
	{
		assertTrue(BuildRule.local("Attack", BuildRule.NO_XP, 0, "").blocking());
		assertTrue(BuildRule.local("Attack", BuildRule.HARD_CAP, 40, "").blocking());
		assertFalse(BuildRule.local("Prayer", BuildRule.SOFT_CAP, 0, "").blocking());
		assertFalse(BuildRule.local("Cooking", BuildRule.GOAL, 60, "").blocking());
	}
}
