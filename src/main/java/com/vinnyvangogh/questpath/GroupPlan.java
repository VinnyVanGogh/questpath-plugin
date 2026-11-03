package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a party can plan together, judged member by member with the same
 * classifier that judges the local account.
 *
 * <p>Three answers, each pure over the bundled graph and what members chose
 * to share:
 * <ul>
 * <li>{@link #together()} -- quests nobody has done that every member can
 * start now, build rules included;</li>
 * <li>{@link #flagged()} -- quests every member could otherwise start, that
 * would break at least one member's build, with who and how. This is the
 * warning a group with a pure in it needs <em>before</em> it plans;</li>
 * <li>{@link #doneBySome()} -- quests some members have finished and others
 * still need.</li>
 * </ul>
 *
 * <p>Only {@link GroupMember#compatible() compatible} members are planned
 * for. An incompatible one is still in {@link #members()}, so the panel can
 * say so, but judging a member whose message this build cannot read would
 * judge an empty account.
 */
final class GroupPlan
{
	/** One member's breach on a flagged quest. */
	static final class MemberBreach
	{
		private final String member;
		private final BuildBreaches.Breach breach;

		MemberBreach(String member, BuildBreaches.Breach breach)
		{
			this.member = member;
			this.breach = breach;
		}

		String member()
		{
			return member;
		}

		BuildBreaches.Breach breach()
		{
			return breach;
		}

		/** "Bob: 18,650 Defence xp (capped at 1)". */
		String describe()
		{
			return member + ": " + breach.describe();
		}
	}

	/** A quest the group could do except that it breaks someone's build. */
	static final class Flag
	{
		private final QuestGraph.Quest quest;
		private final List<MemberBreach> breaches;

		Flag(QuestGraph.Quest quest, List<MemberBreach> breaches)
		{
			this.quest = quest;
			this.breaches = Collections.unmodifiableList(breaches);
		}

		QuestGraph.Quest quest()
		{
			return quest;
		}

		List<MemberBreach> breaches()
		{
			return breaches;
		}
	}

	/** A quest some members have finished. */
	static final class Partial
	{
		private final QuestGraph.Quest quest;
		private final List<String> doneBy;
		private final List<String> stillNeed;

		Partial(QuestGraph.Quest quest, List<String> doneBy, List<String> stillNeed)
		{
			this.quest = quest;
			this.doneBy = Collections.unmodifiableList(doneBy);
			this.stillNeed = Collections.unmodifiableList(stillNeed);
		}

		QuestGraph.Quest quest()
		{
			return quest;
		}

		List<String> doneBy()
		{
			return doneBy;
		}

		List<String> stillNeed()
		{
			return stillNeed;
		}
	}

	private final List<GroupMember> members;
	private final List<GroupMember> planned;
	private final List<QuestGraph.Quest> together = new ArrayList<>();
	private final Map<String, Flag> flagged = new LinkedHashMap<>();
	private final List<Partial> doneBySome = new ArrayList<>();
	private final Map<GroupMember, Integer> doneCounts = new IdentityHashMap<>();

	private GroupPlan(List<GroupMember> members)
	{
		this.members = Collections.unmodifiableList(new ArrayList<>(members));
		List<GroupMember> compatible = new ArrayList<>();
		for (GroupMember member : members)
		{
			if (member.compatible())
			{
				compatible.add(member);
			}
		}
		this.planned = Collections.unmodifiableList(compatible);
	}

	static GroupPlan of(QuestGraph graph, List<GroupMember> members)
	{
		GroupPlan plan = new GroupPlan(members);
		if (plan.planned.isEmpty())
		{
			return plan;
		}

		// Two classifiers per member: one with their rules, which is the real
		// verdict, and one without, which answers "could they otherwise" --
		// a quest that breaks a build is BUILD_BLOCKED before its other
		// requirements are even looked at, so without the second pass a
		// flagged quest the pure could not have done anyway would be
		// indistinguishable from one that is genuinely on the table.
		Map<GroupMember, QuestClassifier> withRules = new IdentityHashMap<>();
		Map<GroupMember, QuestClassifier> withoutRules = new IdentityHashMap<>();
		for (GroupMember member : plan.planned)
		{
			withRules.put(member, new QuestClassifier(graph, member.state()));
			withoutRules.put(member, new QuestClassifier(graph, member.state().withoutRules()));
			plan.doneCounts.put(member, 0);
		}

		for (QuestGraph.Quest quest : graph.quests())
		{
			List<String> doneBy = new ArrayList<>();
			List<String> stillNeed = new ArrayList<>();
			boolean allDoable = true;
			boolean allOtherwiseDoable = true;
			List<MemberBreach> breaches = new ArrayList<>();

			for (GroupMember member : plan.planned)
			{
				if (member.state().isComplete(quest.name()))
				{
					doneBy.add(member.name());
					plan.doneCounts.merge(member, 1, Integer::sum);
					allDoable = false;
					allOtherwiseDoable = false;
					continue;
				}
				stillNeed.add(member.name());
				if (!withRules.get(member).classify(quest).doable())
				{
					allDoable = false;
				}
				if (!withoutRules.get(member).classify(quest).doable())
				{
					allOtherwiseDoable = false;
				}
				for (BuildBreaches.Breach breach : BuildBreaches.of(quest, member.state()))
				{
					breaches.add(new MemberBreach(member.name(), breach));
				}
			}

			if (allDoable)
			{
				plan.together.add(quest);
			}
			else if (allOtherwiseDoable && !breaches.isEmpty())
			{
				plan.flagged.put(quest.name(), new Flag(quest, breaches));
			}
			if (!doneBy.isEmpty() && !stillNeed.isEmpty())
			{
				plan.doneBySome.add(new Partial(quest, doneBy, stillNeed));
			}
		}
		return plan;
	}

	/** Everyone in the party who shares, compatible or not, in the order given. */
	List<GroupMember> members()
	{
		return members;
	}

	/** The members this plan judged. */
	List<GroupMember> planned()
	{
		return planned;
	}

	List<QuestGraph.Quest> together()
	{
		return Collections.unmodifiableList(together);
	}

	List<Flag> flagged()
	{
		return Collections.unmodifiableList(new ArrayList<>(flagged.values()));
	}

	/** The flag on one quest by name, or null when it is not flagged. */
	Flag flag(String questName)
	{
		return flagged.get(questName);
	}

	List<Partial> doneBySome()
	{
		return Collections.unmodifiableList(doneBySome);
	}

	/** How many graph quests one planned member has finished; 0 for anyone not planned. */
	int doneCount(GroupMember member)
	{
		Integer count = doneCounts.get(member);
		return count == null ? 0 : count;
	}

	/** Who should hand what to whom, and what is left to get, for one quest. */
	ItemSplit split(QuestGraph.Quest quest)
	{
		return ItemSplit.of(quest, planned);
	}
}
