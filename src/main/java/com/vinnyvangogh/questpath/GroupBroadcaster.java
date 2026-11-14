package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decides when this client tells its party anything.
 *
 * <p><b>Opted out means silent.</b> With sharing off, {@link #offer} returns
 * before the account is even read, so no later change in the path can leak
 * anything by mistake. The one exception is a single {@link
 * GroupMember#withdrawal()} on the transition from on to off, which carries no
 * account data and tells everyone else to drop what they were sent.
 *
 * <p>Two tempos, the same split {@code AccountStateSender} uses for the
 * questpath push: quests, levels, rules and items go out the moment they
 * change; experience alone rides {@link #EXPERIENCE_INTERVAL}, or a member
 * training a skill would message the party every few seconds.
 *
 * <p>Not thread-safe; called from the client thread only.
 */
final class GroupBroadcaster
{
	static final Duration EXPERIENCE_INTERVAL = Duration.ofMinutes(2);

	/**
	 * An unchanged state is re-sent this often, so a member idling in a bank
	 * still reads as live. Without it, silence from someone with nothing new
	 * to say and silence from someone whose client died look the same.
	 */
	static final Duration HEARTBEAT = Duration.ofMinutes(5);

	/** A member not heard from for this long is marked stale; see {@link #stale}. */
	static final Duration STALE_AFTER = HEARTBEAT.multipliedBy(2).plusMinutes(1);

	private final Consumer<QuestpathGroupState> transport;
	private final Supplier<Instant> clock;

	private boolean haveSent;
	private int lastStructural;
	private int lastExperience;
	private Instant lastSentAt = Instant.EPOCH;

	GroupBroadcaster(Consumer<QuestpathGroupState> transport, Supplier<Instant> clock)
	{
		this.transport = transport;
		this.clock = clock;
	}

	/**
	 * @param enabled the player's opt-in
	 * @param inParty whether there is a party to tell
	 * @param snapshot reads the account; only called when opted in and in a party
	 * @param force send even if nothing changed -- a member just joined and has
	 *     heard nothing yet
	 */
	void offer(boolean enabled, boolean inParty, Supplier<QuestpathGroupState> snapshot, boolean force)
	{
		if (!enabled)
		{
			if (haveSent && inParty)
			{
				transport.accept(GroupMember.withdrawal());
			}
			reset();
			return;
		}
		if (!inParty)
		{
			reset();
			return;
		}

		QuestpathGroupState message = snapshot.get();
		if (message == null)
		{
			return;
		}
		int structural = Objects.hash(message.completed, message.started, message.levels, rulesKey(message),
			message.held, message.bankSeen, message.route, message.accountType, message.groupStorage);
		int experience = Objects.hashCode(message.experience);
		Instant now = clock.get();

		if (!force && !changed(structural, experience, now))
		{
			return;
		}
		transport.accept(message);
		haveSent = true;
		lastStructural = structural;
		lastExperience = experience;
		lastSentAt = now;
	}

	/** Whether there is anything the party has not heard: first send, a structural change, a heartbeat, or xp on its timer. */
	private boolean changed(int structural, int experience, Instant now)
	{
		if (!haveSent || structural != lastStructural || heartbeatDue())
		{
			return true;
		}
		return experience != lastExperience
			&& Duration.between(lastSentAt, now).compareTo(EXPERIENCE_INTERVAL) >= 0;
	}

	/**
	 * Whether a member who has sent before has been quiet for a whole {@link
	 * #HEARTBEAT}. Cheap -- no account read -- so the plugin can ask every
	 * tick and only build a snapshot when it is true.
	 */
	boolean heartbeatDue()
	{
		return haveSent && Duration.between(lastSentAt, clock.get()).compareTo(HEARTBEAT) >= 0;
	}

	/**
	 * Whether a member last heard at {@code heardAt} has missed two
	 * heartbeats. Two, not one, so a single dropped message does not mark a
	 * live member stale.
	 */
	static boolean stale(Instant heardAt, Instant now)
	{
		return heardAt == null || Duration.between(heardAt, now).compareTo(STALE_AFTER) > 0;
	}

	/** Forget what was sent, so the next party hears the state from scratch. */
	void reset()
	{
		haveSent = false;
		lastStructural = 0;
		lastExperience = 0;
		lastSentAt = Instant.EPOCH;
	}

	/** BuildRule has no equals; hash what it says instead. */
	private static int rulesKey(QuestpathGroupState message)
	{
		if (message.rules == null)
		{
			return 0;
		}
		int hash = 1;
		for (BuildRule rule : message.rules)
		{
			hash = hash * 31 + Objects.hash(rule.skillName(), rule.type(), rule.value(), rule.blocking());
		}
		return hash;
	}
}
