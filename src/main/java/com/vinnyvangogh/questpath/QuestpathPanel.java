package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The questpath sidebar.
 *
 * Today it answers one question, the one that needs no quest data and no
 * server: what does the next combat level cost, in every stat that could buy
 * it. For a pure that is the question behind every training decision — one
 * Strength level is worth having, one Defence level may be the end of the
 * build.
 *
 * Everything is rebuilt on {@link #update}, which is cheap at this size and
 * removes a class of bug where a stale row survives a refresh. If this panel
 * grows a quest table, that changes.
 */
class QuestpathPanel extends PluginPanel
{
	private final JLabel combatLevel = new JLabel();
	private final JLabel combatProgress = new JLabel();
	private final JLabel rangedCeiling = new JLabel();
	private final JPanel costs = new JPanel();
	private final JLabel emptyState = new JLabel();

	QuestpathPanel()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		body.add(heading("Combat level"));
		body.add(combatLevel);
		body.add(combatProgress);
		body.add(Box.createVerticalStrut(12));

		body.add(heading("Free Ranged / Magic ceiling"));
		body.add(rangedCeiling);
		body.add(Box.createVerticalStrut(12));

		body.add(heading("Levels to the next combat level"));
		costs.setLayout(new GridLayout(0, 2, 4, 2));
		costs.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		body.add(costs);

		emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(emptyState);

		combatLevel.setFont(FontManager.getRunescapeBoldFont());
		for (JLabel label : new JLabel[]{combatProgress, rangedCeiling})
		{
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		add(body, BorderLayout.NORTH);
		showLoggedOut();
	}

	/**
	 * Renders the readout for one set of levels. Safe to call from the client
	 * thread: it hops to the EDT itself, because Swing state must only be
	 * touched there and every caller here is a game event.
	 */
	void update(CombatStats stats)
	{
		SwingUtilities.invokeLater(() -> render(stats));
	}

	/**
	 * Says there is nothing to show yet rather than showing a combat level
	 * built from levels the client has not loaded. A logged-out client reports
	 * every skill as 1, which renders as a confident, wrong "combat 3".
	 */
	void showLoggedOut()
	{
		SwingUtilities.invokeLater(() ->
		{
			combatLevel.setText("—");
			combatProgress.setText("");
			rangedCeiling.setText("");
			costs.removeAll();
			emptyState.setText("Log in to see your levels.");
			revalidate();
			repaint();
		});
	}

	private void render(CombatStats stats)
	{
		int level = CombatMath.combatLevel(stats);
		combatLevel.setText(String.valueOf(level));
		combatProgress.setText(String.format("%.0f%% of the way to %d",
			CombatMath.combatProgress(stats) * 100.0, level + 1));

		int ceiling = CombatMath.freeRangedCeiling(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));
		rangedCeiling.setText(ceiling + " — free at Attack " + stats.get(Skill.ATTACK)
			+ ", Strength " + stats.get(Skill.STRENGTH));

		costs.removeAll();
		Map<Skill, Integer> deltas = CombatMath.levelsToNextCombat(stats);
		if (deltas.isEmpty())
		{
			emptyState.setText("Nothing raises combat at or below 99.");
		}
		else
		{
			emptyState.setText("");
			// Cheapest first: for a pure the interesting fact is which stat is
			// about to cost a combat level, not the alphabet.
			List<Map.Entry<Skill, Integer>> ordered = new ArrayList<>(deltas.entrySet());
			ordered.sort(Comparator.comparingInt(Map.Entry::getValue));

			for (Map.Entry<Skill, Integer> entry : ordered)
			{
				Skill skill = entry.getKey();
				int delta = entry.getValue();
				costs.add(cell(skillName(skill), ColorScheme.LIGHT_GRAY_COLOR));
				costs.add(cell("+" + delta + "  (" + (stats.get(skill) + delta) + ")", costColor(delta)));
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * A stat one level from costing a combat level is the warning a pure
	 * actually wants; a stat fifty levels away is context. The threshold is a
	 * display choice, not a rule the build enforces.
	 */
	private static Color costColor(int delta)
	{
		if (delta <= 1)
		{
			return ColorScheme.PROGRESS_ERROR_COLOR;
		}
		if (delta <= 3)
		{
			return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		return ColorScheme.PROGRESS_COMPLETE_COLOR;
	}

	private static String skillName(Skill skill)
	{
		String name = skill.getName();
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}

	private static JLabel cell(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		return label;
	}

	private static Component heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		return label;
	}
}
