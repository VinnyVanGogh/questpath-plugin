package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;

/**
 * Names the current diary task, why it is worth walking to, and how fresh
 * questpath's read on that tier's progress is -- drawn while {@link
 * QuestpathConfig#questJournalFilter()} permits diaries. That filter is this
 * overlay's entire on/off switch: it replaced the old, separate {@code
 * overlayDiaryRoute} boolean -- see {@link QuestJournalFilter}'s own class
 * doc, and {@code QuestpathPlugin}'s startup migration for what happens to
 * an account that had the old switch on.
 *

 * <p>Drawing only, the same split {@link ShopRouteOverlay} keeps from {@link
 * ShopRouteStops}: every decision about which task is current and where it
 * sends you already happened in {@link DiaryRouteStops}, so this class only
 * ever reads {@link DiaryRouteStops#stops} and {@link
 * DiaryRouteStops#destinationOf}'s result off it, never recomputes a
 * destination of its own.
 *
 * <p><b>No push yet is said, not shown as empty.</b> A questpath old enough
 * to predate diary routing, or simply not running, means {@link
 * PushResponse#hasDiary()} is false -- see that method's own doc for why
 * that is a different fact from "no diary tiers targeted." This overlay
 * renders that as "Diary route needs a questpath sync," never as nothing at
 * all, following the same rule {@code docs/PLAN-diary-routing.md} §7.4
 * states for every surface in this feature: unknown is not empty.
 */
class DiaryRouteOverlay extends QuestpathOverlay
{
	private final Supplier<List<QuestGraph.DiaryTask>> graphTasks;
	private final Supplier<PushResponse.Diary> diarySupplier;
	private final Supplier<WorldPoint> playerLocation;

	DiaryRouteOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons,
		Supplier<List<QuestGraph.DiaryTask>> graphTasks, Supplier<PushResponse.Diary> diarySupplier,
		Supplier<WorldPoint> playerLocation)
	{
		super(plugin, config, icons, "Diary route");
		this.graphTasks = graphTasks;
		this.diarySupplier = diarySupplier;
		this.playerLocation = playerLocation;
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.questJournalFilter().showsDiaries();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayDiaryRouteWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayDiaryRouteLength();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		PushResponse.Diary diary = diarySupplier.get();
		if (diary == null)
		{
			// Absent, not empty -- see this class's own doc and
			// PushResponse#hasDiary()'s.
			return List.of(OverlayRow.note("Diary route needs a questpath sync.", OverlayTheme.MUTED));
		}

		List<DiaryRouteStops.Stop> stops =
			DiaryRouteStops.stops(graphTasks.get(), diary, playerLocation.get());
		if (stops.isEmpty())
		{
			// Two different facts behind the same empty stop list: questpath
			// has never actually read this account's per-task diary
			// progress (so "nothing doable" would be a guess dressed as an
			// answer), versus it has, and there genuinely is nothing to
			// route to right now. See DiaryJournal#progressNeverSynced's
			// own doc -- the same check the Diaries panel section uses, so
			// both surfaces draw the same line between the two.
			if (DiaryJournal.progressNeverSynced(diary))
			{
				return List.of(OverlayRow.note("Diary progress not synced yet.", OverlayTheme.MUTED));
			}
			return List.of(OverlayRow.note(
				"No actionable diary task with a known location right now.", OverlayTheme.MUTED));
		}

		DiaryRouteStops.Stop current = stops.get(0);
		QuestGraph.DiaryTask task = current.task();

		List<OverlayRow> rows = new ArrayList<>();
		rows.add(OverlayRow.hero(task.diary() + " " + task.tier(), OverlayTheme.ACCENT));
		rows.add(OverlayRow.note(task.text(), OverlayTheme.TEXT));

		String distance = current.distanceTiles() == Integer.MAX_VALUE
			? ""
			: current.distanceTiles() + " tiles";
		rows.add(OverlayRow.line("-> " + current.destination().pageName(), distance,
			OverlayTheme.TEXT, OverlayTheme.MUTED));

		for (String warning : current.warnings())
		{
			rows.add(OverlayRow.note(warning, OverlayTheme.WARN));
		}

		rows.add(OverlayRow.note(staleness(diary, task.tierId()), OverlayTheme.MUTED));
		return rows;
	}

	/**
	 * What to say about how fresh this tier's per-task progress is --
	 * {@code "progress read 3 days ago"}, or a distinct line for a tier
	 * questpath cannot read per-task progress for at all.
	 */
	private static String staleness(PushResponse.Diary diary, long tierId)
	{
		for (PushResponse.Diary.TaskSync sync : diary.taskSync())
		{
			if (sync.tierId() != tierId)
			{
				continue;
			}
			if ("shape_mismatch".equals(sync.state()))
			{
				// Distinct from "none": questpath has data, but it no longer
				// lines up with this tier's own task count, so per-task
				// progress cannot be trusted for it -- see
				// docs/research/diary-build-brief.md D1.
				return "Per-task progress is unavailable for this tier (shape mismatch).";
			}
			Instant syncedAt = sync.syncedAt();
			if (syncedAt == null)
			{
				return "Progress read time unknown.";
			}
			return "Progress read " + ago(syncedAt, Instant.now()) + ".";
		}
		// No entry at all -- "none", per PushResponse.Diary#taskSync()'s own doc.
		return "Progress has never been read for this tier.";
	}

	private static String ago(Instant at, Instant now)
	{
		long seconds = Duration.between(at, now).getSeconds();
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return (seconds / 60) + "m ago";
		}
		if (seconds < 86400)
		{
			return (seconds / 3600) + "h ago";
		}
		return (seconds / 86400) + "d ago";
	}
}
