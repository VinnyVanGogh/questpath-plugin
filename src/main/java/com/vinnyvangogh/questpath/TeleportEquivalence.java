package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Equivalence between teleport tablets and spell runes.
 *
 * <p>A player shopping for quest requirements or recommended teleports can
 * satisfy them with either teleport tablets or the spell runes (assuming the
 * required Magic level is met). In shops, tablets are not sold, but rune shops
 * such as Aubury's Rune Shop in Varrock sell the component runes.
 */
public final class TeleportEquivalence
{
	private TeleportEquivalence()
	{
	}

	public static final class RuneIngredient
	{
		private final int itemId;
		private final String name;
		private final int quantity;

		public RuneIngredient(int itemId, String name, int quantity)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
		}

		public int itemId()
		{
			return itemId;
		}

		public String name()
		{
			return name;
		}

		public int quantity()
		{
			return quantity;
		}
	}

	public static final class Recipe
	{
		private final String name;
		private final int tabletItemId;
		private final String tabletName;
		private final int magicLevel;
		private final List<RuneIngredient> runes;

		public Recipe(String name, int tabletItemId, String tabletName, int magicLevel, List<RuneIngredient> runes)
		{
			this.name = name;
			this.tabletItemId = tabletItemId;
			this.tabletName = tabletName;
			this.magicLevel = magicLevel;
			this.runes = runes == null ? Collections.emptyList() : Collections.unmodifiableList(runes);
		}

		public String name()
		{
			return name;
		}

		public int tabletItemId()
		{
			return tabletItemId;
		}

		public String tabletName()
		{
			return tabletName;
		}

		public int magicLevel()
		{
			return magicLevel;
		}

		public List<RuneIngredient> runes()
		{
			return runes;
		}
	}

	public static final Recipe CAMELOT = new Recipe(
		"Camelot",
		8010,
		"Camelot teleport",
		45,
		List.of(
			new RuneIngredient(ItemID.AIRRUNE, "Air rune", 5),
			new RuneIngredient(ItemID.LAWRUNE, "Law rune", 1)
		)
	);

	public static final Recipe VARROCK = new Recipe(
		"Varrock",
		8007,
		"Varrock teleport",
		25,
		List.of(
			new RuneIngredient(ItemID.FIRERUNE, "Fire rune", 1),
			new RuneIngredient(ItemID.LAWRUNE, "Law rune", 1),
			new RuneIngredient(ItemID.AIRRUNE, "Air rune", 3)
		)
	);

	public static final Recipe LUMBRIDGE = new Recipe(
		"Lumbridge",
		8008,
		"Lumbridge teleport",
		31,
		List.of(
			new RuneIngredient(ItemID.EARTHRUNE, "Earth rune", 1),
			new RuneIngredient(ItemID.LAWRUNE, "Law rune", 1),
			new RuneIngredient(ItemID.AIRRUNE, "Air rune", 3)
		)
	);

	public static final Recipe FALADOR = new Recipe(
		"Falador",
		8009,
		"Falador teleport",
		37,
		List.of(
			new RuneIngredient(ItemID.WATERRUNE, "Water rune", 1),
			new RuneIngredient(ItemID.LAWRUNE, "Law rune", 1),
			new RuneIngredient(ItemID.AIRRUNE, "Air rune", 3)
		)
	);

	public static final Recipe ARDOUGNE = new Recipe(
		"Ardougne",
		8011,
		"Ardougne teleport",
		51,
		List.of(
			new RuneIngredient(ItemID.WATERRUNE, "Water rune", 2),
			new RuneIngredient(ItemID.LAWRUNE, "Law rune", 2)
		)
	);

	public static final Recipe DRAYNOR = new Recipe(
		"Draynor",
		8013,
		"Draynor manor teleport",
		0,
		Collections.emptyList()
	);

	private static final List<Recipe> ALL_RECIPES = List.of(
		CAMELOT,
		VARROCK,
		LUMBRIDGE,
		FALADOR,
		ARDOUGNE,
		DRAYNOR
	);

	public static List<Recipe> recipes()
	{
		return ALL_RECIPES;
	}

	public static Recipe findRecipe(int itemId, String name)
	{
		if (itemId > 0)
		{
			for (Recipe recipe : ALL_RECIPES)
			{
				if (recipe.tabletItemId() == itemId)
				{
					return recipe;
				}
			}
			if (itemId == 19615) // Draynor manor teleport tablet variant
			{
				return DRAYNOR;
			}
		}
		if (name != null && !name.isEmpty())
		{
			String lower = name.toLowerCase().trim();
			for (Recipe recipe : ALL_RECIPES)
			{
				String rName = recipe.name().toLowerCase();
				if (lower.equals(recipe.tabletName().toLowerCase())
					|| lower.contains(rName + " teleport")
					|| lower.contains(rName + " tab")
					|| lower.equals(rName))
				{
					return recipe;
				}
			}
			if (lower.contains("draynor"))
			{
				return DRAYNOR;
			}
		}
		return null;
	}

	public static Recipe findRecipe(String name)
	{
		return findRecipe(0, name);
	}

	public static boolean isTeleport(int itemId, String name)
	{
		return findRecipe(itemId, name) != null;
	}

	public static boolean isTeleport(String name)
	{
		return findRecipe(0, name) != null;
	}

	public static long tabletsHeld(Recipe recipe, Map<String, Long> held)
	{
		if (recipe == null || held == null || held.isEmpty())
		{
			return 0L;
		}
		long count = 0L;
		String base = recipe.name().toLowerCase();
		for (Map.Entry<String, Long> entry : held.entrySet())
		{
			String key = entry.getKey();
			if (key == null)
			{
				continue;
			}
			String lower = key.toLowerCase().trim();
			if (lower.equals(recipe.tabletName().toLowerCase())
				|| lower.equals(base + " teleport")
				|| lower.equals(base + " teleport tablet")
				|| lower.equals(base + " tablet")
				|| lower.equals(base + " tab")
				|| (recipe == DRAYNOR && lower.contains("draynor")
					&& (lower.contains("teleport") || lower.contains("tablet") || lower.contains("tab"))))
			{
				count += entry.getValue() == null ? 0L : entry.getValue();
			}
		}
		return count;
	}

	public static long runesToCasts(Recipe recipe, Map<String, Long> held)
	{
		if (recipe == null || recipe.runes().isEmpty() || held == null || held.isEmpty())
		{
			return 0L;
		}
		long maxCasts = Long.MAX_VALUE;
		for (RuneIngredient rune : recipe.runes())
		{
			long owned = held.getOrDefault(rune.name().toLowerCase(), 0L);
			long casts = owned / rune.quantity();
			if (casts < maxCasts)
			{
				maxCasts = casts;
			}
		}
		return maxCasts == Long.MAX_VALUE ? 0L : maxCasts;
	}

	public static long equivalentCasts(Recipe recipe, Map<String, Long> held, int magicLevel)
	{
		if (recipe == null || held == null)
		{
			return 0L;
		}
		long tablets = tabletsHeld(recipe, held);
		long runeCasts = (magicLevel >= recipe.magicLevel()) ? runesToCasts(recipe, held) : 0L;
		return tablets + runeCasts;
	}

	public static long equivalentCasts(Recipe recipe, Map<String, Long> held)
	{
		return equivalentCasts(recipe, held, 99);
	}

	public static boolean isCovered(Recipe recipe, int quantity, Map<String, Long> held, int magicLevel)
	{
		if (recipe == null)
		{
			return false;
		}
		return equivalentCasts(recipe, held, magicLevel) >= quantity;
	}

	public static boolean isCovered(Recipe recipe, int quantity, Map<String, Long> held)
	{
		return isCovered(recipe, quantity, held, 99);
	}

	public static boolean isCovered(int itemId, String name, int quantity, Map<String, Long> held, int magicLevel)
	{
		Recipe recipe = findRecipe(itemId, name);
		if (recipe != null)
		{
			return isCovered(recipe, quantity, held, magicLevel);
		}
		if (name == null || held == null)
		{
			return false;
		}
		return held.getOrDefault(name.toLowerCase(), 0L) >= quantity;
	}

	public static boolean isCovered(int itemId, String name, int quantity, Map<String, Long> held)
	{
		return isCovered(itemId, name, quantity, held, 99);
	}

	public static boolean isCovered(String name, int quantity, Map<String, Long> held, int magicLevel)
	{
		return isCovered(0, name, quantity, held, magicLevel);
	}

	public static boolean isCovered(String name, int quantity, Map<String, Long> held)
	{
		return isCovered(0, name, quantity, held, 99);
	}

	public static List<RuneIngredient> decomposeToRunes(Recipe recipe, int quantity)
	{
		if (recipe == null || recipe.runes().isEmpty() || quantity <= 0)
		{
			return Collections.emptyList();
		}
		List<RuneIngredient> out = new ArrayList<>(recipe.runes().size());
		for (RuneIngredient rune : recipe.runes())
		{
			out.add(new RuneIngredient(rune.itemId(), rune.name(), rune.quantity() * quantity));
		}
		return out;
	}

	public static List<RuneIngredient> decomposeToRunes(int itemId, String name, int quantity)
	{
		Recipe recipe = findRecipe(itemId, name);
		return recipe == null ? Collections.emptyList() : decomposeToRunes(recipe, quantity);
	}

	public static List<String> runeShopNames()
	{
		return List.of(
			"Aubury's Rune Shop.",
			"Betty's Magic Emporium",
			"The Runic Emporium",
			"Baba Yaga's Magic Shop.",
			"Magic Guild Store (Runes and Staves)",
			"Void Knight Magic Store"
		);
	}
}
