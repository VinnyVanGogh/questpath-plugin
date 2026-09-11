package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two things the plain route list cannot say.
 *
 * <p>"Next on your route" is an ordered list of quest names with a tick beside
 * the finished ones. That is the right shape for the question it answers --
 * what now -- and it is silent about two facts that change what a player does
 * this evening.
 *
 * <p><b>Outstanding.</b> A quest on the plan can be sitting there needing 41
 * Cooking, and the list renders it exactly like the one you could start in the
 * next ten seconds. The plan is right to keep it: a quest waiting on a level
 * belongs on the plan with the level named, and only a quest the build can
 * never do should be dropped. But naming it and nothing else is the
 * true-but-useless answer -- someone reads the next three rows, walks to the
 * third, and finds out there.
 *
 * <p><b>Partial.</b> Quests the build says to start and never finish are not
 * on the plan at all and never can be, because a route target is something the
 * plan intends to complete. See {@link PartialRun}. They are real work the
 * build calls for, and without a section of their own the only place they
 * appear is a string in a preset nothing reads.
 *
 * <p>Both belong beside the route rather than inside it. Folding outstanding
 * quests into the list would mean re-ordering it away from what questpath
 * saved, and folding partial runs in would mean claiming the plan intends to
 * finish them.
 *
 * <p>Pure: a graph, an account, a route and the pushed partial runs in; two
 * lists out. No client, no Swing, no I/O.
 */
class RouteWork
{
	/** One quest on the route that still needs something first. */
	static final class Outstanding
	{
		private final String questName;
		private final QuestStatus status;
		private final QuestBlockers blockers;

		Outstanding(String questName, QuestStatus status, QuestBlockers blockers)
		{
			this.questName = questName;
			this.status = status;
			this.blockers = blockers;
		}

		String questName()
		{
			return questName;
		}

		/**
		 * The verdict, so a row can distinguish "a level short" from "a quest
		 * short" from "a boost would cover it" without re-reading the
		 * blockers.
		 */
		QuestStatus status()
		{
			return status;
		}

		/** Everything standing in the way, all the way down. */
		QuestBlockers blockers()
		{
			return blockers;
		}
	}

	private final List<Outstanding> outstanding = new ArrayList<>();
	private final List<PartialRun> partial = new ArrayList<>();

	/**
	 * How many quests on the plan were checked to produce this.
	 *
	 * <p>Kept so the section can say "3 of the 38 on your plan" rather than
	 * "3". Three outstanding out of five is a plan that is mostly waiting;
	 * three out of thirty-eight is a plan that is mostly ready, and the bare
	 * number cannot tell those apart.
	 */
	private int considered;

	/**
	 * Judges every unfinished quest on the route.
	 *
	 * <p>Null graph, null account or null route all produce an empty result
	 * rather than a partial one. Each of those means the plugin cannot judge
	 * anything yet -- no bundle, no levels loaded, no plan -- and a section
	 * that quietly showed zero outstanding quests would be reporting "your
	 * plan is clear" on no evidence at all. The panel renders an empty result
	 * as no section rather than as good news.
	 *
	 * @param partialRuns the build's start-only quests as pushed, or null. Not
	 *     derived from the graph: which quests a build stops halfway through
	 *     is a property of the build, and the graph is account-agnostic.
	 */
	static RouteWork of(QuestGraph graph, PlayerState account, NextQuestStatus route,
		List<PartialRun> partialRuns)
	{
		RouteWork work = new RouteWork();

		if (partialRuns != null)
		{
			for (PartialRun run : partialRuns)
			{
				// An incomplete row is dropped rather than drawn with a blank
				// tooltip; see PartialRun#usable.
				if (run != null && run.usable())
				{
					work.partial.add(run);
				}
			}
		}

		if (graph == null || account == null || route == null)
		{
			return work;
		}

		QuestClassifier classifier = new QuestClassifier(graph, account);
		for (NextQuestStatus.Step step : route.route())
		{
			if (step.done())
			{
				continue;
			}
			QuestGraph.Quest quest = graph.byName(step.questName());
			if (quest == null)
			{
				// A quest on the plan that this bundle has never heard of is
				// a stale bundle, not a clear quest. Skipped rather than
				// reported as ready: the section's job is to name what is in
				// the way, and it cannot name what it cannot look up.
				continue;
			}
			work.considered++;

			QuestStatus status = classifier.classify(quest);
			if (status == QuestStatus.ELIGIBLE)
			{
				// A fast path, not the gate. An eligible quest has nothing in
				// its way by definition, so the blockers check below would
				// drop it anyway -- this only avoids paying for the walk to
				// find that out, once per ready quest on a plan that can run
				// to forty.
				//
				// Worth saying plainly because it reads like the gate and is
				// not: deleting this line changes what the section costs and
				// not what it shows, which a test cannot tell you.
				continue;
			}
			if (status == QuestStatus.COMPLETE)
			{
				// This one is a real guard rather than a shortcut. The done
				// flag on a route step and the account's own completed set
				// arrive here as two separate arguments, and nothing makes a
				// caller pass a matching pair. When they disagree, the
				// account is the one to believe -- it is what every other
				// verdict in the panel is judged against.
				continue;
			}
			QuestBlockers blockers = QuestBlockers.of(graph, account, quest);
			if (blockers.isEmpty() && status != QuestStatus.BOOSTABLE)
			{
				// Nothing to name. A status that is neither eligible nor
				// boostable with no blockers behind it means the verdict came
				// from something this section does not describe -- a build
				// rule, a refusal -- and those have their own places in the
				// panel. Listing the quest here under "still needs work"
				// without being able to say what work would be the empty
				// tooltip again.
				continue;
			}
			work.outstanding.add(new Outstanding(step.questName(), status, blockers));
		}

		// Closest first. The list is read to decide what to do next, so the
		// quest a boost would unlock belongs above the one behind four other
		// quests -- and within a band, the smaller job first.
		work.outstanding.sort((a, b) ->
		{
			int byStatus = Integer.compare(rank(a.status()), rank(b.status()));
			if (byStatus != 0)
			{
				return byStatus;
			}
			int byQuests = Integer.compare(a.blockers().quests().size(), b.blockers().quests().size());
			if (byQuests != 0)
			{
				return byQuests;
			}
			return Integer.compare(a.blockers().skills().size(), b.blockers().skills().size());
		});
		return work;
	}

	/**
	 * How close a verdict is to being actionable, smallest first.
	 *
	 * <p>Boostable is nearest: the gap closes with a drink. A missing level is
	 * next, because training is work you can start immediately. A missing
	 * quest is furthest, because it is work with its own requirements behind
	 * it. Anything else sorts last rather than being ranked -- this section
	 * only ever holds the three above, and a silent fourth should not land in
	 * the middle of them.
	 */
	private static int rank(QuestStatus status)
	{
		switch (status)
		{
			case BOOSTABLE:
				return 0;
			case SKILL_BLOCKED:
				return 1;
			case QUEST_BLOCKED:
				return 2;
			default:
				return 3;
		}
	}

	/** Quests on the plan that still need something, closest first. */
	List<Outstanding> outstanding()
	{
		return Collections.unmodifiableList(outstanding);
	}

	/** The build's start-only quests, in the order the preset lists them. */
	List<PartialRun> partial()
	{
		return Collections.unmodifiableList(partial);
	}

	/** How many unfinished quests on the plan were judged. */
	int considered()
	{
		return considered;
	}

	/** Whether there is anything at all for the panel to draw. */
	boolean isEmpty()
	{
		return outstanding.isEmpty() && partial.isEmpty();
	}
}
