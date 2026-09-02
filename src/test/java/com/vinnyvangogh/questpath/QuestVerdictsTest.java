package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The grouping the Quests tab renders, over the real bundled graph.
 */
public class QuestVerdictsTest
{
	private static final Gson GSON = new Gson();

	private static PlayerState freshAccount(java.util.List<BuildRule> rules)
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		return new PlayerState(levels, experience, new HashSet<>(), rules);
	}

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	/**
	 * Without rules nothing can be BUILD_BLOCKED, so every verdict is "as far
	 * as I know". The panel has to say so, and this is the flag it reads --
	 * rendering a pure's quest list as a main's is the exact mistake this
	 * plugin exists to prevent.
	 */
	@Test
	public void rulesKnownIsFalseWhenNoneHaveArrived() throws Exception
	{
		QuestVerdicts verdicts = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));
		assertFalse(verdicts.rulesKnown());
		assertEquals("nothing can break a rule that was never received",
			0, verdicts.count(QuestStatus.BUILD_BLOCKED));
	}

	/**
	 * A fresh account with no rules is short of levels and prerequisites, not
	 * blocked by its build. If this ever came back all-ELIGIBLE the engine
	 * would be doing nothing.
	 */
	@Test
	public void aFreshAccountIsMostlyBlockedByLevelsAndQuests() throws Exception
	{
		QuestVerdicts verdicts = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));

		assertTrue("a level 1 account cannot do most quests",
			verdicts.count(QuestStatus.SKILL_BLOCKED) > 0);
		assertTrue("some quests are behind others",
			verdicts.count(QuestStatus.QUEST_BLOCKED) > 0);
		assertTrue("and a few need nothing at all",
			verdicts.count(QuestStatus.ELIGIBLE) > 0);
		assertEquals("a fresh account has finished nothing",
			0, verdicts.count(QuestStatus.COMPLETE));
	}

	/**
	 * The whole point, on the real graph: a NO_XP Defence closes quests that a
	 * levels-only tool would call merely unmet.
	 */
	@Test
	public void aNoXpRuleClosesQuestsPermanently() throws Exception
	{
		QuestVerdicts unconstrained = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));
		QuestVerdicts pure = QuestVerdicts.of(graph(),
			freshAccount(Collections.singletonList(ruleOf("defence", "NO_XP", 0))));

		assertTrue(pure.rulesKnown());
		assertTrue("a 1-Defence rule must permanently close some quests",
			pure.count(QuestStatus.BUILD_BLOCKED) > 0);
		assertTrue("and poison others through their prerequisites",
			pure.count(QuestStatus.UNREACHABLE) > 0);
		assertTrue("so fewer quests are doable than without the rule",
			pure.count(QuestStatus.ELIGIBLE) < unconstrained.count(QuestStatus.ELIGIBLE));
	}

	/**
	 * Groups list in full. They used to stop at eight with "and 49 more",
	 * which named 49 quests you could not read -- the counts in the headings
	 * already gave the summary, so the truncated list added nothing.
	 */
	@Test
	public void aGroupListsEveryQuestInIt() throws Exception
	{
		QuestVerdicts verdicts = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));

		int listed = 0;
		for (QuestStatus status : QuestVerdicts.sections())
		{
			assertEquals(status + ": the list and the count must agree",
				verdicts.count(status), verdicts.quests(status).size());
			listed += verdicts.quests(status).size();
		}
		assertTrue("the sections between them must cover most of the graph", listed > 100);
	}

	/**
	 * The signature is what stops ~200 labels being rebuilt on every
	 * experience drop. It has to move when the answer moves and hold still
	 * when it does not.
	 */
	@Test
	public void theSignatureTracksWhatIsRendered() throws Exception
	{
		QuestVerdicts unconstrained = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));
		QuestVerdicts same = QuestVerdicts.of(graph(), freshAccount(Collections.emptyList()));
		assertEquals("an unchanged account must not force a rebuild",
			unconstrained.signature(), same.signature());

		QuestVerdicts pure = QuestVerdicts.of(graph(),
			freshAccount(Collections.singletonList(ruleOf("defence", "NO_XP", 0))));
		assertTrue("a rule that closes quests must force a rebuild",
			pure.signature() != unconstrained.signature());
	}

	@Test
	public void everySectionHasAReadableLabel()
	{
		for (QuestStatus status : QuestVerdicts.sections())
		{
			String label = QuestVerdicts.label(status);
			assertFalse(status + " has no label", label.isEmpty());
			assertFalse(status + " shows the enum name to the player", label.equals(status.name()));
		}
	}

	private static BuildRule ruleOf(String skill, String type, int value)
	{
		String json = "{\"build_rules\":[{\"skill\":\"" + skill + "\",\"type\":\"" + type + "\""
			+ (value > 0 ? ",\"value\":" + value : "") + "}]}";
		return GSON.fromJson(json, PushResponse.class).buildRules().get(0);
	}
}
