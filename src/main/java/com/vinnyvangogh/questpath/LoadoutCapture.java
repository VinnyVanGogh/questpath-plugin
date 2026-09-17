package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemEquipmentStats;
import net.runelite.http.api.item.ItemStats;

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
		Integer weaponItemId = null;
		int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		for (int slot = 0; slot < contents.length && slot < EquipmentInventorySlot.values().length; slot++)
		{
			Item item = contents[slot];
			if (item == null || item.getId() == EMPTY_SLOT_ITEM_ID || item.getQuantity() <= 0)
			{
				continue;
			}
			items.add(new LoadoutItem(slot, item.getId(), item.getQuantity(), nameOf(itemManager, item.getId())));
			if (slot == weaponSlot)
			{
				weaponItemId = item.getId();
			}
		}
		List<String> weaponStyles = captureWeaponStyles(itemManager, weaponItemId);
		return Optional.of(new Loadout(name, items, null, null, null, null, null, null,
			System.currentTimeMillis(), weaponStyles));
	}

	/** The item's display name, or empty when {@code ItemManager} has no composition for it. */
	private static String nameOf(ItemManager itemManager, int itemId)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition == null || composition.getName() == null ? "" : composition.getName();
	}

	/**
	 * The set of {@link Loadout#weaponStyles()} tokens for whatever is (or
	 * isn't) in the weapon slot.
	 *
	 * <p><b>Why offensive bonuses, not the weapon-type varbit.</b> RuneLite
	 * exposes the equipped weapon's category as {@code
	 * client.getVarbitValue(net.runelite.api.gameval.VarbitID.COMBAT_WEAPON_CATEGORY)}
	 * (confirmed present in the {@code runelite-api} 1.12.37 jar; the older
	 * {@code Varbits.EQUIPPED_WEAPON_TYPE} also still exists but is not the
	 * {@code gameval} constant this codebase prefers). But nothing in the jar
	 * documents what its integer values mean -- RuneLite's own {@code
	 * attackstyles} plugin (the only other reader of this varbit in the whole
	 * RuneLite source tree) does not hardcode a category table either; it
	 * resolves the value at runtime through {@code
	 * client.getEnum(EnumID.WEAPON_STYLES)} and a {@code StructComposition}
	 * lookup, machinery this plugin has no other use for and that would still
	 * only tell us which attack-style *options* the weapon offers (including,
	 * for a staff, its melee bash options) -- not the simple melee/ranged/
	 * magic bucket this feature wants for a staff.
	 *
	 * <p>{@link ItemEquipmentStats}, already the source {@link LockedGear}
	 * reads for {@code str}/{@code rstr}/{@code mdmg}, carries exactly that
	 * bucket directly: a weapon has a positive {@code astab}, {@code aslash}
	 * or {@code acrush} bonus iff it offers a melee attack roll, a positive
	 * {@code arange} bonus iff it offers a ranged one, a positive {@code
	 * amagic} bonus iff it offers a magic one. A plain elemental staff's
	 * near-zero melee bonuses correctly leave it magic-only; a salamander's
	 * simultaneous melee and ranged bonuses correctly come back ambiguous.
	 * See {@link #classifyWeaponStyles} for the actual bucketing, kept pure
	 * and separately testable.
	 *
	 * @return null when the weapon's stats are genuinely unknown to {@code
	 *     ItemManager} -- never guessed from the item's name or slot
	 */
	private static List<String> captureWeaponStyles(ItemManager itemManager, Integer weaponItemId)
	{
		if (weaponItemId == null)
		{
			// Unarmed: no weapon-slot item at all. Falls into the same
			// "everything else, melee" bucket classifyWeaponStyles gives a
			// weapon with no positive offensive bonus in any style.
			return classifyWeaponStyles(0, 0, 0, 0, 0);
		}
		ItemStats stats = itemManager.getItemStats(weaponItemId, false);
		if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
		{
			return null;
		}
		ItemEquipmentStats equipment = stats.getEquipment();
		return classifyWeaponStyles(equipment.getAstab(), equipment.getAslash(), equipment.getAcrush(),
			equipment.getArange(), equipment.getAmagic());
	}

	/**
	 * Buckets a weapon's offensive bonuses into {@link Loadout#weaponStyles()}
	 * tokens: melee when any of {@code astab}/{@code aslash}/{@code acrush} is
	 * positive, ranged when {@code arange} is positive, magic when {@code
	 * amagic} is positive -- any, all, or (for a bonus-less weapon-slot item)
	 * none of the three, in which case this falls back to melee alone, the
	 * same bucket unarmed uses. Never returns null or an empty list. Package-
	 * private and free of every RuneLite type so a test can call it directly
	 * with hand-picked bonuses, with nothing to fake.
	 */
	static List<String> classifyWeaponStyles(int astab, int aslash, int acrush, int arange, int amagic)
	{
		List<String> styles = new ArrayList<>(3);
		if (astab > 0 || aslash > 0 || acrush > 0)
		{
			styles.add(Loadout.STYLE_MELEE);
		}
		if (arange > 0)
		{
			styles.add(Loadout.STYLE_RANGED);
		}
		if (amagic > 0)
		{
			styles.add(Loadout.STYLE_MAGIC);
		}
		if (styles.isEmpty())
		{
			styles.add(Loadout.STYLE_MELEE);
		}
		return Collections.unmodifiableList(styles);
	}
}
