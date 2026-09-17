package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * questpath's per-account item plan, from the push response's {@code
 * item_plan}: which member of an "X or Y" requirement to target, and what can
 * be made from what the account already owns before it is sent to kill, buy
 * or gather anything.
 *
 * <p><b>questpath decides; this plugin renders.</b> The choice and the
 * allocation (owned items go to direct requirements first, recipes use only
 * what is left, tools are not consumed, build rules and levels respected) are
 * {@code internal/items.PlanItems} in the osrs repo. Nothing here re-derives
 * them -- the same discipline {@link PushResponse.Diary} follows for diary
 * verdicts. The one check made locally is {@link Item#makeQuantityGiven}:
 * whether the materials a step names are still in hand, because the plan is
 * as old as the last push and a make step whose horn has since been dropped
 * must not keep an item off the shopping trip.
 *
 * <p>Only requirements the quest graph cannot already express arrive: a real
 * choice, a make step, or a recipe the account may not perform. Anything else
 * is absent, and its graph line already is the plan. See RESEARCH.md section
 * 23 in the osrs repo for the contract.
 */
class ItemPlan
{
	@SerializedName("items")
	private List<Item> items;

	private transient Map<String, Item> byRequirement;

	/** Every decided requirement, in questpath's order. */
	List<Item> items()
	{
		return items == null ? Collections.emptyList() : items;
	}

	/**
	 * The decision for a graph line, matched case-insensitively on the name
	 * the graph gives it -- or null when questpath had nothing to add.
	 */
	Item forRequirement(String graphName)
	{
		if (graphName == null)
		{
			return null;
		}
		if (byRequirement == null)
		{
			Map<String, Item> index = new HashMap<>();
			for (Item item : items())
			{
				if (item != null && item.requirement() != null)
				{
					index.putIfAbsent(item.requirement().toLowerCase(), item);
				}
			}
			byRequirement = index;
		}
		return byRequirement.get(graphName.toLowerCase());
	}

	/** One decided requirement. */
	static class Item
	{
		@SerializedName("requirement")
		private String requirement;

		@SerializedName("members")
		private List<String> members;

		@SerializedName("target")
		private String target;

		@SerializedName("target_item_id")
		private int targetItemId;

		@SerializedName("choice")
		private String choice;

		@SerializedName("needed")
		private long needed;

		@SerializedName("owned")
		private long owned;

		@SerializedName("shortfall")
		private long shortfall;

		@SerializedName("make")
		private List<MakeStep> make;

		@SerializedName("make_blocked")
		private List<String> makeBlocked;

		/** The graph's name for this line -- the join key. */
		String requirement()
		{
			return requirement;
		}

		/** Every item that satisfies it, in the wiki's order. */
		List<String> members()
		{
			return members == null ? Collections.emptyList() : members;
		}

		/** The member to hold, make or get. Falls back to the requirement. */
		String target()
		{
			return target == null || target.isEmpty() ? requirement : target;
		}

		/** The target's game item id, or 0 when questpath holds none. */
		int targetItemId()
		{
			return targetItemId;
		}

		/** {@code only}, {@code held}, {@code make}, {@code reachable} or {@code unobtainable}, verbatim. */
		String choice()
		{
			return choice == null ? "" : choice;
		}

		/** Whether there was a real choice among two or more members. */
		boolean hasAlternatives()
		{
			return members().size() > 1;
		}

		/**
		 * Whether no member can be reached, held or made. The target is then
		 * for display only and must never be routed to.
		 */
		boolean unobtainable()
		{
			return "unobtainable".equals(choice);
		}

		List<MakeStep> make()
		{
			return make == null ? Collections.emptyList() : make;
		}

		/** Why a recipe the account holds everything for was not recommended. */
		List<String> makeBlocked()
		{
			return makeBlocked == null ? Collections.emptyList() : makeBlocked;
		}

		/**
		 * How many of the target the make steps cover, counting only steps
		 * whose tools and materials the account still holds.
		 *
		 * @param held current holdings by lowercased name
		 */
		long makeQuantityGiven(Map<String, Long> held)
		{
			long total = 0;
			for (MakeStep step : make())
			{
				if (step.stillPossible(held))
				{
					total += step.quantity();
				}
			}
			return total;
		}

		/** The instructions of the steps still possible, in order. */
		List<String> instructionsGiven(Map<String, Long> held)
		{
			List<String> out = new java.util.ArrayList<>();
			for (MakeStep step : make())
			{
				if (step.stillPossible(held) && !step.instruction().isEmpty())
				{
					out.add(step.instruction());
				}
			}
			return out;
		}
	}

	/** One make-from-owned step. */
	static class MakeStep
	{
		@SerializedName("quantity")
		private long quantity;

		@SerializedName("instruction")
		private String instruction;

		@SerializedName("tools")
		private List<String> tools;

		@SerializedName("materials")
		private List<Material> materials;

		@SerializedName("recipe")
		private String recipe;

		long quantity()
		{
			return quantity;
		}

		/** The step in questpath's words -- "use Pestle and mortar on Unicorn horn (in bank)". */
		String instruction()
		{
			return instruction == null ? "" : instruction;
		}

		List<String> tools()
		{
			return tools == null ? Collections.emptyList() : tools;
		}

		List<Material> materials()
		{
			return materials == null ? Collections.emptyList() : materials;
		}

		/**
		 * Whether every tool and material this step names is still held in
		 * the amount it names. Presence only -- the allocation was questpath's.
		 */
		boolean stillPossible(Map<String, Long> held)
		{
			if (held == null || quantity <= 0)
			{
				return false;
			}
			for (String tool : tools())
			{
				if (tool == null || held.getOrDefault(tool.toLowerCase(), 0L) < 1)
				{
					return false;
				}
			}
			for (Material material : materials())
			{
				if (material.name() == null
					|| held.getOrDefault(material.name().toLowerCase(), 0L) < material.quantity())
				{
					return false;
				}
			}
			return true;
		}
	}

	/** One consumed material and how many the step uses in total. */
	static class Material
	{
		@SerializedName("name")
		private String name;

		@SerializedName("quantity")
		private long quantity;

		String name()
		{
			return name;
		}

		long quantity()
		{
			return quantity;
		}
	}
}
