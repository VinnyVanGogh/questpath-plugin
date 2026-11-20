package com.vinnyvangogh.questpath;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * When to warn that a quest cannot be finished yet, and only once.
 *
 * <p>Two signals reach the same warning. Quest Helper's {@code PluginMessage}
 * (namespace {@code questhelper}, name {@code selected}, data {@code quest};
 * an empty payload means cleared) names the quest the player picked, and the
 * game's own quest state names one the player has started. The first is inert
 * until Quest Helper sends it; the second works today.
 *
 * <p>Warn-only by design: nothing here touches clicks or menus. Not
 * thread-safe; call it on the client thread.
 */
final class QuestWarnings
{
	static final String NAMESPACE = "questhelper";
	static final String SELECTED = "selected";

	private final BooleanSupplier enabled;
	private final Predicate<String> muted;
	private final Consumer<QuestWarning> sink;

	/** The quest Quest Helper currently has selected, handled or not. */
	private String selected;

	/** In-progress quests at the last look; null until the first look primes it. */
	private Set<String> lastInProgress;

	private QuestWarning current;

	QuestWarnings(BooleanSupplier enabled, Predicate<String> muted, Consumer<QuestWarning> sink)
	{
		this.enabled = enabled;
		this.muted = muted;
		this.sink = sink;
	}

	/**
	 * Quest Helper selected a quest, or cleared its selection (null or blank).
	 * The same quest selected twice in a row warns once; clearing and picking
	 * it again warns again.
	 */
	void onSelected(String helperName, QuestGraph graph, PlayerState account)
	{
		if (helperName == null || helperName.trim().isEmpty())
		{
			selected = null;
			return;
		}
		if (helperName.equals(selected))
		{
			return;
		}
		selected = helperName;
		warnFor(QuestWarning.resolve(graph, helperName), graph, account);
	}

	/**
	 * The client's in-progress quests, read on the client thread. A quest that
	 * was not in progress at the last look and is now has just been started.
	 * The first look after login only records a baseline, so quests already
	 * under way never warn.
	 */
	void onQuestStates(Set<String> inProgress, QuestGraph graph, PlayerState account)
	{
		Set<String> before = lastInProgress;
		lastInProgress = new HashSet<>(inProgress);
		if (before == null)
		{
			return;
		}
		for (String name : inProgress)
		{
			if (before.contains(name))
			{
				continue;
			}
			QuestGraph.Quest quest = QuestWarning.resolve(graph, name);
			if (quest != null && selected != null && sameQuest(selected, quest))
			{
				// Already handled when it was selected; do not say it twice.
				continue;
			}
			warnFor(quest, graph, account);
		}
	}

	/** Forget everything: a logout, so the next login is a fresh baseline. */
	void reset()
	{
		selected = null;
		lastInProgress = null;
		current = null;
	}

	/** The warning on screen, or null. */
	QuestWarning current()
	{
		return current;
	}

	void dismiss()
	{
		current = null;
	}

	private static boolean sameQuest(String helperName, QuestGraph.Quest quest)
	{
		return helperName.trim().equalsIgnoreCase(quest.name());
	}

	private void warnFor(QuestGraph.Quest quest, QuestGraph graph, PlayerState account)
	{
		if (quest == null || !enabled.getAsBoolean() || muted.test(quest.name()))
		{
			return;
		}
		QuestWarning warning = QuestWarning.of(graph, account, quest);
		if (warning == null)
		{
			return;
		}
		current = warning;
		sink.accept(warning);
	}
}
