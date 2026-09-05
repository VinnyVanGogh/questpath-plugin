package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which items on the current shop trip the player has chosen to skip.
 *
 * <p>The report this closes: a player looking at "Send shop route to
 * Shortest Path" with no way to say "not that one, not right now" -- the
 * only lever offered was routing to every unsatisfied item or none of them.
 * A skip moves one item to the back of the shopping order and out of what
 * gets sent to Shortest Path, without forgetting it: {@link
 * QuestpathPanel}'s own shopping list still lists it, {@link
 * ShopRouteOverlay} still counts it in the covered/total bar, and {@link
 * #unskip} undoes the whole thing. A skip that silently dropped an item
 * would be exactly the failure this project keeps designing against -- see
 * {@code QuestItems}'s own "no silent fallbacks" discipline, which this
 * mirrors for a player action rather than a data gap.
 *
 * <p>Keyed case-insensitively, the same discipline {@link QuestItems} uses
 * for held quantities: the wiki's own casing of an item name is not
 * guaranteed consistent between two lines that mean the same item. The
 * display-case name last skipped under is kept alongside the key so the
 * panel's undo list reads like an item name, not a lookup key.
 *
 * <p>Session state, not config: {@link QuestpathPlugin} holds the one
 * instance for the life of the plugin and {@link #clear} is called on
 * logout, the same lifecycle {@code shopRouteSentAt} already has -- a skip
 * chosen against one account's shopping list means nothing for the next
 * account to log in.
 */
final class ShopItemSkips
{
	/** Lowercased item name -> the display-case name it was last skipped under. */
	private final Map<String, String> skipped = new LinkedHashMap<>();

	/** Whether name (matched case-insensitively) is currently skipped. */
	boolean isSkipped(String name)
	{
		return name != null && skipped.containsKey(name.toLowerCase());
	}

	/**
	 * Moves name to the back of the shopping order and out of routing, until
	 * {@link #unskip}. A second skip of the same name (whatever its case)
	 * simply refreshes the display-case name; it is not a second, independent
	 * skip to undo twice.
	 */
	void skip(String name)
	{
		if (name != null && !name.isEmpty())
		{
			skipped.put(name.toLowerCase(), name);
		}
	}

	/** Undoes {@link #skip}. Restoring a name that was never skipped is a no-op. */
	void unskip(String name)
	{
		if (name != null)
		{
			skipped.remove(name.toLowerCase());
		}
	}

	/**
	 * Every skipped item, in the display-case it was skipped under, oldest
	 * skip first -- the order a player would expect their own actions listed
	 * back in.
	 */
	List<String> names()
	{
		return new ArrayList<>(skipped.values());
	}

	/** How many items are currently skipped. */
	int size()
	{
		return skipped.size();
	}

	/** Forgets every skip. Called on logout -- see the class doc. */
	void clear()
	{
		skipped.clear();
	}
}
