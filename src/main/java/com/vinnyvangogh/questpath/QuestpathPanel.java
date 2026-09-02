package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The questpath sidebar.
 *
 * Everything here is derived from the account's own levels — no quest data, no
 * bundle, no server. Three questions, in the order a capped account asks them:
 *
 * <ol>
 *   <li>Where is my combat level, and how close is the next one?</li>
 *   <li>How much free Ranged and Magic do I still have, in levels and in XP?</li>
 *   <li>What would each stat cost me to buy the next combat level?</li>
 * </ol>
 *
 * The panel is rebuilt wholesale on each update. At this size that is cheap
 * and it removes a class of bug where a stale row survives a refresh. When it
 * grows a quest table, that changes.
 */
class QuestpathPanel extends PluginPanel
{
	private final JPanel body = new JPanel();
	private final JLabel emptyState = new JLabel();

	QuestpathPanel()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

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
	 * Says there is nothing to show rather than showing a combat level built
	 * from levels the client has not loaded. A logged-out client reports every
	 * skill as 1, which renders as a confident, wrong "combat 3".
	 */
	void showLoggedOut()
	{
		SwingUtilities.invokeLater(() ->
		{
			body.removeAll();
			emptyState.setText("Log in to see your levels.");
			body.add(emptyState);
			body.revalidate();
			body.repaint();
		});
	}

	private void render(CombatStats stats)
	{
		body.removeAll();

		int attack = stats.get(Skill.ATTACK);
		int strength = stats.get(Skill.STRENGTH);
		int ceiling = CombatMath.freeRangedCeiling(attack, strength);

		body.add(heading("Combat"));
		body.add(combatBlock(stats));
		body.add(Box.createVerticalStrut(14));

		body.add(heading("Free Ranged / Magic"));
		body.add(ceilingBlock(stats, ceiling));
		body.add(Box.createVerticalStrut(14));

		body.add(heading("Cost of the next combat level"));
		body.add(costBlock(stats));

		body.revalidate();
		body.repaint();
	}

	/** Combat level, and how far into it the account is. */
	private static JPanel combatBlock(CombatStats stats)
	{
		JPanel panel = column();

		int level = CombatMath.combatLevel(stats);
		JLabel value = new JLabel(String.valueOf(level));
		value.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
		value.setForeground(Color.WHITE);
		panel.add(value);

		int percent = (int) Math.round(CombatMath.combatProgress(stats) * 100.0);
		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue(percent);
		bar.setStringPainted(true);
		bar.setString(percent + "% to " + (level + 1));
		bar.setForeground(ColorScheme.BRAND_ORANGE);
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		panel.add(bar);

		return panel;
	}

	/**
	 * How much Ranged and Magic are still free, in levels and in the XP those
	 * levels are actually worth.
	 *
	 * A skill sitting above the ceiling is called out rather than shown as
	 * zero headroom: it means the account has already spent combat levels on
	 * that skill, which is the one thing a capped build cannot undo.
	 */
	private static JPanel ceilingBlock(CombatStats stats, int ceiling)
	{
		JPanel panel = column();

		JLabel summary = new JLabel("Ceiling " + ceiling
			+ " — at Attack " + stats.get(Skill.ATTACK) + ", Strength " + stats.get(Skill.STRENGTH));
		summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(summary);
		panel.add(Box.createVerticalStrut(4));

		JPanel grid = grid();
		addCeilingRow(grid, "Ranged", stats.get(Skill.RANGED), ceiling);
		addCeilingRow(grid, "Magic", stats.get(Skill.MAGIC), ceiling);
		panel.add(grid);

		CombatMath.CeilingStep step = CombatMath.nextCeilingStep(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));
		panel.add(Box.createVerticalStrut(4));
		JLabel next = new JLabel(step == null
			? "No Strength level raises it further."
			: "Strength " + step.atStrength + " raises it to " + step.ceiling
				+ " (+" + step.gained + ")");
		next.setFont(FontManager.getRunescapeSmallFont());
		next.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(next);

		return panel;
	}

	private static void addCeilingRow(JPanel grid, String name, int current, int ceiling)
	{
		int headroom = ceiling - current;
		String detail;
		Color color;
		if (headroom > 0)
		{
			detail = "+" + headroom + " free  ·  " + XpTable.format(XpTable.xpBetween(current, ceiling)) + " xp";
			color = ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		else if (headroom == 0)
		{
			detail = "at the ceiling";
			color = ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		else
		{
			detail = (-headroom) + " past the ceiling";
			color = ColorScheme.PROGRESS_ERROR_COLOR;
		}

		addRow(grid, name + " " + current, detail, color);
	}

	/**
	 * What each stat would cost to buy the next combat level, cheapest first.
	 *
	 * Levels alone are misleading — "Prayer +3" and "Strength +1" read as
	 * comparable until the XP is beside them — so both are shown. For a pure
	 * this is the whole decision: the cheap ones are the ones that end the
	 * build.
	 */
	private static JPanel costBlock(CombatStats stats)
	{
		JPanel panel = column();

		Map<Skill, Integer> deltas = CombatMath.levelsToNextCombat(stats);
		if (deltas.isEmpty())
		{
			JLabel none = new JLabel("Nothing raises combat at or below 99.");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(none);
			return panel;
		}

		List<Map.Entry<Skill, Integer>> ordered = new ArrayList<>(deltas.entrySet());
		ordered.sort(Comparator.comparingInt(entry ->
			(int) Math.min(Integer.MAX_VALUE, xpCost(stats, entry.getKey(), entry.getValue()))));

		JPanel grid = grid();
		for (Map.Entry<Skill, Integer> entry : ordered)
		{
			Skill skill = entry.getKey();
			int delta = entry.getValue();
			int target = stats.get(skill) + delta;
			addRow(grid,
				skillName(skill) + " " + target,
				"+" + delta + "  ·  " + XpTable.format(xpCost(stats, skill, delta)) + " xp",
				costColor(delta));
		}
		panel.add(grid);
		return panel;
	}

	/**
	 * The XP between where the skill is now and the level that buys a combat
	 * level.
	 *
	 * This is an upper bound on the real cost: the client reports levels, not
	 * XP, so it assumes the account is sitting exactly on its current level's
	 * threshold. Reading real XP would need a StatChanged-driven cache, and
	 * overstating the grind slightly is the safe direction to be wrong in.
	 */
	private static long xpCost(CombatStats stats, Skill skill, int delta)
	{
		int current = stats.get(skill);
		return XpTable.xpBetween(current, current + delta);
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

	private static void addRow(JPanel grid, String label, String detail, Color detailColor)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(1, 0, 1, 6);

		JLabel left = new JLabel(label);
		left.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		grid.add(left, c);

		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		JLabel right = new JLabel(detail, SwingConstants.RIGHT);
		right.setForeground(detailColor);
		grid.add(right, c);
	}

	private static String skillName(Skill skill)
	{
		String name = skill.getName();
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JPanel grid()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static Component heading(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 3));
		return label;
	}
}
