package com.vinnyvangogh.questpath;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * One account's state, in the shape questpath already knows how to apply.
 *
 * <p>This is deliberately WikiSync's document, not a nicer one of our own.
 * questpath's importer is fetch-then-apply, and only the fetch half was ever
 * WikiSync-specific; sending the same shape means the push reuses the whole
 * matcher, the whole writer, and the fixtures that already test them. A
 * prettier schema would have bought nothing and cost a second implementation
 * of everything downstream.
 *
 * <p>Quest states are the same three integers WikiSync uses: 0 not started,
 * 1 in progress, 2 complete.
 */
final class AccountState
{
	static final int NOT_STARTED = 0;
	static final int IN_PROGRESS = 1;
	static final int COMPLETE = 2;

	private AccountState()
	{
	}

	/**
	 * Reads the account's quests and levels straight from the client.
	 *
	 * <p><b>Must run on the client thread.</b> {@link Quest#getState} reads
	 * varbits, which is not safe from the Swing EDT or an OkHttp callback.
	 *
	 * <p>Quest names come from {@link Quest#getName()}, which is the wiki's
	 * own name — the same string WikiSync sends and the same one questpath's
	 * matcher resolves. Nothing is translated here on purpose: a rename that
	 * needs handling should be handled in the one place that already handles
	 * WikiSync's, not in two.
	 */
	static Map<String, Object> read(Client client)
	{
		Map<String, Integer> quests = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			if (state == null)
			{
				// Nothing known about this quest yet. Sending 0 would claim
				// "not started", which is a different thing from "the client
				// has not told us".
				continue;
			}
			quests.put(quest.getName(), stateOf(state));
		}

		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("username", client.getLocalPlayer() == null ? "" : client.getLocalPlayer().getName());
		payload.put("quests", quests);
		payload.put("levels", levels);
		return payload;
	}

	private static int stateOf(QuestState state)
	{
		switch (state)
		{
			case FINISHED:
				return COMPLETE;
			case IN_PROGRESS:
				return IN_PROGRESS;
			default:
				return NOT_STARTED;
		}
	}

	/**
	 * A cheap identity for a payload, so an unchanged account is not pushed
	 * repeatedly.
	 *
	 * Quest state changes are rare and level-ups are not much more frequent,
	 * but the events that would trigger a push are not rare at all. Comparing
	 * what would be sent is more honest than a timer: it pushes exactly when
	 * something the server cares about actually moved.
	 */
	static int fingerprint(Map<String, Object> payload)
	{
		Object quests = payload.get("quests");
		Object levels = payload.get("levels");
		return (quests == null ? 0 : quests.hashCode()) * 31
			+ (levels == null ? 0 : levels.hashCode());
	}
}
