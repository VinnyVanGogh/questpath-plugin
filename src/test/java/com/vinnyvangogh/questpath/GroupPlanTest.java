package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.vinnyvangogh.questpath.groupshop.Transfer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Group tab's answers: what the group can do together, what would break
 * a capped member's build, and who should hand what to whom.
 */
public class GroupPlanTest
{
	private static final Gson GSON = new Gson();

	/**
	 * Four quests, small enough to reason about by hand. Skill names are
	 * lower-case, as the bundled export writes them.
	 */
	private static final String GRAPH = "{\"version\":12,\"diary_tasks\":[],\"quests\":["
		+ "{\"id\":1,\"name\":\"Cook's Assistant\",\"rewards\":[{\"skill\":\"cooking\",\"xp\":300}]},"
		+ "{\"id\":2,\"name\":\"Dragon Slayer I\",\"rewards\":["
		+ "{\"skill\":\"strength\",\"xp\":18650},{\"skill\":\"defence\",\"xp\":18650}]},"
		+ "{\"id\":3,\"name\":\"Witch's Potion\",\"rewards\":[{\"skill\":\"magic\",\"xp\":325}],\"items\":["
		+ "{\"name\":\"Eye of newt\",\"item_id\":221,\"quantity\":1},"
		+ "{\"name\":\"Onion\",\"item_id\":1957,\"quantity\":2},"
		+ "{\"name\":\"Rat's tail\",\"item_id\":300,\"quantity\":1,\"obtainable\":true},"
		+ "{\"name\":\"Coins\",\"item_id\":995,\"optional\":true},"
		+ "{\"name\":\"a light source\",\"unresolved\":true},"
		+ "{\"name\":\"\",\"group_name\":\"Pickaxe\",\"alternatives\":[{\"name\":\"Bronze pickaxe\"}]}"
		+ "]},"
		+ "{\"id\":4,\"name\":\"Waterfall Quest\",\"requirements\":[{\"skill\":\"defence\",\"level\":30}],"
		+ "\"rewards\":[{\"skill\":\"attack\",\"xp\":13750}]}"
		+ "]}";

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.parse(GSON, new StringReader(GRAPH));
	}

	private static GroupMember member(long id, String name, List<String> completed, List<BuildRule> rules,
		Map<String, Long> held, Map<String, Integer> experience)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		levels.put("Defence", 40);
		Set<String> relevant = new HashSet<>(Arrays.asList("eye of newt", "onion", "rat's tail"));
		QuestpathGroupState message = GroupMember.encode(new HashSet<>(completed), Collections.emptySet(),
			levels, experience, rules, held, held != null, relevant);
		return GroupMember.decode(id, name, GSON.fromJson(GSON.toJson(message), QuestpathGroupState.class));
	}

	private static GroupMember member(long id, String name, List<String> completed, List<BuildRule> rules,
		Map<String, Long> held)
	{
		return member(id, name, completed, rules, held, Collections.emptyMap());
	}

	private static Map<String, Long> held(Object... pairs)
	{
		Map<String, Long> out = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((String) pairs[i], (Long) pairs[i + 1]);
		}
		return out;
	}

	static List<String> names(List<QuestGraph.Quest> quests)
	{
		List<String> out = new ArrayList<>();
		for (QuestGraph.Quest quest : quests)
		{
			out.add(quest.name());
		}
		return out;
	}

	static BuildRule oneDefence()
	{
		return BuildRule.local("Defence", BuildRule.HARD_CAP, 1, "1 def pure");
	}

	/** The point of the feature: a 1 Defence pure is warned before the group plans Dragon Slayer. */
	@Test
	public void aOneDefenceMemberIsFlaggedOnAQuestThatRewardsDefence() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.singletonList(oneDefence()), null);

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, bob));

		assertFalse("Dragon Slayer must not be offered as a group quest",
			names(plan.together()).contains("Dragon Slayer I"));
		GroupPlan.Flag flag = plan.flag("Dragon Slayer I");
		assertNotNull("Dragon Slayer must be flagged", flag);
		assertEquals(1, flag.breaches().size());
		GroupPlan.MemberBreach breach = flag.breaches().get(0);
		assertEquals("Bob", breach.member());
		assertEquals("defence", breach.breach().skill());
		assertEquals(BuildBreaches.Kind.REWARD, breach.breach().kind());
		assertEquals(18650, breach.breach().amount());
		assertTrue(breach.describe(), breach.describe().contains("18,650 Defence xp"));
		assertTrue(breach.describe(), breach.describe().contains("capped at 1"));
	}

	/** Only the capped member's own skills count; a reward in another skill is no breach. */
	@Test
	public void questsRewardingOtherSkillsStayGroupQuests() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.singletonList(oneDefence()), null);

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, bob));

		assertTrue(names(plan.together()).contains("Witch's Potion"));
		assertTrue(names(plan.together()).contains("Cook's Assistant"));
		assertNull(plan.flag("Witch's Potion"));
	}

	/** A cap with headroom is not a breach: 18,650 xp from zero is level 33, under a 70 cap. */
	@Test
	public void aCapWithRoomForTheRewardIsNotFlagged() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(),
			Collections.singletonList(BuildRule.local("Defence", BuildRule.HARD_CAP, 70, "")), null);

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, bob));

		assertNull(plan.flag("Dragon Slayer I"));
		assertTrue(names(plan.together()).contains("Dragon Slayer I"));
	}

	/** The member's real experience counts: one more Defence level is too many for a cap of 32 at 18k xp. */
	@Test
	public void theCapIsJudgedFromTheMembersOwnExperience() throws Exception
	{
		Map<String, Integer> xp = new LinkedHashMap<>();
		xp.put("Defence", 18000);
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(),
			Collections.singletonList(BuildRule.local("Defence", BuildRule.HARD_CAP, 32, "")), null, xp);

		assertNotNull(GroupPlan.of(graph(), Arrays.asList(alice, bob)).flag("Dragon Slayer I"));
	}

	@Test
	public void noXpMeansAnyRewardAtAllIsFlagged() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember cy = member(3, "Cy", Collections.emptyList(),
			Collections.singletonList(BuildRule.local("Cooking", BuildRule.NO_XP, 0, "")), null);

		GroupPlan.Flag flag = GroupPlan.of(graph(), Arrays.asList(alice, cy)).flag("Cook's Assistant");
		assertNotNull(flag);
		assertTrue(flag.breaches().get(0).describe().contains("no xp at all"));
	}

	/** A requirement above the cap is the other half: a 1 Defence pure cannot do Waterfall's 30 Defence. */
	@Test
	public void aRequirementAboveTheCapIsFlaggedToo() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.singletonList(oneDefence()), null);

		GroupPlan.Flag flag = GroupPlan.of(graph(), Arrays.asList(alice, bob)).flag("Waterfall Quest");
		assertNotNull(flag);
		assertEquals(BuildBreaches.Kind.REQUIREMENT, flag.breaches().get(0).breach().kind());
	}

	/** A quest one member has done is not a "together" quest, but the others still need it. */
	@Test
	public void aQuestDoneBySomeIsListedWithWhoStillNeedsIt() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.singletonList("Cook's Assistant"),
			Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.emptyList(), null);

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, bob));

		assertFalse(names(plan.together()).contains("Cook's Assistant"));
		assertEquals(1, plan.doneBySome().size());
		GroupPlan.Partial partial = plan.doneBySome().get(0);
		assertEquals("Cook's Assistant", partial.quest().name());
		assertEquals(Collections.singletonList("Alice"), partial.doneBy());
		assertEquals(Collections.singletonList("Bob"), partial.stillNeed());
		assertEquals(1, plan.doneCount(alice));
		assertEquals(0, plan.doneCount(bob));
	}

	/** A member whose message this build cannot read is shown, never planned for. */
	@Test
	public void anIncompatibleMemberIsListedButNotPlannedFor() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember newer = GroupMember.decode(9, "Dee",
			GSON.fromJson("{\"version\":99}", QuestpathGroupState.class));

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, newer));

		assertEquals(2, plan.members().size());
		assertEquals(1, plan.planned().size());
		assertTrue(names(plan.together()).contains("Cook's Assistant"));
	}

	// ----------------------------------------------------------------------
	// Shopping split
	// ----------------------------------------------------------------------

	private static ItemSplit.Line line(ItemSplit split, String name)
	{
		for (ItemSplit.Line line : split.lines())
		{
			if (line.name().equals(name))
			{
				return line;
			}
		}
		return null;
	}

	/**
	 * Every member doing the quest needs their own copy. Alice holds a spare
	 * eye of newt, so she hands it to Bob rather than Bob buying one; nobody
	 * has onions, so each gets their own two.
	 */
	@Test
	public void spareItemsAreHandedOverBeforeAnythingIsBought() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(),
			held("eye of newt", 2L));
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.emptyList(), held());

		GroupPlan plan = GroupPlan.of(graph(), Arrays.asList(alice, bob));
		ItemSplit split = plan.split(graph().byName("Witch's Potion"));

		ItemSplit.Line eye = line(split, "Eye of newt");
		assertNotNull(eye);
		assertEquals(2, eye.totalNeeded());
		assertEquals(1, eye.transfers().size());
		Transfer transfer = eye.transfers().get(0);
		assertEquals("Alice", transfer.from());
		assertEquals("Bob", transfer.to());
		assertEquals(1L, transfer.count());
		assertTrue("covered by the hand-over", eye.toGet().isEmpty());

		ItemSplit.Line onion = line(split, "Onion");
		assertEquals(4, onion.totalNeeded());
		assertTrue(onion.transfers().isEmpty());
		assertEquals(Long.valueOf(2), onion.toGet().get("Alice"));
		assertEquals(Long.valueOf(2), onion.toGet().get("Bob"));
	}

	/** Someone who already finished the quest needs nothing, but their spares still count. */
	@Test
	public void aMemberWhoFinishedTheQuestOnlyGives() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.singletonList("Witch's Potion"),
			Collections.emptyList(), held("onion", 5L));
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.emptyList(), held());

		ItemSplit.Line onion = line(GroupPlan.of(graph(), Arrays.asList(alice, bob))
			.split(graph().byName("Witch's Potion")), "Onion");

		assertEquals(2, onion.totalNeeded());
		assertEquals(1, onion.transfers().size());
		assertEquals("Alice", onion.transfers().get(0).from());
		assertEquals(2L, onion.transfers().get(0).count());
		assertFalse(onion.toGet().containsKey("Alice"));
	}

	/**
	 * A member who shares no items is "unknown", not "holds nothing" -- the
	 * split must not tell them to buy what may be sitting in their bank.
	 */
	@Test
	public void aMemberWithoutSharedItemsIsUnknownNotShort() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), held());
		GroupMember cy = member(3, "Cy", Collections.emptyList(), Collections.emptyList(), null);

		ItemSplit split = GroupPlan.of(graph(), Arrays.asList(alice, cy)).split(graph().byName("Witch's Potion"));
		ItemSplit.Line onion = line(split, "Onion");

		assertEquals(Collections.singletonList("Cy"), onion.unknown());
		assertFalse(onion.toGet().containsKey("Cy"));
		assertEquals(Long.valueOf(2), onion.toGet().get("Alice"));
		assertEquals(Collections.singletonList("Cy"), split.membersWithoutItems());
	}

	/**
	 * Optional, unmatched and category lines are not split -- but are counted,
	 * so the list never passes itself off as everything the quest needs.
	 */
	@Test
	public void linesThatCannotBeSplitAreCountedNotDropped() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), held());

		ItemSplit split = GroupPlan.of(graph(), Collections.singletonList(alice))
			.split(graph().byName("Witch's Potion"));

		assertNull(line(split, "Coins"));
		assertNull(line(split, "a light source"));
		assertEquals(3, split.lines().size());
		assertEquals(3, split.unsplit());
		assertTrue(line(split, "Rat's tail").obtainable());
	}

	/** With nobody left to do it, there is nothing to split. */
	@Test
	public void aQuestEveryoneFinishedSplitsToNothing() throws Exception
	{
		GroupMember alice = member(1, "Alice", Collections.singletonList("Witch's Potion"),
			Collections.emptyList(), held("onion", 5L));

		ItemSplit split = GroupPlan.of(graph(), Collections.singletonList(alice))
			.split(graph().byName("Witch's Potion"));
		assertTrue(split.doers().isEmpty());
		for (ItemSplit.Line line : split.lines())
		{
			assertEquals(0, line.totalNeeded());
		}
	}

	/** Over the real bundled graph: Dragon Slayer I's Defence reward flags a 1 Defence pure. */
	@Test
	public void theBundledGraphFlagsDragonSlayerForAOneDefencePure() throws Exception
	{
		QuestGraph bundled = QuestGraph.load(GSON);
		GroupMember alice = member(1, "Alice", Collections.emptyList(), Collections.emptyList(), null);
		GroupMember bob = member(2, "Bob", Collections.emptyList(), Collections.singletonList(oneDefence()), null);

		GroupPlan plan = GroupPlan.of(bundled, Arrays.asList(alice, bob));
		GroupPlan.Flag flag = plan.flag("Dragon Slayer I");

		assertNotNull(flag);
		assertEquals("Bob", flag.breaches().get(0).member());
		assertFalse(names(plan.together()).contains("Dragon Slayer I"));
	}
}
