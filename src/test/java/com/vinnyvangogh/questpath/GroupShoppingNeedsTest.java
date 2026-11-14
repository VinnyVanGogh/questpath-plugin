package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.vinnyvangogh.questpath.groupshop.AccountKind;
import com.vinnyvangogh.questpath.groupshop.GroupShopAlert;
import com.vinnyvangogh.questpath.groupshop.GroupShoppingList;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import com.vinnyvangogh.questpath.groupshop.ItemNeed;
import com.vinnyvangogh.questpath.groupshop.MemberNeeds;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.vars.AccountType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Quest Route's side of the group shopping list: which quests count for each
 * member, what crosses the party wire for it, and when a member is stale.
 */
public class GroupShoppingNeedsTest
{
	private static final Gson GSON = new Gson();

	/** Dragon Slayer gives Defence xp and asks for a bucket; Witch's Potion asks for onions; Cook's for a bucket. */
	private static final String GRAPH = "{\"version\":12,\"diary_tasks\":[],\"quests\":["
		+ "{\"id\":1,\"name\":\"Cook's Assistant\",\"rewards\":[{\"skill\":\"cooking\",\"xp\":300}],\"items\":["
		+ "{\"name\":\"Bucket\",\"item_id\":1925,\"quantity\":1}]},"
		+ "{\"id\":2,\"name\":\"Dragon Slayer I\",\"rewards\":["
		+ "{\"skill\":\"strength\",\"xp\":18650},{\"skill\":\"defence\",\"xp\":18650}],\"items\":["
		+ "{\"name\":\"Bucket\",\"item_id\":1925,\"quantity\":5}]},"
		+ "{\"id\":3,\"name\":\"Witch's Potion\",\"rewards\":[{\"skill\":\"magic\",\"xp\":325}],\"items\":["
		+ "{\"name\":\"Onion\",\"item_id\":1957,\"quantity\":2},"
		+ "{\"name\":\"Coins\",\"item_id\":995,\"optional\":true},"
		+ "{\"name\":\"a light source\",\"unresolved\":true}"
		+ "]}"
		+ "]}";

