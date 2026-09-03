package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The overlay a player leaves on permanently: what quest comes next, and how
 * far along the saved route the account is.
 *
 * <p>The sidebar already lists the whole plan. This exists for the moment the
 * player is not looking at it -- mid-quest, walking somewhere -- and wants
 * the answer to "what now" without opening the panel. So the head of the
 * route carries the visual weight of a {@link OverlayRow#hero}, the progress
 * bar is what makes "4 of 21" mean something at a glance, and the few quests
 * after it are a preview of what is coming, not a second copy of the
 * sidebar.
 */
class NextQuestOverlay extends QuestpathOverlay
{
	/**
	 * How many upcoming quests to preview below the current one.
	 *
	 * Three or four reads as "here is what's coming next"; twenty is the
	 * sidebar this overlay exists to be a glanceable alternative to.
	 */
	private static final int LOOKAHEAD = 4;

	NextQuestOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Next on your route");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.overlayNextQuest();
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		NextQuestStatus route = snapshot.route();
		if (route == null)
		{
			// Distinct from NO_ROUTE: the client simply has not loaded quest
			// states yet, so questpath cannot tell finished from unfinished,
			// let alone say there is no plan. Saying "no route" here would be
			// a guess dressed up as an answer.
			return Collections.singletonList(OverlayRow.note(
				"Quest progress hasn't loaded yet, so the route can't be judged.",
				OverlayTheme.MUTED));
		}

		switch (route.state())
		{
			case NO_ROUTE:
				return Collections.singletonList(OverlayRow.note(
					"No route saved in questpath yet. Save a plan there and it will show up here.",
					OverlayTheme.MUTED));

			case ALL_DONE:
				return allDoneRows(route);

			case NEXT:
			default:
				return nextRows(route);
		}
	}

	/**
	 * A congratulation, the full bar, and the count -- never an empty list.
	 * Finishing a plan is a real state and worth the screen space, not a
	 * silent disappearance of the panel.
	 */
	private List<OverlayRow> allDoneRows(NextQuestStatus route)
	{
		List<OverlayRow> rows = new ArrayList<>();
		rows.add(OverlayRow.hero("Route complete!", OverlayTheme.GOOD));
		rows.add(OverlayRow.bar("Route progress", route.doneCount() + " of " + route.size(),
			1.0, OverlayTheme.GOOD));
		return rows;
	}

	/**
	 * The next quest as the answer, the bar as the context, and a capped
	 * preview of what follows -- the lookahead reads from
	 * {@link NextQuestStatus#upcoming()} rather than the raw route because
	 * finished quests are history and this panel's space is worth more spent
	 * on what is left.
	 */
	private List<OverlayRow> nextRows(NextQuestStatus route)
	{
		List<OverlayRow> rows = new ArrayList<>();
		rows.add(OverlayRow.hero(route.questName(), OverlayTheme.ACCENT));

		double fraction = route.size() == 0 ? 0 : route.doneCount() / (double) route.size();
		rows.add(OverlayRow.bar("Route progress", route.doneCount() + " of " + route.size(),
			fraction, OverlayTheme.GOOD));

		// upcoming() puts the current quest first, so the preview starts
		// after it -- otherwise the hero row and the first preview line
		// would say the same name twice.
		List<NextQuestStatus.Step> after = route.upcoming();
		if (after.size() > 1)
		{
			rows.add(OverlayRow.header("Then"));
			int shown = Math.min(LOOKAHEAD, after.size() - 1);
			for (int i = 1; i <= shown; i++)
			{
				rows.add(OverlayRow.line(after.get(i).questName(), null,
					OverlayTheme.MUTED, OverlayTheme.MUTED));
			}
			int more = after.size() - 1 - shown;
			if (more > 0)
			{
				rows.add(OverlayRow.note("+ " + more + " more after that", OverlayTheme.MUTED));
			}
		}

		return rows;
	}
}
