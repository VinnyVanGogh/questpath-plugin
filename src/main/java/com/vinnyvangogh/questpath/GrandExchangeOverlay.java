package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * What is queued, what is next, and what is left -- the Grand Exchange
 * shopping list as three answers rather than one.
 *
 * <p><b>Why three sections and not a list.</b> A flat list of outstanding
 * items cannot say the one thing that matters while you are standing at a
 * counter with eight slots: which of them you have already spent. The
 * information the player asked for is a state machine, and these are its
 * states.
 *
 * <ul>
 * <li><b>Next</b> -- the one item to buy right now, with the number to type.
 *     One item, because a list of eight things to do next is a list of no
 *     things to do next.</li>
 * <li><b>Queued</b> -- already on offer, with how far along each is. This is
 *     the section that moves on its own: place an offer and an item crosses
 *     from Next into here without being collected, abort one and it crosses
 *     straight back. See {@link GrandExchangeOffers} for exactly which offer
 *     states count as live and why a cancelled one stops counting the moment
 *     it is cancelled rather than when its collection box is emptied.</li>
 * <li><b>Left</b> -- everything still to do after Next, so the trip has a
 *     visible size. A shopping list with no end in sight is one people
 *     abandon halfway.</li>
 * </ul>
 *
 * <p><b>The quantity is the point of the Next row, not decoration.</b>
 * Clicking questpath's own row in the search box fills the item name in and
 * nothing else -- the game's quantity box keeps whatever it had, and there
 * is no supported way to type into it from here that this project has
 * verified. So the number has to be readable at the moment it is typed by
 * hand, which is this panel's most load-bearing job.
 *
 * <p><b>Only while the Grand Exchange is actually open</b>, and only for
 * accounts that have one. Everywhere else the walking shop tour is the right
 * answer and this would be a second, contradictory shopping list on the same
 * screen. The ironman gate is {@link QuestpathPlugin#grandExchangeAvailable}
 * -- this project's rule against ever suggesting something an account cannot
 * act on, applied the same way {@link QuestpathPanel} applies it.
 *
 * <p>{@link #enabled} reads client widgets, which is safe here and only
 * here: RuneLite renders overlays on the client thread. {@link
 * NeededItemsOverlay} asks the same kind of question about the bank for the
 * same reason.
 */
class GrandExchangeOverlay extends QuestpathOverlay
{
	/** Queued rows before the panel switches to "and N more" -- there are only ever eight slots. */
	private static final int MAX_QUEUED_ROWS = 8;

	/** Remaining-item rows before the panel switches to "and N more". */
	private static final int MAX_LEFT_ROWS = 10;

	/**
	 * Not-sold-here rows before the panel switches to "and N more".
	 *
	 * <p>Shorter than the buy list on purpose. This section exists so the
	 * trip does not look finished when it is not; it is not where those
	 * items get worked out, which is what the shop route and "Everything you
	 * need" are for.
	 */
	private static final int MAX_ELSEWHERE_ROWS = 6;

	private final Client client;
	private final Supplier<List<GrandExchangeShoppingList.Line>> list;

	GrandExchangeOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons,
		Client client, Supplier<List<GrandExchangeShoppingList.Line>> list)
	{
		super(plugin, config, icons, "Grand Exchange");
		this.client = client;
		this.list = list;
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		if (!config.overlayGrandExchange())
		{
			return false;
		}
		QuestpathPlugin plugin = plugin();
		if (plugin == null || !plugin.grandExchangeAvailable())
		{
			return false;
		}
		return isOpen();
	}

	/**
	 * Whether the Grand Exchange's own window is on screen.
	 *
	 * <p>{@code InterfaceID.GeOffers.UNIVERSE} (30474240; group 465, child 0)
	 * is the window's root -- the same widget {@code QuestpathPlugin}'s own
	 * {@code geSearchBridge} open-check uses. Unlike that check this does
	 * <em>not</em> also require the search chatbox to be up: the whole point
	 * of this panel is to be readable while typing a quantity, which happens
	 * on the offer screen with the chatbox already gone.
	 */
	private boolean isOpen()
	{
		Widget window = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		return window != null && !window.isHidden();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayGrandExchangeWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayGrandExchangeLength();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		List<GrandExchangeShoppingList.Line> lines = list.get();
		if (lines == null)
		{
			// No bundle yet, or nothing seen in any container. Not the same
			// as "nothing to buy", and drawing it as an empty panel would
			// read as exactly that.
			return List.of(OverlayRow.note(
				"questpath cannot answer this yet -- no item list loaded, "
					+ "or nothing seen in your bags, worn gear or bank.",
				OverlayTheme.MUTED));
		}
		if (lines.isEmpty())
		{
			return List.of(OverlayRow.note(
				"Everything your doable quests need is already in hand.",
				OverlayTheme.GOOD));
		}

		List<GrandExchangeShoppingList.Line> queued = GrandExchangeShoppingList.queued(lines);
		List<GrandExchangeShoppingList.Line> left = GrandExchangeShoppingList.left(lines);
		List<GrandExchangeShoppingList.Line> elsewhere = GrandExchangeShoppingList.unbuyable(lines);
		GrandExchangeShoppingList.Line next = GrandExchangeShoppingList.next(lines);

		List<OverlayRow> rows = new ArrayList<>();

		if (next == null)
		{
			// Nothing left to buy here -- but there are two ways to arrive at
			// that and they mean different things to somebody deciding
			// whether to walk away from the counter. Saying "everything is on
			// offer" while a Karamjan rum is still outstanding would be the
			// panel congratulating the player on a trip they have not
			// finished.
			rows.add(OverlayRow.note(
				elsewhere.isEmpty()
					? "Everything you still need is on offer. Nothing left to buy."
					: "Nothing left that the Grand Exchange sells -- what remains is below.",
				elsewhere.isEmpty() ? OverlayTheme.GOOD : OverlayTheme.MUTED));
		}
		else
		{
			rows.add(OverlayRow.header("Next"));
			// Named in the accent and counted in the warning colour, matching
			// how "Already started" is drawn on the shopping list: this is
			// the live row, and the number on the right is the one thing the
			// player has to copy by hand into the quantity box.
			rows.add(OverlayRow.item(next.itemId(), next.name(), "x" + next.outstanding(),
				false, OverlayTheme.ACCENT, OverlayTheme.WARN));
			// The wiki's own caveat, under the item it qualifies. Only on the
			// Next row: this is the one item the player is about to type a
			// quantity for, and a caveat that arrives after the purchase is
			// not a caveat. "4 if you want your own blurite sword" is the line
			// that earned this -- the count above says two, and two is right
			// for the quest and wrong for anyone who wants the second sword.
			for (String note : next.notes())
			{
				rows.add(OverlayRow.note(note, OverlayTheme.MUTED));
			}
		}

		if (!queued.isEmpty())
		{
			rows.add(OverlayRow.header("Queued (" + queued.size() + ")"));
			int shown = Math.min(queued.size(), ctx.itemListCap(MAX_QUEUED_ROWS));
			for (int i = 0; i < shown; i++)
			{
				GrandExchangeShoppingList.Line line = queued.get(i);
				// Green once there is enough in the collection box to cover
				// what this item was bought for, because that one is a walk
				// away from being genuinely done; muted while it is still
				// filling, because there is nothing to do about it yet.
				rows.add(OverlayRow.item(line.itemId(), line.name(), line.progress(),
					true, OverlayTheme.TEXT,
					line.collectable() ? OverlayTheme.GOOD : OverlayTheme.MUTED));
			}
			int remaining = queued.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow("...and " + remaining + " more on offer."));
			}
		}

		// Everything after Next. Listing Next again here would make the
		// count above it wrong by one in the only place it is read.
		List<GrandExchangeShoppingList.Line> rest = left.isEmpty()
			? left
			: left.subList(1, left.size());
		if (!rest.isEmpty())
		{
			rows.add(OverlayRow.header("Left (" + rest.size() + ")"));
			int shown = Math.min(rest.size(), ctx.itemListCap(MAX_LEFT_ROWS));
			for (int i = 0; i < shown; i++)
			{
				GrandExchangeShoppingList.Line line = rest.get(i);
				rows.add(OverlayRow.item(line.itemId(), line.name(), "x" + line.outstanding(),
					false, OverlayTheme.TEXT, OverlayTheme.MUTED));
			}
			int remaining = rest.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow("...and " + remaining + " more to buy."));
			}
		}

		// Still needed, just not from here. Never in Next, never in Left --
		// see GrandExchangeShoppingList.Availability for why Karamjan rum
		// was the case that forced this, and why "questpath cannot look this
		// up" is kept apart from "the game does not trade it".
		if (!elsewhere.isEmpty())
		{
			rows.add(OverlayRow.header("Not sold here (" + elsewhere.size() + ")"));
			int shown = Math.min(elsewhere.size(), ctx.itemListCap(MAX_ELSEWHERE_ROWS));
			for (int i = 0; i < shown; i++)
			{
				GrandExchangeShoppingList.Line line = elsewhere.get(i);
				rows.add(OverlayRow.item(line.itemId(), line.name(), reason(line),
					false, OverlayTheme.MUTED, OverlayTheme.MUTED));
			}
			int remaining = elsewhere.size() - shown;
			if (remaining > 0)
			{
				rows.add(OverlayRow.overflow("...and " + remaining + " more from elsewhere."));
			}
		}

		return rows;
	}

	/**
	 * Why a line is not on the buy list, in the width of a right-hand value.
	 *
	 * <p>The reasons stay distinct in the words as well as in the enum.
	 * "not traded" is a fact about the game and the player can stop looking;
	 * "can't look up" is a fact about questpath's own data, and the item may
	 * well be on the Grand Exchange after all; "check wiki" is a requirement
	 * that names a kind of thing ("Seeds", "A light source") rather than one
	 * item, so nothing failed and the wiki says which ones will do; "members
	 * world" is a fact about where the player is standing, and it stops being
	 * true the moment they hop. Printing any of them as another would be the
	 * quiet kind of wrong this project keeps ruling out -- and "members
	 * world" most of all, because it is the only one the player can fix in
	 * thirty seconds.
	 */
	static String reason(GrandExchangeShoppingList.Line line)
	{
		switch (line.availability())
		{
			case NOT_ON_GE:
				return "not traded";
			case MEMBERS_ONLY:
				return "members world";
			case GENERIC:
				return "check wiki";
			default:
				return "can't look up";
		}
	}
}
