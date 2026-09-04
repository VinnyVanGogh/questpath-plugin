package com.vinnyvangogh.questpath;

/**
 * Decides whether a quest that just finished should advance Quest Helper to
 * the next route step.
 *
 * <p>Pure state machine, no client, no config and no {@link
 * QuestHelperBridge}: handed successive {@link NextQuestStatus} readings, it
 * says whether the route's own head just moved because the step it was
 * pointing at finished. Whether Quest Helper can actually take the next
 * step, and whether the player wants any of this at all, are
 * QuestpathPlugin's business, not this class's -- keeping them apart is what
 * makes the interesting part -- which transitions count, and which do not --
 * testable without a running game.
 *
 * <p>The guard against firing on login, on a resync, or on the burst of
 * varbits that establishes the baseline is the same shape as {@link
 * ItemCompletionWatcher}'s: the first observation after a {@link #reset} can
 * never itself be a transition, because there is nothing yet to compare it
 * against -- it only ever records where the route's head currently is.
 */
class AutoAdvancePlanner
{
	/** What just happened, and what -- if anything -- should follow. */
	enum Action
	{
		/** No transition worth acting on: nothing changed, or nothing has been observed yet. */
		NONE,
		/** The route's current step just finished, and another one follows it. */
		ADVANCE,
		/** The route's current step just finished, and it was the last one on the route. */
		ROUTE_FINISHED
	}

	/** One decision: what happened, and the quest names it involved. */
	static final class Decision
	{
		private static final Decision NONE = new Decision(Action.NONE, null, null);

		private final Action action;
		private final String finished;
		private final String next;

		private Decision(Action action, String finished, String next)
		{
			this.action = action;
			this.finished = finished;
			this.next = next;
		}

		static Decision none()
		{
			return NONE;
		}

		static Decision advance(String finished, String next)
		{
			return new Decision(Action.ADVANCE, finished, next);
		}

		static Decision routeFinished(String finished)
		{
			return new Decision(Action.ROUTE_FINISHED, finished, null);
		}

		Action action()
		{
			return action;
		}

		/** The quest that just finished. Meaningful for {@link Action#ADVANCE} and {@link Action#ROUTE_FINISHED}. */
		String finished()
		{
			return finished;
		}

		/** The quest to start next. Meaningful only for {@link Action#ADVANCE}. */
		String next()
		{
			return next;
		}
	}

	/** The route's NEXT-state head as of the last real observation, or null. */
	private String previousNext;

	/**
	 * Forgets what it last saw. Called on login, on plugin startUp, and on
	 * logout -- every point at which the next observation must be treated as
	 * a fresh baseline rather than a continuation, because otherwise it
	 * would compare a route from a different account, or from before the
	 * plugin last started, against this one. See the class doc.
	 */
	void reset()
	{
		previousNext = null;
	}

	/**
	 * What, if anything, should happen now that route reflects the latest
	 * completed-quest state.
	 *
	 * @param route the freshly computed route status. Must not be null --
	 *     callers that cannot judge the route yet (see
	 *     QuestpathPlugin#nextQuest) must not call this at all, the same
	 *     way they must not call {@link #reset} for it: an unjudgeable pass
	 *     is neither a baseline nor a transition, and observing it here
	 *     would corrupt the baseline for whichever real observation comes
	 *     next.
	 */
	Decision observe(NextQuestStatus route)
	{
		String finished = previousNext;
		previousNext = route.state() == NextQuestStatus.State.NEXT ? route.questName() : null;

		if (finished == null || !isNowDone(route, finished))
		{
			return Decision.none();
		}

		switch (route.state())
		{
			case NEXT:
				return Decision.advance(finished, route.questName());
			case ALL_DONE:
				return Decision.routeFinished(finished);
			default:
				// NO_ROUTE: the saved route was cleared or replaced between
				// two observations (a rejected sync, say). Nothing sane to
				// advance to.
				return Decision.none();
		}
	}

	/** Whether questName is on route and marked done there. False for a name no longer on the route at all. */
	private static boolean isNowDone(NextQuestStatus route, String questName)
	{
		for (NextQuestStatus.Step step : route.route())
		{
			if (step.questName().equals(questName))
			{
				return step.done();
			}
		}
		return false;
	}
}
