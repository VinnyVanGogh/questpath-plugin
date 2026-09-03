package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * The plan's item list, beside the bank, in the order it is worth acting on.
 *
 * <p>Three bands, and the order is the feature:
 *
 * <ol>
 * <li><b>Ran out</b> -- the bank is holding an empty placeholder slot and you
 *     own none. The game's own record that you had these and spent them,
 *     which is both the most surprising row to find and the most likely to be
 *     a mistake.
 * <li><b>Still short</b> -- including everything you hold part of, nearest to
 *     done first.
 * <li><b>Already have</b> -- listed last and only on request, because a bank
 *     trip is about what is missing and a long tail of solved rows buries it.
 * </ol>
 *
 * <p>Only drawn while the bank is open and the title-bar button is lit. It
 * exists next to {@link BankNeedsOverlay}, which marks the same items in the
 * grid: the marks say which slot, the list says what is not in any slot at
 * all -- a thing no amount of outlining an empty bank can convey.
 */
class BankListOverlay extends QuestpathOverlay
{
	/** Rows per band before the panel says how many more there are. */
	private static final int MAX_ROWS = 12;

	private final Client client;

	BankListOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons,
		Client client)
	{
		super(plugin, config, icons, "Needed from your bank");
		this.client = client;
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.bankList() && bankOpen();
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

	/**
	 * Whether the bank is actually on screen.
	 *
	 * Checked rather than assumed from a game state: this panel is about the
	 * screen in front of you, and drawing it over the world because the bank
	 * happened to be open a minute ago would make it one more thing to move
	 * out of the way.
	 */
	private boolean bankOpen()
	{
		if (client == null)
		{
			return false;
		}
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		return bank != null && !bank.isHidden();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		QuestpathPlugin plugin = plugin();
		if (plugin != null && !plugin.bankNeedsShown())
		{
			// Switched off at the title bar. Nothing to say, and saying
			// nothing is how the button reads as off.
			return List.of();
		}

		QuestItems items = snapshot.items();
		if (items == null)
		{
			return List.of(OverlayRow.note(
				"questpath has not loaded an item list yet.", OverlayTheme.MUTED));
		}

		List<QuestItems.Total> ranOut = new ArrayList<>();
		List<QuestItems.Total> shortOf = new ArrayList<>();
		List<QuestItems.Total> have = new ArrayList<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.satisfied())
			{
				have.add(total);
			}
			else if (total.ranOut())
			{
				ranOut.add(total);
			}
			else
			{
				shortOf.add(total);
			}
		}

		if (ranOut.isEmpty() && shortOf.isEmpty())
		{
			return List.of(OverlayRow.note(
				"Your bank covers everything the plan needs.", OverlayTheme.GOOD));
		}

		List<OverlayRow> rows = new ArrayList<>();
		rows.add(OverlayRow.bar("Covered", have.size() + " / " + items.totals().size(),
			items.totals().isEmpty() ? 1.0 : (double) have.size() / items.totals().size(),
			OverlayTheme.WARN));

		// Named separately rather than folded into "still short", because the
		// two are different mistakes. An empty placeholder is something you
		// used to have; a plain shortfall is something you never bought.
		band(rows, ctx, "Ran out (" + ranOut.size() + ")", ranOut, OverlayTheme.BAD);
		band(rows, ctx, "Still short (" + shortOf.size() + ")", shortOf, OverlayTheme.TEXT);

		if (!have.isEmpty() && !config.overlayItemsHideHeld())
		{
			band(rows, ctx, "Already have (" + have.size() + ")", have, OverlayTheme.MUTED);
		}

		return rows;
	}

	private void band(List<OverlayRow> rows, OverlayContext ctx, String heading,
		List<QuestItems.Total> band, java.awt.Color nameColour)
	{
		if (band.isEmpty())
		{
			return;
		}
		rows.add(OverlayRow.header(heading));
		int shown = Math.min(band.size(), ctx.itemListCap(MAX_ROWS));
		for (int i = 0; i < shown; i++)
		{
			QuestItems.Total total = band.get(i);
			rows.add(OverlayRow.item(total.itemId(), total.name(), total.describe(),
				total.satisfied(), nameColour,
				total.satisfied() ? OverlayTheme.GOOD : OverlayTheme.WARN));
		}
		int remaining = band.size() - shown;
		if (remaining > 0)
		{
			rows.add(OverlayRow.overflow("...and " + remaining + " more."));
		}
	}
}
