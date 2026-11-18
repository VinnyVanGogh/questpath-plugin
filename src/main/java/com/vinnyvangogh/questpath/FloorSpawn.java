package com.vinnyvangogh.questpath;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** One kind of ground item on one tile: the identity "said once per spawn" is counted by. */
final class FloorSpawn
{
	final WorldPoint at;
	final int itemId;

	FloorSpawn(WorldPoint at, int itemId)
	{
		this.at = at;
		this.itemId = itemId;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof FloorSpawn))
		{
			return false;
		}
		FloorSpawn other = (FloorSpawn) o;
		return itemId == other.itemId && Objects.equals(at, other.at);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(at, itemId);
	}
}
