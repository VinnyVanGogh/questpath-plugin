package com.vinnyvangogh.questpath;

/**
 * Which half of the achievement-diary/quest split the Quests tab shows, and
 * which of the two route overlays it drives -- one control for both, the
 * same shape the in-game quest journal's own Quests/Diaries/All filter
 * already has.
 *
 * <p><b>Drives both the panel and the overlays.</b> {@link
 * QuestpathConfig#questJournalFilter()} is read from three places: the
 * Quests tab's own section list ({@code QuestpathPanel}), {@link
 * NextQuestOverlay#enabled}, and {@link DiaryRouteOverlay#enabled}. {@code
 * QUESTS} shows the quest route and hides every diary section and the diary
 * overlay; {@code DIARIES} does the reverse; {@code ALL} shows both, each
 * keeping its own order -- there is no merged planner here, and none is
 * planned.
 *
 * <p><b>This replaced a second, separate on/off switch.</b> Before this
 * setting existed, the diary route overlay had its own boolean --
 * {@code overlayDiaryRoute} -- with no equivalent for the panel's own diary
 * sections, which always rendered regardless of that switch. Folding both
 * into one filter removes that inconsistency along with the second control;
 * see {@code QuestpathConfig#questJournalFilter()}'s own doc for the default
 * chosen to keep a fresh install's diary overlay off exactly like before,
 * and {@code QuestpathPlugin}'s startup migration for what happens to an
 * existing user's old {@code overlayDiaryRoute} value.
 */
public enum QuestJournalFilter
{
	QUESTS("Quests"),
	DIARIES("Diaries"),
	ALL("All");

	private final String label;

	QuestJournalFilter(String label)
	{
		this.label = label;
	}

	public String label()
	{
		return label;
	}

	/** Whether the quest route -- the panel's quest sections and {@link NextQuestOverlay} -- should show. */
	boolean showsQuests()
	{
		return this != DIARIES;
	}

	/** Whether the diary route -- the panel's diary sections and {@link DiaryRouteOverlay} -- should show. */
	boolean showsDiaries()
	{
		return this != QUESTS;
	}

	/**
	 * What RuneLite's config dropdown shows. It renders an enum by its
	 * toString, so without this the options would read QUESTS/DIARIES/ALL.
	 */
	@Override
	public String toString()
	{
		return label;
	}
}
