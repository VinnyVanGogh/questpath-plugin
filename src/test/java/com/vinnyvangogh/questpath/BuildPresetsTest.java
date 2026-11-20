package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class BuildPresetsTest
{
	private static final Gson GSON = new Gson();

	private static QuestVerdicts verdictsFor(List<BuildRule> rules) throws Exception
	{
		PlayerState account = new PlayerState(new HashMap<String, Integer>(), new HashMap<String, Long>(),
			new HashSet<String>(), rules);
		return QuestVerdicts.of(QuestGraph.load(GSON), account);
	}

	@Test
	public void thereAreExactlyThreeStarterBuilds()
	{
		assertEquals(3, BuildPresets.ALL.size());
		assertEquals("60 Attack zerker", BuildPresets.ZERKER.label());
		assertEquals("60 Attack pure", BuildPresets.PURE.label());
		assertEquals("Maxed main", BuildPresets.MAXED.label());
	}

	@Test
	public void zerkerCapsAttackAt60AndDefenceAt45()
	{
		List<BuildRule> rules = BuildPresets.ZERKER.rules();
		assertEquals(2, rules.size());
		assertEquals("Attack", rules.get(0).skillName());
		assertEquals(BuildRule.HARD_CAP, rules.get(0).type());
		assertEquals(60, rules.get(0).value());
		assertEquals("Defence", rules.get(1).skillName());
		assertEquals(BuildRule.HARD_CAP, rules.get(1).type());
		assertEquals(45, rules.get(1).value());
	}

	@Test
	public void pureCapsAttackAt60AndTakesNoDefenceXp()
	{
		List<BuildRule> rules = BuildPresets.PURE.rules();
		assertEquals(60, rules.get(0).value());
		assertEquals("Defence", rules.get(1).skillName());
		assertEquals(BuildRule.NO_XP, rules.get(1).type());
		assertTrue(rules.get(1).blocking());
	}

	@Test
	public void maxedMainHasNoLimits()
	{
		assertTrue(BuildPresets.MAXED.rules().isEmpty());
	}

	@Test
	public void slugsAreUniqueBecauseTheyAreStored()
	{
		java.util.Set<String> seen = new HashSet<>();
		for (BuildPresets.Preset preset : BuildPresets.ALL)
		{
			assertTrue("duplicate slug " + preset.slug(), seen.add(preset.slug()));
		}
		assertFalse("the saved build is not a starter", seen.contains(BuildPresets.SAVED));
	}

	@Test
	public void anUnknownSlugIsNullNotAnError()
	{
		assertNull(BuildPresets.find("from_a_newer_build"));
		assertNull(BuildPresets.find("members_void_pure"));
		assertNull(BuildPresets.find(BuildPresets.SAVED));
		assertNull(BuildPresets.find(null));
	}

	@Test
	public void aStarterWinsOverSavedRules()
	{
		List<BuildRule> saved = Collections.singletonList(BuildRule.local("Magic", BuildRule.NO_XP, 0, ""));
		assertSame(BuildPresets.PURE.rules(), BuildPresets.localRules(BuildPresets.PURE.slug(), saved));
		assertFalse(BuildPresets.usesSaved(BuildPresets.PURE.slug(), saved));
	}

	@Test
	public void savedRulesStandInForAnOldOrMissingSlug()
	{
		List<BuildRule> saved = Collections.singletonList(BuildRule.local("Magic", BuildRule.NO_XP, 0, ""));
		assertSame(saved, BuildPresets.localRules("members_void_pure", saved));
		assertSame(saved, BuildPresets.localRules(null, saved));
		assertTrue(BuildPresets.usesSaved(BuildPresets.SAVED, saved));
		assertTrue(BuildPresets.localRules(null, null).isEmpty());
		assertFalse(BuildPresets.usesSaved(null, Collections.<BuildRule>emptyList()));
	}

	/** The verdicts really do change with the starter: pure closes quests a maxed main can do. */
	@Test
	public void presetsDriveTheQuestVerdicts() throws Exception
	{
		QuestVerdicts maxed = verdictsFor(BuildPresets.MAXED.rules());
		QuestVerdicts pure = verdictsFor(BuildPresets.PURE.rules());
		QuestVerdicts zerker = verdictsFor(BuildPresets.ZERKER.rules());

		assertEquals(0, maxed.count(QuestStatus.BUILD_BLOCKED));
		assertTrue("a Defence no-XP build closes quests", pure.count(QuestStatus.BUILD_BLOCKED) > 0);
		assertTrue("capped Attack and Defence close quests", zerker.count(QuestStatus.BUILD_BLOCKED) > 0);
		assertTrue("pure is stricter than zerker on Defence",
			pure.count(QuestStatus.BUILD_BLOCKED) != zerker.count(QuestStatus.BUILD_BLOCKED));
	}
}
