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
	/**
	 * Started-item rows before the panel switches to "and N more".
	 *
	 * An inventory is twenty-eight slots and worn equipment eleven, so no
	 * account can carry part-stacks of more distinct items than this however
	 * long its plan is -- the section is self-limiting, and this only exists
	 * so a panel cannot be driven off the screen by a count that should be
	 * impossible. It is deliberately not the list-length setting: a cap the
	 * player chose for a ninety-item shopping list is the wrong cap for the
	 * two things in their bags.
	 */
	private static final int MAX_STARTED_ROWS = 39;
	/** Held-item grid slots before the panel switches to "and N more". */
	private static final int MAX_HELD_CELLS = 12;

	/** Held only to ask whether the bank is open; see {@link #enabled}. */
	private final net.runelite.api.Client client;

	NeededItemsOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons,
		net.runelite.api.Client client)
	{
		super(plugin, config, icons, "Everything you need");
		this.client = client;
	}

	/**
	 * On, except while the bank list is actually up.
	 *
	 * <p>{@link BankListOverlay} draws the same merged list, ordered for a
	 * bank screen. With both switched on and the bank open the player got two
	 * near-identical panels on top of each other. This is one feature with
	 * two presentations rather than two features: opening the bank swaps
	 * which one is showing instead of adding a second.
	 *
	 * <p><b>Was:</b> stood down whenever {@code config.bankList()} was true
	 * and the bank was open, regardless of whether the in-bank title-bar
	 * button was actually lit. {@link BankListOverlay} itself refuses to draw
	 * anything while that button is off, so with the button off and both
	 * config toggles on, this overlay stood down for a panel that was never
	 * showing -- both lists gone, and the only way back was to untick {@code
	 * bankList}, which brought this one back but could never bring the other
	 * one back, because unticking {@code overlayItems} does nothing to the
	 * title-bar button either. That was the actual coupling, not the two
	 * config toggles disagreeing with each other.
	 *
	 * <p>Fixed by asking {@link BankListOverlay#isShowing}, its own full
	 * "would I actually draw right now" test, rather than re-deriving two
	 * thirds of it here. Two copies would be free to disagree, and both ways
	 * they could are visible -- overlapping panels, or no panel at all.
	 *
	 * <p>{@link ShopRouteOverlay#isShowing} is asked the same way and for the
	 * same reason: while a shop route sent to Shortest Path is active, "where
	 * you're going and why" replaces "everything you need" rather than
	 * stacking beside it, for exactly the span that route stays active.
	 */
	@Override
	boolean enabled(QuestpathConfig config)
	{
		if (BankListOverlay.isShowing(plugin(), config, client))
		{
			return false;
		}
		if (ShopRouteOverlay.isShowing(plugin(), config))
		{
			return false;
		}
		return config.overlayItems();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayItemsWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayItemsLength();
	}

	/** Group storage's part in the counts, for a group ironman only; see {@link GroupStorageView#note}. */
	private static void addGroupStorageNote(List<OverlayRow> rows, QuestItems items)
	{
		String note = items.groupStorage().note(System.currentTimeMillis());
		if (note != null)
		{
			rows.add(OverlayRow.note(note, OverlayTheme.MUTED));
		}
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
		List<QuestItems.Total> started = new ArrayList<>();
		List<QuestItems.Total> shortItems = new ArrayList<>();
		List<QuestItems.Total> heldItems = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.satisfied())
			{
				satisfiedCount++;
				heldItems.add(total);
			}
			else if (total.inProgress())
			{
				// Some already in your bags and still not enough. Kept out of
				// the capped list below rather than merely sorted to the top
				// of it, because a cap of seven would otherwise let ninety
				// alphabetically earlier items push the log you are standing
				// over off the panel.
				started.add(total);
			}
			else
			{
				shortItems.add(total);
			}
		}

		// The shopping trip's size, first thing on the panel: how much of the
		// merged list is already covered.
		rows.add(OverlayRow.bar("Covered", satisfiedCount + " / " + totals.size(),
			totals.isEmpty() ? 1.0 : (double) satisfiedCount / totals.size(),
			// Green only when nothing is outstanding at all. Started items are
			// no longer in shortItems, so testing that alone would turn the
			// bar green while the panel below it still listed things to get.
			shortItems.isEmpty() && started.isEmpty()
				? OverlayTheme.GOOD
				: OverlayTheme.WARN));

		if (items.freshness() == QuestItems.Freshness.LAST_SYNC)
		{
			// A real bank, just not this session's. Worth saying, because a
			// stack spent since the last sync still shows here -- but it is a
			// footnote, not the warning the carried-only case needs.
			rows.add(OverlayRow.note(
				"Counts are from your last questpath sync plus what you are carrying.",
				OverlayTheme.MUTED));
		}
		else if (!items.bankSeen())
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
		addGroupStorageNote(rows, items);

		if (!started.isEmpty())
		{
			// Above the shopping list and outside its cap. This is the only
			// part of the panel that is about right now: you are holding some
			// of these, which means you are somewhere they come from, and the
			// panel's one job at that moment is to say how many more.
			rows.add(OverlayRow.header("Already started (" + started.size() + ")"));
			int shown = Math.min(started.size(), MAX_STARTED_ROWS);
			for (int i = 0; i < shown; i++)
			{
				QuestItems.Total total = started.get(i);
				// Named in the accent rather than the body colour: on a panel
				// where every other short row is also orange-valued, the name
				// is the only place left to say "this one is live".
				rows.add(OverlayRow.item(total.itemId(), total.name(), total.describe(),
					false, OverlayTheme.ACCENT, OverlayTheme.WARN));
			}
			int remaining = started.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow(
					"...and " + remaining + " more you have started."));
			}
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
