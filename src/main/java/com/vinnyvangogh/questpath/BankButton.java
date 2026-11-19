package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * questpath's own button in the bank's title bar, beside the close cross.
 *
 * <p>A tab, not a toggle over the tab you already had open: pressed, the bank
 * grid becomes questpath's list; pressed again, or any real tab clicked, it
 * goes back. The bank is where the shopping list stops being a list and
 * becomes a series of withdrawals, and it is the one screen where the answer
 * could not be seen without alt-tabbing to a sidebar.
 *
 * <p>Two widgets rather than one: a square button that carries the click and
 * the selected state, and the quest icon drawn on top of it. That is the
 * shape the bank's own controls have, and one widget cannot be both a button
 * frame and an icon.
 */
@Slf4j
class BankButton
{
	/**
	 * Hung off UNIVERSE, the bank's root layer -- <b>not</b> TITLE.
	 *
	 * TITLE is a text widget. A child created on it is never laid out, which
	 * is why the first attempt at this button was correct in every respect
	 * except being visible.
	 */
	private static final int SIZE = 25;

	/**
	 * Absolute, from the left, inside the bank's root layer.
	 *
	 * Not an anchored right inset: the bank frame is a fixed width and its
	 * own controls are placed absolutely, so matching them is what puts this
	 * on the same line as the cross rather than near it.
	 *
	 * <p>One button's width left of 408, which is where Quest Helper puts its
	 * own. Sitting on 408 stacked the two exactly on top of each other, and
	 * whichever drew second won -- so anyone running both plugins lost one of
	 * them with no hint as to why. Neither of us can move the other, so this
	 * one steps aside. If Quest Helper is not installed the only cost is a
	 * button-sized gap beside the cross.
	 */
	private static final int BUTTON_X = 380;
	private static final int BUTTON_Y = 5;

	/**
	 * Leftmost x this will step to when another plugin is in the way, and the
	 * widest widget treated as a neighbouring button rather than bank furniture.
	 * Past MIN_X the row is the bank's own title text.
	 */
	private static final int MIN_X = 250;
	private static final int MAX_NEIGHBOUR = 40;

	/** The bank's own small square button, unselected and selected. */
	private static final int SPRITE_BUTTON = 195;
	private static final int SPRITE_BUTTON_SELECTED = 196;

	/**
	 * SideiconsInterface.INVENTORY, the backpack.
	 *
	 * Deliberately not a quest icon. Quest Helper's button is a blue quest
	 * book, and two adjacent buttons that both say "quests" are two buttons
	 * nobody can tell apart at 19 pixels. A bag also happens to be the more
	 * honest label: this tab is about the items, not the quests.
	 */
	private static final int SPRITE_ICON = 777;
	private static final int ICON_INSET = 3;

	/**
	 * The button's menu target, and the marker that keeps it out of the
	 * close-on-another-tab rule. Shared with the plugin rather than spelled
	 * twice: two copies of a string that must match is a bug waiting for
	 * whoever edits one of them.
	 */
	static final String TARGET = "Quest Route";

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
	 * <p>Safe to call on every bank open, and it has to be: the client
	 * rebuilds the bank's widget tree each time it loads, so a Widget held
	 * from a previous open is stale rather than reusable -- which is why this
	 * looks the parent up again rather than trusting the field.
	 */
	void attach()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			// Bank not open, or the interface changed shape under us. Not an
			// error worth a stack trace: the next open tries again.
			return;
		}

		int x = BankButtonSlots.pick(occupiedOnRow(parent), BUTTON_X, SIZE, MIN_X);

		button = graphic(parent, SPRITE_BUTTON, SIZE, x, BUTTON_Y);
		button.setName(TARGET);
		button.setHasListener(true);
		button.setAction(1, "View tab");
		button.setOnOpListener((JavaScriptCallback) event -> toggle());
		button.revalidate();

		// No name and no listener, so it raises no menu entry of its own and
		// clicks fall through to the button underneath it. An icon that
		// swallowed the click would make the middle of the button dead.
		icon = graphic(parent, SPRITE_ICON, SIZE - ICON_INSET * 2,
			x + ICON_INSET, BUTTON_Y + ICON_INSET);

		refresh();
	}

	/**
	 * The x and width of every small visible widget other plugins have already
	 * hung on the title-bar row. Our own are skipped, and anything wider than a
	 * button is the bank's own furniture (title, frame), not a neighbour.
	 */
	private List<int[]> occupiedOnRow(Widget parent)
	{
		List<int[]> row = new ArrayList<>();
		Widget[] children = parent.getDynamicChildren();
		if (children == null)
		{
			return row;
		}
		for (Widget child : children)
		{
			if (child == null || child == button || child == icon || child.isSelfHidden())
			{
				continue;
			}
			boolean onRow = child.getOriginalY() < BUTTON_Y + SIZE
				&& child.getOriginalY() + child.getOriginalHeight() > BUTTON_Y;
			boolean buttonSized = child.getOriginalWidth() > 0 && child.getOriginalWidth() <= MAX_NEIGHBOUR;
			if (onRow && buttonSized && child.getOriginalX() >= MIN_X)
			{
				row.add(new int[]{child.getOriginalX(), child.getOriginalWidth()});
			}
		}
		return row;
	}

	/** Repaints the button for the current state. */
	void refresh()
	{
		if (button == null)
		{
			return;
		}
		button.setSpriteId(shown.getAsBoolean() ? SPRITE_BUTTON_SELECTED : SPRITE_BUTTON);
		button.revalidate();
		if (icon != null)
		{
			icon.revalidate();
		}
	}

	/** Forgets the widgets. The client discards them when the bank closes. */
	void detach()
	{
		if (button != null)
		{
			button.setHidden(true);
		}
		if (icon != null)
		{
			icon.setHidden(true);
		}
		button = null;
		icon = null;
	}

	private static Widget graphic(Widget parent, int spriteId, int size, int x, int y)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(size);
		widget.setOriginalHeight(size);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	private void toggle()
	{
		onToggle.run();
		refresh();
	}
}
