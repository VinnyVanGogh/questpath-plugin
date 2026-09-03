package com.vinnyvangogh.questpath;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the "Next on your route" section says, and the three ways it can say
 * it.
 *
 * <p>An account can be in exactly one of three situations, and they must not
 * be collapsed into one message: no plan has ever been saved in questpath, a
 * plan exists and everything on it is already finished, or a plan exists and
 * there is a next quest to name. The first is a prompt to go save one, the
 * second is a congratulation, and the third is the answer this section
 * exists for -- rendering any of them as the others would be wrong in a way
 * that looks like it is working.
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

	private final State state;
	private final String questName;

	private NextQuestStatus(State state, String questName)
	{
		this.state = state;
		this.questName = questName;
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
		if (routeOrder.isEmpty())
		{
			return new NextQuestStatus(State.NO_ROUTE, null);
		}
		for (String name : routeOrder)
		{
			if (!completedQuestNames.contains(name))
			{
				return new NextQuestStatus(State.NEXT, name);
			}
		}
		return new NextQuestStatus(State.ALL_DONE, null);
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
		return state == that.state && Objects.equals(questName, that.questName);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(state, questName);
	}
}
