package com.vinnyvangogh.questpath.groupshop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a near-shop alert says about the group: which shops stock something
 * the group is still short of, and the words for it.
 *
 * <p>Only the "which" and the "what". <em>When</em> -- once per visit, as you
 * come up on the shop -- is the host's visit gate's job (Quest Path's {@code
 * ShopApproachWatcher}), so the group's shops and the player's own share one
 * memory of where you have already been told.
 */
public final class GroupShopAlert
{
	/** How many items one line names before it says how many more. */
	static final int ITEMS_NAMED = 3;

	private GroupShopAlert()
	{
	}

	/**
	 * Shops the viewer can be served at, each with the still-needed lines it
	 * sells, in list order. A covered line, or one only unknown members need,
	 * never makes a shop a candidate.
	 */
	public static Map<String, List<GroupShoppingList.Line>> byShop(List<GroupShoppingList.Line> lines,
		ItemSources.Lookup lookup)
	{
		Map<String, List<GroupShoppingList.Line>> out = new LinkedHashMap<>();
		if (lines == null || lookup == null)
		{
			return out;
		}
		for (GroupShoppingList.Line line : lines)
		{
			if (line.stillNeeded() <= 0)
			{
				continue;
			}
			for (String shop : lookup.shops(line.name()))
			{
				if (lookup.shopUsable(shop))
				{
					out.computeIfAbsent(shop, k -> new ArrayList<>()).add(line);
				}
			}
		}
		return out;
	}

	/** "group needs 28 more Bucket, 3 more Rope" -- or empty for no lines. */
	public static String describe(List<GroupShoppingList.Line> lines)
	{
		if (lines == null || lines.isEmpty())
		{
			return "";
		}
		StringBuilder text = new StringBuilder("group needs ");
		int named = Math.min(ITEMS_NAMED, lines.size());
		for (int i = 0; i < named; i++)
		{
			GroupShoppingList.Line line = lines.get(i);
			text.append(i == 0 ? "" : ", ").append(line.stillNeeded()).append(" more ").append(line.name());
		}
		if (lines.size() > named)
		{
			text.append(" and ").append(lines.size() - named).append(" other item")
				.append(lines.size() - named == 1 ? "" : "s");
		}
		return text.toString();
	}
}
