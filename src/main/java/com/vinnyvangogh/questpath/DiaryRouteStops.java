package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * The pure decision behind the diary route: which outstanding diary task is
 * worth walking to next, in what order, and where each one actually sends
 * you.
 *
 * <p>Copies the shape {@link ShopRouteStops} already proved out, for the same
 * reason: no client, no {@code Graphics2D}, no overlay -- just the graph's
 * account-agnostic diary tasks and the push response's per-account verdicts,
 * so the decision can be exercised directly in a test. See {@link
 * ShopRouteStops}'s own class doc for the report that made "one place decides
 * the destination" a rule here rather than a suggestion: an item sold in
 * three places was once labelled with a shop the player was not walking to,
 * because the label and the route read the same list by different rules.
 * {@link #destinationOf} is that one place for a diary task -- both the
 * overlay's label and the {@code ShortestPathBridge} call must go through it,
 * never compute their own nearest location.
 *
 * <p><b>A task is identified by {@code (tier_id, ordinal)}, never by any
 * id.</b> The Go ingest deletes and reinserts every tier's tasks on each
 * re-ingest, so a row id would not survive it -- see {@link
 * QuestGraph.DiaryTask}'s own class doc. Every join in this class is keyed on
 * that pair.
 *
 * <p><b>Distance is Chebyshev, not Euclidean.</b> OSRS movement is
 * eight-directional on a tile grid, so the number of steps between two tiles
 * is {@code max(|dx|, |dy|)}, not the straight-line distance {@link
 * ShopRouteStops} uses for its own, unrelated reasons. {@code
 * WorldPoint#distanceTo} already computes exactly this -- confirmed by {@code
 * javap -c} against the installed {@code runelite-api-1.12.37.jar}: same
 * plane, it returns {@code Math.max(Math.abs(dx), Math.abs(dy))}; different
 * planes, it returns {@code Integer.MAX_VALUE} outright. That second fact is
 * used deliberately here rather than worked around: sorting by that distance
 * places every cross-plane candidate after every same-plane one for free,
 * which is exactly {@link #stops}'s own ordering rule.
 */
final class DiaryRouteStops
{
	private DiaryRouteStops()
	{
	}

	/** Identifies a diary task the only way that survives a re-ingest -- see the class doc. */
	static final class TaskKey
	{
		private final long tierId;
		private final int ordinal;

		TaskKey(long tierId, int ordinal)
		{
			this.tierId = tierId;
			this.ordinal = ordinal;
		}

		long tierId()
		{
			return tierId;
		}

		int ordinal()
		{
			return ordinal;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof TaskKey))
			{
				return false;
			}
			TaskKey other = (TaskKey) o;
			return tierId == other.tierId && ordinal == other.ordinal;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(tierId, ordinal);
		}

		@Override
		public String toString()
		{
			return "(tier " + tierId + ", ordinal " + ordinal + ")";
		}
	}

	/**
	 * One outstanding, actionable, locatable diary task worth walking to: the
	 * graph task itself, the single location {@link #destinationOf} chose for
	 * it, how far away that is right now, whether its tier is pinned, and the
	 * warnings questpath attached to it.
	 */
	static final class Stop
	{
		private final QuestGraph.DiaryTask task;
		private final QuestGraph.Location destination;
		private final int distanceTiles;
		private final boolean pinned;
		private final List<String> warnings;

		Stop(QuestGraph.DiaryTask task, QuestGraph.Location destination, int distanceTiles,
			boolean pinned, List<String> warnings)
		{
			this.task = task;
			this.destination = destination;
			this.distanceTiles = distanceTiles;
			this.pinned = pinned;
			this.warnings = warnings == null ? List.of() : warnings;
		}

		/** The task itself: its diary, tier, text and every location candidate. */
		QuestGraph.DiaryTask task()
		{
			return task;
		}

		/** The single location {@link #destinationOf} chose -- never null for a {@code Stop}. */
		QuestGraph.Location destination()
		{
			return destination;
		}

		/**
		 * Tiles from the player to {@link #destination()} at the moment this
		 * list was built, or {@code Integer.MAX_VALUE} when the player's
		 * position was not known, or when the destination is on a different
		 * plane -- see the class doc for why that second case is folded into
		 * the same sentinel rather than reported separately.
		 */
		int distanceTiles()
		{
			return distanceTiles;
		}

		/** Whether this task's tier was pinned in the account's diary targets. */
		boolean pinned()
		{
			return pinned;
		}

		/**
		 * What questpath said to watch out for -- a boostable skill shortfall,
		 * an in-progress quest prerequisite, and the like. Never a reason this
		 * task is unreachable; an unreachable task is not a {@code Stop} at
		 * all.
		 */
		List<String> warnings()
		{
			return warnings;
		}
	}

	/**
	 * An actionable task the pushed graph and the pushed diary verdicts
	 * disagree about: questpath says {@code (tier_id, ordinal)} is
	 * actionable, but this plugin's own graph -- bundled or freshly fetched,
	 * either way possibly a different export than questpath's own -- carries
	 * no task at all under that key.
	 *
	 * <p>Never silently dropped. A graph/server mismatch is worth surfacing
	 * on the panel by name, the same way an unresolved quest name already is
	 * in {@link PushResponse#unmatched()} -- both are real disagreements
	 * between the two sides that the player can only notice if something
	 * says so.
	 */
	static final class Unmatched
	{
		private final long tierId;
		private final int ordinal;
		private final List<String> warnings;

		Unmatched(long tierId, int ordinal, List<String> warnings)
		{
			this.tierId = tierId;
			this.ordinal = ordinal;
			this.warnings = warnings == null ? List.of() : warnings;
		}

		long tierId()
		{
			return tierId;
		}

		int ordinal()
		{
			return ordinal;
		}

		List<String> warnings()
		{
			return warnings;
		}
	}

	/** Every graph task keyed by {@code (tier_id, ordinal)} -- built once per call, never cached. */
	private static Map<TaskKey, QuestGraph.DiaryTask> index(List<QuestGraph.DiaryTask> graphTasks)
	{
		Map<TaskKey, QuestGraph.DiaryTask> byKey = new HashMap<>();
		if (graphTasks != null)
		{
			for (QuestGraph.DiaryTask task : graphTasks)
			{
				byKey.put(new TaskKey(task.tierId(), task.ordinal()), task);
			}
		}
		return byKey;
	}

	/** Every tier id the account pinned, per {@link PushResponse.Diary#targets()}. */
	private static Set<Long> pinnedTierIds(PushResponse.Diary diary)
	{
		Set<Long> pinned = new HashSet<>();
		if (diary != null)
		{
			for (PushResponse.Diary.Target target : diary.targets())
			{
				if (target.pinned())
				{
					pinned.add(target.tierId());
				}
			}
		}
		return pinned;
	}

	/**
	 * The single location a task should be walked to and named for: the
	 * nearest to {@code from}, preferring a same-plane candidate over any
	 * other-plane one -- see the class doc for why sorting on {@code
	 * WorldPoint#distanceTo} already gives both for free.
	 *
	 * <p><b>This is the one place that decides.</b> Both the overlay's label
	 * and the {@code ShortestPathBridge} call must read this method, never
	 * compute their own nearest candidate -- see the class doc.
	 *
	 * @param from where the player is, or null before the client has a
	 *     position; every candidate is then equally "zero away" and the
	 *     first one the graph listed wins, the same null convention {@link
	 *     ShopRouteStops#destinationOf} uses
	 * @return the chosen location, or null when {@code task} carries none --
	 *     a location-unknown task must never be handed a guessed destination
	 */
	static QuestGraph.Location destinationOf(QuestGraph.DiaryTask task, WorldPoint from)
	{
		if (task == null)
		{
			return null;
		}
		QuestGraph.Location best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (QuestGraph.Location candidate : task.locations())
		{
			int distance = from == null ? 0 : from.distanceTo(new WorldPoint(candidate.x(), candidate.y(), candidate.plane()));
			if (best == null || distance < bestDistance)
			{
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * Every actionable, locatable task worth walking to, nearest and most
	 * urgent first.
	 *
	 * <p><b>Candidates.</b> Every entry in {@link PushResponse.Diary#actionable()}
	 * joined to the plugin's own graph by {@code (tier_id, ordinal)} -- an
	 * entry the graph does not recognise is left out of this list entirely
	 * and appears in {@link #unmatched} instead, and an entry the graph
	 * recognises but with no {@link QuestGraph.DiaryTask#locations()} is left
	 * out here and appears in {@link #locationUnknown} instead. Neither is
	 * silently dropped; each has its own list for exactly that reason.
	 *
	 * <p><b>Order.</b> Every task on a pinned tier sorts before every task on
	 * an unpinned one; within each of those two groups, nearest first by
	 * {@link Stop#distanceTiles()} -- which already sorts every cross-plane
	 * destination after every same-plane one, see the class doc. Ties fall
	 * back to {@code (tier_id, ordinal)} so the order is deterministic rather
	 * than dependent on {@code actionable()}'s own arrival order.
	 *
	 * @param graphTasks the plugin's own bundled or fetched graph's diary
	 *     tasks -- {@code QuestGraph#diaryTasks()}
	 * @param diary the push response's diary block, or null when the server
	 *     did not send one -- both are treated as "no candidates", never as
	 *     an error, because the caller (the overlay, the panel) is
	 *     responsible for saying "needs a questpath sync" rather than this
	 *     method inventing that message
	 * @param player where the player is, or null before the client has a
	 *     position
	 */
	static List<Stop> stops(List<QuestGraph.DiaryTask> graphTasks, PushResponse.Diary diary, WorldPoint player)
	{
		List<Stop> out = new ArrayList<>();
		if (diary == null)
		{
			return out;
		}
		Map<TaskKey, QuestGraph.DiaryTask> byKey = index(graphTasks);
		Set<Long> pinnedTiers = pinnedTierIds(diary);

		for (PushResponse.Diary.Actionable actionable : diary.actionable())
		{
			QuestGraph.DiaryTask task = byKey.get(new TaskKey(actionable.tierId(), actionable.ordinal()));
			if (task == null || task.locations().isEmpty())
			{
				// Unmatched or location-unknown -- see this method's own
				// class doc. Neither belongs in the routable list.
				continue;
			}
			QuestGraph.Location destination = destinationOf(task, player);
			int distance = player == null
				? 0
				: player.distanceTo(new WorldPoint(destination.x(), destination.y(), destination.plane()));
			out.add(new Stop(task, destination, distance, pinnedTiers.contains(task.tierId()),
				actionable.warnings()));
		}

		out.sort(Comparator
			.comparing(Stop::pinned).reversed()
			.thenComparingInt(Stop::distanceTiles)
			.thenComparingLong(stop -> stop.task().tierId())
			.thenComparingInt(stop -> stop.task().ordinal()));
		return out;
	}

	/**
	 * Every actionable task the account's server reported that this plugin's
	 * own graph does not recognise by {@code (tier_id, ordinal)} -- a
	 * graph/server mismatch, for the panel to say so rather than silently
	 * dropping the task. See {@link Unmatched}'s own class doc.
	 */
	static List<Unmatched> unmatched(List<QuestGraph.DiaryTask> graphTasks, PushResponse.Diary diary)
	{
		List<Unmatched> out = new ArrayList<>();
		if (diary == null)
		{
			return out;
		}
		Map<TaskKey, QuestGraph.DiaryTask> byKey = index(graphTasks);
		for (PushResponse.Diary.Actionable actionable : diary.actionable())
		{
			if (!byKey.containsKey(new TaskKey(actionable.tierId(), actionable.ordinal())))
			{
				out.add(new Unmatched(actionable.tierId(), actionable.ordinal(), actionable.warnings()));
			}
		}
		return out;
	}

	/**
	 * Every actionable task this plugin's graph recognises but carries no
	 * location for -- worth listing on the panel as "location unknown," and
	 * never routed to or dropped. See {@link QuestGraph.DiaryTask#locations()}'s
	 * own doc for why an empty list is a real, checked fact rather than a
	 * gap.
	 */
	static List<QuestGraph.DiaryTask> locationUnknown(List<QuestGraph.DiaryTask> graphTasks, PushResponse.Diary diary)
	{
		List<QuestGraph.DiaryTask> out = new ArrayList<>();
		if (diary == null)
		{
			return out;
		}
		Map<TaskKey, QuestGraph.DiaryTask> byKey = index(graphTasks);
		for (PushResponse.Diary.Actionable actionable : diary.actionable())
		{
			QuestGraph.DiaryTask task = byKey.get(new TaskKey(actionable.tierId(), actionable.ordinal()));
			if (task != null && task.locations().isEmpty())
			{
				out.add(task);
			}
		}
		return out;
	}
}
