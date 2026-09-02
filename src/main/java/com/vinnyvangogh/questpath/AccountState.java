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
	static Map<String, Object> read(Client client, OwnedItems owned)
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
		Map<String, Integer> experience = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
			experience.put(skill.getName(), client.getSkillExperience(skill));
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("username", client.getLocalPlayer() == null ? "" : client.getLocalPlayer().getName());
		payload.put("quests", quests);
		payload.put("levels", levels);
		// Experience is the plugin's own addition to WikiSync's document,
		// which carries levels and no xp. questpath stores snapshots, and a
		// level alone cannot say how far into it an account is -- which is
		// the difference between "Strength 83" and "Strength 83, 4k from 84".
		// questpath derives the level from this rather than from the levels
		// block above, so the two can never disagree in its database.
		payload.put("experience", experience);
		// Only containers actually seen. A bank that has never been opened is
		// omitted rather than sent empty: questpath treats an empty list as
		// "this really is empty" and would wipe what it already had.
		if (!owned.isEmpty())
		{
			payload.put("items", owned.forPayload());
		}
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
	 * A cheap identity for the parts of a payload that change rarely: quest
	 * states and levels.
	 *
	 * A change here is worth a push immediately — a quest finished, a level
	 * landed. The username is excluded because a display name is not state
	 * the server acts on.
	 */
	static int fingerprint(Map<String, Object> payload)
	{
		Object quests = payload.get("quests");
		Object levels = payload.get("levels");
		return ((quests == null ? 0 : quests.hashCode()) * 31
			+ (levels == null ? 0 : levels.hashCode())) * 31
			+ itemsFingerprint(payload);
	}

	/**
	 * A cheap identity for experience alone, which changes constantly.
	 *
	 * Kept separate from {@link #fingerprint} on purpose. Folding xp into the
	 * structural fingerprint would mean a request on every experience drop —
	 * one every few seconds while training — which is the request-per-tick
	 * problem the gate exists to prevent. Folding it out entirely would mean
	 * questpath's stored xp only moved on level-up, so an account four hours
	 * into a grind would look untouched.
	 *
	 * So xp gets its own comparison on a timer; see QuestpathPlugin.
	 */
	static int experienceFingerprint(Map<String, Object> payload)
	{
		Object experience = payload.get("experience");
		return experience == null ? 0 : experience.hashCode();
	}

	/**
	 * A cheap identity for what the account holds.
	 *
	 * Folded into the structural fingerprint rather than given its own timer:
	 * a bank changes when you deposit, which is a deliberate act and not the
	 * per-tick drip experience is. Opening the bank once is one push.
	 */
	static int itemsFingerprint(Map<String, Object> payload)
	{
		Object items = payload.get("items");
		return items == null ? 0 : items.hashCode();
	}
}
