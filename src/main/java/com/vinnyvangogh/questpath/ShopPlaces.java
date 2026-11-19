package com.vinnyvangogh.questpath;

import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * Shop name to tile, which is all the route ever asked of a shop, plus the
 * town or area the bundled data files it under -- so a stop can say "Obli's
 * General Store (Shilo Village)" rather than a bare name.
 *
 * <p>A {@link Function} first so every existing caller keeps working
 * unchanged; the area rides along the same way {@link RouteSources} carries
 * drop rules on the monster locator. A plain lambda locator has no area and
 * {@link #areaOf} on it answers empty, which {@link #label} renders as just
 * the name.
 */
final class ShopPlaces implements Function<String, WorldPoint>
{
	private final Function<String, WorldPoint> locator;
	private final Function<String, String> areas;

	private final Function<String, String> blocks;

	ShopPlaces(Function<String, WorldPoint> locator, Function<String, String> areas)
	{
		this(locator, areas, name -> "");
	}

	/** @param blocks why a shop is not usable ("needs 75 Thieving, you have 2"), or empty */
	ShopPlaces(Function<String, WorldPoint> locator, Function<String, String> areas,
		Function<String, String> blocks)
	{
		this.locator = locator;
		this.areas = areas;
		this.blocks = blocks;
	}

	/** Why this account cannot use the shop, or empty when unknown or usable. */
	static String blockReason(Function<String, WorldPoint> locator, String shopName)
	{
		if (!(locator instanceof ShopPlaces) || shopName == null)
		{
			return "";
		}
		String reason = ((ShopPlaces) locator).blocks.apply(shopName);
		return reason == null ? "" : reason;
	}

	@Override
	public WorldPoint apply(String shopName)
	{
		return locator.apply(shopName);
	}

	/** The town or area for a shop, or empty when the data carries none. */
	static String areaOf(Function<String, WorldPoint> locator, String shopName)
	{
		if (!(locator instanceof ShopPlaces) || shopName == null)
		{
			return "";
		}
		String area = ((ShopPlaces) locator).areas.apply(shopName);
		return area == null ? "" : area.trim();
	}

	/** "Shop name (Town)", or just the name when no area is known. */
	static String label(String shopName, String area)
	{
		if (shopName == null)
		{
			return "";
		}
		return area == null || area.isEmpty() || shopName.isEmpty() ? shopName : shopName + " (" + area + ")";
	}
}
