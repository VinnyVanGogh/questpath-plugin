package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;

/**
 * The shopping list: one merged total per item across every quest the account
 * can currently start, ticked against the bank.
 *
 * <p>This is the overlay for standing at a bank, which is why it leans on
 * icon-only density more than the other four -- a grid of sprites is a bank
 * trip read in a second, where a wall of quest names is not. It is also the
 * one overlay where the difference between "not owned" and "not on you" is
 * load-bearing: {@link QuestItems#bankSeen()} decides which claim every
 * number on the panel is allowed to make, and getting that wrong would turn
 * an unopened bank into a false "go buy this".
 */
class NeededItemsOverlay extends QuestpathOverlay
{
	/** Short-list rows before the panel switches to "and N more". */
	private static final int MAX_SHORT_ROWS = 10;
	/** Held-item grid slots before the panel switches to "and N more". */
	private static final int MAX_HELD_CELLS = 12;

	NeededItemsOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Everything you need");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.overlayItems();
	}

	@Override
	OverlayWidth width(QuestpathConfig config)
	{
		return config.overlayItemsWidth();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		QuestItems items = snapshot.items();
		if (items == null)
		{
			// No bundle yet, or the client has shown no containers at all --
			// neither is "nothing needed", and saying so as an empty panel
			// would read as exactly that.
			return List.of(OverlayRow.note(
				"questpath cannot answer this yet -- no item list loaded, "
					+ "or nothing seen in your bags, worn gear or bank.",
				OverlayTheme.MUTED));
		}

		if (items.isEmpty())
		{
			// No doable quest needs anything at all, item-wise. Different
			// from an empty totals() list below: that one still had quests
			// asking for things, all of which happened to be already held.
			return List.of(OverlayRow.note(
				"None of the quests you can currently start need any items.",
				OverlayTheme.MUTED));
		}

		List<QuestItems.Total> totals = items.totals();
		if (totals.isEmpty())
		{
			// Everything any doable quest requires is already held. A real,
			// good answer, and worth saying rather than hiding the panel.
			return List.of(OverlayRow.note(
				"Everything your doable quests need is already in hand.",
				OverlayTheme.GOOD));
		}

		List<OverlayRow> rows = new ArrayList<>();

		int satisfiedCount = 0;
		List<QuestItems.Total> shortItems = new ArrayList<>();
		List<QuestItems.Total> heldItems = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.satisfied())
			{
				satisfiedCount++;
				heldItems.add(total);
			}
			else
			{
				shortItems.add(total);
			}
		}

		// The shopping trip's size, first thing on the panel: how much of the
		// merged list is already covered.
		rows.add(OverlayRow.bar("Items held", satisfiedCount + " / " + totals.size(),
			totals.isEmpty() ? 1.0 : (double) satisfiedCount / totals.size(),
			shortItems.isEmpty() ? OverlayTheme.GOOD : OverlayTheme.WARN));

		if (!items.bankSeen())
		{
			// The critical caveat: with no bank opened this session, every
			// owned() count above is inventory and equipment only. A "0 / 5"
			// row below means "none on you", not "none owned", and that
			// distinction has to survive onto the panel in words, not just
			// in a number nobody would read as a warning on its own.
			rows.add(OverlayRow.note(
				"Bank not opened this session -- counts below are what you "
					+ "are carrying and wearing only.",
				OverlayTheme.WARN));
		}

		if (!shortItems.isEmpty())
		{
			rows.add(OverlayRow.header("Still short (" + shortItems.size() + ")"));
			int shown = Math.min(shortItems.size(), ctx.itemListCap(MAX_SHORT_ROWS));
			for (int i = 0; i < shown; i++)
			{
				QuestItems.Total total = shortItems.get(i);
				rows.add(OverlayRow.item(total.itemId(), total.name(), total.describe(),
					total.satisfied(), OverlayTheme.TEXT, OverlayTheme.WARN));
			}
			int remaining = shortItems.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow("...and " + remaining + " more short."));
			}
		}

		// Hiding the held half is the cheapest way to make this panel fit,
		// and it costs nothing: the bar above still counts them, so what is
		// dropped is still accounted for on screen rather than silently gone.
		if (!heldItems.isEmpty() && !config.overlayItemsHideHeld())
		{
			// Already held needs no name, no quantity to chase -- just the
			// sprite, which is what the grid form is for. Full item rows
			// here would repeat the bar above at ten times the height.
			rows.add(OverlayRow.header("Already held (" + heldItems.size() + ")"));
			int shown = Math.min(heldItems.size(), ctx.itemListCap(MAX_HELD_CELLS));
			List<OverlayRow.Cell> cells = new ArrayList<>();
			for (int i = 0; i < shown; i++)
			{
				QuestItems.Total total = heldItems.get(i);
				cells.add(new OverlayRow.Cell(total.itemId(), total.describe(), true));
			}
			rows.add(OverlayRow.grid(cells));
			int remaining = heldItems.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow("...and " + remaining + " more already held."));
			}
		}

		return rows;
	}
}
