package com.vinnyvangogh.questpath;

import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;

/**
 * questpath's own button in the bank's title bar, beside the close cross.
 *
 * <p>A toggle, not a screen: pressed, it marks what the plan still needs in
 * the bank's own grid and lists it in order. The bank is where the shopping
 * list stops being a list and becomes a series of withdrawals, and it is the
 * one place the answer cannot currently be seen without alt-tabbing to a
 * sidebar.
 *
 * <p>Built as a child of the bank's own frame rather than drawn as an
 * overlay, so it sits inside the interface, moves with it, and survives the
 * player resizing the client. The cost is that it has to be rebuilt every
 * time the bank interface loads, because the client discards its widget tree
 * in between -- {@link #attach()} is therefore idempotent and cheap to call
 * from a widget-loaded event.
 */
@Slf4j
class BankButton
{
	/**
	 * Roughly the size of the bank's own title-bar controls.
	 *
	 * Matched by eye rather than read from the interface: the cross is drawn
	 * from a sprite whose bounds are not exposed, and a button a few pixels
	 * off the game's own is less wrong than one that guesses at a widget id
	 * and vanishes when the interface is rebuilt.
	 */
	private static final int SIZE = 15;

	/** Clearance from the right edge, leaving the close cross its own space. */
	private static final int RIGHT_INSET = 42;

	private static final int TOP_INSET = 4;

	/**
	 * TAB_QUESTS, the blue quest-book icon, the same sprite the sidebar's
	 * Quests tab uses. Lit when the marks are on and dimmed when they are
	 * not -- RS2_TAB_QUESTS_SELECTED does not exist as a pair, so the off
	 * state reuses the same art and the button's own right-click text carries
	 * the state in words.
	 */
	private static final int SPRITE_ON = 776;
	private static final int SPRITE_OFF = 776;

	private final Client client;
	private final BooleanSupplier shown;
	private final Runnable onToggle;

	private Widget button;

	BankButton(Client client, BooleanSupplier shown, Runnable onToggle)
	{
		this.client = client;
		this.shown = shown;
		this.onToggle = onToggle;
	}

	/**
	 * Creates the button, or refreshes the one already there.
	 *
	 * <p>Safe to call on every bank open. The client rebuilds the bank's
	 * widget tree each time it loads, so a Widget held from a previous open is
	 * stale rather than reusable -- which is why this looks the parent up
	 * again rather than trusting the field.
	 */
	void attach()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (parent == null)
		{
			parent = client.getWidget(InterfaceID.Bankmain.FRAME);
		}
		if (parent == null)
		{
			// Bank not open, or the interface changed shape under us. Not an
			// error worth a stack trace: the next open tries again.
			return;
		}

		Widget created = parent.createChild(-1, WidgetType.GRAPHIC);
		created.setOriginalWidth(SIZE);
		created.setOriginalHeight(SIZE);
		created.setOriginalX(RIGHT_INSET);
		created.setOriginalY(TOP_INSET);
		// Anchored to the top-right so it keeps its place beside the cross
		// whatever width the interface is drawn at.
		created.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		created.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		created.setHasListener(true);
		created.setNoClickThrough(true);
		created.setOnOpListener((JavaScriptCallback) event -> toggle());
		created.revalidate();

		button = created;
		refresh();
	}

	/** Repaints the button for the current state. */
	void refresh()
	{
		if (button == null)
		{
			return;
		}
		boolean on = shown.getAsBoolean();
		button.setSpriteId(on ? SPRITE_ON : SPRITE_OFF);
		button.setAction(0, on
			? "Hide <col=ff9040>Quest Path</col> items"
			: "Show <col=ff9040>Quest Path</col> items");
		button.revalidate();
	}

	/** Forgets the widget. The client discards it when the bank closes. */
	void detach()
	{
		button = null;
	}

	private void toggle()
	{
		onToggle.run();
		refresh();
	}
}
