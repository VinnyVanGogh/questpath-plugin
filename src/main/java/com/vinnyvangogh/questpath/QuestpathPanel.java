package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
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
	void update(CombatStats stats, SkillXp xp, SyncStatus sync)
	{
		SwingUtilities.invokeLater(() -> render(stats, xp, sync));
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

	private void render(CombatStats stats, SkillXp xp, SyncStatus sync)
	{
		body.removeAll();

		int attack = stats.get(Skill.ATTACK);
		int strength = stats.get(Skill.STRENGTH);
		int ceiling = CombatMath.freeRangedCeiling(attack, strength);

		body.add(heading(PanelSections.COMBAT.title()));
		body.add(combatBlock(stats));
		body.add(Box.createVerticalStrut(14));

		body.add(heading(PanelSections.CEILING.title()));
		body.add(ceilingBlock(stats, xp, ceiling));
		body.add(Box.createVerticalStrut(14));

		body.add(heading(PanelSections.COST.title()));
		body.add(costBlock(stats, xp));
		body.add(Box.createVerticalStrut(14));

		body.add(heading(PanelSections.SYNC.title()));
		body.add(syncBlock(sync));

		// Everything still to be built, named and explained rather than left
		// out. A section that silently does not exist reads as a tool that
		// does less than it does; one that says what it will show reads as a
		// tool being built. Neither is allowed to look like an empty state,
		// which would mean questpath had looked and found nothing.
		for (PanelSections section : PanelSections.values())
		{
			if (section.status() != PanelSections.Status.NOT_BUILT)
			{
				continue;
			}
			body.add(Box.createVerticalStrut(14));
			body.add(heading(section.title()));
			body.add(notBuiltBlock(section));
		}

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
	private static JPanel ceilingBlock(CombatStats stats, SkillXp xp, int ceiling)
	{
		JPanel panel = column();

		JLabel summary = new JLabel("Ceiling " + ceiling
			+ " — at Attack " + stats.get(Skill.ATTACK) + ", Strength " + stats.get(Skill.STRENGTH));
		summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(summary);
		panel.add(Box.createVerticalStrut(4));

		JPanel grid = grid();
		addCeilingRow(grid, xp, Skill.RANGED, stats.get(Skill.RANGED), ceiling);
		addCeilingRow(grid, xp, Skill.MAGIC, stats.get(Skill.MAGIC), ceiling);
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

	private static void addCeilingRow(JPanel grid, SkillXp xp, Skill skill, int current, int ceiling)
	{
		int headroom = ceiling - current;
		String detail;
		Color color;
		if (headroom > 0)
		{
			detail = "+" + headroom + " free" + xpSuffix(xp, skill, ceiling);
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

		addRow(grid, skillName(skill) + " " + current, detail, color);
	}

	/**
	 * What each stat would cost to buy the next combat level, cheapest first.
	 *
	 * Levels alone are misleading — "Prayer +3" and "Strength +1" read as
	 * comparable until the XP is beside them — so both are shown. For a pure
	 * this is the whole decision: the cheap ones are the ones that end the
	 * build.
	 */
	private static JPanel costBlock(CombatStats stats, SkillXp xp)
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

		// Ordered by what the step actually costs. At a pure's stats almost
		// everything is "+1 level", which discriminates nothing; the XP is
		// what separates a Defence level that ends the build from a Strength
		// level that is a week of work. A skill whose XP is not known yet
		// sorts last rather than sorting as free.
		List<Map.Entry<Skill, Integer>> ordered = new ArrayList<>(deltas.entrySet());
		ordered.sort(Comparator.comparingLong(entry ->
		{
			long cost = xp.toLevel(entry.getKey(), stats.get(entry.getKey()) + entry.getValue());
			return cost < 0 ? Long.MAX_VALUE : cost;
		}));

		JPanel grid = grid();
		for (Map.Entry<Skill, Integer> entry : ordered)
		{
			Skill skill = entry.getKey();
			int delta = entry.getValue();
			int target = stats.get(skill) + delta;
			addRow(grid,
				skillName(skill) + " " + target,
				"+" + delta + xpSuffix(xp, skill, target),
				costColor(delta));
		}
		panel.add(grid);
		return panel;
	}

	/**
	 * The experience part of a row, or nothing at all when the client has not
	 * supplied this skill's XP yet.
	 *
	 * Nothing, deliberately. The earlier version priced every row from level
	 * thresholds — xpFor(target) minus xpFor(current) — which is an upper
	 * bound on the cost, not the cost: someone most of the way through
	 * Strength 83 was told the next level costs 278.3k. An exact figure is
	 * available from the client, and where it is not, an omission is honest
	 * where a bound reads as fact.
	 */
	private static String xpSuffix(SkillXp xp, Skill skill, int targetLevel)
	{
		long cost = xp.toLevel(skill, targetLevel);
		if (cost < 0)
		{
			return "";
		}
		return "  ·  " + XpTable.format(cost) + " xp";
	}


	/**
	 * Whether the account-state push is working, in the words that tell the
	 * difference between the ways it can fail.
	 *
	 * The push is deliberately silent in game, which leaves no way to tell
	 * "working" from "wrong token" from "questpath is closed". Two of those
	 * three are things only the player can fix, and neither is discoverable
	 * without being told.
	 */
	private static JPanel syncBlock(SyncStatus sync)
	{
		JPanel panel = column();

		JLabel line = new JLabel(sync.describe(Instant.now()));
		line.setForeground(sync.needsAttention()
			? ColorScheme.PROGRESS_ERROR_COLOR
			: ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(line);

		if (sync.state() == SyncStatus.State.NO_TOKEN)
		{
			JLabel hint = new JLabel("Run: questpath -plugin-token");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(hint);
		}
		return panel;
	}

	/**
	 * A section that does not exist yet, saying so and saying what it will be.
	 *
	 * Not an empty state and not a spinner. "questpath looked and found
	 * nothing" and "questpath cannot answer this yet" are different claims,
	 * and only one of them is true here.
	 */
	private static JPanel notBuiltBlock(PanelSections section)
	{
		JPanel panel = column();

		JLabel badge = new JLabel("Not built yet");
		badge.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		badge.setForeground(ColorScheme.BRAND_ORANGE.darker());
		panel.add(badge);

		JLabel plan = new JLabel(wrap(section.plan()));
		plan.setFont(FontManager.getRunescapeSmallFont());
		plan.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(plan);

		return panel;
	}

	/**
	 * Swing labels do not wrap, and the sidebar is narrow. HTML is the
	 * supported way to get a multi-line label, with the width pinned so it
	 * does not grow the panel wider than the sidebar allows.
	 */
	private static String wrap(String text)
	{
		return "<html><body style='width:150px'>" + escape(text) + "</body></html>";
	}

	/**
	 * These strings are ours, not user input, but a stray angle bracket in one
	 * would be swallowed as markup by the HTML label above and quietly delete
	 * the rest of the sentence.
	 */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
