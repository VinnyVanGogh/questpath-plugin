package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;

/**
 * Where to go next for an item, when making it is one of the ways to get it.
 *
 * <p>Board, 2026-11-14: Molten glass sent a low-level Group Ironman to a boss,
 * because the route knew two ways to get an item -- buy it, kill for it --
 * and Molten glass is neither sold nor sensibly killed for. It is made: a
 * Bucket of sand and a Soda ash at a furnace. So an item now has four
 * sources, tried in this order:
 *
 * <ol>
 * <li>held already -- not a stop at all; {@link QuestItems.Total#covered()}
 *     and, for an ingredient, {@link RouteSources#held} decide that;</li>
 * <li>a shop that sells it;</li>
 * <li>a recipe the account has the levels for -- each missing ingredient by
 *     its own best source, recursively, then the nearest facility;</li>
 * <li>a practical drop -- see {@link DropPracticality}.</li>
 * </ol>
 *
 * <p><b>One leg at a time.</b> The route is one stop per item, so a made
 * item's stop is the first leg it still needs: the first missing
 * ingredient's source, and once all are held, the furnace. Picking up the
 * sand changes what is held, which changes the leg -- the same "arrive and
 * the next stop moves on" every other stop already has.
 */
final class MakeChain
{
	/**
	 * How deep a recipe may nest. Real chains are two or three long; this
	 * exists so a cycle in the data (A from B, B from A) ends, not to model a
	 * real limit.
	 */
	static final int MAX_DEPTH = 4;

	private MakeChain()
	{
	}

	/**
	 * The next leg for {@code count} of an item, by the order in the class
	 * doc, or null when nothing practical remains.
	 */
	static ShopRouteStops.Destination sourceOf(String item, long count, List<String> shopNames,
		List<String> dropSourceNames, Function<String, WorldPoint> shopLocator, RouteSources sources,
		WorldPoint from, int depth)
	{
		ShopRouteStops.Destination shop = ShopRouteStops.nearestOf(shopNames, shopLocator, from,
			ShopRouteStops.Destination.Kind.SHOP);
		if (shop != null)
		{
			return shop;
		}
		ShopRouteStops.Destination made = depth < MAX_DEPTH
			? nextLeg(item, count, shopLocator, sources, from, depth)
			: null;
		if (made != null)
		{
			return made;
		}
		return ShopRouteStops.nearestOf(dropSourceNames, sources, from,
			ShopRouteStops.Destination.Kind.MONSTER);
	}

	/**
	 * The next leg of making {@code count} of an item, or null when the
	 * account cannot make it -- no recipe, a level short, an ingredient with
	 * no practical source, or no placed facility.
	 */
	static ShopRouteStops.Destination nextLeg(String item, long count,
		Function<String, WorldPoint> shopLocator, RouteSources sources, WorldPoint from, int depth)
	{
		for (QuestGraph.Recipe recipe : sources.recipesFor(item))
		{
			if (!missingLevels(recipe, sources).isEmpty())
			{
				continue;
			}
			ShopRouteStops.Destination leg = legOf(recipe, Math.max(1L, count), shopLocator, sources, from, depth);
			if (leg != null)
			{
				return leg;
			}
		}
		return null;
	}

	private static ShopRouteStops.Destination legOf(QuestGraph.Recipe recipe, long count,
		Function<String, WorldPoint> shopLocator, RouteSources sources, WorldPoint from, int depth)
	{
		long makes = (count + recipe.outputQuantity() - 1) / recipe.outputQuantity();
		for (QuestGraph.Material material : recipe.materials())
		{
			long shortBy = material.quantity() * makes - sources.held(material.name());
			if (shortBy <= 0)
			{
				continue;
			}
			ShopRouteStops.Destination leg = sourceOf(material.name(), shortBy, material.shopNames(),
				material.dropSourceNames(), shopLocator, sources, from, depth + 1);
			// An ingredient nothing practical provides breaks this recipe;
			// the caller tries the next recipe, then the item's own drops.
			return leg == null ? null : leg.forIngredient(material.name());
		}
		if (recipe.facility().isEmpty())
		{
			// Made from the inventory, anywhere. Everything is held, so the
			// stop is wherever the player stands; without a position there is
			// nowhere to say.
			return from == null ? null
				: new ShopRouteStops.Destination("", from, 0, ShopRouteStops.Destination.Kind.FACILITY,
					java.util.Collections.singletonList(from), "", makeText(recipe));
		}
		return ShopRouteStops.nearestPoint(recipe.facility(), sources.facilities(recipe.facility()), from,
			makeText(recipe));
	}

	/** "use Bucket of sand + Soda ash". */
	static String makeText(QuestGraph.Recipe recipe)
	{
		List<String> names = new ArrayList<>();
		for (QuestGraph.Material material : recipe.materials())
		{
			names.add(material.quantity() > 1 ? material.quantity() + " x " + material.name() : material.name());
		}
		return "use " + String.join(" + ", names);
	}

	/** "30 Crafting, 20 Smithing" short of this recipe, or empty when the account has them. */
	static String missingLevels(QuestGraph.Recipe recipe, RouteSources sources)
	{
		List<String> missing = new ArrayList<>();
		for (QuestGraph.Requirement skill : recipe.skills())
		{
			if (sources.level(skill.skill()) < skill.level())
			{
				missing.add(skill.level() + " " + skill.skill());
			}
		}
		return String.join(", ", missing);
	}

	/**
	 * Why an item with a recipe cannot be made yet -- "needs 30 Crafting" --
	 * or empty when it has no recipe or one the account can do.
	 */
	static String cannotMakeYet(String item, RouteSources sources)
	{
		String first = "";
		for (QuestGraph.Recipe recipe : sources.recipesFor(item))
		{
			String missing = missingLevels(recipe, sources);
			if (missing.isEmpty())
			{
				return "";
			}
			if (first.isEmpty())
			{
				first = "needs " + missing;
			}
		}
		return first;
	}
}
