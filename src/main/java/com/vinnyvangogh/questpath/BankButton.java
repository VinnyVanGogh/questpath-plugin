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
	 * The bank's own small square button, unselected and selected.
	 *
	 * The state has to be visible in the art. This is a tab, and a tab that
	 * looks identical whether or not it is the open one leaves the player
	 * reading the title bar to find out which view they are in.
	 */
	private static final int SPRITE_BUTTON = 195;
	private static final int SPRITE_BUTTON_SELECTED = 196;

	/** TAB_QUESTS, the quest-book icon, drawn on top of the button. */
	private static final int SPRITE_ICON = 776;

	/** The icon is inset inside the button so the button's edge still reads. */
	private static final int ICON_SIZE = 11;

	/**
	 * The button's menu target, and the marker that keeps it out of the
	 * close-on-another-tab rule. Shared with the plugin rather than spelled
	 * twice: two copies of a string that must match is a bug waiting for
	 * whoever edits one of them.
	 */
	static final String TARGET = "<col=ff9040>Quest Path</col>";

	private final Client client;
	private final BooleanSupplier shown;
	private final Runnable onToggle;

	private Widget button;
	private Widget icon;

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

		// Drawn separately and deliberately not clickable: a child with its
		// own listener would swallow the clicks meant for the button under it.
		Widget drawnIcon = parent.createChild(-1, WidgetType.GRAPHIC);
		drawnIcon.setSpriteId(SPRITE_ICON);
		drawnIcon.setOriginalWidth(ICON_SIZE);
		drawnIcon.setOriginalHeight(ICON_SIZE);
		drawnIcon.setOriginalX(RIGHT_INSET + (SIZE - ICON_SIZE) / 2);
		drawnIcon.setOriginalY(TOP_INSET + (SIZE - ICON_SIZE) / 2);
		drawnIcon.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		drawnIcon.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		drawnIcon.setNoClickThrough(false);
		drawnIcon.revalidate();

		button = created;
		icon = drawnIcon;
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
		button.setSpriteId(on ? SPRITE_BUTTON_SELECTED : SPRITE_BUTTON);
		// "View tab" so it reads like the bank's own tabs, which is what it
		// is. The name is the menu target, and it is what tells the
		// close-on-other-tab rule that this particular "View tab" is ours --
		// without it, clicking this button closes the tab it just opened.
		button.setName(TARGET);
		button.setAction(0, "View tab");
		button.revalidate();
		if (icon != null)
		{
			icon.revalidate();
		}
	}

	/** Forgets the widget. The client discards it when the bank closes. */
	void detach()
	{
		button = null;
		icon = null;
	}

	private void toggle()
	{
		onToggle.run();
		refresh();
	}
}
