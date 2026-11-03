package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;

/**
 * Everything the Group tab renders, read on the client thread in one pass
 * and handed to the EDT whole.
 *
 * <p>The states are kept apart on purpose. "Not in a party", "nobody else
 * shares" and "the client has not loaded quest states yet" each read as an
 * empty group if collapsed into one, and each needs a different thing from
 * the player.
 */
final class GroupView
{
	enum State
	{
		/** No quest graph: nothing can be judged. */
		NO_GRAPH,
		/** Not in a RuneLite party. */
		NOT_IN_PARTY,
		/** In a party, but this client's own quest states have not loaded. */
		LOADING,
		/** A plan, possibly of just this account if nobody else shares. */
		READY
	}

	private final State state;
	private final boolean sharing;
	private final GroupPlan plan;
	private final List<String> notSharing;

	private GroupView(State state, boolean sharing, GroupPlan plan, List<String> notSharing)
	{
		this.state = state;
		this.sharing = sharing;
		this.plan = plan;
		this.notSharing = Collections.unmodifiableList(notSharing);
	}

	static GroupView of(State state, boolean sharing)
	{
		return new GroupView(state, sharing, null, Collections.emptyList());
	}

	static GroupView ready(boolean sharing, GroupPlan plan, List<String> notSharing)
	{
		return new GroupView(State.READY, sharing, plan, notSharing);
	}

	State state()
	{
		return state;
	}

	/** Whether this player has opted in to sharing. */
	boolean sharing()
	{
		return sharing;
	}

	/** Null unless {@link State#READY}. The local account is always its first member. */
	GroupPlan plan()
	{
		return plan;
	}

	/** Party members who have sent nothing: Quest Path not running, or sharing off. */
	List<String> notSharing()
	{
		return notSharing;
	}
}
