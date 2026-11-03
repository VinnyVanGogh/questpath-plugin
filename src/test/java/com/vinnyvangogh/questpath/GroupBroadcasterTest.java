package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * When this client says anything to the party, and above all when it says
 * nothing.
 */
public class GroupBroadcasterTest
{
	private final List<QuestpathGroupState> sent = new ArrayList<>();
	private Instant now = Instant.parse("2026-11-03T12:00:00Z");
	private final GroupBroadcaster broadcaster = new GroupBroadcaster(sent::add, () -> now);

	private static QuestpathGroupState state(String completedQuest, int attackXp)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		levels.put("Attack", 1);
		Map<String, Integer> xp = new LinkedHashMap<>();
		xp.put("Attack", attackXp);
		return GroupMember.encode(new HashSet<>(Collections.singletonList(completedQuest)),
			Collections.emptySet(), levels, xp, Collections.emptyList(), null, false, Collections.emptySet());
	}

	/**
	 * The opt-in, pinned. Off means no message of any kind, however often the
	 * plugin asks and whether or not it insists -- and the account is not even
	 * read, so there is nothing that could leak by mistake later in the path.
	 */
	@Test
	public void anOptedOutMemberSendsNothingAndReadsNothing()
	{
		AtomicInteger reads = new AtomicInteger();
		Supplier<QuestpathGroupState> snapshot = () ->
		{
			reads.incrementAndGet();
			return state("Cook's Assistant", 0);
		};

		for (int i = 0; i < 5; i++)
		{
			broadcaster.offer(false, true, snapshot, false);
			broadcaster.offer(false, true, snapshot, true);
			now = now.plus(Duration.ofMinutes(10));
		}

		assertTrue(sent.isEmpty());
		assertEquals(0, reads.get());
	}

	@Test
	public void outsideAPartyNothingIsSentEvenWhenOptedIn()
	{
		broadcaster.offer(true, false, () -> state("Cook's Assistant", 0), true);
		assertTrue(sent.isEmpty());
	}

	@Test
	public void sendsOnceThenOnlyOnChange()
	{
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		assertEquals(1, sent.size());

		broadcaster.offer(true, true, () -> state("Dragon Slayer I", 0), false);
		assertEquals(2, sent.size());
	}

	/**
	 * Experience moves every few seconds while training; it rides a timer, not
	 * every change, or a training member floods the party.
	 */
	@Test
	public void experienceAloneWaitsForTheInterval()
	{
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 50), false);
		assertEquals(1, sent.size());

		now = now.plus(GroupBroadcaster.EXPERIENCE_INTERVAL);
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 60), false);
		assertEquals(2, sent.size());
	}

	/** A member joining has missed everything; forcing re-announces. */
	@Test
	public void forceResendsUnchangedState()
	{
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), true);
		assertEquals(2, sent.size());
	}

	/**
	 * Turning sharing off after it was on tells the party to forget this
	 * member -- once, carrying no account data -- and then goes silent.
	 */
	@Test
	public void optingOutAfterSharingWithdrawsOnceThenGoesSilent()
	{
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(false, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(false, true, () -> state("Cook's Assistant", 0), true);

		assertEquals(2, sent.size());
		QuestpathGroupState withdrawal = sent.get(1);
		assertTrue(withdrawal.withdrawn);
		assertNull(withdrawal.completed);
		assertNull(withdrawal.levels);
		assertNull(withdrawal.experience);
		assertNull(withdrawal.held);
		assertNull(withdrawal.rules);
	}

	@Test
	public void nothingToSayIsNotSent()
	{
		broadcaster.offer(true, true, () -> null, true);
		assertTrue(sent.isEmpty());
	}

	/** Leaving a party resets the gate, so the next party hears the state. */
	@Test
	public void rejoiningAPartySendsAgain()
	{
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(true, false, () -> state("Cook's Assistant", 0), false);
		broadcaster.offer(true, true, () -> state("Cook's Assistant", 0), false);
		assertEquals(2, sent.size());
	}
}
