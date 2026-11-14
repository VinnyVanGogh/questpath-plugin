package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import com.vinnyvangogh.questpath.groupshop.ItemNeed;
import com.vinnyvangogh.questpath.groupshop.MemberNeeds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Turns party members, as Quest Route decodes them, into the plain inputs the
 * {@code groupshop} package plans with.
 *
 * <p>Which quests count for a member, as the Board decided it:
 * <ul>
 * <li>their active route, when they have one -- the quests they said they are
 * doing, in their order;</li>
 * <li>otherwise every quest they have not finished that their build allows.</li>
 * </ul>
 * Either way a quest the classifier calls permanent -- {@link
 * QuestStatus#BUILD_BLOCKED}, {@link QuestStatus#UNREACHABLE} or {@link
 * QuestStatus#REFUSED} -- never counts. A route can name one (the route was
 * saved before a rule changed), and buying items for a quest that would break
 * the build is exactly what a capped account is here to avoid.
 *
 * <p>Item lines follow {@link ItemSplit}'s rule: optional lines, unmatched
 * wiki prose and category lines ("any pickaxe") are not shopped for.
 */
final class GroupShoppingNeeds
{
	private GroupShoppingNeeds()
	{
	}

	/** One per compatible member, in the order given. */
	static List<MemberNeeds> of(QuestGraph graph, List<GroupMember> members, Predicate<GroupMember> stale)
	{
		List<MemberNeeds> out = new ArrayList<>();
		if (graph == null || members == null)
		{
			return out;
		}
		for (GroupMember member : members)
		{
			if (!member.compatible())
			{
				continue;
			}
			List<ItemNeed> needs = new ArrayList<>();
			for (QuestGraph.Quest quest : questsFor(graph, member))
			{
				addNeeds(needs, quest);
			}
			out.add(new MemberNeeds(member.name(), member.kind(), member.heldCounts(), member.bankSeen(),
				stale != null && stale.test(member), needs));
		}
		return out;
	}

	/** The quests counted for one member; see the class doc. */
	static List<QuestGraph.Quest> questsFor(QuestGraph graph, GroupMember member)
	{
		QuestClassifier classifier = new QuestClassifier(graph, member.state());
		List<QuestGraph.Quest> candidates = new ArrayList<>();
		if (member.route().isEmpty())
		{
			candidates.addAll(graph.quests());
		}
		else
		{
			for (String name : member.route())
			{
				QuestGraph.Quest quest = name == null ? null : graph.byName(name);
				if (quest != null)
				{
					candidates.add(quest);
				}
			}
		}
		List<QuestGraph.Quest> out = new ArrayList<>();
		for (QuestGraph.Quest quest : candidates)
		{
			QuestStatus status = classifier.classify(quest);
			if (status != null && status != QuestStatus.COMPLETE && !status.permanent())
			{
				out.add(quest);
			}
		}
		return out;
	}

	private static void addNeeds(List<ItemNeed> needs, QuestGraph.Quest quest)
	{
		for (QuestGraph.Item item : quest.items())
		{
			if (item.optional() || item.unresolved() || item.grouped() || item.name().isEmpty())
			{
				continue;
			}
			boolean stated = item.quantity() > 0;
			needs.add(new ItemNeed(quest.name(), item.name(), item.itemId(), stated ? item.quantity() : 1, stated,
				item.obtainable()));
		}
	}

	/** The newest group storage snapshot any member carries, or null. */
	static GroupStorage storage(List<GroupMember> members)
	{
		List<GroupStorage> snapshots = new ArrayList<>();
		for (GroupMember member : members)
		{
			snapshots.add(member.groupStorage());
		}
		return GroupStorage.freshest(snapshots);
	}
}
