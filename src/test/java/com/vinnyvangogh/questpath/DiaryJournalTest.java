package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The pure decision behind the Diaries view of the Quests tab: how tasks
 * group into Area &gt; Tier, and which of the states the feature brief names
 * -- done, doable, blocked, deferred, unknown, not-synced -- each one
 * resolves to.
 *
 * <p>Fixtures are built with a plain {@code Gson}, never {@link
 * QuestGraph#load}, the same convention {@link DiaryRouteStopsTest} already
 * uses and explains: the bundled {@code quests.json} is a separate concern,
 * and this class only needs the two small, package-private model types.
 */
public class DiaryJournalTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph.DiaryTask task(long tierId, int ordinal, String diaryName, String tier)
	{
		return task(tierId, ordinal, diaryName, tier, "[{\"page_name\":\"Somewhere\",\"x\":1,\"y\":1,\"plane\":0}]");
	}

	private static QuestGraph.DiaryTask task(long tierId, int ordinal, String diaryName, String tier,
		String locationsJson)
	{
		String json = "{\"tier_id\":" + tierId + ",\"ordinal\":" + ordinal
			+ ",\"diary\":\"" + diaryName + "\",\"tier\":\"" + tier + "\",\"text\":\"Do the thing.\""
			+ (locationsJson == null ? "" : ",\"locations\":" + locationsJson) + "}";
		return GSON.fromJson(json, QuestGraph.DiaryTask.class);
	}

	private static PushResponse.Diary diary(String json)
	{
		return GSON.fromJson(json, PushResponse.Diary.class);
	}

	// ------------------------------------------------------------------
	// group(): Area > Tier > task

	/** Areas come back alphabetical, tiers Easy/Medium/Hard/Elite, tasks by ordinal. */
	@Test
	public void groupsAreaThenTierThenOrdinal()
	{
		List<QuestGraph.DiaryTask> tasks = List.of(
			task(2, 2, "Falador", "Easy"),
			task(2, 1, "Falador", "Easy"),
			task(3, 1, "Falador", "Medium"),
			task(1, 1, "Ardougne", "Elite"));
		PushResponse.Diary diary = diary("{}");

		List<DiaryJournal.Area> areas = DiaryJournal.group(tasks, diary);

		assertEquals(2, areas.size());
		assertEquals("Ardougne", areas.get(0).name());
		assertEquals("Falador", areas.get(1).name());

		DiaryJournal.Area falador = areas.get(1);
		assertEquals(2, falador.tiers().size());
		assertEquals("Easy", falador.tiers().get(0).name());
		assertEquals("Medium", falador.tiers().get(1).name());
		assertEquals(2, falador.tiers().get(0).entries().size());
		assertEquals(1, falador.tiers().get(0).entries().get(0).task().ordinal());
		assertEquals(2, falador.tiers().get(0).entries().get(1).task().ordinal());
	}

	/** A tier name outside Easy/Medium/Hard/Elite sorts after all four rather than being dropped. */
	@Test
	public void anUnrecognisedTierNameSortsAfterTheStandardFour()
	{
		List<QuestGraph.DiaryTask> tasks = List.of(
			task(1, 1, "Ardougne", "Elite"),
			task(2, 1, "Ardougne", "Bonus"));

		List<DiaryJournal.Tier> tiers = DiaryJournal.group(tasks, diary("{}")).get(0).tiers();

		assertEquals("Elite", tiers.get(0).name());
		assertEquals("Bonus", tiers.get(1).name());
	}

	// ------------------------------------------------------------------
	// resolve(): the five states diary != null can produce

	/** In actionable() -- doable, whatever else is true. */
	@Test
	public void anActionableTaskIsDoable()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.DOABLE, entry.state());
	}

	/** In outstanding(), not deferred, status BLOCKED -- blocked, reason carried verbatim. */
	@Test
	public void anOutstandingBlockedTaskCarriesItsReason()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{\"outstanding\":[{\"tier_id\":1,\"ordinal\":1,"
			+ "\"status\":\"BLOCKED\",\"reason\":\"quest not complete\",\"deferred\":false}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.BLOCKED, entry.state());
		assertEquals("quest not complete", entry.reason());
	}

	/**
	 * Status UNKNOWN_PROGRESS shows as unknown, never folded into blocked --
	 * pinned explicitly per the feature brief.
	 */
	@Test
	public void unknownProgressStatusIsUnknownNotBlocked()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{\"outstanding\":[{\"tier_id\":1,\"ordinal\":1,"
			+ "\"status\":\"UNKNOWN_PROGRESS\",\"deferred\":false}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.UNKNOWN, entry.state());
	}

	/** deferred=true wins over the underlying status, even BLOCKED. */
	@Test
	public void aDeferredOutstandingTaskIsDeferredEvenIfAlsoBlocked()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{\"outstanding\":[{\"tier_id\":1,\"ordinal\":1,"
			+ "\"status\":\"BLOCKED\",\"reason\":\"quest not complete\",\"deferred\":true}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.DEFERRED, entry.state());
	}

	/** In neither list, and diary != null -- done, per the server's own omit-when-done convention. */
	@Test
	public void aTaskInNeitherListIsDoneWhenDiaryIsPresent()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		// task_sync alone is real content -- see progressNeverSynced's own
		// doc for why that is enough to trust an absence as done, distinct
		// from the fully-empty fixture below.
		PushResponse.Diary diary = diary("{\"task_sync\":[{\"tier_id\":1,\"state\":\"ok\"}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.DONE, entry.state());
	}

	/**
	 * The same "absent from both lists" shape must NOT read as done when
	 * there is no diary push at all -- see the class doc's caveat, and the
	 * corpus lesson it exists to avoid: a missing push must never look like
	 * a finished diary.
	 */
	@Test
	public void everyTaskIsNotSyncedWhenDiaryIsNull()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), null).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.NOT_SYNCED, entry.state());
	}

	/**
	 * The same "absent from both lists" shape must also NOT read as done
	 * when {@code diary} is technically non-null but carries nothing at all
	 * -- the "server returned empty" case the feature brief names alongside
	 * "no diary data" and "not synced yet". See {@link
	 * DiaryJournal#progressNeverSynced}'s own doc for the {@code
	 * pluginstate.go} early-return paths that produce exactly this shape.
	 */
	@Test
	public void everyTaskIsNotSyncedWhenTheDiaryBlockIsEntirelyEmpty()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.NOT_SYNCED, entry.state());
	}

	// ------------------------------------------------------------------
	// locationUnknown()

	/** No locations on the task -- flagged, independent of state. */
	@Test
	public void locationUnknownIsOrthogonalToState()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", null);
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertEquals(DiaryJournal.TaskState.DOABLE, entry.state());
		assertTrue(entry.locationUnknown());
	}

	@Test
	public void aTaskWithALocationIsNotFlagged()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy");
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		DiaryJournal.Entry entry = DiaryJournal.group(List.of(task), diary).get(0).tiers().get(0).entries().get(0);

		assertFalse(entry.locationUnknown());
	}

	// ------------------------------------------------------------------
	// progressNeverSynced()

	@Test
	public void neverSyncedWhenDiaryIsNull()
	{
		assertTrue(DiaryJournal.progressNeverSynced(null));
	}

	/** targets, actionable, outstanding and task_sync all empty -- the "server returned empty" shape. */
	@Test
	public void neverSyncedWhenTheWholeDiaryBlockIsEmpty()
	{
		assertTrue(DiaryJournal.progressNeverSynced(diary("{}")));
	}

	/**
	 * task_sync entries alone -- even ones whose own state is {@code
	 * "none"} -- are real content: this account has targeted tiers
	 * questpath is tracking, so it is not the "nothing was ever classified"
	 * case this method exists to catch. A tier's own {@code "none"} state
	 * is told truthfully at the per-task level instead, via {@link
	 * DiaryJournal.TaskState#UNKNOWN} -- see this method's own doc.
	 */
	@Test
	public void syncedWhenTaskSyncCarriesEntriesEvenIfTheirStateIsNone()
	{
		assertFalse(DiaryJournal.progressNeverSynced(
			diary("{\"task_sync\":[{\"tier_id\":1,\"state\":\"none\"},{\"tier_id\":2,\"state\":\"none\"}]}")));
	}

	@Test
	public void syncedWhenActionableAloneIsPopulated()
	{
		assertFalse(DiaryJournal.progressNeverSynced(diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}")));
	}

	@Test
	public void syncedWhenOutstandingAloneIsPopulated()
	{
		assertFalse(DiaryJournal.progressNeverSynced(
			diary("{\"outstanding\":[{\"tier_id\":1,\"ordinal\":1,\"status\":\"BLOCKED\"}]}")));
	}

	@Test
	public void syncedWhenTargetsAloneIsPopulated()
	{
		assertFalse(DiaryJournal.progressNeverSynced(
			diary("{\"targets\":[{\"tier_id\":1,\"pinned\":false,\"deferred\":false}]}")));
	}
}
