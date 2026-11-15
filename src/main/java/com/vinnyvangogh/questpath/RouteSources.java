package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;

/**
 * Everything the shop route consults beyond shops: where a monster is and
 * whether this account may be sent there, how an item is made, where a
 * facility stands, and what the account and its group already hold.
 *
 * <p>Used where a bare drop locator used to be passed, and still is one --
 * {@link #apply} answers "where is this monster" -- so every caller that only
 * ever wanted a drop tile keeps working. The difference is that the answer is
 * now filtered by {@link DropPracticality} as well as by {@link
 * DropRegionPolicy}: a boss or a Slayer-locked monster resolves to nowhere,
 * in the one place every route, label and group list reads.
 *
 * <p>Pure apart from what {@link Account} reports; tests build one from a
 * parsed graph with {@link Index#of} and plain maps.
 */
final class RouteSources implements Function<String, WorldPoint>
{
	/** What the account is and has, as of the last refresh. */
	interface Account
	{
		/** Real level in a skill, by name, any case. */
		int level(String skill);

		int combatLevel();

		/** How many of an item the account holds, bank included; any case. */
		long held(String item);

		/** "Name (count)" for each group member holding this; empty off a group. */
		List<String> groupHolders(String item);
	}

	/** An account that has nothing and is in no group: for drop-only callers. */
	static final Account NO_ACCOUNT = new Account()
	{
		@Override
		public int level(String skill)
		{
			return "hitpoints".equalsIgnoreCase(skill) ? 10 : 1;
		}

		@Override
		public int combatLevel()
		{
			return 3;
		}

		@Override
		public long held(String item)
		{
			return 0L;
		}

		@Override
		public List<String> groupHolders(String item)
		{
			return Collections.emptyList();
		}
	};

	/** The graph's recipe, facility and drop-gate tables, indexed by lower-cased name. */
	static final class Index
	{
		static final Index EMPTY = new Index(Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap());

		private final Map<String, List<QuestGraph.Recipe>> recipes;
		private final Map<String, List<WorldPoint>> facilities;
		private final Map<String, QuestGraph.DropSource> dropSources;

		private Index(Map<String, List<QuestGraph.Recipe>> recipes, Map<String, List<WorldPoint>> facilities,
			Map<String, QuestGraph.DropSource> dropSources)
		{
			this.recipes = recipes;
			this.facilities = facilities;
			this.dropSources = dropSources;
		}

		static Index of(QuestGraph graph)
		{
			if (graph == null)
			{
				return EMPTY;
			}
			Map<String, List<QuestGraph.Recipe>> recipes = new HashMap<>();
			for (QuestGraph.Recipe recipe : graph.recipes())
			{
				recipes.computeIfAbsent(recipe.product().toLowerCase(), k -> new ArrayList<>()).add(recipe);
			}
			Map<String, List<WorldPoint>> facilities = new HashMap<>();
			for (QuestGraph.Shop facility : graph.facilities())
			{
				facilities.computeIfAbsent(facility.name().toLowerCase(), k -> new ArrayList<>())
					.add(new WorldPoint(facility.x(), facility.y(), facility.plane()));
			}
			Map<String, QuestGraph.DropSource> dropSources = new HashMap<>();
			for (QuestGraph.DropSource source : graph.dropSources())
			{
				dropSources.putIfAbsent(source.name().toLowerCase(), source);
			}
			return new Index(recipes, facilities, dropSources);
		}
	}

	private final Supplier<Index> index;
	private final Function<String, WorldPoint> drops;
	private final Account account;

	/**
	 * @param drops resolves a monster to a tile, already filtered by
	 *     {@link DropRegionPolicy}; this adds {@link DropPracticality}
	 */
	RouteSources(Index index, Function<String, WorldPoint> drops, Account account)
	{
		this(() -> index, drops, account);
	}

	/**
	 * Reading the index through a supplier, for a host whose graph can be
	 * replaced (a live fetch) after this was handed to a panel or overlay.
	 */
	RouteSources(Supplier<Index> index, Function<String, WorldPoint> drops, Account account)
	{
		this.index = index;
		this.drops = drops;
		this.account = account == null ? NO_ACCOUNT : account;
	}

	private Index index()
	{
		Index current = index.get();
		return current == null ? Index.EMPTY : current;
	}

	/** Drops only, gated by nothing new: what every caller had before recipes. */
	static RouteSources dropsOnly(Function<String, WorldPoint> drops)
	{
		return drops instanceof RouteSources
			? (RouteSources) drops
			: new RouteSources(Index.EMPTY, drops, NO_ACCOUNT);
	}

	/** Where to kill this monster, or null when it is unplaced, off limits, or impractical. */
	@Override
	public WorldPoint apply(String monster)
	{
		if (monster == null || !dropBlockReason(monster).isEmpty())
		{
			return null;
		}
		return drops.apply(monster);
	}

	/** Why this monster is no route stop for this account -- "boss, needs 93 Slayer" -- or empty. */
	String dropBlockReason(String monster)
	{
		if (monster == null)
		{
			return "";
		}
		return DropPracticality.blockReason(index().dropSources.get(monster.toLowerCase()),
			account.level("Slayer"), account.combatLevel());
	}

	/** Every recipe that makes this item, in the export's order. */
	List<QuestGraph.Recipe> recipesFor(String product)
	{
		if (product == null)
		{
			return Collections.emptyList();
		}
		return index().recipes.getOrDefault(product.toLowerCase(), Collections.emptyList());
	}

	/** Every placed facility by this name. */
	List<WorldPoint> facilities(String name)
	{
		if (name == null)
		{
			return Collections.emptyList();
		}
		return index().facilities.getOrDefault(name.toLowerCase(), Collections.emptyList());
	}

	int level(String skill)
	{
		return account.level(skill);
	}

	long held(String item)
	{
		return account.held(item);
	}

	List<String> groupHolders(String item)
	{
		List<String> holders = account.groupHolders(item);
		return holders == null ? Collections.emptyList() : holders;
	}

	/**
	 * One display source with its block reason beside it when it names a
	 * dropper this account will not be sent to: "Drop: Thermonuclear smoke
	 * devil (1/30) -- boss, needs 93 Slayer". Anything else comes back as it
	 * went in.
	 */
	String annotate(String source)
	{
		if (source == null || !source.startsWith("Drop: "))
		{
			return source;
		}
		String rest = source.substring("Drop: ".length());
		int paren = rest.indexOf(" (");
		String monster = paren < 0 ? rest : rest.substring(0, paren);
		String reason = dropBlockReason(monster.trim());
		return reason.isEmpty() ? source : source + " -- " + reason;
	}

	/** {@link #annotate} over a whole list. */
	List<String> annotate(List<String> sources)
	{
		if (sources == null || sources.isEmpty())
		{
			return sources;
		}
		List<String> out = new ArrayList<>(sources.size());
		for (String source : sources)
		{
			out.add(annotate(source));
		}
		return out;
	}
}
