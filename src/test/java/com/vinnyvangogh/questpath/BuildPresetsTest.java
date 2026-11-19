package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;

public class BuildPresetsTest
{
	@Test
	public void slugsAreUniqueBecauseTheyAreStored()
	{
		Set<String> seen = new HashSet<>();
		for (BuildPresets.Preset preset : BuildPresets.ALL)
		{
			assertTrue("duplicate slug " + preset.slug(), seen.add(preset.slug()));
		}
	}

	@Test
	public void everyRuleNamesARealSkillAndAValidLevel()
	{
		for (BuildPresets.Preset preset : BuildPresets.ALL)
		{
			assertTrue(preset.slug() + " has no rules", !preset.rules().isEmpty());
			for (BuildRule rule : preset.rules())
			{
				assertNotNull(preset.slug() + ": " + rule.skillName(), rule.skill());
				assertTrue(rule.skill() != Skill.OVERALL);
				if (BuildRuleCodec.takesValue(rule.type()))
				{
					assertTrue(preset.slug() + " level " + rule.value(), rule.value() >= 1 && rule.value() <= 99);
				}
			}
		}
	}

	@Test
	public void oneDefencePureHasNoDefenceXpAndAnAttackCap()
	{
		BuildPresets.Preset pure = BuildPresets.find("members_one_defence_pure_60");
		assertNotNull(pure);
		boolean defence = false;
		boolean attack = false;
		for (BuildRule rule : pure.rules())
		{
			defence |= rule.skillName().equals("Defence") && rule.type().equals(BuildRule.NO_XP);
			attack |= rule.skillName().equals("Attack") && rule.type().equals(BuildRule.HARD_CAP)
				&& rule.value() == 60;
		}
		assertTrue(defence);
		assertTrue(attack);
	}

	@Test
	public void obsidianMaulerBlocksAttackDefenceAndPrayerXp()
	{
		BuildPresets.Preset mauler = BuildPresets.find("members_obsidian_mauler_1def");
		int noXp = 0;
		for (BuildRule rule : mauler.rules())
		{
			if (rule.type().equals(BuildRule.NO_XP))
			{
				noXp++;
				assertTrue(rule.blocking());
			}
		}
		assertEquals(3, noXp);
	}

	@Test
	public void goalsWarnButNeverBlock()
	{
		for (BuildRule rule : BuildPresets.find("members_void_pure").rules())
		{
			if (rule.type().equals(BuildRule.GOAL))
			{
				assertTrue(!rule.blocking());
			}
		}
	}

	@Test
	public void aSavedRuleSetIsRecognisedAsItsPreset()
	{
		BuildPresets.Preset zerker = BuildPresets.find("members_berserker_45_60");
		assertEquals(zerker.slug(), BuildPresets.identify(zerker.rules()).slug());
	}

	@Test
	public void editedOrEmptyRulesAreCustom()
	{
		BuildPresets.Preset zerker = BuildPresets.find("members_berserker_45_60");
		assertNull(BuildPresets.identify(Collections.emptyList()));
		assertNull(BuildPresets.identify(BuildRuleCodec.set(zerker.rules(), "Attack", BuildRule.HARD_CAP, 61, "")));
	}

	@Test
	public void anUnknownSlugIsNullNotAnError()
	{
		assertNull(BuildPresets.find("from_a_newer_build"));
		assertNull(BuildPresets.find(null));
	}

	@Test
	public void bothHalvesOfTheGameHavePresets()
	{
		assertTrue(!BuildPresets.of(BuildPresets.Category.F2P).isEmpty());
		assertTrue(!BuildPresets.of(BuildPresets.Category.MEMBERS).isEmpty());
	}
}
