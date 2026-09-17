package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

/**
 * Snapshots currently worn equipment into a {@link Loadout}. Capture only --
 * nothing here touches persistence; see {@link LoadoutStore} for that.
 *
 * <p>Reads the worn-equipment container via {@code
 * client.getItemContainer(int)} and the current, non-deprecated {@code
 * net.runelite.api.gameval.InventoryID.WORN} constant, per {@code
 * runelite-api.md} &sect;1-2 (the legacy {@code
 * net.runelite.api.InventoryID.EQUIPMENT} enum overload still compiles but is
 * class-level {@code @Deprecated}).
 *
 * <p><b>Empty-slot convention, not a verified signature.</b> {@code
 * ItemContainer#getItems()} returns one array slot per equipment slot, and an
 * unoccupied slot's {@link Item} carries id {@code -1} -- the standard
 * RuneLite sentinel for "nothing here", used the same way across the plugin
 * ecosystem. This is runtime data behaviour, not an API signature, so unlike
 * everything else this class relies on it could not be confirmed with {@code
 * javap} against the jars {@code runelite-api.md} names; whoever verifies
 * this task in-game should confirm a loadout captured with an empty slot
 * (e.g. no cape, no ring) omits that slot rather than recording a phantom
 * item id {@code -1}.
 */
public final class LoadoutCapture
{
	/**
	 * The RuneLite sentinel item id for an unoccupied equipment slot. See
	 * this class's own javadoc for why this is a documented convention rather
	 * than a verified constant.
	 */
	private static final int EMPTY_SLOT_ITEM_ID = -1;

	private LoadoutCapture()
	{
	}

	/**
	 * Snapshots the account's currently worn equipment as a new, unsaved
	 * {@link Loadout} named {@code name}. None of the six combat selections
	 * are set -- {@link Loadout#hasMeleeSelections()}, {@link
	 * Loadout#hasRangedSelections()} and {@link Loadout#hasMagicSelections()}
	 * are all false on the result.
	 *
	 * <p><b>Must be called on the client thread.</b> {@link
	 * Client#getItemContainer(int)} and {@link
	 * ItemManager#getItemComposition(int)} are both client-thread-only calls;
	 * nothing in this method defers or checks that for you.
	 *
	 * <p>Item ids are recorded exactly as captured -- never canonicalised via
	 * {@link ItemManager#canonicalize(int)} -- because a noted or placeholder
	 * id cannot be worn in the first place, so every id this method sees is
	 * already the wearable one.
	 *
	 * @return empty when the worn container itself is null -- not logged in,
	 *     or the client has not finished loading it yet -- never a {@link
	 *     Loadout} with zero items, which would be indistinguishable from an
	 *     account genuinely wearing nothing
	 */
	public static Optional<Loadout> fromWorn(Client client, ItemManager itemManager, String name)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return Optional.empty();
		}
		Item[] contents = worn.getItems();
		List<LoadoutItem> items = new ArrayList<>();
		for (int slot = 0; slot < contents.length && slot < EquipmentInventorySlot.values().length; slot++)
		{
			Item item = contents[slot];
			if (item == null || item.getId() == EMPTY_SLOT_ITEM_ID || item.getQuantity() <= 0)
			{
				continue;
			}
			items.add(new LoadoutItem(slot, item.getId(), item.getQuantity(), nameOf(itemManager, item.getId())));
		}
		return Optional.of(new Loadout(name, items, null, null, null, null, null, null, System.currentTimeMillis()));
	}

	/** The item's display name, or empty when {@code ItemManager} has no composition for it. */
	private static String nameOf(ItemManager itemManager, int itemId)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition == null || composition.getName() == null ? "" : composition.getName();
	}
}
