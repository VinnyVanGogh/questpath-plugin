package com.vinnyvangogh.questpath;

/**
 * What the Sync tab shows about the account it is actually sending.
 *
 * <p>The push is silent by design, which used to leave the tab able to say
 * only whether the last one worked. That answers "is it on" and nothing about
 * <em>what</em> went, or for whom -- and "for whom" is the question that
 * matters: three pushes once wrote one character's quest states over another
 * account's, and every one of them looked like an ordinary sync from inside
 * the game. A tab naming the character, its quest points and the totals being
 * sent makes that visible at a glance instead of in a database afterwards.
 *
 * <p>Every number here is read from the client, not from what questpath sent
 * back. The point is to show what this client believes it is sending, so that
 * a disagreement with what questpath received is visible as a disagreement.
 */
final class AccountReport
{
	private final String characterName;
	private final int combatLevel;
	private final int questPoints;
	private final int totalLevel;
	private final long totalXp;
	private final int questsComplete;
	private final int questsInProgress;
	private final int questsKnown;
	private final int itemStacks;
	private final boolean bankSeen;

	AccountReport(String characterName, int combatLevel, int questPoints, int totalLevel,
		long totalXp, int questsComplete, int questsInProgress, int questsKnown,
		int itemStacks, boolean bankSeen)
	{
		this.characterName = characterName;
		this.combatLevel = combatLevel;
		this.questPoints = questPoints;
		this.totalLevel = totalLevel;
		this.totalXp = totalXp;
		this.questsComplete = questsComplete;
		this.questsInProgress = questsInProgress;
		this.questsKnown = questsKnown;
		this.itemStacks = itemStacks;
		this.bankSeen = bankSeen;
	}

	/**
	 * The character whose state is being sent.
	 *
	 * Never null in a report that exists: the plugin refuses to build one
	 * without a local player, for the same reason it refuses to push without
	 * a name -- an unattributed push is one questpath cannot route to the
	 * right account.
	 */
	String characterName()
	{
		return characterName;
	}

	int combatLevel()
	{
		return combatLevel;
	}

	int questPoints()
	{
		return questPoints;
	}

	int totalLevel()
	{
		return totalLevel;
	}

	long totalXp()
	{
		return totalXp;
	}

	int questsComplete()
	{
		return questsComplete;
	}

	int questsInProgress()
	{
		return questsInProgress;
	}

	/**
	 * How many quests the client could say anything about at all.
	 *
	 * Not the same as the number that exist: a quest whose state the client
	 * has not loaded is skipped by the push rather than sent as "not
	 * started", so this is the size of the claim actually being made.
	 */
	int questsKnown()
	{
		return questsKnown;
	}

	/** How many distinct item stacks are riding along, across every container. */
	int itemStacks()
	{
		return itemStacks;
	}

	/**
	 * Whether the bank has been opened this session.
	 *
	 * The one number here that is a warning rather than a fact: until it is
	 * true, every item answer questpath gives for this account is working
	 * from an older upload or from nothing.
	 */
	boolean bankSeen()
	{
		return bankSeen;
	}
}
