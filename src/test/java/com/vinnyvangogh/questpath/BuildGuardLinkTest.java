package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

public class BuildGuardLinkTest
{
	private final List<PluginMessage> sent = new ArrayList<>();
	private final BuildGuardLink link = new BuildGuardLink(o -> sent.add((PluginMessage) o));

	private static Map<String, Object> data(Object... pairs)
	{
		Map<String, Object> map = new HashMap<>();
		map.put("v", 1);
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static Map<String, Object> row(String skill, String kind, Integer level)
	{
		Map<String, Object> row = new HashMap<>();
		row.put("skill", skill);
		row.put("kind", kind);
		if (level != null)
		{
			row.put("level", level);
		}
		return row;
	}

	private boolean receive(String name, Map<String, Object> payload)
	{
		return link.handle(BuildGuardLink.QUEST_ROUTE, name, payload);
	}

	// ---- what we send ----------------------------------------------------

	@Test
	public void startupSendsHelloThenARulesRequestToBuildGuard()
	{
		link.sendStartup();
		assertEquals(2, sent.size());
		assertEquals(BuildGuardLink.BUILD_GUARD, sent.get(0).getNamespace());
		assertEquals("hello", sent.get(0).getName());
		assertEquals("rules-request", sent.get(1).getName());
		for (PluginMessage m : sent)
		{
			assertEquals(1, m.getData().get("v"));
			assertEquals("quest-route", m.getData().get("source"));
		}
	}

	@Test
	public void openPanelCarriesTheSection()
	{
		link.openPanel("rules");
		assertEquals("open-panel", sent.get(0).getName());
		assertEquals("rules", sent.get(0).getData().get("section"));
		sent.clear();
		link.openPanel(null);
		assertFalse(sent.get(0).getData().containsKey("section"));
	}

	@Test
	public void checkCarriesSkillAndXp()
	{
		link.check("attack", 4000);
		assertEquals("check", sent.get(0).getName());
		assertEquals("attack", sent.get(0).getData().get("skill"));
		assertEquals(4000, sent.get(0).getData().get("xp_gain"));
	}

	@Test
	public void importRulesSendsSavedRulesInTheWireShape()
	{
		link.importRules(Arrays.asList(
			BuildRule.local("Attack", BuildRule.HARD_CAP, 60, ""),
			BuildRule.local("Defence", BuildRule.NO_XP, 0, "")));
		assertEquals("import-rules", sent.get(0).getName());
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rules = (List<Map<String, Object>>) sent.get(0).getData().get("rules");
		assertEquals(2, rules.size());
		assertEquals("attack", rules.get(0).get("skill"));
		assertEquals("hard-cap", rules.get(0).get("kind"));
		assertEquals(60, rules.get(0).get("level"));
		assertEquals("no-xp", rules.get(1).get("kind"));
		assertFalse(rules.get(1).containsKey("level"));
	}

	// ---- presence ----------------------------------------------------------

	@Test
	public void isAbsentUntilBuildGuardSpeaks()
	{
		assertFalse(link.present());
		assertFalse(link.rulesKnown());
	}

	@Test
	public void presentMakesItPresentAndReportsTheChangeOnce()
	{
		assertTrue(receive("present", data()));
		assertTrue(link.present());
		assertFalse("a second present changes nothing", receive("present", data()));
	}

	@Test
	public void helloFromBuildGuardIsAnsweredWithPresent()
	{
		assertTrue(receive("hello", data()));
		assertTrue(link.present());
		assertEquals(1, sent.size());
		assertEquals("present", sent.get(0).getName());
		assertEquals(BuildGuardLink.BUILD_GUARD, sent.get(0).getNamespace());
	}

	@Test
	public void rulesAlsoProvePresenceInCaseTheHelloWasMissed()
	{
		receive("rules", data("rules", Collections.emptyList()));
		assertTrue(link.present());
		assertTrue(link.rulesKnown());
	}

	@Test
	public void resetForgetsEverything()
	{
		receive("rules", data("rules", Collections.singletonList(row("attack", "hard-cap", 60))));
		link.reset();
		assertFalse(link.present());
		assertFalse(link.rulesKnown());
		assertTrue(link.rules().isEmpty());
	}

	// ---- rules -------------------------------------------------------------

	@Test
	public void rulesAreDecodedFromAllFourKinds()
	{
		boolean changed = receive("rules", data(
			"account_type", "iron",
			"rules", Arrays.asList(
				row("attack", "hard-cap", 60),
				row("defence", "no-xp", null),
				row("prayer", "soft-cap", 43),
				row("strength", "goal", 99))));
		assertTrue(changed);
		assertEquals("iron", link.accountType());
		List<BuildRule> rules = link.rules();
		assertEquals(4, rules.size());
		assertEquals("Attack", rules.get(0).skillName());
		assertEquals(BuildRule.HARD_CAP, rules.get(0).type());
		assertEquals(60, rules.get(0).value());
		assertTrue(rules.get(0).blocking());
		assertEquals(BuildRule.NO_XP, rules.get(1).type());
		assertEquals(BuildRule.SOFT_CAP, rules.get(2).type());
		assertFalse(rules.get(2).blocking());
		assertEquals(BuildRule.GOAL, rules.get(3).type());
	}

	@Test
	public void aNewRulesBroadcastReplacesTheOldOne()
	{
		receive("rules", data("rules", Collections.singletonList(row("attack", "hard-cap", 60))));
		receive("rules", data("rules", Collections.singletonList(row("magic", "no-xp", null))));
		assertEquals(1, link.rules().size());
		assertEquals("Magic", link.rules().get(0).skillName());
	}

	@Test
	public void spellingVariantsAreAccepted()
	{
		receive("rules", data("rules", Arrays.asList(
			row("Attack", "HARD_CAP", 60), row("DEFENCE", "No XP", null))));
		assertEquals(BuildRule.HARD_CAP, link.rules().get(0).type());
		assertEquals(BuildRule.NO_XP, link.rules().get(1).type());
		assertEquals("Defence", link.rules().get(1).skillName());
	}

	@Test
	public void rowsItCannotReadAreSkippedNotFatal()
	{
		Map<String, Object> noSkill = new HashMap<>();
		noSkill.put("kind", "hard-cap");
		receive("rules", data("rules", Arrays.asList(
			row("attack", "a-kind-from-the-future", 60),
			noSkill,
			"not a map",
			row("magic", "hard-cap", 50))));
		assertEquals(1, link.rules().size());
		assertEquals("Magic", link.rules().get(0).skillName());
	}

	@Test
	public void unknownFieldsAreIgnored()
	{
		Map<String, Object> extra = row("attack", "hard-cap", 60);
		extra.put("colour", "red");
		receive("rules", data("rules", Collections.singletonList(extra), "something_new", 7));
		assertEquals(1, link.rules().size());
	}

	@Test
	public void aRulesPayloadWithNoListMeansNoRules()
	{
		receive("rules", data());
		assertTrue(link.rulesKnown());
		assertTrue(link.rules().isEmpty());
	}

	// ---- versioning and routing ---------------------------------------------

	@Test
	public void anUnknownVersionIsIgnored()
	{
		Map<String, Object> v2 = data("rules", Collections.singletonList(row("attack", "hard-cap", 60)));
		v2.put("v", 2);
		assertFalse(receive("rules", v2));
		assertFalse(link.present());
		assertTrue(link.rules().isEmpty());
	}

	@Test
	public void aMissingOrNonNumericVersionIsIgnored()
	{
		Map<String, Object> none = new HashMap<>();
		assertFalse(receive("present", none));
		none.put("v", "1");
		assertFalse(receive("present", none));
		assertFalse(link.present());
	}

	@Test
	public void aNumericVersionOfAnyBoxedTypeIsAccepted()
	{
		Map<String, Object> asDouble = new HashMap<>();
		asDouble.put("v", 1.0d);
		assertTrue(receive("present", asDouble));
	}

	@Test
	public void otherNamespacesAndUnknownNamesAreIgnored()
	{
		assertFalse(link.handle("shortestpath", "present", data()));
		assertFalse(link.handle(BuildGuardLink.BUILD_GUARD, "present", data()));
		assertFalse(receive("a-message-from-the-future", data()));
		assertFalse(receive(null, data()));
		assertFalse(link.handle(BuildGuardLink.QUEST_ROUTE, "present", null));
		assertFalse(link.present());
	}

	// ---- migration -----------------------------------------------------------

	private static final List<BuildRule> SAVED = Collections.singletonList(
		BuildRule.local("Defence", BuildRule.HARD_CAP, 13, ""));

	@Test
	public void savedRulesAreSentOnceWhenBuildGuardIsPresent()
	{
		receive("present", data());
		int[] marked = {0};
		assertTrue(link.migrateOnce(SAVED, false, () -> marked[0]++));
		assertEquals(1, marked[0]);
		assertEquals("import-rules", sent.get(sent.size() - 1).getName());
		assertEquals(BuildGuardLink.BUILD_GUARD, sent.get(sent.size() - 1).getNamespace());

		sent.clear();
		assertFalse("already migrated: not again", link.migrateOnce(SAVED, true, () -> marked[0]++));
		assertTrue(sent.isEmpty());
		assertEquals(1, marked[0]);
	}

	@Test
	public void nothingIsSentOrMarkedWhileBuildGuardIsAbsent()
	{
		int[] marked = {0};
		assertFalse(link.migrateOnce(SAVED, false, () -> marked[0]++));
		assertTrue(sent.isEmpty());
		assertEquals("rules stay put for the day it arrives", 0, marked[0]);
	}

	@Test
	public void anAccountWithNothingSavedIsMarkedWithoutSendingAnything()
	{
		receive("present", data());
		int[] marked = {0};
		assertTrue(link.migrateOnce(Collections.<BuildRule>emptyList(), false, () -> marked[0]++));
		assertEquals(1, marked[0]);
		assertTrue(sent.isEmpty());
	}

	@Test
	public void importedRulesRoundTripThroughTheDecoder()
	{
		link.importRules(Arrays.asList(
			BuildRule.local("Attack", BuildRule.HARD_CAP, 60, ""),
			BuildRule.local("Defence", BuildRule.NO_XP, 0, "")));
		List<BuildRule> back = BuildGuardLink.decode(sent.get(0).getData().get("rules"));
		assertEquals(2, back.size());
		assertEquals("Attack", back.get(0).skillName());
		assertEquals(60, back.get(0).value());
		assertEquals(BuildRule.NO_XP, back.get(1).type());
	}

	// ---- check-result --------------------------------------------------------

	@Test
	public void checkResultIsRecordedAndDoesNotRequestARedraw()
	{
		assertNull(link.lastCheck());
		assertFalse(receive("check-result", data("skill", "attack", "allowed", false,
			"rule", "hard-cap 60", "margin", -120)));
		BuildGuardLink.CheckResult result = link.lastCheck();
		assertEquals("attack", result.skill);
		assertFalse(result.allowed);
		assertEquals("hard-cap 60", result.rule);
		assertEquals(-120, result.margin);
	}

	@Test
	public void aCheckResultWithoutAllowedFalseCountsAsAllowed()
	{
		receive("check-result", data("skill", "magic"));
		assertTrue(link.lastCheck().allowed);
		assertEquals(0, link.lastCheck().margin);
	}
}
