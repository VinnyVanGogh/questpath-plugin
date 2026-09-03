package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The account's saved route, and where along it the account currently is.
 *
 * <p>An account can be in exactly one of three situations, and they must not
 * be collapsed into one message: no plan has ever been saved in questpath, a
 * plan exists and everything on it is already finished, or a plan exists and
 * there is a next quest to name. The first is a prompt to go save one, the
 * second is a congratulation, and the third is the answer this section
 * exists for -- rendering any of them as the others would be wrong in a way
 * that looks like it is working.
 *
 * <p>The whole route is carried, not only its head. Naming one quest answers
 * "what now" and nothing else: it cannot say how much of the plan is left, or
 * that the plan is nearly done, and a sidebar showing a single name beside a
 * browser listing 218 quests reads as though the route were one quest long.
 * The head stays a distinct, prominent thing, because "what now" is still the
 * question asked most often.
 */
final class NextQuestStatus
{
	enum State
	{
		/** questpath has no saved route for this account at all. */
		NO_ROUTE,
		/** A route is saved, and every quest on it is already finished. */
		ALL_DONE,
		/** A route is saved, and this is the first quest on it not finished. */
		NEXT
	}

	/** One quest on the route, in saved order, and whether it is finished. */
	static final class Step
	{
		private final String questName;
		private final boolean done;

		Step(String questName, boolean done)
		{
			this.questName = questName;
			this.done = done;
		}

		String questName()
		{
			return questName;
		}

		boolean done()
		{
			return done;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof Step))
			{
				return false;
			}
			Step that = (Step) other;
			return done == that.done && Objects.equals(questName, that.questName);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(questName, done);
		}

		@Override
		public String toString()
		{
			return questName + (done ? " (done)" : "");
		}
	}

	private final State state;
	private final String questName;
	private final List<Step> route;

	private NextQuestStatus(State state, String questName, List<Step> route)
	{
		this.state = state;
		this.questName = questName;
		this.route = Collections.unmodifiableList(route);
	}

	/**
	 * Works out which of the three states applies, from the route order the
	 * push response carried and the quests the client reports as finished.
	 *
	 * @param routeOrder quest names in the account's saved plan, in order.
	 *     Empty (never null) means nothing is saved -- see
	 *     PushResponse.routeOrder().
	 * @param completedQuestNames every quest name the client reports as
	 *     FINISHED this session.
	 */
	static NextQuestStatus of(List<String> routeOrder, Set<String> completedQuestNames)
	{
		List<Step> steps = new ArrayList<>(routeOrder.size());
		String next = null;
		for (String name : routeOrder)
		{
			boolean done = completedQuestNames.contains(name);
			if (!done && next == null)
			{
				next = name;
			}
			steps.add(new Step(name, done));
		}

		if (steps.isEmpty())
		{
			return new NextQuestStatus(State.NO_ROUTE, null, steps);
		}
		if (next == null)
		{
			return new NextQuestStatus(State.ALL_DONE, null, steps);
		}
		return new NextQuestStatus(State.NEXT, next, steps);
	}

	State state()
	{
		return state;
	}

	/** The next undone quest's name. Only meaningful when state() == NEXT. */
	String questName()
	{
		return questName;
	}

	/**
	 * The whole route in saved order, finished quests included.
	 *
	 * Finished ones are kept rather than filtered out because they are the
	 * progress: "4 of 21 done" means nothing without the four, and dropping
	 * them would make a route nearly finished look identical to one nobody
	 * has started.
	 */
	List<Step> route()
	{
		return route;
	}

	/**
	 * The outstanding quests, in route order, next one first.
	 *
	 * What an overlay wants: on screen, during play, the finished half of the
	 * plan is history and the space is better spent on more of what is left.
	 */
	List<Step> upcoming()
	{
		List<Step> out = new ArrayList<>();
		for (Step step : route)
		{
			if (!step.done())
			{
				out.add(step);
			}
		}
		return out;
	}

	/** How many quests on the route are finished. */
	int doneCount()
	{
		int count = 0;
		for (Step step : route)
		{
			if (step.done())
			{
				count++;
			}
		}
		return count;
	}

	/** How many quests the route holds in total. */
	int size()
	{
		return route.size();
	}

	/** How many quests on the route are still outstanding. */
	int remaining()
	{
		return size() - doneCount();
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof NextQuestStatus))
		{
			return false;
		}
		NextQuestStatus that = (NextQuestStatus) other;
		return state == that.state
			&& Objects.equals(questName, that.questName)
			&& Objects.equals(route, that.route);
	}

	/**
	 * Covers the whole route, not only the head.
	 *
	 * The panel caches on this hash, so a route re-saved in questpath -- same
	 * next quest, different tail -- must change it. Hashing the head alone
	 * would leave the old tail on screen with nothing to say it was stale.
	 */
	@Override
	public int hashCode()
	{
		return Objects.hash(state, questName, route);
	}
}
