package com.vinnyvangogh.questpath;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The truth table {@link NextQuestOverlay}, {@link DiaryRouteOverlay} and
 * the Quests tab all read off this enum: which of the quest route and the
 * diary route each of the three values permits.
 */
public class QuestJournalFilterTest
{
	@Test
	public void questsShowsOnlyQuests()
	{
		assertTrue(QuestJournalFilter.QUESTS.showsQuests());
		assertFalse(QuestJournalFilter.QUESTS.showsDiaries());
	}

	@Test
	public void diariesShowsOnlyDiaries()
	{
		assertFalse(QuestJournalFilter.DIARIES.showsQuests());
		assertTrue(QuestJournalFilter.DIARIES.showsDiaries());
	}

	@Test
	public void allShowsBoth()
	{
		assertTrue(QuestJournalFilter.ALL.showsQuests());
		assertTrue(QuestJournalFilter.ALL.showsDiaries());
	}
}
