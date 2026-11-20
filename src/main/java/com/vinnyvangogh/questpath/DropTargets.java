package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which NPCs and objects in the scene are worth outlining, because they are
 * the only place an item the route still needs comes from.
 *
 * <p>Pure: it reads the {@link QuestItems} the plugin already builds and
 * answers with names. Nothing here touches the client, so the "still need"
 * and source-lookup rules are testable without one. The scene half lives in
 * {@link DropTargetOverlay}.
 *
 * <p>The source data is the bundled quest graph's own {@code sources} strings
 * ({@code "Drop: Imp (5/128)"}), best source first, which come from the OSRS
 * wiki's quest requirement tables and drop tables --
 * https://oldschool.runescape.wiki/ . The drops that lead the list are the
 * item's main sources and are outlined; the list stops at the first source
 * that is not a drop, because once a shop is at least as good as a monster,
 * buying is the better answer. A drop from a monster's junk table ("Drop:
 * Yama#Junk (3/11)") is never a target: the wiki files it under junk because
 * it is not what you kill that monster for. The name is matched against NPC
 * and object names in the scene, so a source that is a world object ("Drop:
 * Cadavaberry bush (Always)") is highlighted the same way as a monster.
 */
final class DropTargets
{
	private static final String DROP_PREFIX = "Drop:";
	private static final String JUNK_TABLE = "junk";

	private DropTargets()
	{
	}

	/** One source worth outlining, and what it gives. */
	static final class Target
	{
		private final String sourceName;
		private final String itemName;
		private final long outstanding;

		Target(String sourceName, String itemName, long outstanding)
		{
			this.sourceName = sourceName;
			this.itemName = itemName;
			this.outstanding = outstanding;
		}

		/** The NPC or object's name as the wiki gives it, e.g. "Imp". */
		String sourceName()
		{
			return sourceName;
		}

		/** The item still needed, e.g. "White bead". */
		String itemName()
		{
			return itemName;
		}

		/** How many more are needed; always above zero. */
		long outstanding()
		{
			return outstanding;
		}

		/** The text drawn beside the outline: "drops White bead". */
		String label()
		{
			return "drops " + itemName;
		}
	}

	/**
	 * The names that drop an item as its main sources: the leading drops
	 * before the first source of any other kind (a shop), junk tables left
	 * out; empty when the best source is not a drop, or there is none.
	 *
	 * <p>"Drop: Yama#Junk (3/11)" names "Yama" on its junk table: the wiki's
	 * table anchor and the rate are not part of the creature's name, and a
	 * junk-table drop is skipped rather than outlined.
	 */
	static List<String> dropOnlyNames(List<String> sources)
	{
		if (sources == null)
		{
			return Collections.emptyList();
		}
		Map<String, String> names = new LinkedHashMap<>();
		for (String source : sources)
		{
			if (source == null || !source.startsWith(DROP_PREFIX))
			{
				break;
			}
			String name = source.substring(DROP_PREFIX.length());
			int cut = name.indexOf(" (");
			if (cut >= 0)
			{
				name = name.substring(0, cut);
			}
			cut = name.indexOf('#');
			if (cut >= 0)
			{
				if (JUNK_TABLE.equalsIgnoreCase(name.substring(cut + 1).trim()))
				{
					continue;
				}
				name = name.substring(0, cut);
			}
			name = name.trim();
			if (!name.isEmpty())
			{
				names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
			}
		}
		return new ArrayList<>(names.values());
	}

	/**
	 * Every source to outline for what the route still needs.
	 *
	 * @param currentQuest restrict to items this quest needs, or null for
	 *     every quest the list covers
	 */
	static List<Target> targets(List<QuestItems.Total> totals, String currentQuest)
	{
		if (totals == null)
		{
			return Collections.emptyList();
		}
		List<Target> out = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.covered() || total.outstanding() <= 0)
			{
				continue;
			}
			if (currentQuest != null && !mentions(total.questNames(), currentQuest))
			{
				continue;
			}
			for (String name : dropOnlyNames(total.sources()))
			{
				out.add(new Target(name, total.name(), total.outstanding()));
			}
		}
		return out;
	}

	/**
	 * "drops from Imps" for the item's row on the needs list, or null when the
	 * item is not drop-only.
	 */
	static String dropsFrom(List<String> sources)
	{
		List<String> names = dropOnlyNames(sources);
		if (names.isEmpty())
		{
			return null;
		}
		StringBuilder out = new StringBuilder("drops from ");
		for (int i = 0; i < names.size() && i < 3; i++)
		{
			if (i > 0)
			{
				out.append(i == names.size() - 1 || i == 2 ? " or " : ", ");
			}
			out.append(plural(names.get(i)));
		}
		if (names.size() > 3)
		{
			out.append(" and more");
		}
		return out.toString();
	}

	private static String plural(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith("s") ? name : name + "s";
	}

	private static boolean mentions(List<String> quests, String quest)
	{
		for (String candidate : quests)
		{
			if (candidate.equalsIgnoreCase(quest))
			{
				return true;
			}
		}
		return false;
	}
}
