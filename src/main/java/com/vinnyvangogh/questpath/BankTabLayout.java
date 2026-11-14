package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

/**
 * Rebuilds the bank's own item grid as questpath's list, grouped by what the
 * plan still needs.
 *
 * <p>Marking the real slots, which is what {@link BankNeedsOverlay} does,
 * cannot answer the question that matters most at a bank: an item you own
 * <em>none</em> of has no slot to outline, so the thing most worth knowing is
 * the one thing an outline can never say. This can, because it draws the list
 * rather than annotating the grid.
 *
 * <p>The important part, and the reason this is not just a drawn panel: the
 * rows for items you <em>do</em> own are the bank's own widgets, moved. They
 * keep their item ids and have their Withdraw actions rebuilt onto them, so
 * they are withdrawn from exactly like any other bank slot. Only the
 * zero-count rows are inert, and they are inert because there is genuinely
 * nothing to withdraw.
 *
 * <p>The technique -- hide the container's children, reuse them in a new
 * order, reapply the withdraw menu, then fix the scroll height -- is the one
 * Quest Helper's bank tab uses. This is an independent implementation of it
 * against the same client API rather than a copy of their code.
 */
@Slf4j
class BankTabLayout
{
	/** The bank's own grid geometry. Matched so rows line up with the real thing. */
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_X_SPACING = 48;
	private static final int ITEM_Y_SPACING = 36;
	private static final int ROW_START_X = 51;
	private static final int HEADING_HEIGHT = 15;
	private static final int RULE_HEIGHT = 2;
	private static final int RULE_GAP = 5;

	/** Checkbox.CHECKED and Checkbox.CROSSED: the got-it / still-missing marks. */
	private static final int SPRITE_TICK = 1217;
	private static final int SPRITE_CROSS = 1216;

	/** ~bankmain_drawitem's filler id for an empty slot. */
	private static final int BLANK_SLOT = 6512;

	private final Client client;
	private final ClientThread clientThread;
	private final QuestpathPlugin plugin;

	/** Widgets this class created, deleted wholesale before every rebuild. */
	private final List<Widget> added = new ArrayList<>();

	/** How far into the container's existing children the rebuild has got. */
	private int nextChild;

