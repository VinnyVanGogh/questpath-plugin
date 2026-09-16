package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import net.runelite.api.EquipmentInventorySlot;

/**
 * One worn item captured into a {@link Loadout}: which slot, which item, how
 * many, and the display name it had at the moment it was saved.
 *
 * <p>The name travels with the item rather than being looked up again later.
 * {@link Loadout} is meant to render (in another task's panel) even when
 * {@code ItemManager} is unavailable -- between logins, or before the
 * item cache has finished loading -- and a row with an id but no name is not
 * something a player can recognise at a glance.
 *
 * <p>Immutable and Gson-serialisable with {@code @SerializedName}, the same
 * shape {@link BuildRule} uses -- but unlike {@code BuildRule}, this class is
 * meant to be constructed directly by application code too (by {@link
 * LoadoutCapture}), so its constructor validates rather than only accepting
 * whatever Gson happens to set on its fields. See {@link LoadoutCodec}'s
 * class javadoc for how a corrupt stored value is still caught even though
 * Gson bypasses this constructor when deserialising.
 */
public final class LoadoutItem
{
	@SerializedName("slot")
	private final int slot;

	@SerializedName("itemId")
	private final int itemId;

	@SerializedName("quantity")
	private final int quantity;

	@SerializedName("name")
	private final String name;

	/**
	 * @param slot the {@link EquipmentInventorySlot} index this item occupied,
	 *     0-13
	 * @param itemId the item id as captured -- never canonicalised; see {@link
	 *     LoadoutCapture}'s class javadoc for why
	 * @param quantity how many, at least 1 -- a worn item is never a zero-count row
	 * @param name the item's display name at capture time, defaulted to empty
	 *     rather than null when not given
	 * @throws IllegalArgumentException if {@code slot} is outside the range of
	 *     {@link EquipmentInventorySlot}, {@code itemId} is negative, or
	 *     {@code quantity} is less than 1
	 */
	public LoadoutItem(int slot, int itemId, int quantity, String name)
	{
		if (slot < 0 || slot >= EquipmentInventorySlot.values().length)
		{
			throw new IllegalArgumentException("slot out of range: " + slot);
		}
		if (itemId < 0)
		{
			throw new IllegalArgumentException("itemId must not be negative: " + itemId);
		}
		if (quantity < 1)
		{
			throw new IllegalArgumentException("quantity must be at least 1: " + quantity);
		}
		this.slot = slot;
		this.itemId = itemId;
		this.quantity = quantity;
		this.name = name == null ? "" : name;
	}

	/** The {@link EquipmentInventorySlot} index this item occupied. */
	public int slot()
	{
		return slot;
	}

	/** The item id as captured, never canonicalised. */
	public int itemId()
	{
		return itemId;
	}

	/** How many were worn in this slot -- always at least 1. */
	public int quantity()
	{
		return quantity;
	}

	/** The item's display name at capture time. Never null; empty when none was given. */
	public String name()
	{
		return name;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof LoadoutItem))
		{
			return false;
		}
		LoadoutItem that = (LoadoutItem) other;
		return slot == that.slot && itemId == that.itemId && quantity == that.quantity
			&& name.equals(that.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(slot, itemId, quantity, name);
	}

	@Override
	public String toString()
	{
		return "LoadoutItem{slot=" + slot + ", itemId=" + itemId + ", quantity=" + quantity
			+ ", name='" + name + "'}";
	}
}
