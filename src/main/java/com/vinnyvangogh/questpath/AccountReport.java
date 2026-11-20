package com.vinnyvangogh.questpath;

import lombok.Value;
import lombok.experimental.Accessors;

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
@Value
@Accessors(fluent = true)
class AccountReport
{
	/**
	 * The character whose state is being sent. Never null in a report that
	 * exists: the plugin refuses to build one without a local player, for the
	 * same reason it refuses to push without a name -- an unattributed push
	 * is one questpath cannot route to the right account.
	 */
	String characterName;
	int combatLevel;
	int questPoints;
	int totalLevel;
	long totalXp;
	int questsComplete;
	int questsInProgress;
	/**
	 * How many quests the client could say anything about at all. Not the
	 * number that exist: a quest whose state the client has not loaded is
	 * skipped by the push rather than sent as "not started", so this is the
	 * size of the claim actually being made.
	 */
	int questsKnown;
	/** How many distinct item stacks are riding along, across every container. */
	int itemStacks;
	/**
	 * Whether the bank has been opened this session. The one number here
	 * that is a warning rather than a fact: until it is true, every item
	 * answer questpath gives for this account is working from an older
	 * upload or from nothing.
	 */
	boolean bankSeen;
}
