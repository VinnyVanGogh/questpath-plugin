package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Groups the plugin's own bundled diary tasks into Area &gt; Tier &gt; Task,
 * the same shape the in-game achievement diary journal uses, and resolves
 * each task's state from the push response -- the pure decision behind the
 * Diaries view of the Quests tab.
 *
 * <p>No client, no Swing, no overlay -- same split {@link DiaryRouteStops}
 * already keeps from its own overlay and panel callers, for the same reason:
 * the grouping and the state a task gets called are both decisions worth
 * exercising directly in a test, not only by eye in the running client.
 *
 * <p><b>A task is identified by {@code (tier_id, ordinal)}</b>, reusing
 * {@link DiaryRouteStops.TaskKey} rather than a second key type -- see that
 * class's own doc for why a row id would not survive a re-ingest.
 *
 * <p><b>Done is inferred, never asserted.</b> The server omits a finished
 * task from both {@link PushResponse.Diary#actionable()} and {@link
 * PushResponse.Diary#outstanding()} entirely (see {@code pluginstate.go}'s
 * own comment on that loop), so a task absent from both normally reads as
 * {@link TaskState#DONE} -- except that {@link #group} takes {@code diary}
 * as possibly null, and {@link #progressNeverSynced} decides, once per
 * call rather than once per task, whether this push carries any real diary
 * data at all. When it does not -- {@code diary == null}, or a technically
 * non-null block with nothing whatsoever in it -- every task reads {@link
 * TaskState#NOT_SYNCED} instead, so a missing or empty push is never
 * mistaken for a finished diary.
 */
final class DiaryJournal
{
	private DiaryJournal()
	{
	}

	/**
	 * One task's resolved state, in the same four words the feature brief
	 * uses: done, doable, blocked, deferred -- plus unknown, which {@link
	 * PushResponse.Diary.Outstanding#status()} of {@code "UNKNOWN_PROGRESS"}
	 * gets called out as explicitly rather than folded into blocked.
	 *
	 * <p>Deferred wins over unknown/blocked when both are true of the same
	 * task: a deferred tier is an account-level choice ("stop nudging me
	 * about this"), which is more informative to show than whatever
	 * incidental reason the task itself would otherwise carry.
	 */
	enum TaskState
	{
		/** Not in either list the server sent -- see the class doc's caveat. */
		DONE,
		/** In {@link PushResponse.Diary#actionable()}. */
		DOABLE,
		/** In {@link PushResponse.Diary#outstanding()}, not deferred, not unknown-progress. */
		BLOCKED,
		/** In {@link PushResponse.Diary#outstanding()} with {@code deferred} set. */
		DEFERRED,
		/** In {@link PushResponse.Diary#outstanding()} with status {@code UNKNOWN_PROGRESS}. */
		UNKNOWN,
		/** No diary push has arrived at all -- every task in the tree gets this. */
		NOT_SYNCED
	}

	/** One task, its resolved state, and questpath's own reason when it carries one. */
	static final class Entry
	{
		private final QuestGraph.DiaryTask task;
		private final TaskState state;
		private final String reason;

		Entry(QuestGraph.DiaryTask task, TaskState state, String reason)
		{
			this.task = task;
			this.state = state;
			this.reason = reason == null ? "" : reason;
		}

		QuestGraph.DiaryTask task()
		{
			return task;
		}

		TaskState state()
		{
			return state;
		}

		/** Why the task is blocked, in questpath's own words -- empty for every other state. */
		String reason()
		{
			return reason;
		}

		/**
		 * Whether the plugin's own graph has nowhere to send the player for
		 * this task -- orthogonal to {@link #state()}, per the feature
		 * brief: a doable task can be exactly as location-unknown as a
		 * blocked one.
		 */
		boolean locationUnknown()
		{
			return task.locations().isEmpty();
		}
	}

	/** One tier's tasks, in the diary's own ordinal order. */
	static final class Tier
	{
		private final String name;
		private final List<Entry> entries;

		Tier(String name, List<Entry> entries)
		{
			this.name = name;
			this.entries = entries;
		}

		String name()
		{
			return name;
		}

		List<Entry> entries()
		{
			return entries;
		}
	}

	/** One diary area's tiers, ranked Easy/Medium/Hard/Elite first. */
	static final class Area
	{
		private final String name;
		private final List<Tier> tiers;

		Area(String name, List<Tier> tiers)
		{
			this.name = name;
			this.tiers = tiers;
		}

		String name()
		{
			return name;
		}

		List<Tier> tiers()
		{
			return tiers;
		}
	}

	/**
	 * The standard four diary tiers, ranked in the order every in-game diary
	 * journal already lists them. A tier name outside this list (there is
	 * none today) sorts after all four, alphabetically among themselves,
	 * rather than being dropped.
	 */
	private static final List<String> TIER_ORDER = List.of("Easy", "Medium", "Hard", "Elite");

	/**
	 * Groups every bundled diary task into Area &gt; Tier &gt; Entry, each
	 * entry's state resolved from {@code diary}.
	 *
	 * @param graphTasks the plugin's own bundled or fetched graph's diary
	 *     tasks -- {@code QuestGraph#diaryTasks()}
	 * @param diary the push response's diary block, or null when the server
	 *     has not sent one yet -- every entry then reads {@link
	 *     TaskState#NOT_SYNCED} rather than this method guessing at
	 *     completion, so the tree stays browsable before the first sync
	 *     rather than disappearing entirely; see {@link #progressNeverSynced}
	 *     for the message a caller should show alongside it
	 * @return one {@link Area} per distinct {@link QuestGraph.DiaryTask#diary()},
	 *     alphabetical, each carrying its tiers ranked per {@link
	 *     #TIER_ORDER}
	 */
	static List<Area> group(List<QuestGraph.DiaryTask> graphTasks, PushResponse.Diary diary)
	{
		Map<String, Map<String, List<QuestGraph.DiaryTask>>> byAreaThenTier = new TreeMap<>();
		for (QuestGraph.DiaryTask task : safe(graphTasks))
		{
			byAreaThenTier
				.computeIfAbsent(task.diary(), k -> new TreeMap<>())
				.computeIfAbsent(task.tier(), k -> new ArrayList<>())
				.add(task);
		}

		Set<DiaryRouteStops.TaskKey> actionable = actionableKeys(diary);
		Map<DiaryRouteStops.TaskKey, PushResponse.Diary.Outstanding> outstanding = outstandingByKey(diary);
		// Computed once per call, not per task: see progressNeverSynced's
		// own doc for why every task reads NOT_SYNCED together rather than
		// each one re-deriving the same fact about the whole push.
		boolean neverSynced = progressNeverSynced(diary);

		List<Area> areas = new ArrayList<>();
		for (Map.Entry<String, Map<String, List<QuestGraph.DiaryTask>>> areaEntry : byAreaThenTier.entrySet())
		{
			List<String> tierNames = new ArrayList<>(areaEntry.getValue().keySet());
			tierNames.sort(DiaryJournal::compareTiers);

			List<Tier> tiers = new ArrayList<>();
			for (String tierName : tierNames)
			{
				List<QuestGraph.DiaryTask> tasks = new ArrayList<>(areaEntry.getValue().get(tierName));
				tasks.sort(Comparator.comparingInt(QuestGraph.DiaryTask::ordinal));

				List<Entry> entries = new ArrayList<>();
				for (QuestGraph.DiaryTask task : tasks)
				{
					entries.add(resolve(task, neverSynced, actionable, outstanding));
				}
				tiers.add(new Tier(tierName, entries));
			}
			areas.add(new Area(areaEntry.getKey(), tiers));
		}
		return areas;
	}

	/**
	 * Whether this push carries no real diary data at all -- true when
	 * {@code diary} itself is null (see {@link PushResponse#hasDiary()}),
	 * and also when it arrived but {@link PushResponse.Diary#targets()},
	 * {@link PushResponse.Diary#actionable()}, {@link
	 * PushResponse.Diary#outstanding()} and {@link
	 * PushResponse.Diary#taskSync()} are every one of them empty -- the
	 * shape a questpath predating diary routing, an account that has never
	 * synced anything, or a request that errored partway through building
	 * the diary block all produce identically. See {@code pluginstate.go}'s
	 * {@code diaryStateFor}: several of its own early returns leave exactly
	 * this all-empty {@code Diary} behind.
	 *
	 * <p><b>Deliberately not keyed on {@link PushResponse.Diary#taskSync()}
	 * alone.</b> A tier whose own sync state is {@code "none"} still gets a
	 * real per-task verdict from the classifier -- {@link
	 * TaskState#UNKNOWN}, via {@link #resolve} -- so that per-tier gap is
	 * already told truthfully at the task level and does not need this
	 * whole-diary check to catch it too. This method is for the coarser
	 * case: nothing was classified for this account at all, so inferring
	 * {@link TaskState#DONE} from a task's mere absence would be exactly
	 * the "no data looks like all done" mistake the feature brief warns
	 * against.
	 *
	 * <p>Kept separate from {@link #group} -- and pure and shared by both the
	 * overlay and the panel -- so the same rule decides "say not-synced
	 * instead of nothing-doable" everywhere it is said, rather than each
	 * caller inventing its own reading of the same facts.
	 */
	static boolean progressNeverSynced(PushResponse.Diary diary)
	{
		if (diary == null)
		{
			return true;
		}
		return diary.targets().isEmpty() && diary.actionable().isEmpty()
			&& diary.outstanding().isEmpty() && diary.taskSync().isEmpty();
	}

	private static Entry resolve(QuestGraph.DiaryTask task, boolean neverSynced,
		Set<DiaryRouteStops.TaskKey> actionable,
		Map<DiaryRouteStops.TaskKey, PushResponse.Diary.Outstanding> outstanding)
	{
		if (neverSynced)
		{
			return new Entry(task, TaskState.NOT_SYNCED, "");
		}

		DiaryRouteStops.TaskKey key = new DiaryRouteStops.TaskKey(task.tierId(), task.ordinal());
		if (actionable.contains(key))
		{
			return new Entry(task, TaskState.DOABLE, "");
		}

		PushResponse.Diary.Outstanding entry = outstanding.get(key);
		if (entry == null)
		{
			// Neither list mentions it, and this push carries real diary
			// data (neverSynced is false) -- see the class doc's "done is
			// inferred" caveat and progressNeverSynced's own doc for why
			// this branch is only reached once that is ruled out.
			return new Entry(task, TaskState.DONE, "");
		}
		if (entry.deferred())
		{
			return new Entry(task, TaskState.DEFERRED, entry.reason());
		}
		if ("UNKNOWN_PROGRESS".equals(entry.status()))
		{
			return new Entry(task, TaskState.UNKNOWN, entry.reason());
		}
		return new Entry(task, TaskState.BLOCKED, entry.reason());
	}

	private static Set<DiaryRouteStops.TaskKey> actionableKeys(PushResponse.Diary diary)
	{
		Set<DiaryRouteStops.TaskKey> keys = new HashSet<>();
		if (diary != null)
		{
			for (PushResponse.Diary.Actionable actionable : diary.actionable())
			{
				keys.add(new DiaryRouteStops.TaskKey(actionable.tierId(), actionable.ordinal()));
			}
		}
		return keys;
	}

	private static Map<DiaryRouteStops.TaskKey, PushResponse.Diary.Outstanding> outstandingByKey(
		PushResponse.Diary diary)
	{
		Map<DiaryRouteStops.TaskKey, PushResponse.Diary.Outstanding> byKey = new HashMap<>();
		if (diary != null)
		{
			for (PushResponse.Diary.Outstanding outstanding : diary.outstanding())
			{
				byKey.put(new DiaryRouteStops.TaskKey(outstanding.tierId(), outstanding.ordinal()), outstanding);
			}
		}
		return byKey;
	}

	private static int compareTiers(String a, String b)
	{
		int rankA = TIER_ORDER.indexOf(a);
		int rankB = TIER_ORDER.indexOf(b);
		if (rankA == -1)
		{
			rankA = TIER_ORDER.size();
		}
		if (rankB == -1)
		{
			rankB = TIER_ORDER.size();
		}
		if (rankA != rankB)
		{
			return Integer.compare(rankA, rankB);
		}
		return a.compareTo(b);
	}

	private static List<QuestGraph.DiaryTask> safe(List<QuestGraph.DiaryTask> tasks)
	{
		return tasks == null ? Collections.emptyList() : tasks;
	}
}
