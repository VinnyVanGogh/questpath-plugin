package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

/**
 * What questpath answers a push with.
 *
 * Only the parts the plugin acts on are modelled. Gson ignores the rest, which
 * means an older plugin keeps working against a newer questpath — the counts
 * it does not know about simply do not arrive.
 */
class PushResponse
{
	@SerializedName("player")
	private String player;

	@SerializedName("unmatched")
	private List<String> unmatched;

	@SerializedName("build_rules")
	private List<BuildRule> buildRules;

	@SerializedName("constraints")
	private List<PlayerConstraint> constraints;

	@SerializedName("route_order")
	private List<String> routeOrder;

	@SerializedName("partial_runs")
	private List<PartialRun> partialRuns;

	@SerializedName("known_items")
	private java.util.Map<String, Long> knownItems;

	/**
	 * Per-account diary routing data, or {@code null} when this server
	 * predates it. {@code null} and "present but empty" are genuinely
	 * different facts here -- see {@link #hasDiary()} -- unlike most of the
	 * other fields on this class, which normalise an absent key to the same
	 * empty answer a real empty one would give. A questpath old enough to
	 * not send this field cannot support diary routing at all, which is a
	 * different claim from "this account has no diary tiers targeted," and
	 * the panel and overlay must be able to tell the two apart to show
	 * "Diary route needs a questpath sync" only for the first one.
	 */
	@SerializedName("diary")
	private Diary diary;

	/**
	 * Whether this push response carries diary routing data at all -- see
	 * {@link #diary}'s own doc for why this is checked separately from
	 * whether {@link #diary()} returns something with content in it.
	 */
	boolean hasDiary()
	{
		return diary != null;
	}

	/**
	 * The diary block, or null when {@link #hasDiary()} is false. Callers
	 * must check {@link #hasDiary()} first rather than treating a null
	 * return as "no diary data," because that is exactly the ambiguity this
	 * pair of methods exists to remove.
	 */
	Diary diary()
	{
		return diary;
	}

	/**
	 * Per-account diary routing data: which tiers are targeted and how,
	 * which outstanding tasks the account can currently act on, which it
	 * cannot and why, and how fresh each tier's per-task progress is.
	 *
	 * <p>Computed entirely server-side by {@code internal/planner}'s
	 * actionability rules -- see {@code docs/research/diary-build-brief.md}
	 * D4 in the {@code osrs} repo. This plugin never re-derives a verdict;
	 * it only reads the one questpath already worked out, the same
	 * discipline {@link #buildRules()} and {@link #constraints()} already
	 * follow for quests.
	 */
	static class Diary
	{
		@SerializedName("targets")
		private List<Target> targets;

		@SerializedName("actionable")
		private List<Actionable> actionable;

		@SerializedName("outstanding")
		private List<Outstanding> outstanding;

		@SerializedName("task_sync")
		private List<TaskSync> taskSync;

		/** Every diary tier the account has targeted, with its pin/defer flags. */
		List<Target> targets()
		{
			return targets == null ? Collections.emptyList() : targets;
		}

		/**
		 * Every outstanding task, on a targeted tier or not, that this
		 * account can currently act on -- questpath's own verdict, per
		 * {@code (tier_id, ordinal)}. {@link DiaryRouteStops} is the only
		 * place this plugin turns this list into a route; nothing else
		 * should re-derive actionability from it.
		 */
		List<Actionable> actionable()
		{
			return actionable == null ? Collections.emptyList() : actionable;
		}

		/**
		 * Every outstanding task this account cannot currently act on, with
		 * questpath's own reason -- a boostable-but-not-enough skill gap, an
		 * incomplete quest prerequisite, a permanent tier, and so on. Listed
		 * for the panel, never routed to.
		 */
		List<Outstanding> outstanding()
		{
			return outstanding == null ? Collections.emptyList() : outstanding;
		}

		/**
		 * How fresh each targeted tier's per-task progress is, one entry per
		 * tier. A tier absent from this list has never reported per-task
		 * progress at all, which the panel should render the same way a
		 * {@link TaskSync#state()} of {@code "none"} would.
		 */
		List<TaskSync> taskSync()
		{
			return taskSync == null ? Collections.emptyList() : taskSync;
		}

		/** One targeted diary tier and its pin/defer flags. */
		static class Target
		{
			@SerializedName("tier_id")
			private long tierId;

			@SerializedName("pinned")
			private boolean pinned;

			@SerializedName("deferred")
			private boolean deferred;

			long tierId()
			{
				return tierId;
			}

			/** Whether this tier's tasks win over every unpinned tier's tasks on the route. */
			boolean pinned()
			{
				return pinned;
			}

			/** Whether this tier's tasks are never nudged, only listed. */
			boolean deferred()
			{
				return deferred;
			}
		}

		/** One outstanding task this account can currently act on. */
		static class Actionable
		{
			@SerializedName("tier_id")
			private long tierId;

			@SerializedName("ordinal")
			private int ordinal;

			@SerializedName("warnings")
			private List<String> warnings;

			long tierId()
			{
				return tierId;
			}

