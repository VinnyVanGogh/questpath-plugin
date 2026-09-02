package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The wire contract with questpath's build rules, and the distinction the
 * panel draws from it.
 *
 * NO_XP and HARD_CAP end a build if crossed; SOFT_CAP and GOAL only inform.
 * Rendering them the same would turn "approaching a target" into "about to
 * ruin your account", and the second warning stops meaning anything once it
 * fires for the first.
 */
public class BuildRuleTest
{
	private static final Gson GSON = new Gson();

	private static PushResponse parse(String json)
	{
		return GSON.fromJson(json, PushResponse.class);
	}

	@Test
	public void parsesWhatQuestpathSends()
	{
		PushResponse response = parse("{\"player\":\"its chyl\",\"build_rules\":["
			+ "{\"skill\":\"defence\",\"type\":\"NO_XP\",\"blocking\":true,\"note\":\"1 def\"},"
			+ "{\"skill\":\"ranged\",\"type\":\"HARD_CAP\",\"value\":56,\"blocking\":true}]}");

		assertEquals("its chyl", response.player());
		List<BuildRule> rules = response.buildRules();
		assertEquals(2, rules.size());

		assertEquals("defence", rules.get(0).skillName());
		assertEquals(Skill.DEFENCE, rules.get(0).skill());
		assertTrue(rules.get(0).blocking());
		assertFalse("NO_XP carries no level", rules.get(0).hasValue());
		assertEquals("1 def", rules.get(0).note());

		assertEquals(56, rules.get(1).value());
		assertTrue(rules.get(1).hasValue());
	}

	/**
	 * questpath may send a skill this client build does not know. The rule is
	 * kept and shown under questpath's name rather than dropped: it might be
	 * the one ending the build.
	 */
	@Test
	public void anUnknownSkillResolvesToNullWithoutLosingTheRule()
	{
		BuildRule rule = parse("{\"build_rules\":[{\"skill\":\"basketweaving\",\"type\":\"NO_XP\",\"blocking\":true}]}")
			.buildRules().get(0);

		assertNull(rule.skill());
		assertEquals("basketweaving", rule.skillName());
		assertEquals("no xp at all", rule.describe());
	}

	/** A response with no rules block is empty, not null. */
	@Test
	public void aResponseWithoutRulesIsEmpty()
	{
		assertTrue(parse("{\"player\":\"its chyl\"}").buildRules().isEmpty());
		assertTrue(parse("{\"player\":\"its chyl\"}").unmatched().isEmpty());
	}

	@Test
	public void unmatchedQuestNamesSurviveAsNames()
	{
		List<String> unmatched = parse("{\"unmatched\":[\"Not A Real Quest\",\"Another\"]}").unmatched();
		assertEquals(Arrays.asList("Not A Real Quest", "Another"), unmatched);
	}

	@Test
	public void describeSaysWhatTheLimitActuallyIs()
	{
		assertEquals("no xp at all", ruleOf("NO_XP", 0).describe());
		assertEquals("capped at 56", ruleOf("HARD_CAP", 56).describe());
		assertEquals("warns, never blocks", ruleOf("SOFT_CAP", 0).describe());
		assertEquals("goal 50", ruleOf("GOAL", 50).describe());
	}

	/**
	 * A type this build does not know is rendered plainly rather than hidden.
	 * questpath adding a rule kind must not make a limit vanish from the tab.
	 */
	@Test
	public void anUnknownRuleTypeIsShownNotSwallowed()
	{
		assertEquals("SOMETHING_NEW", ruleOf("SOMETHING_NEW", 0).describe());
	}

	/**
	 * Ordered by what ends a build first. A pure scanning this tab wants the
	 * thing that would ruin the account at the top, not alphabetical order.
	 */
	@Test
	public void orderedPutsWhatEndsABuildFirst()
	{
		List<BuildRule> ordered = BuildRule.ordered(Arrays.asList(
			named("thieving", "GOAL", 50),
			named("prayer", "SOFT_CAP", 0),
			named("ranged", "HARD_CAP", 56),
			named("attack", "NO_XP", 0),
			named("defence", "NO_XP", 0)));

		assertEquals("attack", ordered.get(0).skillName());
		assertEquals("defence", ordered.get(1).skillName());
		assertEquals("ranged", ordered.get(2).skillName());
		assertEquals("prayer", ordered.get(3).skillName());
		assertEquals("thieving", ordered.get(4).skillName());
	}

	private static BuildRule ruleOf(String type, int value)
	{
		return named("defence", type, value);
	}

	private static BuildRule named(String skill, String type, int value)
	{
		String json = "{\"build_rules\":[{\"skill\":\"" + skill + "\",\"type\":\"" + type + "\""
			+ (value > 0 ? ",\"value\":" + value : "") + "}]}";
		return parse(json).buildRules().get(0);
	}
}
