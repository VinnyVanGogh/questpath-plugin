package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Get while you're here" -- items sitting inside a quest already under way
 * that some other quest on the plan also needs.
 *
 * <p>This is the most time-sensitive panel of the set. A pickup is only
 * useful to know about while the account is physically standing in the quest
 * that grants it; the moment the quest finishes, the same fact belongs in a
 * shopping list instead, which is what {@code NeededItemsOverlay} is for. So
 * this overlay is deliberately narrow and deliberately quiet: nothing to grab
 * right now means nothing on screen, rather than a panel confirming that.
 */
class PickupsOverlay extends QuestpathOverlay
{
	/** Rows beyond this many are folded into a single "N more" line. */
	private static final int MAX_ROWS = 10;

	PickupsOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Get while you're here");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.overlayPickups();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayPickupsWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayPickupsLength();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		QuestItems items = snapshot.items();
		if (items == null)
		{
			return Collections.singletonList(
				OverlayRow.note("Bank and quest data not read yet.", OverlayTheme.MUTED));
		}

		if (items.freshness() == QuestItems.Freshness.LAST_SYNC)
		{
			// Handled as a footnote rather than a warning: questpath had a
			// bank on record, so this is stale at worst rather than blind.
		}
		else if (!items.bankSeen())
		{
			// The most misleading state this overlay can be in: without a
			// bank read, "not owned" and "not on you right now" look
			// identical, and a pickup list built on the second reads as
			// wrong advice rather than incomplete advice.
			return Collections.singletonList(OverlayRow.note(
				"Bank not opened this session -- this list only knows what's "
					+ "in your inventory and equipment, and may include "
					+ "things you've already banked.",
				OverlayTheme.WARN));
		}

		List<QuestItems.Pickup> pickups = items.pickups();
		if (pickups == null || pickups.isEmpty())
		{
			// Nothing to grab right now is not information worth a panel:
			// the player has other overlays for standing information, and a
			// permanent "nothing to pick up" box is clutter on top of a
			// game. Hiding is the correct answer for this one overlay.
			return Collections.emptyList();
		}

		Map<String, List<QuestItems.Pickup>> byQuest = new LinkedHashMap<>();
		for (QuestItems.Pickup pickup : pickups)
		{
			byQuest.computeIfAbsent(pickup.questName(), name -> new ArrayList<>()).add(pickup);
		}

		List<OverlayRow> out = new ArrayList<>();
		int shown = 0;
		int remaining = pickups.size();
		outer:
		for (Map.Entry<String, List<QuestItems.Pickup>> group : byQuest.entrySet())
		{
			out.add(OverlayRow.header(group.getKey()));
			for (QuestItems.Pickup pickup : group.getValue())
			{
				if (shown >= ctx.itemListCap(MAX_ROWS))
				{
					break outer;
				}
				String right = pickup.alsoWantedBy() > 1
					? pickup.alsoWantedBy() + " quests"
					: "";
				out.add(OverlayRow.item(pickup.itemId(), pickup.itemName(), right,
					false, OverlayTheme.TEXT, OverlayTheme.ACCENT));

				// Where it comes from, under the name.
				//
				// Without this the panel says "get a banana while you are in
				// Recipe for Disaster" and stops -- which reads as a claim
				// that the quest hands you one, when what the wiki actually
				// marked is that the item is obtainable during it. The
				// sources say whether that means a drop, a shop two squares
				// away, or an afternoon of farming, and questpath already
				// knows: they were being carried and thrown away here.
				//
				// One line, not the list. Two shop entries stacked under
				// every item turns a glanceable prompt into a wall, and the
				// first is the best one -- questpath orders them.
				List<String> sources = pickup.sources();
				if (ctx.mode().showsText() && !sources.isEmpty())
				{
					out.add(OverlayRow.note(sources.get(0), OverlayTheme.MUTED));
				}
				shown++;
				remaining--;
			}
		}

		if (remaining > 0)
		{
			out.add(OverlayRow.overflow(remaining + " more not shown."));
		}

		return out;
	}
}
