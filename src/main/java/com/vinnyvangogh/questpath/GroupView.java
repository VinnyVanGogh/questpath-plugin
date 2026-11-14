package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.GroupShoppingList;
import com.vinnyvangogh.questpath.groupshop.ItemSources;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
	private GroupShoppingList shopping;
	private Map<String, ItemSources.Options> sources = Collections.emptyMap();

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

	/**
	 * Adds the group shopping list and, per lower-cased item name, where the
	 * viewer can get each item. Both are built on the client thread: shop
	 * reachability and tradeability read client state the panel's thread
	 * must not touch.
	 */
	GroupView withShopping(GroupShoppingList list, Map<String, ItemSources.Options> itemSources)
	{
		this.shopping = list;
		this.sources = itemSources == null ? Collections.emptyMap() : Collections.unmodifiableMap(itemSources);
		return this;
	}

	/** Null unless {@link State#READY}. */
	GroupShoppingList shopping()
	{
		return shopping;
	}

	/** Where the viewer can get an item, or null when nothing was looked up. */
	ItemSources.Options sources(String itemName)
	{
		return itemName == null ? null : sources.get(itemName.toLowerCase());
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

	/** Party members who have sent nothing: Quest Route not running, or sharing off. */
	List<String> notSharing()
	{
		return notSharing;
	}
}
