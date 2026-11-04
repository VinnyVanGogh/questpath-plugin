package com.vinnyvangogh.questpath.groupshop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The group totals: what the whole group still has to get, after each
 * member's own holding, tradeable spares and group storage.
 */
public class GroupShoppingListTest
{
	private static final GroupShoppingList.Tradeability ALL_TRADEABLE = (id, name) -> true;
	private static final GroupShoppingList.Tradeability NONE_TRADEABLE = (id, name) -> false;

	private static Map<String, Long> held(Object... pairs)
	{
		Map<String, Long> out = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return out;
	}

	private static ItemNeed buckets(String quest, long count)
	{
		return new ItemNeed(quest, "Bucket", 1925, count, true, false);
	}

	private static MemberNeeds member(String name, AccountKind kind, Map<String, Long> held, ItemNeed... needs)
	{
		return new MemberNeeds(name, kind, held, true, false, Arrays.asList(needs));
	}

	private static List<MemberNeeds> fiveNeedingSixBuckets(Map<String, Long> firstHolds)
	{
		List<MemberNeeds> out = new ArrayList<>();
		String[] names = {"You", "Alice", "Bob", "Cara", "Dee"};
		for (int i = 0; i < names.length; i++)
		{
			out.add(member(names[i], AccountKind.GROUP_IRONMAN, i == 0 ? firstHolds : held(),
				buckets("Quest A", 6)));
		}
		return out;
	}

	private static GroupShoppingList.Line only(GroupShoppingList list)
	{
		assertEquals(1, list.lines().size());
		return list.lines().get(0);
	}

	/** The example the group asked with: 6 each across 5 members, 2 held, get 28. */
	@Test
	public void sixEachAcrossFiveMembersMinusTwoHeldIsTwentyEight()
	{
		GroupShoppingList list = GroupShoppingList.of(fiveNeedingSixBuckets(held("bucket", 2)), null, ALL_TRADEABLE);

		GroupShoppingList.Line line = only(list);
		assertEquals("Bucket", line.name());
		assertEquals(30, line.totalNeeded());
		assertEquals(28, line.stillNeeded());
		assertEquals(4, line.share("You").shortBy());
		assertEquals(6, line.share("Alice").shortBy());
		assertEquals(Long.valueOf(2), line.holders().get("You"));
	}

	/** A member who leaves the party is not passed in, and their need goes with them. */
	@Test
	public void aMemberLeavingThePartyTakesTheirNeedWithThem()
	{
		List<MemberNeeds> members = fiveNeedingSixBuckets(held("bucket", 2));
		members.remove(4);

		GroupShoppingList.Line line = only(GroupShoppingList.of(members, null, ALL_TRADEABLE));

		assertEquals(24, line.totalNeeded());
		assertEquals(22, line.stillNeeded());
		assertNull(line.share("Dee"));
	}

