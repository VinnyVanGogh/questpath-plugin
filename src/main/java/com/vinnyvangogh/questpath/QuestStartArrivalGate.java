package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;

/**
 * Whether an arrival {@link QuestStartArrivalWatcher} just reported should
 * actually be acted on, given whether Quest Helper already has a quest
 * selected.
 *
 * <p>Kept separate from both {@link QuestStartArrivalWatcher} and {@link
 * QuestHelperBridge#hasSelectedQuest()}, the same separation {@link
 * AutoAdvancePlanner} keeps from its own callers: the watcher answers "did
 * the player just walk up to this", the bridge answers "does Quest Helper
 * already have something selected", and this answers the one question that
 * decides whether to act on both -- without either of the other two needing
 * to know this exists.
 *
 * <p><b>Why Quest Helper's selection, not an account-level {@code
 * IN_PROGRESS} quest.</b> The report this closes: Prince Ali Rescue was in
 * progress, arrival at A Porcine of Interest's start switched the sidebar
 * over anyway. The account's own quest log is the wrong signal to gate on,
 * because a real account can have several quests {@code IN_PROGRESS} at once
 * -- Recipe for Disaster alone can sit that way for hundreds of hours -- and
 * gating on "any quest in progress" would mean this feature never fires
 * again for such an account, not merely never hijacks one. What the report
 * actually describes is Quest Helper's own screen changing out from under
 * the player, and the player's own workflow -- open Quest Helper, do a
 * stretch of a quest, close the panel -- is exactly what {@code
 * getSelectedQuest()} still answers correctly even after the panel is
 * closed: the selection persists until something changes it, which is the
 * "am I already using this for something" question this feature actually
 * needs answered.
 */
final class QuestStartArrivalGate
{
	private QuestStartArrivalGate()
	{
	}

	/**
	 * @param arrived quest names {@link QuestStartArrivalWatcher#observe}
	 *     reported this tick. Never null.
	 * @param questHelperHasSelection whether Quest Helper already has a
	 *     quest selected, per {@link QuestHelperBridge#hasSelectedQuest()}
	 * @return {@code arrived} unchanged when Quest Helper has nothing
	 *     selected, or an empty list when it does -- never a partial subset,
	 *     because a real selection means none of this tick's arrivals should
	 *     be acted on, not just the first
	 */
	static List<String> allow(List<String> arrived, boolean questHelperHasSelection)
	{
		if (questHelperHasSelection || arrived.isEmpty())
		{
			return Collections.emptyList();
		}
		return arrived;
	}
}
