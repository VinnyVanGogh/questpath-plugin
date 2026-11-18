package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.vinnyvangogh.questpath.groupshop.AccountKind;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Group storage counted as held for a Group Ironman's own item lists.
 *
 * <p>The report: a quest asked for a blood rune, the list said "you need a
 * blood rune", and one was sitting in group storage.
 */
public class GroupStorageCountsTest
{
	private static final Gson GSON = new Gson();
	private static final long HOUR = 3_600_000L;

	/** One doable quest wanting {@code quantity} blood runes. */
	private static QuestGraph graph(int quantity)
	{
		return GSON.fromJson("{\"version\":13,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Blood rune\",\"item_id\":565,\"quantity\":" + quantity + "}],"
			+ "\"recommended_items\":[]}],\"diary_tasks\":[]}", QuestGraph.class);
	}

	private static PlayerState account()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(), Collections.emptyList());
	}

	private static GroupStorage storage(long bloodRunes, long at, String by)
	{
		Map<String, Long> counts = new HashMap<>();
		counts.put("blood rune", bloodRunes);
		return new GroupStorage(counts, at, by);
	}

	private static QuestItems items(int need, Map<String, Long> held, boolean bankSeen,
		Map<String, Long> known, GroupStorageView view)
	{
		return QuestItems.of(graph(need), account(), held, bankSeen, Collections.<String>emptySet(),
			Collections.<String>emptySet(), known, Collections.<String, Long>emptyMap(),
			Collections.<String>emptySet(), null, view);
	}

	private static QuestItems items(int need, Map<String, Long> held, GroupStorageView view)
	{
		return items(need, held, true, Collections.<String, Long>emptyMap(), view);
	}

	private static Map<String, Long> held(long bloodRunes)
	{
		Map<String, Long> out = new HashMap<>();
		if (bloodRunes > 0)
		{
			out.put("blood rune", bloodRunes);
		}
		return out;
	}

	private static QuestItems.Line line(QuestItems items)
	{
		return items.quests().get(0).lines().get(0);
	}

	private static QuestItems.Total total(QuestItems items)
	{
		return items.totals().get(0);
	}

	private static GroupStorageView gim(GroupStorage... snapshots)
	{
		return GroupStorageView.of(AccountKind.GROUP_IRONMAN, Arrays.asList(snapshots));
	}

	@Test
	public void bloodRuneOnlyInStorageSatisfiesTheLineAndSaysWhere()
	{
		QuestItems items = items(1, held(0), gim(storage(1, 1_000L, "you")));

		assertTrue(line(items).held());
		assertEquals("1 / 1, in group storage", line(items).describe());
		assertTrue(line(items).needsGroupStorage());
		// The shop route and the Grand Exchange list both skip covered totals:
		// this is "go withdraw", never "go buy".
		assertTrue(total(items).satisfied());
		assertTrue(total(items).covered());
		assertEquals(0L, total(items).outstanding());
		assertTrue(total(items).describe().endsWith(", in group storage"));
	}

	@Test
	public void storageIsNotMentionedWhenTheAccountAlreadyHasEnough()
	{
		QuestItems items = items(1, held(1), gim(storage(5, 1_000L, "you")));

		assertTrue(line(items).held());
		assertFalse(line(items).needsGroupStorage());
		assertEquals("6 / 1", line(items).describe());
	}

	@Test
	public void memberFresherSnapshotWinsOverOwnOlderOne()
	{
		GroupStorageView view = gim(storage(0, 1_000L, "you"), storage(1, 2_000L, "Iron Mate"));

		assertEquals(GroupStorageView.State.SEEN, view.state());
		assertEquals("Iron Mate", view.snapshot().seenBy());
		assertTrue(line(items(1, held(0), view)).held());

		GroupStorageView ownNewer = gim(storage(0, 3_000L, "you"), storage(1, 2_000L, "Iron Mate"));
		assertEquals("you", ownNewer.snapshot().seenBy());
		assertFalse("the newer snapshot says it was taken out", line(items(1, held(0), ownNewer)).held());
	}

	@Test
	public void storageNeverSeenIsUnknownAndStillNeeded()
	{
		GroupStorageView view = gim(null, null);
		QuestItems items = items(1, held(0), view);

		assertEquals(GroupStorageView.State.UNSEEN, view.state());
		assertFalse(line(items).held());
		assertEquals("0 / 1", line(items).describe());
		String note = items.groupStorage().note(10_000L);
		assertNotNull(note);
		assertTrue(note, note.contains("not seen yet"));
		assertTrue(note, note.contains("open it once"));
	}

	@Test
	public void nonGroupIronmanIgnoresStorage()
	{
		for (AccountKind kind : new AccountKind[]{AccountKind.NORMAL, AccountKind.IRONMAN, AccountKind.UNKNOWN})
		{
			GroupStorageView view = GroupStorageView.of(kind, Collections.singletonList(storage(1, 1_000L, "x")));
			assertSame(GroupStorageView.NONE, view);
			QuestItems items = items(1, held(0), view);
			assertFalse(kind.name(), line(items).held());
			assertNull(kind.name(), items.groupStorage().note(10_000L));
		}
	}

	@Test
	public void theOldBuilderIsUnchangedForEveryoneElse()
	{
		QuestItems items = QuestItems.of(graph(1), account(), held(0), true, Collections.<String>emptySet(),
			Collections.<String>emptySet(), Collections.<String, Long>emptyMap(), Collections.<String, Long>emptyMap(),
			Collections.<String>emptySet(), null);

		assertFalse(line(items).held());
		assertSame(GroupStorageView.NONE, items.groupStorage());
	}

	@Test
	public void storageBankAndInventoryAddUp()
	{
		// OwnedItems already totals bank, bags and worn into one map; storage
		// adds on top of that, never maxed against it.
		assertFalse(line(items(3, held(1), gim(storage(1, 1_000L, "you")))).held());
		QuestItems enough = items(3, held(1), gim(storage(2, 1_000L, "you")));
		assertTrue(line(enough).held());
		assertEquals("3 / 3, in group storage", line(enough).describe());
	}

	@Test
	public void storageAddsOnTopOfAnOldSyncRecordRatherThanBeingSwallowedByIt()
	{
		// Bank unopened: questpath's record says one in the bank. Taking the
		// max of (record, live + storage) would read 1; the account has 2.
		Map<String, Long> known = held(1);
		QuestItems items = items(2, held(0), false, known, gim(storage(1, 1_000L, "you")));

		assertTrue(line(items).held());
	}

	@Test
	public void noteSaysWhoSawItAndHowLongAgo()
	{
		GroupStorageView view = gim(storage(1, 1_000L, "Iron Mate"));
		String note = view.note(1_000L + 2 * HOUR);

		assertTrue(note, note.contains("Iron Mate"));
		assertTrue(note, note.contains("2h ago"));
		assertEquals("just now", GroupStorageView.age(-5_000L));
		assertEquals("5m ago", GroupStorageView.age(5 * 60_000L));
		assertEquals("3d ago", GroupStorageView.age(72 * HOUR));
	}

	@Test
	public void snapshotSurvivesLogoutAndLoginThroughTheProfileConfig()
	{
		Map<String, Long> counts = held(4);
		String stored = GroupStorageStore.encode(GSON, counts, 12_345L);

		GroupStorageStore.Snapshot back = GroupStorageStore.decode(GSON, stored);
		assertNotNull(back);
		assertEquals(12_345L, back.at);
		assertEquals(Long.valueOf(4L), back.counts.get("blood rune"));

		GroupStorageView view = gim(new GroupStorage(back.counts, back.at, "you"));
		assertTrue(line(items(4, held(0), view)).held());
	}

	@Test
	public void anotherAccountsProfileHasNoSnapshotSoStorageIsUnknownAgain()
	{
		// RS-profile config is per account: switching reads that account's
		// key, which is absent for an account that never opened storage.
		assertNull(GroupStorageStore.decode(GSON, null));
		assertNull(GroupStorageStore.decode(GSON, ""));
		assertNull("unreadable is never-seen, not empty", GroupStorageStore.decode(GSON, "{not json"));
		assertNull(GroupStorageStore.decode(GSON, "{\"at\":0,\"counts\":{}}"));

		// A non-group account logging in drops storage entirely, even with a
		// member's snapshot still in the party.
		assertSame(GroupStorageView.NONE, GroupStorageView.of(AccountKind.IRONMAN,
			Collections.singletonList(storage(1, 1_000L, "Iron Mate"))));
		assertEquals(GroupStorageView.State.UNSEEN, gim((GroupStorage) null).state());
	}
}
