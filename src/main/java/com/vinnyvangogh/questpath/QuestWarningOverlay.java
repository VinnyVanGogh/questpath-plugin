package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The dismissable popup; its menu is RuneLite's own overlay menu, never the game's. */
class QuestWarningOverlay extends QuestpathOverlay
{
	static final String MUTE = "Don't warn again for this quest";

	QuestWarningOverlay(QuestpathPlugin plugin, QuestpathConfig config, OverlayIcons icons)
	{
		super(plugin, config, icons, "Quest warning");
		setPosition(OverlayPosition.TOP_CENTER);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Dismiss", "Quest warning");
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, MUTE, "Quest warning");
	}

	@Override
	boolean enabled(QuestpathConfig config)
	{
		return config.questWarnings();
	}

	@Override
	PanelWidth width(QuestpathConfig config)
	{
		return PanelWidth.WIDE;
	}

	@Override
	PanelListLength length(QuestpathConfig config)
	{
		return PanelListLength.EVERYTHING;
	}

	@Override
	List<OverlayRow> rows(OverlaySnapshot snapshot, OverlayContext ctx)
	{
		return rowsFor(plugin().questWarning());
	}

	static List<OverlayRow> rowsFor(QuestWarning warning)
	{
		if (warning == null)
		{
			return Collections.emptyList();
		}
		List<OverlayRow> rows = new ArrayList<>();
		rows.add(OverlayRow.hero(warning.headline(), OverlayTheme.WARN));
		for (String line : warning.lines)
		{
			rows.add(OverlayRow.note(line, OverlayTheme.MUTED));
		}
		return rows;
	}
}
