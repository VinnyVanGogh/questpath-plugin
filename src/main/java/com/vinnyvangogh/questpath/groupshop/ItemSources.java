package com.vinnyvangogh.questpath.groupshop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the player looking at the list can get an item: the Grand Exchange
 * for an account that has one, otherwise the shops they can be served at and
 * the monsters they can reach.
 *
 * <p>Judged for the <em>viewer</em>, not for the member short of it -- the
 * viewer is the one about to walk somewhere. A shop the viewer cannot be
 * served at is never listed, but it is counted in {@link
 * Options#unreachableShops()}, so "no shop" and "no shop you can use" stay
 * different answers.
 */
public final class ItemSources
{
	private ItemSources()
	{
	}

	/** What the host plugin knows about where things come from. */
	public interface Lookup
	{
		/** Shops that sell this item, best first. */
		List<String> shops(String item);

		/** Whether the viewer can walk into this shop and be served. */
		boolean shopUsable(String shop);

		/** Monsters the viewer can reach that drop this item, best first. */
		List<String> drops(String item);

		/** Whether the Grand Exchange trades this item. */
		boolean onGrandExchange(int itemId, String item);
	}

	public enum Kind
	{
		GRAND_EXCHANGE,
		SHOP,
		DROP
	}

	/** One place to get an item. */
	public static final class Source
	{
		private final Kind kind;
		private final String name;

		Source(Kind kind, String name)
		{
			this.kind = kind;
			this.name = name;
		}

		public Kind kind()
		{
			return kind;
		}

		/** The shop or monster name; "Grand Exchange" for that kind. */
		public String name()
		{
			return name;
		}

		/** "GE", "Shop: Rommik's Crafty Supplies", "Drop: Goblin". */
		public String describe()
		{
			switch (kind)
			{
				case GRAND_EXCHANGE:
					return "GE";
				case SHOP:
					return "Shop: " + name;
				case DROP:
				default:
					return "Drop: " + name;
			}
		}
	}

	/** Every usable source, best first, and how many shops were left out. */
	public static final class Options
	{
		private final List<Source> sources;
		private final int unreachableShops;

		Options(List<Source> sources, int unreachableShops)
		{
			this.sources = Collections.unmodifiableList(sources);
			this.unreachableShops = unreachableShops;
		}

		public List<Source> sources()
		{
			return sources;
		}

		/** Shops that sell it but would not serve the viewer. */
		public int unreachableShops()
		{
			return unreachableShops;
		}

		/** Usable shop names only, for routing. */
		public List<String> shops()
		{
			List<String> out = new ArrayList<>();
			for (Source source : sources)
			{
				if (source.kind() == Kind.SHOP)
				{
					out.add(source.name());
				}
			}
			return out;
		}
	}

	/**
	 * @param viewer the account reading the list. Only {@link
	 *     AccountKind#NORMAL} is offered the Grand Exchange; an ironman of
	 *     either kind, or an account whose type is not known, gets drops
	 *     instead.
	 */
	public static Options forViewer(AccountKind viewer, String item, int itemId, Lookup lookup)
	{
		List<Source> sources = new ArrayList<>();
		if (lookup == null || item == null)
		{
			return new Options(sources, 0);
		}
		boolean grandExchange = viewer != null && viewer.canUseGrandExchange();
		if (grandExchange && lookup.onGrandExchange(itemId, item))
		{
			sources.add(new Source(Kind.GRAND_EXCHANGE, "Grand Exchange"));
		}
		int unreachable = 0;
		for (String shop : lookup.shops(item))
		{
			if (lookup.shopUsable(shop))
			{
				sources.add(new Source(Kind.SHOP, shop));
			}
			else
			{
				unreachable++;
			}
		}
		if (!grandExchange)
		{
			for (String drop : lookup.drops(item))
			{
				sources.add(new Source(Kind.DROP, drop));
			}
		}
		return new Options(sources, unreachable);
	}
}