	BankTabLayout(Client client, ClientThread clientThread, QuestpathPlugin plugin)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.plugin = plugin;
	}

	/** One heading and the items under it. */
	private static final class Section
	{
		private final String title;
		private final Color colour;
		private final List<Row> rows = new ArrayList<>();

		Section(String title, Color colour)
		{
			this.title = title;
			this.colour = colour;
		}
	}

	/** One item's row: what it is, how many you have, how many are wanted. */
	static final class Row
	{
		private final int itemId;
		private final String name;
		private final long owned;
		private final int wanted;

		Row(int itemId, String name, long owned, int wanted)
		{
			this.itemId = itemId;
			this.name = name;
			this.owned = owned;
			this.wanted = wanted;
		}

		/**
		 * How much of the requirement is met, 0 to 1.
		 *
		 * The ordering key for the shortfall section. Absolute counts are the
		 * wrong measure there: one of two Guam leaves and one of five pigeon
		 * cages are both "one short of nothing" by count, and the first is
		 * halfway home while the second has barely started.
		 */
		String name()
		{
			return name;
		}

		double progress()
		{
			if (wanted <= 0)
			{
				return 1.0;
			}
			return Math.min(1.0, (double) owned / wanted);
		}

		long outstanding()
		{
			return Math.max(0L, wanted - owned);
		}
	}

	/**
	 * Nearest to finished first, and never alphabetical.
	 *
	 * Alphabetical order says nothing about what to do next. Ordering by how
	 * close each requirement is to met puts the ones a single withdrawal would
	 * finish at the top, and the ones you have not started at the bottom where
	 * they belong -- a bank trip is a series of decisions about what is worth
	 * completing now.
	 *
	 * <p>Ties on proportion break on the smaller absolute gap: two rows both
	 * half done are not equally close when one needs one more and the other
	 * needs fifty.
	 */
	static void byClosestToDone(List<Row> rows)
	{
		rows.sort((a, b) ->
		{
			int byProgress = Double.compare(b.progress(), a.progress());
			if (byProgress != 0)
			{
				return byProgress;
			}
			int byGap = Long.compare(a.outstanding(), b.outstanding());
			if (byGap != 0)
			{
				return byGap;
			}
			return a.name.compareToIgnoreCase(b.name);
		});
	}

	/**
	 * Rebuilds the grid, or restores it.
	 *
	 * <p>Called after the client has finished laying the bank out. Always
	 * clears what the previous pass added first: the client rebuilds the grid
	 * on every search, tab change and withdrawal, and leaving old headings
	 * behind would stack them.
	 */
	void onBankBuilt()
	{
		removeAdded();

		if (!active())
		{
			return;
		}

		// The bank writes the selected tab's name here during its own build.
		// Left alone it says "Tab 9" over questpath's list, which is a lie
		// about which tab is open and the reason this ever looked like the
		// layout had hijacked the bank rather than being a tab of its own.
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null)
		{
			title.setText("Tab <col=ff9040>Quest Route</col>");
		}

		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			return;
		}

		// At tick end, because the client is still inside its own build when
		// the script fires and the children are not final until it leaves.
		clientThread.invokeAtTickEnd(() -> rebuild(container));
	}

	/**
	 * Whether the bank should be showing questpath's layout right now.
	 *
	 * Three conditions and all of them are the player's: the feature is on,
	 * the title-bar button is lit, and there is a snapshot to lay out.
	 */
	boolean active()
	{
		QuestpathConfig config = plugin.config();
		if (config == null || !config.bankTab() || !plugin.bankNeedsShown())
		{
			return false;
		}
		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		return snapshot != null && snapshot.items() != null;
	}

	private void rebuild(Widget container)
	{
		try
		{
			OverlaySnapshot snapshot = plugin.overlaySnapshot();
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (snapshot == null || snapshot.items() == null || bank == null)
			{
				return;
			}

			hideExisting(container);
			nextChild = 0;

			int height = 0;
			for (Section section : sectionsFor(snapshot.items(), bank))
			{
				height = drawSection(container, section, height);
			}

			// Without this the grid keeps the scroll extent the client worked
			// out for the real bank, and half the list is unreachable.
			int visible = container.getHeight();
			container.setScrollHeight(Math.max(height, visible));
			int scroll = container.getScrollY();
			clientThread.invokeLater(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scroll));
		}
		catch (RuntimeException e)
		{
			// A half-built grid is worse than none. Put the bank back.
			log.debug("questpath: bank layout failed, restoring", e);
			removeAdded();
		}
	}

	/**
	 * The plan's items in the order they are worth acting on, then everything
	 * else in the bank.
	 *
	 * The last section is what keeps this a bank rather than a report: every
	 * item the plan does not care about is still here, still withdrawable,
	 * just below the part you came for.
	 */
	private List<Section> sectionsFor(QuestItems items, ItemContainer bank)
	{
		Section ranOut = new Section("Ran out", OverlayTheme.BAD);
		Section shortOf = new Section("Still needed", OverlayTheme.WARN);
		Section ready = new Section("Ready to withdraw", OverlayTheme.GOOD);

		Set<Integer> claimed = new LinkedHashSet<>();
		for (QuestItems.Total total : items.totals())
		{
			int itemId = total.itemId();
			if (itemId <= 0)
			{
				// questpath never resolved a game id, so there is no sprite to
				// draw and no slot to match. It stays on the sidebar's list.
				continue;
			}
			claimed.add(itemId);
			Row row = new Row(itemId, total.name(), total.owned(), total.required());
			if (total.satisfied())
			{
				ready.rows.add(row);
			}
			else if (total.ranOut())
			{
				ranOut.rows.add(row);
			}
			else
			{
				shortOf.rows.add(row);
			}
		}

		Section rest = new Section("Everything else", OverlayTheme.MUTED);
		Set<Integer> seen = new LinkedHashSet<>();
		for (Item item : bank.getItems())
		{
			int itemId = item.getId();
			if (itemId <= 0 || itemId == BLANK_SLOT || claimed.contains(itemId)
				|| !seen.add(itemId))
			{
				continue;
			}
			ItemComposition definition = client.getItemDefinition(itemId);
			rest.rows.add(new Row(itemId, definition == null ? "" : definition.getName(),
				item.getQuantity(), 0));
		}

		// Only the shortfall bands are ordered by progress. "Ready" is every
		// row at 1.0 and "Everything else" has no requirement to be near, so
		// sorting either by it would just be an expensive way to keep the
		// order they arrived in.
		byClosestToDone(shortOf.rows);
		byClosestToDone(ranOut.rows);

		List<Section> out = new ArrayList<>();
		for (Section section : new Section[]{ranOut, shortOf, ready, rest})
		{
			if (!section.rows.isEmpty())
			{
				out.add(section);
			}
		}
		return out;
	}

	private int drawSection(Widget container, Section section, int top)
	{
		int y = heading(container, section, top);

		int placed = 0;
		for (Row row : section.rows)
		{
			Widget slot = container.getChild(nextChild);
			if (slot == null)
			{
				// Ran out of reusable children. The bank only has as many as
				// it had items, so a plan wanting more than the bank holds
				// stops here rather than drawing on top of itself.
				break;
			}
			int x = ROW_START_X + (placed % ITEMS_PER_ROW) * ITEM_X_SPACING;
			int rowY = y + (placed / ITEMS_PER_ROW) * ITEM_Y_SPACING;
			drawItem(slot, row, x, rowY);
			if (row.wanted > 0)
			{
				label(container, row, x, rowY);
			}
			nextChild++;
			placed++;
		}

		int rows = (placed + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		return y + rows * ITEM_Y_SPACING;
	}

	private int heading(Widget container, Section section, int top)
	{
		Widget text = container.createChild(-1, WidgetType.TEXT);
		text.setText(section.title);
		text.setTextColor(section.colour.getRGB());
		text.setFontId(495);
		text.setOriginalX(ROW_START_X);
		text.setOriginalY(top + RULE_GAP);
		text.setOriginalWidth(container.getWidth());
		text.setOriginalHeight(HEADING_HEIGHT);
		text.setNoClickThrough(true);
		text.revalidate();
		added.add(text);

		return top + RULE_GAP + HEADING_HEIGHT + RULE_HEIGHT;
	}

	/**
	 * Turns one of the bank's own widgets into this row.
	 *
	 * The withdraw menu is rebuilt by hand because moving a widget does not
	 * carry its actions with it -- the client wrote them during its own build
	 * and this pass replaced what the widget represents. Reapplying them is
	 * what keeps a row you own genuinely withdrawable rather than a picture of
	 * an item.
	 */
	private void drawItem(Widget slot, Row row, int x, int y)
	{
		ItemComposition definition = client.getItemDefinition(row.itemId);

		slot.setItemId(row.itemId);
		slot.setItemQuantity((int) Math.min(Integer.MAX_VALUE, row.owned));
		slot.setItemQuantityMode(ItemQuantityMode.ALWAYS);
		slot.setOriginalX(x);
		slot.setOriginalY(y);
		slot.setOriginalWidth(36);
		slot.setOriginalHeight(32);
		slot.setName("<col=ff9040>" + (definition == null ? "" : definition.getName()) + "</col>");
		slot.clearActions();
		// Dragging would reorder the real bank against a layout that is not
		// the real bank's order, so it is disabled rather than left to
		// misbehave quietly.
		slot.setDragDeadTime(1000);
		slot.setOnDragListener((Object[]) null);
		slot.setOnDragCompleteListener((JavaScriptCallback) event ->
		{
		});

		if (row.owned <= 0)
		{
			// Nothing to withdraw, so no withdraw menu. Dimmed, which is the
			// same language the bank uses for a placeholder.
			slot.setOpacity(120);
			slot.setItemQuantity(0);
			slot.setItemQuantityMode(ItemQuantityMode.NEVER);
			slot.setAction(1, "Details");
		}
		else
		{
			slot.setOpacity(0);
			withdrawActions(slot);
		}

		slot.setHidden(false);
		slot.revalidate();
	}

	/**
	 * The bank's own withdraw menu, rebuilt for a moved widget.
	 *
	 * The first entry has to match the quantity button the player has
	 * selected, or left-clicking a row in this layout withdraws a different
	 * amount from left-clicking the same item in the normal bank -- which is
	 * the kind of difference that loses someone a stack.
	 */
	private void withdrawActions(Widget slot)
	{
		int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
		int requested = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);

		String primary;
		switch (quantityType)
		{
			case 1:
				primary = "5";
				break;
			case 2:
				primary = "10";
				break;
			case 3:
				primary = Integer.toString(Math.max(1, requested));
				break;
			case 4:
				primary = "All";
				break;
			default:
				primary = "1";
				break;
		}

		slot.setAction(0, "Withdraw-" + primary);
		if (quantityType != 0)
		{
			slot.setAction(1, "Withdraw-1");
		}
		slot.setAction(2, "Withdraw-5");
		slot.setAction(3, "Withdraw-10");
		if (requested > 0)
		{
			slot.setAction(4, "Withdraw-" + requested);
		}
		slot.setAction(5, "Withdraw-X");
		slot.setAction(6, "Withdraw-All");
		slot.setAction(7, "Withdraw-All-but-1");
		if (client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 0)
		{
			slot.setAction(9, "Placeholder");
		}
		slot.setAction(10, "Examine");

		slot.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID,
			ScriptEvent.WIDGET_INDEX, ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y,
			InterfaceID.Bankmain.SCROLLBAR, 0);
	}

	/**
	 * The "have / need" caption and its tick or cross.
	 *
	 * Drawn over the slot rather than beside it because the grid is eight
	 * items wide and there is no beside. The count the bank itself prints is
	 * how many you own; this one is how many the plan wants, which is the
	 * number you cannot get anywhere else on this screen.
	 */
	private void label(Widget container, Row row, int x, int y)
	{
		boolean enough = row.owned >= row.wanted;

		Widget text = container.createChild(-1, WidgetType.TEXT);
		text.setText("/" + row.wanted);
		text.setTextColor((enough ? OverlayTheme.GOOD : OverlayTheme.WARN).getRGB());
		text.setFontId(494);
		text.setTextShadowed(true);
		text.setOriginalX(x + 2);
		text.setOriginalY(y + 21);
		text.setOriginalWidth(ITEM_X_SPACING);
		text.setOriginalHeight(HEADING_HEIGHT);
		text.setNoClickThrough(true);
		text.revalidate();
		added.add(text);

		Widget mark = container.createChild(-1, WidgetType.GRAPHIC);
		mark.setSpriteId(enough ? SPRITE_TICK : SPRITE_CROSS);
		mark.setOriginalX(x + 26);
		mark.setOriginalY(y - 1);
		mark.setOriginalWidth(10);
		mark.setOriginalHeight(10);
		mark.setNoClickThrough(true);
		mark.revalidate();
		added.add(mark);
	}

	/**
	 * Hides everything the client laid out, so the rebuild starts from an
	 * empty grid using the same widgets.
	 */
	private void hideExisting(Widget container)
	{
		Widget[] children = container.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			child.setHidden(true);
		}
	}

	/**
	 * Deletes the headings and captions this class made.
	 *
	 * The item slots are the bank's own and are left alone -- the client
	 * rewrites them on its next build, and deleting them would take the bank
	 * with them.
	 */
	void removeAdded()
	{
		for (Widget widget : added)
		{
			if (widget != null)
			{
				widget.setHidden(true);
			}
		}
		added.clear();
	}
}