	private static final Set<String> RELEVANT = new HashSet<>(Arrays.asList("bucket", "onion"));

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.parse(GSON, new StringReader(GRAPH));
	}

	private static GroupMember member(String name, List<String> completed, List<BuildRule> rules, List<String> route,
		AccountKind kind, Map<String, Long> held, Map<String, Long> storage)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		levels.put("Defence", 1);
		QuestpathGroupState message = GroupMember.encode(new HashSet<>(completed), Collections.emptySet(), levels,
			Collections.emptyMap(), rules, held, held != null, RELEVANT);
		GroupMember.withShopping(message, route, kind, storage, 1000L, RELEVANT);
		return GroupMember.decode(name.hashCode(), name, GSON.fromJson(GSON.toJson(message), QuestpathGroupState.class));
	}

	private static List<String> questNames(List<QuestGraph.Quest> quests)
	{
		return GroupPlanTest.names(quests);
	}

	private static BuildRule oneDefence()
	{
		return GroupPlanTest.oneDefence();
	}

	/** A quest that breaks the member's build never counts, so nobody shops for it. */
	@Test
	public void buildBlockedQuestsAreExcluded() throws Exception
	{
		GroupMember pure = member("Pure", Collections.emptyList(), Collections.singletonList(oneDefence()),
			Collections.emptyList(), AccountKind.NORMAL, null, null);

		List<String> quests = questNames(GroupShoppingNeeds.questsFor(graph(), pure));

		assertFalse(quests.contains("Dragon Slayer I"));
		assertTrue(quests.contains("Cook's Assistant"));
	}

	/** A route that names a build-blocked quest still does not count it. */
	@Test
	public void aRouteNeverCountsABuildBlockedQuest() throws Exception
	{
		GroupMember pure = member("Pure", Collections.emptyList(), Collections.singletonList(oneDefence()),
			Arrays.asList("Dragon Slayer I", "Witch's Potion"), AccountKind.NORMAL, null, null);

		assertEquals(Collections.singletonList("Witch's Potion"),
			questNames(GroupShoppingNeeds.questsFor(graph(), pure)));
	}

	/** With a route, only the route counts -- not every quest the build allows. */
	@Test
	public void aRouteLimitsTheQuestsCounted() throws Exception
	{
		GroupMember main = member("Main", Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList("Witch's Potion"), AccountKind.NORMAL, null, null);

		assertEquals(Collections.singletonList("Witch's Potion"),
			questNames(GroupShoppingNeeds.questsFor(graph(), main)));
	}

	/** Without a route, every quest not done that the build allows counts; done ones never do. */
	@Test
	public void withoutARouteEveryAllowedUnfinishedQuestCounts() throws Exception
	{
		GroupMember main = member("Main", Collections.singletonList("Cook's Assistant"), Collections.emptyList(),
			Collections.emptyList(), AccountKind.NORMAL, null, null);

		assertEquals(Arrays.asList("Dragon Slayer I", "Witch's Potion"),
			questNames(GroupShoppingNeeds.questsFor(graph(), main)));
	}

	/** Optional and unmatched lines are not shopped for; counts come through. */
	@Test
	public void needsSkipOptionalAndUnresolvedLines() throws Exception
	{
		GroupMember main = member("Main", Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList("Witch's Potion"), AccountKind.NORMAL, null, null);

		List<MemberNeeds> needs = GroupShoppingNeeds.of(graph(), Collections.singletonList(main), m -> false);

		assertEquals(1, needs.size());
		List<ItemNeed> items = needs.get(0).needs();
		assertEquals(1, items.size());
		assertEquals("Onion", items.get(0).item());
		assertEquals(2, items.get(0).count());
	}

	/** End to end: a pure and a main, both on Cook's; only the main also counts Dragon Slayer's buckets. */
	@Test
	public void theGroupTotalLeavesOutTheBuildBlockedQuestsBuckets() throws Exception
	{
		Map<String, Long> none = new LinkedHashMap<>();
		GroupMember pure = member("Pure", Collections.emptyList(), Collections.singletonList(oneDefence()),
			Collections.emptyList(), AccountKind.NORMAL, none, null);
		GroupMember main = member("Main", Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), AccountKind.NORMAL, none, null);

		GroupShoppingList list = GroupShoppingList.of(
			GroupShoppingNeeds.of(graph(), Arrays.asList(pure, main), m -> false), null, (id, name) -> true);

		GroupShoppingList.Line bucket = list.filtered(null, null, false).get(0);
		assertEquals("Bucket", bucket.name());
		assertEquals(1, bucket.share("Pure").need());
		assertEquals(6, bucket.share("Main").need());
		assertEquals(7, bucket.stillNeeded());
	}

	/** The new wire fields round-trip through Gson, the codec RuneLite's party relay uses. */
	@Test
	public void routeKindAndStorageCrossTheWire()
	{
		Map<String, Long> storage = new LinkedHashMap<>();
		storage.put("Bucket", 4L);
		storage.put("Dragon bones", 100L);
		GroupMember gim = member("Gim", Collections.emptyList(), Collections.emptyList(),
			Collections.singletonList("Cook's Assistant"), AccountKind.GROUP_IRONMAN, null, storage);

		assertEquals(AccountKind.GROUP_IRONMAN, gim.kind());
		assertEquals(Collections.singletonList("Cook's Assistant"), gim.route());
		GroupStorage seen = gim.groupStorage();
		assertEquals(4L, seen.count("bucket"));
		assertEquals("only quest items are shared", 0L, seen.count("dragon bones"));
		assertEquals(1000L, seen.seenAtMillis());
		assertEquals("Gim", seen.seenBy());
	}

	/** Storage is only ever sent for a group ironman. */
	@Test
	public void storageIsNotSentForOtherAccounts()
	{
		Map<String, Long> storage = new LinkedHashMap<>();
		storage.put("bucket", 4L);
		GroupMember main = member("Main", Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), AccountKind.NORMAL, null, storage);

		assertNull(main.groupStorage());
	}

	/** A message from a build before these fields reads as no route and unknown kind, never a guess. */
	@Test
	public void anOlderMessageReadsAsUnknownKindAndNoRoute()
	{
		QuestpathGroupState old = GroupMember.encode(Collections.singleton("Cook's Assistant"),
			Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), null,
			false, RELEVANT);
		GroupMember member = GroupMember.decode(5, "Old", GSON.fromJson(GSON.toJson(old), QuestpathGroupState.class));

		assertEquals(AccountKind.UNKNOWN, member.kind());
		assertTrue(member.route().isEmpty());
		assertNull(member.groupStorage());
	}

	@Test
	public void accountTypesMapToTradingRules()
	{
		assertEquals(AccountKind.NORMAL, GrandExchangeAccounts.kindOf(AccountType.NORMAL));
		assertEquals(AccountKind.IRONMAN, GrandExchangeAccounts.kindOf(AccountType.ULTIMATE_IRONMAN));
		assertEquals(AccountKind.GROUP_IRONMAN, GrandExchangeAccounts.kindOf(AccountType.HARDCORE_GROUP_IRONMAN));
		assertEquals(AccountKind.UNKNOWN, GrandExchangeAccounts.kindOf(null));
	}

	/** Two missed heartbeats, not one, mark a member stale; never heard is stale. */
	@Test
	public void staleAfterTwoMissedHeartbeats()
	{
		Instant now = Instant.parse("2026-11-04T12:00:00Z");

		assertFalse(GroupBroadcaster.stale(now.minus(GroupBroadcaster.HEARTBEAT.plusMinutes(1)), now));
		assertTrue(GroupBroadcaster.stale(now.minus(GroupBroadcaster.STALE_AFTER).minusSeconds(1), now));
		assertTrue(GroupBroadcaster.stale(null, now));
	}

	/** An unchanged state is re-sent after a heartbeat, so the party can tell quiet from gone. */
	@Test
	public void anUnchangedStateIsResentEachHeartbeat()
	{
		List<QuestpathGroupState> sent = new ArrayList<>();
		Instant[] now = {Instant.parse("2026-11-04T12:00:00Z")};
		GroupBroadcaster broadcaster = new GroupBroadcaster(sent::add, () -> now[0]);
		QuestpathGroupState state = GroupMember.encode(Collections.singleton("Cook's Assistant"),
			Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), null,
			false, RELEVANT);

		broadcaster.offer(true, true, () -> state, false);
		assertFalse(broadcaster.heartbeatDue());
		now[0] = now[0].plus(GroupBroadcaster.HEARTBEAT);
		assertTrue(broadcaster.heartbeatDue());
		broadcaster.offer(true, true, () -> state, false);

		assertEquals(2, sent.size());
		assertFalse(broadcaster.heartbeatDue());
	}

	/** A change to the shared route is structural: it goes out at once. */
	@Test
	public void aRouteChangeIsSentImmediately()
	{
		List<QuestpathGroupState> sent = new ArrayList<>();
		GroupBroadcaster broadcaster = new GroupBroadcaster(sent::add, () -> Instant.parse("2026-11-04T12:00:00Z"));

		broadcaster.offer(true, true, () -> GroupMember.withShopping(GroupMember.encode(Collections.emptySet(),
			Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), null,
			false, RELEVANT), Collections.singletonList("Cook's Assistant"), AccountKind.NORMAL, null, 0L,
			RELEVANT), false);
		broadcaster.offer(true, true, () -> GroupMember.withShopping(GroupMember.encode(Collections.emptySet(),
			Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), null,
			false, RELEVANT), Collections.singletonList("Witch's Potion"), AccountKind.NORMAL, null, 0L,
			RELEVANT), false);

		assertEquals(2, sent.size());
	}

	/**
	 * The group's shops ride the same approach watcher as the player's own:
	 * one alert as you come up on the shop, none while you stand there, one
	 * again after you leave and come back.
	 */
	@Test
	public void theGroupAlertFiresOncePerVisit()
	{
		MemberNeeds you = new MemberNeeds("You", AccountKind.NORMAL, new LinkedHashMap<>(), true, false,
			Collections.singletonList(new ItemNeed("Cook's Assistant", "Bucket", 1925, 3, true, false)));
		GroupShoppingList list = GroupShoppingList.of(Collections.singletonList(you), null, (id, name) -> true);
		Map<String, List<GroupShoppingList.Line>> byShop = GroupShopAlert.byShop(list.lines(),
			new com.vinnyvangogh.questpath.groupshop.ItemSources.Lookup()
			{
				@Override
				public List<String> shops(String item)
				{
					return Collections.singletonList("General Store");
				}

				@Override
				public boolean shopUsable(String shop)
				{
					return true;
				}

				@Override
				public List<String> drops(String item)
				{
					return Collections.emptyList();
				}

				@Override
				public boolean onGrandExchange(int itemId, String item)
				{
					return true;
				}
			});
		Map<String, WorldPoint> shops = new LinkedHashMap<>();
		WorldPoint store = new WorldPoint(3200, 3200, 0);
		for (String shop : byShop.keySet())
		{
			shops.put(shop, store);
		}
		WorldPoint far = new WorldPoint(3300, 3300, 0);
		WorldPoint near = new WorldPoint(3205, 3200, 0);
		ShopApproachWatcher watcher = new ShopApproachWatcher();

		assertTrue(watcher.observe(shops, far).isEmpty());
		assertEquals(Collections.singletonList("General Store"), watcher.observe(shops, near));
		assertTrue("standing there says nothing more", watcher.observe(shops, near).isEmpty());
		assertTrue(watcher.observe(shops, far).isEmpty());
		assertEquals(Collections.singletonList("General Store"), watcher.observe(shops, near));
		assertEquals("group needs 3 more Bucket", GroupShopAlert.describe(byShop.get("General Store")));
	}

	/** Duration sanity: the stale window outlasts two heartbeats. */
	@Test
	public void theStaleWindowOutlastsTwoHeartbeats()
	{
		assertTrue(GroupBroadcaster.STALE_AFTER.compareTo(GroupBroadcaster.HEARTBEAT.multipliedBy(2)) > 0);
		assertTrue(GroupBroadcaster.HEARTBEAT.compareTo(Duration.ofMinutes(10)) < 0);
	}
}
