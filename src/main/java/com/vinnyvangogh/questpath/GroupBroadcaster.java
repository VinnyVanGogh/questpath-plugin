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
			message.held, message.bankSeen);
		int experience = Objects.hashCode(message.experience);
		Instant now = clock.get();

		boolean send = force || !haveSent || structural != lastStructural
			|| (experience != lastExperience
				&& Duration.between(lastSentAt, now).compareTo(EXPERIENCE_INTERVAL) >= 0);
		if (!send)
		{
			return;
		}
		transport.accept(message);
		haveSent = true;
		lastStructural = structural;
		lastExperience = experience;
		lastSentAt = now;
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
