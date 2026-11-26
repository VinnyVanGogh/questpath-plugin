package com.vinnyvangogh.questpath;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Warns once per selection or newly started quest. Client thread only. */
final class QuestWarnings
{
	static final String NAMESPACE = "questhelper";
	static final String SELECTED = "selected";

	private final BooleanSupplier enabled;
	private final Predicate<String> muted;
	private final Consumer<QuestWarning> sink;
	private String selected;
	/** In-progress quests at the last look; null until login primes it. */
	private Set<String> lastInProgress;

	QuestWarnings(BooleanSupplier enabled, Predicate<String> muted, Consumer<QuestWarning> sink)
	{
		this.enabled = enabled;
		this.muted = muted;
		this.sink = sink;
	}

	void onSelected(String name, QuestGraph graph, PlayerState account)
	{
		if (name == null || name.trim().isEmpty())
		{
			selected = null;
		}
		else if (!name.equals(selected))
		{
			selected = name;
			warn(QuestWarning.resolve(graph, name), graph, account);
		}
	}

	void onQuestStates(Set<String> inProgress, QuestGraph graph, PlayerState account)
	{
		Set<String> before = lastInProgress;
		lastInProgress = new HashSet<>(inProgress);
		for (String name : inProgress)
		{
			if (before == null || before.contains(name))
			{
				continue;
			}
			QuestGraph.Quest quest = QuestWarning.resolve(graph, name);
			if (quest == null || !quest.name().equalsIgnoreCase(selected))
			{
				warn(quest, graph, account);
			}
		}
	}

	void reset()
	{
		selected = null;
		lastInProgress = null;
	}

	private void warn(QuestGraph.Quest quest, QuestGraph graph, PlayerState account)
	{
		if (quest == null || !enabled.getAsBoolean() || muted.test(quest.name()))
		{
			return;
		}
		QuestWarning warning = QuestWarning.of(graph, account, quest);
		if (warning != null)
		{
			sink.accept(warning);
		}
	}
}