	/** A stale member is still counted, and named on the line, so the reader knows which number may have moved. */
	@Test
	public void aStaleMemberIsCountedAndMarked()
	{
		MemberNeeds fresh = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 2));
		MemberNeeds stale = new MemberNeeds("Bob", AccountKind.NORMAL, held(), true, true,
			Collections.singletonList(buckets("Quest A", 3)));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Arrays.asList(fresh, stale), null, ALL_TRADEABLE));

		assertEquals(5, line.stillNeeded());
		assertEquals(Collections.singletonList("Bob"), line.staleMembers());
		assertTrue(line.share("Bob").stale());
	}

	/** Spares pass between teammates who can trade: a member needing none spares all of theirs. */
	@Test
	public void tradeableSparesPoolBetweenGroupIronmen()
	{
		MemberNeeds you = member("You", AccountKind.GROUP_IRONMAN, held(), buckets("Quest A", 6));
		MemberNeeds bob = member("Bob", AccountKind.GROUP_IRONMAN, held("Bucket", 10));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Arrays.asList(you, bob), null, ALL_TRADEABLE));

		assertEquals(0, line.stillNeeded());
		assertEquals(6, line.share("You").received());
		assertEquals(1, line.transfers().size());
		assertEquals("Bob", line.transfers().get(0).from());
		assertEquals(6, line.transfers().get(0).count());
	}

	/** An untradeable item only ever offsets its holder's own need. */
	@Test
	public void untradeableItemsOnlyOffsetTheHoldersOwnNeed()
	{
		MemberNeeds you = member("You", AccountKind.GROUP_IRONMAN, held("bucket", 1), buckets("Quest A", 6));
		MemberNeeds bob = member("Bob", AccountKind.GROUP_IRONMAN, held("bucket", 10));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Arrays.asList(you, bob), null, NONE_TRADEABLE));

		assertEquals(5, line.stillNeeded());
		assertTrue(line.transfers().isEmpty());
	}

	/** A regular ironman trades with nobody: their spares are theirs alone, and nobody's spares reach them. */
	@Test
	public void aRegularIronmansHoldingOnlyOffsetsTheirOwnNeed()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 6));
		MemberNeeds iron = member("Iron", AccountKind.IRONMAN, held("bucket", 20), buckets("Quest A", 6));
		MemberNeeds main = member("Main", AccountKind.NORMAL, held("bucket", 3));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Arrays.asList(you, iron, main), null, ALL_TRADEABLE));

		assertEquals(0, line.share("Iron").shortBy());
		assertEquals(3, line.share("You").shortBy());
		assertEquals(3, line.stillNeeded());
	}

	/** A group ironman and a normal account cannot trade, even with a tradeable item. */
	@Test
	public void groupIronmenAndNormalAccountsDoNotPool()
	{
		MemberNeeds gim = member("Gim", AccountKind.GROUP_IRONMAN, held(), buckets("Quest A", 6));
		MemberNeeds main = member("Main", AccountKind.NORMAL, held("bucket", 6));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Arrays.asList(gim, main), null, ALL_TRADEABLE));

		assertEquals(6, line.stillNeeded());
	}

	/** An account of unknown type is never pooled: guessing it can trade is the confident wrong answer. */
	@Test
	public void unknownAccountKindsAreNotPooled()
	{
		MemberNeeds you = member("You", AccountKind.UNKNOWN, held(), buckets("Quest A", 2));
		MemberNeeds bob = member("Bob", AccountKind.UNKNOWN, held("bucket", 5));

		assertEquals(2, only(GroupShoppingList.of(Arrays.asList(you, bob), null, ALL_TRADEABLE)).stillNeeded());
	}

	/** Group storage counts for group ironmen, once, from the newest snapshot. */
	@Test
	public void groupStorageCountsOnceFromTheFreshestSnapshot()
	{
		GroupStorage older = new GroupStorage(held("bucket", 20), 1000L, "Bob");
		GroupStorage newer = new GroupStorage(held("bucket", 5), 2000L, "Alice");
		GroupStorage storage = GroupStorage.freshest(Arrays.asList(older, newer));

		GroupShoppingList.Line line = only(GroupShoppingList.of(fiveNeedingSixBuckets(held("bucket", 2)), storage,
			ALL_TRADEABLE));

		assertEquals("Alice", storage.seenBy());
		assertEquals(5, line.storageUsed());
		assertEquals(23, line.stillNeeded());
	}

	/** Storage is a group ironman thing; a normal account cannot draw on it. */
	@Test
	public void groupStorageNeverCoversANormalAccount()
	{
		MemberNeeds main = member("Main", AccountKind.NORMAL, held(), buckets("Quest A", 4));
		GroupStorage storage = new GroupStorage(held("bucket", 20), 1L, "Gim");

		GroupShoppingList.Line line = only(GroupShoppingList.of(Collections.singletonList(main), storage,
			ALL_TRADEABLE));

		assertEquals(4, line.stillNeeded());
		assertEquals(0, line.storageUsed());
	}

	/** A member who shared no counts is unknown: their need is reported apart, never as "buy it". */
	@Test
	public void aMemberWithoutItemCountsIsKeptOutOfStillNeeded()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 2));
		MemberNeeds bob = new MemberNeeds("Bob", AccountKind.NORMAL, null, false, false,
			Collections.singletonList(buckets("Quest A", 6)));

		GroupShoppingList list = GroupShoppingList.of(Arrays.asList(you, bob), null, ALL_TRADEABLE);
		GroupShoppingList.Line line = only(list);

		assertEquals(2, line.stillNeeded());
		assertEquals(6, line.unknownNeed());
		assertEquals(8, line.totalNeeded());
		assertTrue(line.share("Bob").unknown());
		assertEquals(Collections.singletonList("Bob"), list.membersWithoutItems());
	}

	/** Needs from several quests add up for one member. */
	@Test
	public void needsAcrossQuestsAddUp()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 2), buckets("Quest B", 3));

		GroupShoppingList.Line line = only(GroupShoppingList.of(Collections.singletonList(you), null, ALL_TRADEABLE));

		assertEquals(5, line.stillNeeded());
		assertEquals(Arrays.asList("Quest A", "Quest B"), new ArrayList<>(line.quests()));
	}

	/** The quest filter recomputes: holdings are spent on that quest alone. */
	@Test
	public void theQuestFilterRecomputesForThatQuest()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held("bucket", 3), buckets("Quest A", 2),
			buckets("Quest B", 3));
		GroupShoppingList list = GroupShoppingList.of(Collections.singletonList(you), null, ALL_TRADEABLE);

		assertEquals(2, list.lines().get(0).stillNeeded());
		List<GroupShoppingList.Line> questB = list.filtered("Quest B", null, true);
		assertTrue(questB.isEmpty());
		List<GroupShoppingList.Line> questBAll = list.filtered("Quest B", null, false);
		assertEquals(1, questBAll.size());
		assertEquals(0, questBAll.get(0).stillNeeded());
	}

	/** The member filter hides lines that member needs none of, and keeps the group's totals. */
	@Test
	public void theMemberFilterKeepsGroupTotals()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 2));
		MemberNeeds bob = member("Bob", AccountKind.NORMAL, held(), buckets("Quest A", 3),
			new ItemNeed("Quest A", "Rope", 954, 1, true, false));
		GroupShoppingList list = GroupShoppingList.of(Arrays.asList(you, bob), null, ALL_TRADEABLE);

		List<GroupShoppingList.Line> mine = list.filtered(null, "You", false);
		assertEquals(1, mine.size());
		assertEquals(5, mine.get(0).stillNeeded());
		assertEquals(2, list.filtered(null, "Bob", false).size());
	}

	/** Still-needed-only drops a covered line. */
	@Test
	public void stillNeededOnlyDropsCoveredLines()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held("bucket", 2, "rope", 0), buckets("Quest A", 2),
			new ItemNeed("Quest A", "Rope", 954, 1, true, false));
		GroupShoppingList list = GroupShoppingList.of(Collections.singletonList(you), null, ALL_TRADEABLE);

		assertEquals(2, list.filtered(null, null, false).size());
		List<GroupShoppingList.Line> short_ = list.filtered(null, null, true);
		assertEquals(1, short_.size());
		assertEquals("Rope", short_.get(0).name());
	}

	/** No tradeability answer is treated as untradeable: the conservative direction. */
	@Test
	public void noTradeabilityAnswerMeansNoPooling()
	{
		MemberNeeds you = member("You", AccountKind.NORMAL, held(), buckets("Quest A", 1));
		MemberNeeds bob = member("Bob", AccountKind.NORMAL, held("bucket", 5));

		assertEquals(1, only(GroupShoppingList.of(Arrays.asList(you, bob), null, null)).stillNeeded());
	}
}
