package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;

/**
 * Whether an arrival {@link QuestStartArrivalWatcher} just reported should
 * actually be acted on, given what else the account is already doing.
 *
 * <p>Kept separate from {@link QuestStartArrivalWatcher}, {@link
 * QuestHelperBridge#hasSelectedQuest()} and the route-quest read {@link
 * QuestpathPlugin} now also passes in, the same separation {@link
 * AutoAdvancePlanner} keeps from its own callers: the watcher answers "did
 * the player just walk up to this", the two booleans below each answer a
 * narrower "is the account already committed to something", and this
 * answers the one question that decides whether to act on any of it --
 * without any of the others needing to know this exists.
 *
 * <p><b>First version: Quest Helper's selection alone.</b> The report that
 * closed: Prince Ali Rescue was in progress, arrival at A Porcine of
 * Interest's start switched the sidebar over anyway. Quest Helper's own
 * {@code getSelectedQuest()} was chosen over an account-level {@code
 * IN_PROGRESS} quest because an account can legitimately carry several
 * quests in progress at once -- Recipe for Disaster alone can sit that way
 * for hundreds of hours -- and gating on "any quest in progress" would mean
 * this feature never fires again for such an account, not merely never
 * hijacks one.
 *
 * <p><b>Why that alone was not enough.</b> A second report: the real
 * workflow is start a quest, do a bit, close Quest Helper -- and closing it
 * turns out to leave nothing selected, not a selection that survives the
 * panel closing the way the first version's doc assumed. Once closed, {@code
 * hasSelectedQuest()} answers false, which is exactly the moment the gate
 * needs to answer true: it started pointing at Zogre Flesh Eaters, and later
 * offered Tribal Totem while Sheep Herder -- a route quest -- was still in
 * progress with Quest Helper's own panel closed.
 *
 * <p><b>Second signal: a route quest in progress.</b> {@code
 * routeQuestInProgress} is the client's own quest state -- {@code
 * Quest#getState} on whichever quests are on the account's saved route --
 * intersected with the route, not with the whole account. That intersection
 * is deliberate and is what keeps the Recipe for Disaster problem from
 * recurring in a narrower form: an account only pays this gate for a quest
 * it is actually working <i>on the route it asked questpath to plan</i>, not
 * for every quest the account has ever half-started off of it. The
 * documented cost, carried forward rather than hidden: if the account's own
 * saved route genuinely includes something RFD-shaped -- started, on the
 * route, and left in progress for a long stretch -- this gate stays shut for
 * that whole stretch, the same shape of cost the first version was built to
 * avoid, just scoped down to "on the plan" instead of "anywhere on the
 * account". That scoping is the whole reason this is offered as a second,
 * additional signal rather than a replacement for Quest Helper's own
 * selection: either one committing the account to something is enough to
 * hold every arrival back, and blocking on their disjunction is still
 * narrower than blocking on the account's full quest log the way the
 * original design rejected.
 *
 * <p>Neither signal depends on {@code
 * QuestpathConfig#questStartArrivalDepth()}: that setting only narrows which
 * route positions {@link QuestStartCandidates} even measures distance to, so
 * a depth that excludes a route quest from being a distance candidate must
 * not also exclude it from this gate -- the account is still working that
 * quest, at whatever depth. This class predates and outranks that setting
 * for exactly that reason.
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
	 * @param routeQuestInProgress whether any quest on the account's saved
	 *     route is currently {@code IN_PROGRESS} per the client's own quest
	 *     state -- true regardless of whether Quest Helper's panel is open,
	 *     selected, or was ever used for that quest at all. See this class's
	 *     own doc for why the route, not the whole account, is what this is
	 *     intersected against.
	 * @return {@code arrived} unchanged when neither signal says the account
	 *     is already committed to something, or an empty list when either
	 *     does -- never a partial subset, because either one being true
	 *     means none of this tick's arrivals should be acted on, not just
	 *     the first
	 */
	static List<String> allow(List<String> arrived, boolean questHelperHasSelection, boolean routeQuestInProgress)
	{
		if (questHelperHasSelection || routeQuestInProgress || arrived.isEmpty())
		{
			return Collections.emptyList();
		}
		return arrived;
	}
}
