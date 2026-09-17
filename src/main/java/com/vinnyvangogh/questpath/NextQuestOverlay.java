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

	/** Item rows for the next quest, at the Normal list-length setting. */
	private static final int QUEST_ITEM_ROWS = 8;

	NextQuestOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Next on your route");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		// Two independent switches, both required: this overlay's own
		// on/off (unchanged) and the Quests tab filter, which now decides
		// which of the two route overlays the filter permits -- see
		// QuestJournalFilter's own class doc.
		return config.overlayNextQuest() && config.questJournalFilter().showsQuests();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return config.overlayNextQuestWidth();
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return config.overlayNextQuestLength();
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
				return nextRows(route, ctx, snapshot.items());
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
	private List<OverlayRow> nextRows(NextQuestStatus route, OverlayContext ctx, QuestItems items)
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
			int shown = Math.min(ctx.listCap(LOOKAHEAD), after.size() - 1);
			for (int i = 1; i <= shown; i++)
			{
				rows.add(OverlayRow.line(after.get(i).questName(), null,
					OverlayTheme.MUTED, OverlayTheme.MUTED));
			}
			int more = after.size() - 1 - shown;
			if (more > 0)
			{
				rows.add(OverlayRow.overflow("+ " + more + " more after that"));
			}
		}

		if (config.overlayNextQuestItems())
		{
			addQuestItems(rows, route.questName(), items, ctx);
		}

		return rows;
	}

	/**
	 * What the named quest itself needs, ticked against what the account
	 * holds.
	 *
	 * Optional, because it answers a different question from the rest of the
	 * panel: "what now" versus "can I actually start it now". Both are worth
	 * asking and only one of them is worth permanent screen space to everyone,
	 * so it is a setting rather than a default.
	 *
	 * The quest is matched by name against the item lists questpath sent,
	 * which is the same way everything else in this plugin joins the two
	 * sides. A quest with no entry there is not a quest with no items -- it is
	 * one the item list does not cover, most often because the client has
	 * shown no containers yet -- so it says that rather than rendering an
	 * empty list as "you need nothing".
	 */
	private static void addQuestItems(List<OverlayRow> rows, String questName,
		QuestItems items, OverlayContext ctx)
	{
		rows.add(OverlayRow.header("It needs"));

		if (items == null)
		{
			rows.add(OverlayRow.note("Item list not loaded yet.", OverlayTheme.MUTED));
			return;
		}

		QuestItems.QuestNeeds needs = null;
		for (QuestItems.QuestNeeds candidate : items.quests())
		{
			if (candidate.questName().equalsIgnoreCase(questName))
			{
				needs = candidate;
				break;
			}
		}

		if (needs == null || needs.lines().isEmpty())
		{
			rows.add(OverlayRow.note("questpath lists no items for this one.", OverlayTheme.MUTED));
			return;
		}

		if (items.freshness() == QuestItems.Freshness.LAST_SYNC)
		{
			rows.add(OverlayRow.note("Counts are from your last sync.", OverlayTheme.MUTED));
		}
		else if (!items.bankSeen())
		{
			// Same caveat the shopping list carries: with no bank opened,
			// every count is what is on you, so "not held" means "not
			// carried" and not "not owned".
			rows.add(OverlayRow.note("Bank not opened -- counts are what you carry.",
				OverlayTheme.WARN));
		}

		int shown = Math.min(needs.lines().size(), ctx.itemListCap(QUEST_ITEM_ROWS));
		for (int i = 0; i < shown; i++)
		{
			QuestItems.Line line = needs.lines().get(i);
			if (line.unresolved())
			{
				// The wiki asked for something questpath could not match to a
				// real item -- "Any fruit blast excluding a premade fr'
				// blast". There is no id to draw, no count to tick, and no
				// honest way to say whether you have it. Shown in the wiki's
				// own words and marked, because leaving it out is what made
				// this list quietly short in the first place.
				rows.add(OverlayRow.item(0, line.name(), "?", false,
					OverlayTheme.MUTED, OverlayTheme.WARN));
				continue;
			}
			String label = line.optional() ? line.name() + " (optional)" : line.name();
			rows.add(OverlayRow.item(line.itemId(), label, line.describe(), line.held(),
				OverlayTheme.TEXT, line.held() ? OverlayTheme.GOOD : OverlayTheme.WARN));
		}
		int more = needs.lines().size() - shown;
		if (more > 0)
		{
			rows.add(OverlayRow.overflow("+ " + more + " more it needs"));
		}
		if (needs.unresolvedCount() > 0)
		{
			rows.add(OverlayRow.note("? = questpath could not match this to an item,"
				+ " so it cannot check your bank for it.", OverlayTheme.MUTED));
		}
	}
}
