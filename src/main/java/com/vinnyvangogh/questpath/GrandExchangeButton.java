package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * questpath's own button in the top-right corner of the Grand Exchange's
 * item-search chatbox, beside Quest Helper's. Pressed, it replaces the search
 * results with the account's buy list, every row selectable exactly the way a
 * real search result is.
 *
 * <p>The client renders the list itself once handed the item ids, so nothing
 * is drawn by hand. The mechanism follows Quest Helper's sentinel pattern
 * (read from its bytecode, never called): write a sentinel into varcstr 359,
 * set {@link VarClientInt#INPUT_TYPE} to 14, replay the box's key listener so
 * it re-runs its search, then on the search event check the sentinel, consume
 * it, and set the result index, count and ids.
 *
 * <p>questpath's sentinel is {@code "questpath"}, so the two buttons never
 * answer each other, and the button sits left of Quest Helper's fixed 480
 * (see {@link BankButton}).
 *
 * <p>Widget calls can throw {@code AssertionError}, not an exception, so
 * catches are {@code RuntimeException | AssertionError}. Unverified in a
 * running client.
 */
@Slf4j
class GrandExchangeButton
{
	/**
	 * The search text that means "questpath is asking", rather than a player
	 * looking for an item.
	 *
	 * <p>Deliberately not Quest Helper's {@code "quest-helper"}: both
	 * plugins watch the same varcstr on the same event, and sharing a
	 * sentinel would make each one answer the other's button.
	 */
	static final String SEARCH_SENTINEL = "questpath";

	/** The client's own cap on how many results it will render. */
	static final int MAX_RESULTS = 250;

	/** Quest Helper's own button, 20x20 at this x on {@code MES_LAYER}. Read from its bytecode, not guessed. */
	private static final int QUEST_HELPER_X = 480;

	private static final int SIZE = 20;

	/** Kept clear between the two buttons so neither reads as part of the other. */
	private static final int GAP = 4;

	/** Left of Quest Helper's, which is hard-coded to 480 and cannot move out of the way. */
	private static final int X = QUEST_HELPER_X - SIZE - GAP;

	private static final int Y = 0;

	/** The tab-button background sprites the client itself uses: unpressed, then pressed. */
	private static final int SPRITE_UNPRESSED = 195;
	private static final int SPRITE_PRESSED = 196;

	/** Inset and size of the icon drawn inside the button, matching Quest Helper's 14x14 at +3. */
	private static final int ICON_INSET = 3;
	private static final int ICON_SIZE = SIZE - ICON_INSET * 2;

	/**
	 * The bag sprite {@link BankButton} already uses for questpath. Reused
	 * rather than picking a second one, so questpath's two in-game buttons
	 * are recognisably the same plugin.
	 */
	private static final int ICON_SPRITE = 777;

	/** A font already confirmed to render in this interface -- Quest Helper's own title uses it. */
	private static final int FONT_ID = 496;

	/** The click the client plays for its own tab buttons. */
	private static final int CLICK_SOUND = 2266;

	/** What varcint 5 is set to for this box's item search. */
	private static final int GE_INPUT_TYPE = 14;

	/** questpath's brand orange, as {@code Widget#setText}'s {@code <col=>} markup wants it. */
	private static final String BRAND_ORANGE = "dc8a00";

	private final Client client;
	private final java.util.function.Consumer<Runnable> later;
	private final Supplier<List<GrandExchangeShoppingList.Line>> list;

	private boolean active;

	/**
	 * The one item {@link #showOne} asked for, or 0 when the button is showing
	 * the whole buy list. Cleared whenever the button itself is toggled, so a
	 * click on the button always means "the list".
	 */
	private int single;

	private Widget background;
	private Widget icon;
	private Widget title;

	/**
	 * @param later defers a task to the client's next tick -- production
	 *     wiring passes {@code clientThread::invokeLater}. Deferred rather
	 *     than run inline because {@link #updateSearchInterface} re-runs the
	 *     box's own search script, and Quest Helper defers the identical
	 *     call for what is presumably the same reason: the widget being
	 *     re-run is the one whose listener is mid-flight.
	 * @param list the account's buy list, read fresh on every activation
	 */
	GrandExchangeButton(Client client, java.util.function.Consumer<Runnable> later,
		Supplier<List<GrandExchangeShoppingList.Line>> list)
	{
		this.client = client;
		this.later = later;
		this.list = list;
	}

	/**
	 * Draws or redraws the button. Safe to call whenever the search chatbox
	 * might have appeared or been rebuilt.
	 *
	 * <p>Re-activates itself if it was active when the box was last torn
	 * down, which is Quest Helper's own behaviour at the end of {@code
	 * init()} -- otherwise reopening the box leaves a button that looks
	 * unpressed while the list it produced is still on screen.
	 */
	void attach()
	{
		try
		{
			attachNow();
		}
		catch (RuntimeException | AssertionError e)
		{
			// AssertionError is deliberate, not a typo. See class doc.
			log.warn("Could not draw Quest Route's Grand Exchange button", e);
		}
	}

	private void attachNow()
	{
		Widget parent = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (parent == null || parent.isHidden())
		{
			// Not the search chatbox. The client discarded whatever this had.
			background = null;
			icon = null;
			title = null;
			return;
		}

		if (list.get() == null)
		{
			// No buy list to show at all -- an ironman, or nothing loaded
			// yet. A button that opens an empty panel is worse than no
			// button, and this project does not offer what cannot be acted
			// on.
			detachNow();
			return;
		}

		if (!stillPresent(parent, background))
		{
			background = null;
			icon = null;
			title = null;
		}

		boolean rebuilt = background == null;
		if (rebuilt)
		{
			background = parent.createChild(-1, WidgetType.GRAPHIC);
			background.setOriginalWidth(SIZE);
			background.setOriginalHeight(SIZE);
			background.setOriginalX(X);
			background.setOriginalY(Y);
			background.setHasListener(true);
			background.setAction(1, "View what to buy");
			background.setName("Quest Route");
			background.setOnOpListener((JavaScriptCallback) event -> toggle());
			background.setSpriteId(active ? SPRITE_PRESSED : SPRITE_UNPRESSED);
			background.revalidate();

			icon = parent.createChild(-1, WidgetType.GRAPHIC);
			icon.setOriginalWidth(ICON_SIZE);
			icon.setOriginalHeight(ICON_SIZE);
			icon.setOriginalX(X + ICON_INSET);
			icon.setOriginalY(Y + ICON_INSET);
			icon.setSpriteId(ICON_SPRITE);
			icon.revalidate();

			title = createTitle(parent);
		}

		if (rebuilt && active)
		{
			// The box was torn down and rebuilt while this was open. Re-run
			// the activation so the list comes back, rather than leaving a
			// pressed button sitting over the client's own empty search.
			//
			// Gated on rebuilt, not on active alone, and that guard is
			// load-bearing: attach() is also called after script 752, which
			// is the script our own activation causes to run. Re-activating
			// on every call would make each search trigger the next one
			// forever.
			active = false;
			activate();
		}
	}

	/**
	 * Re-runs the list if it is currently showing, for when what to buy has
	 * changed underneath it.
	 *
	 * <p>Placing an offer while this panel is open should drop that item out
	 * of the list, the same way it drops out of the overlay -- otherwise the
	 * one surface that exists to say what to buy next goes on naming
	 * something already bought.
	 *
	 * <p>No loop: this re-runs the search, the search fires {@code
	 * GrandExchangeSearched}, and that handler only writes result ids. It
	 * does not start another search.
	 */
	void refresh()
	{
		if (!active)
		{
			return;
		}
		later.accept(() -> updateSearchInterface(true));
	}

	/**
	 * The title drawn over the search input while the list is showing --
	 * built the same way and in the same place as Quest Helper's, which
	 * takes its width and height from {@link InterfaceID.Chatbox#MES_TEXT2}
	 * so it sits exactly where the input it replaces was.
	 */
	private Widget createTitle(Widget parent)
	{
		Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		Widget line = parent.createChild(-1, WidgetType.TEXT);
		if (input == null)
		{
			return line;
		}
		line.setOriginalWidth(input.getWidth());
		line.setOriginalHeight(input.getHeight());
		line.setOriginalX(0);
		line.setOriginalY(0);
		line.setTextShadowed(false);
		line.setXTextAlignment(1);
		line.setYTextAlignment(1);
		line.setFontId(FONT_ID);
		line.setTextColor(net.runelite.client.ui.JagexColors.CHAT_GAME_EXAMINE_TEXT_OPAQUE_BACKGROUND.getRGB());
		line.setText("<col=" + BRAND_ORANGE + ">Quest Route</col> buy list");
		line.setHidden(!active);
		line.revalidate();
		return line;
	}

	/**
	 * The item ids to hand the client's own result list, in the order they
	 * should appear.
	 *
	 * <p>What to buy, and only that: {@link GrandExchangeShoppingList#left}
	 * already excludes what a slot is already buying and what the Grand
	 * Exchange does not trade, so the first row is the next thing to get and
	 * nothing in the list is something the account cannot act on.
	 *
	 * <p><b>Truncated to {@code short}, which is the client's own shape.</b>
	 * {@code setGeSearchResultIds} takes a {@code short[]}, so an id above
	 * 32767 wraps -- RuneLite's own bundled Grand Exchange plugin and Quest
	 * Helper both hand it exactly the same truncated array, through Guava's
	 * {@code Shorts.toArray}. This is written out longhand rather than
	 * pulling in Guava for one call.
	 */
	short[] resultIds()
	{
		if (single > 0)
		{
			return new short[]{(short) single};
		}
		List<GrandExchangeShoppingList.Line> lines = list.get();
		List<Integer> ids = new ArrayList<>();
		for (GrandExchangeShoppingList.Line line : GrandExchangeShoppingList.left(lines))
		{
			if (line.itemId() > 0 && !ids.contains(line.itemId()))
			{
				ids.add(line.itemId());
			}
			if (ids.size() >= MAX_RESULTS)
			{
				break;
			}
		}
		short[] out = new short[ids.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = (short) ids.get(i).intValue();
		}
		return out;
	}

	/** Flips between questpath's list and the client's own search, with the client's own click. */
	private void toggle()
	{
		try
		{
			if (active)
			{
				close();
			}
			else
			{
				single = 0;
				activate();
			}
			client.playSoundEffect(CLICK_SOUND);
		}
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Toggling Quest Route's Grand Exchange button failed", e);
		}
	}

	/**
	 * Shows exactly one item in the client's own result list -- the sidebar's
	 * click-a-row search. The same sentinel path as the button, never the
	 * item's name typed into the box: see {@link GrandExchangeSearchBridge}'s
	 * class doc for why that distinction is the whole design. Must run on the
	 * client thread.
	 */
	void showOne(int itemId)
	{
		single = itemId;
		if (active)
		{
			// Already showing a list: re-run the sentinel search so the
			// result ids are re-read with the new single item.
			later.accept(() -> updateSearchInterface(true));
			return;
		}
		activate();
	}

	private void activate()
	{
		if (active)
		{
			return;
		}
		if (background != null)
		{
			background.setSpriteId(SPRITE_PRESSED);
			background.revalidate();
		}
		if (title != null)
		{
			title.setHidden(false);
		}
		active = true;
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, SEARCH_SENTINEL);
		client.setVarcIntValue(VarClientInt.INPUT_TYPE, GE_INPUT_TYPE);
		later.accept(() -> updateSearchInterface(true));
	}

	/**
	 * Puts the box back the way it was: the client's own search, with an
	 * empty query.
	 *
	 * <p>Empty rather than whatever the player last typed, because leaving
	 * the sentinel in place would make the next real search re-trigger this
	 * list, and leaving an old query in place would show results nobody
	 * asked for.
	 */
	private void close()
	{
		active = false;
		single = 0;
		if (background != null)
		{
			background.setSpriteId(SPRITE_UNPRESSED);
			background.revalidate();
		}
		if (title != null)
		{
			title.setHidden(true);
		}
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, "");
		client.setVarcIntValue(VarClientInt.INPUT_TYPE, GE_INPUT_TYPE);
		later.accept(() -> updateSearchInterface(false));
	}

	/**
	 * Makes the box react to the search text just written, and hides or
	 * shows its input.
	 *
	 * <p>Replaying the input widget's own {@code getOnKeyListener()} rather
	 * than calling {@code ScriptID.GE_ITEM_SEARCH} is the fix for the very
	 * first version of this feature, which called that script with no
	 * arguments when it takes three and threw on every click. The listener
	 * is the game's own and already correctly shaped -- see {@link
	 * GrandExchangeSearchBridge}'s class doc for the whole story.
	 */
	private void updateSearchInterface(boolean hideInput)
	{
		try
		{
			Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
			if (input == null)
			{
				return;
			}
			Object[] listener = input.getOnKeyListener();
			if (listener == null)
			{
				return;
			}
			client.runScript(listener);
			input.setHidden(hideInput);
		}
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Re-running the Grand Exchange search box failed", e);
		}
	}

	/** Switches the button off and forgets it -- for plugin shutdown. */
	void detach()
	{
		try
		{
			detachNow();
		}
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Removing Quest Route's Grand Exchange button failed", e);
		}
	}

	private void detachNow()
	{
		if (active)
		{
			close();
		}
		if (background != null)
		{
			background.setHidden(true);
		}
		if (icon != null)
		{
			icon.setHidden(true);
		}
		if (title != null)
		{
			title.setHidden(true);
		}
		background = null;
		icon = null;
		title = null;
	}

	/** Whether widget is still among parent's own current children. */
	private static boolean stillPresent(Widget parent, Widget widget)
	{
		if (widget == null)
		{
			return false;
		}
		Widget[] children = parent.getDynamicChildren();
		if (children == null)
		{
			return false;
		}
		for (Widget child : children)
		{
			if (child == widget)
			{
				return true;
			}
		}
		return false;
	}
}