			int ordinal()
			{
				return ordinal;
			}

			/**
			 * What questpath said to watch out for, never a reason this task
			 * is unreachable -- an unreachable task is {@link Outstanding},
			 * not this.
			 */
			List<String> warnings()
			{
				return warnings == null ? Collections.emptyList() : warnings;
			}
		}

		/** One outstanding task this account cannot currently act on. */
		static class Outstanding
		{
			@SerializedName("tier_id")
			private long tierId;

			@SerializedName("ordinal")
			private int ordinal;

			@SerializedName("status")
			private String status;

			@SerializedName("reason")
			private String reason;

			@SerializedName("deferred")
			private boolean deferred;

			long tierId()
			{
				return tierId;
			}

			int ordinal()
			{
				return ordinal;
			}

			/** questpath's own status word for this task -- shown verbatim, never parsed. */
			String status()
			{
				return status == null ? "" : status;
			}

			/** Why this task is not actionable, in questpath's own words. */
			String reason()
			{
				return reason == null ? "" : reason;
			}

			/** Whether this task's tier is deferred -- never nudged, only listed. */
			boolean deferred()
			{
				return deferred;
			}
		}

		/** How fresh one tier's per-task progress is. */
		static class TaskSync
		{
			@SerializedName("tier_id")
			private long tierId;

			@SerializedName("state")
			private String state;

			/**
			 * RFC3339, as questpath sends it -- kept as the raw string Gson
			 * gave it rather than an {@code Instant}, since no {@code Gson}
			 * instance this plugin is handed carries a date adapter. {@link
			 * #syncedAt()} is the safe accessor.
			 */
			@SerializedName("synced_at")
			private String syncedAt;

			long tierId()
			{
				return tierId;
			}

			/** {@code "ok"}, {@code "shape_mismatch"}, or {@code "none"} -- shown verbatim, never parsed into a switch that could fall through silently. */
			String state()
			{
				return state == null ? "" : state;
			}

			/**
			 * When this tier's per-task progress was last read, or null when
			 * it cannot be parsed -- an unparsable or missing timestamp is
			 * "unknown," never "just now" and never "never," so a panel that
			 * cannot show an age must say so rather than guessing one.
			 */
			java.time.Instant syncedAt()
			{
				if (syncedAt == null || syncedAt.isEmpty())
				{
					return null;
				}
				try
				{
					return java.time.Instant.parse(syncedAt);
				}
				catch (java.time.format.DateTimeParseException e)
				{
					return null;
				}
			}
		}
	}

	String player()
	{
		return player == null ? "" : player;
	}

	/**
	 * Quest names the plugin sent that questpath could not resolve.
	 *
	 * Worth surfacing rather than counting: a name the client uses and
	 * questpath does not is a real disagreement between the two, and naming
	 * them is what makes it fixable.
	 */
	List<String> unmatched()
	{
		return unmatched == null ? Collections.emptyList() : unmatched;
	}

	/**
	 * What this account has declared it will not do -- empty from a questpath
	 * older than the constraints field, which is the same thing as having
	 * declared nothing and is handled identically.
	 */
	List<PlayerConstraint> constraints()
	{
		return constraints == null ? Collections.emptyList() : constraints;
	}

	List<BuildRule> buildRules()
	{
		return buildRules == null ? Collections.emptyList() : buildRules;
	}

	/**
	 * The account's saved route from questpath, as quest names in route order.
	 *
	 * questpath always sends this field, even empty, so an empty list here
	 * means "no route saved" -- a null would only happen against a questpath
	 * old enough to predate this field, and is treated the same way rather
	 * than as a crash.
	 */
	/**
	 * What questpath already holds for this account, by lowercased item name.
	 *
	 * The client cannot see a bank until the player opens one, so until then
	 * every "do you have this" answer is about a pocket rather than an
	 * account. This is the fallback, and it turns that from "I can only see
	 * what you are carrying" into "as of your last sync" -- stale being a far
	 * smaller problem than blind, provided the panel says which it is.
	 *
	 * Empty means questpath holds nothing on record, which is different from
	 * the field not arriving; the server sends `{}` rather than dropping it
	 * precisely so the two stay distinguishable.
	 */
	java.util.Map<String, Long> knownItems()
	{
		return knownItems == null ? Collections.emptyMap() : knownItems;
	}

	List<String> routeOrder()
	{
		return routeOrder == null ? Collections.emptyList() : routeOrder;
	}

	/**
	 * The quests this account's build says to start and never finish.
	 *
	 * <p>Absent from the response for an account that has applied no build,
	 * and for a build with no such quests. Those are the same answer to the
	 * plugin -- there is no section to draw -- so questpath drops the field
	 * for both rather than distinguishing them on the wire, and both arrive
	 * here as an empty list.
	 *
	 * <p>That is the opposite of {@link #routeOrder()}, where `[]` and absent
	 * are genuinely different facts and questpath always sends the field.
	 */
	List<PartialRun> partialRuns()
	{
		return partialRuns == null ? Collections.emptyList() : partialRuns;
	}
}
