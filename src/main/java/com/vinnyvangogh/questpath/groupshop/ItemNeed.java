package com.vinnyvangogh.questpath.groupshop;

/** One item one quest asks one member for. */
public final class ItemNeed
{
	private final String quest;
	private final String item;
	private final int itemId;
	private final long count;
	private final boolean quantityStated;
	private final boolean obtainable;

	/**
	 * @param count how many; callers pass 1 for a line the wiki gave no count
	 *     for, and say so with {@code quantityStated = false}
	 * @param obtainable whether the item can be picked up during the quest
	 */
	public ItemNeed(String quest, String item, int itemId, long count, boolean quantityStated, boolean obtainable)
	{
		this.quest = quest == null ? "" : quest;
		this.item = item == null ? "" : item;
		this.itemId = itemId;
		this.count = Math.max(0, count);
		this.quantityStated = quantityStated;
		this.obtainable = obtainable;
	}

	public String quest()
	{
		return quest;
	}

	/** The display name, as the wiki spells it. */
	public String item()
	{
		return item;
	}

	/** The game's item id, or 0 when none is known. */
	public int itemId()
	{
		return itemId;
	}

	public long count()
	{
		return count;
	}

	/** False when the wiki named the item without a count, so {@link #count()} is a floor. */
	public boolean quantityStated()
	{
		return quantityStated;
	}

	public boolean obtainable()
	{
		return obtainable;
	}

	/** The join key: lower-cased name, the same key member holdings use. */
	String key()
	{
		return item.toLowerCase();
	}
}
