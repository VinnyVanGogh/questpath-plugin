package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

/**
 * The questpath sidebar.
 *
 * <p>Five tabs, because one column of nine sections is a scroll rather than a
 * readout. They group by the question being asked rather than by what happens
 * to be built — "what is next", "can I do this quest" and "show me every
 * quest" are three views of one thing, so they share a tab.
 *
 * <p>The tabs are built once, in the constructor, and only their contents are
 * refilled on update. That is not tidiness: the panel refreshes on every
 * experience drop, and rebuilding the tab group would snap the selection back
 * to Combat every few seconds while training — unusable for exactly the player
 * this is written for.
 */
class QuestpathPanel extends PluginPanel
{
	/**
	 * The skill icons are around 25px; the tab strip has five of them across a
	 * 225px sidebar, so they are scaled down to leave room for the padding
	 * between them.
	 */
	private static final int TAB_ICON_SIZE = 20;

	private final Map<PanelSections.Tab, JPanel> tabBodies = new EnumMap<>(PanelSections.Tab.class);
	private final JPanel display = new JPanel(new BorderLayout());

	QuestpathPanel(SkillIconManager skillIcons)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		// One row, equal widths. Five labels at the sidebar's 225px is about
		// six characters each, which is why the tab names are one word.
		tabGroup.setLayout(new GridLayout(0, PanelSections.Tab.values().length, 2, 2));
		tabGroup.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		MaterialTab first = null;
		for (PanelSections.Tab tab : PanelSections.Tab.values())
		{
			JPanel bodyForTab = new JPanel();
			bodyForTab.setLayout(new BoxLayout(bodyForTab, BoxLayout.Y_AXIS));
			tabBodies.put(tab, bodyForTab);

			// Pinned to NORTH so a short tab's content sits at the top rather
			// than stretching to fill the panel.
			JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.add(bodyForTab, BorderLayout.NORTH);

			MaterialTab materialTab = tabFor(skillIcons, tab, tabGroup, wrapper);
			tabGroup.addTab(materialTab);
			if (first == null)
			{
				first = materialTab;
			}
		}

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		if (first != null)
		{
			tabGroup.select(first);
		}
		showLoggedOut();
	}

	/**
	 * One tab, showing the game's own skill-tab art.
	 *
	 * Falls back to the text label if the icon cannot be produced. A tab strip
	 * with a blank tab in it is unusable and undiagnosable — the word is worse
	 * than the picture and far better than nothing.
	 *
	 * The label becomes the tooltip either way: three of the five icons are
	 * borrowed from skills that have nothing to do with the tab, because the
	 * concepts they stand for have no skill, and an icon that has to be
	 * learned needs a way to be learned.
	 */
	private static MaterialTab tabFor(SkillIconManager skillIcons, PanelSections.Tab tab,
		MaterialTabGroup group, JPanel content)
	{
		MaterialTab materialTab = null;
		if (skillIcons != null)
		{
			BufferedImage icon = skillIcons.getSkillImage(tab.icon(), true);
			if (icon != null)
			{
				materialTab = new MaterialTab(
					new ImageIcon(ImageUtil.resizeImage(icon, TAB_ICON_SIZE, TAB_ICON_SIZE)),
					group, content);
			}
		}
		if (materialTab == null)
		{
			materialTab = new MaterialTab(tab.label(), group, content);
		}
		materialTab.setToolTipText(tab.label());
		return materialTab;
	}

	/**
	 * Renders the readout for one set of levels. Safe to call from the client
	 * thread: it hops to the EDT itself, because Swing state must only be
	 * touched there and every caller here is a game event.
	 */
	void update(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items)
	{
		SwingUtilities.invokeLater(() -> render(stats, xp, sync, rules, unmatchedQuests, verdicts, items));
	}

	/**
	 * Says there is nothing to show rather than showing a combat level built
	 * from levels the client has not loaded. A logged-out client reports every
	 * skill as 1, which renders as a confident, wrong "combat 3".
	 *
	 * The unbuilt tabs keep their text: what they say does not depend on being
	 * logged in, and blanking them would make the plugin look broken rather
	 * than logged out.
	 */
	void showLoggedOut()
	{
		SwingUtilities.invokeLater(() ->
		{
			clearBodies();
			tabBodies.get(PanelSections.Tab.COMBAT).add(muted("Log in to see your levels."));
			tabBodies.get(PanelSections.Tab.SYNC).add(muted("Log in to sync."));
			tabBodies.get(PanelSections.Tab.QUESTS).add(muted("Log in to judge your quests."));
			tabBodies.get(PanelSections.Tab.ITEMS).add(muted("Log in to check your bank."));
			renderPlannedSections();
			repaintAll();
		});
	}

	private void render(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items)
	{
		clearBodies();

		int ceiling = CombatMath.freeRangedCeiling(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));

		JPanel combat = tabBodies.get(PanelSections.Tab.COMBAT);
		combat.add(heading(PanelSections.COMBAT.title()));
		combat.add(combatBlock(stats));
		combat.add(Box.createVerticalStrut(14));
		combat.add(heading(PanelSections.CEILING.title()));
		combat.add(ceilingBlock(stats, xp, ceiling));
		combat.add(Box.createVerticalStrut(14));
		combat.add(heading(PanelSections.COST.title()));
		combat.add(costBlock(stats, xp));

		JPanel syncBody = tabBodies.get(PanelSections.Tab.SYNC);
		syncBody.add(heading(PanelSections.SYNC.title()));
		syncBody.add(syncBlock(sync, unmatchedQuests));

		JPanel build = tabBodies.get(PanelSections.Tab.BUILD);
		build.add(heading(PanelSections.BUILD_RULES.title()));
		if (rules.isEmpty())
		{
			// Not "you have no rules". The plugin has no way to learn them
			// except from a push, so an empty list here means it has not been
			// told -- and telling someone their build is unconstrained when
			// nobody has asked would be the worst thing this tab could say.
			build.add(muted("Not received yet. questpath sends these when a"
				+ " push is accepted, so turn on sync and log in."));
		}
		else
		{
			build.add(buildRulesBlock(stats, rules));
		}

		JPanel quests = tabBodies.get(PanelSections.Tab.QUESTS);
		quests.add(heading(PanelSections.QUEST_VERDICT.title()));
		quests.add(verdictBlock(verdicts));

		JPanel itemsBody = tabBodies.get(PanelSections.Tab.ITEMS);
		itemsBody.add(heading(PanelSections.ITEMS.title()));
		itemsBody.add(itemsBlock(items));

		renderPlannedSections();
		repaintAll();
	}

	/**
	 * What the next doable quests need, ticked against the bank.
	 *
	 * The plugin's whole reason to exist: nothing outside the client can see a
	 * bank, so nothing outside the client can tell you that you already have
	 * four of the five things.
	 */
	private static JPanel itemsBlock(QuestItems items)
	{
		JPanel panel = column();

		if (items == null)
		{
			// Not "you need nothing". A missing bundle, a client still
			// loading, and a bank nobody has opened are all "cannot answer".
			panel.add(muted("Open your bank once so the client can see it, and"
				+ " this will fill in."));
			return panel;
		}
		if (items.isEmpty())
		{
			panel.add(muted("No doable quest has an item list."));
			return panel;
		}

		boolean first = true;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			if (!first)
			{
				panel.add(Box.createVerticalStrut(10));
			}
			first = false;

			JLabel title = new JLabel(wrap(needs.questName()
				+ "  ·  " + needs.have() + "/" + needs.needed()));
			title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			title.setForeground(needs.ready()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.BRAND_ORANGE);
			panel.add(title);

			JPanel grid = grid();
			for (QuestItems.Line line : needs.lines())
			{
				String label = line.optional() ? line.name() + " (optional)" : line.name();
				addRow(grid, label, line.describe(), line.held()
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.LIGHT_GRAY_COLOR);
			}
			panel.add(grid);
		}
		return panel;
	}

	/**
	 * Every quest, judged and grouped by what to do about it.
	 *
	 * The groups are the answer. "Do these now", "these need levels" and
	 * "these are off the table while your build stands" are three different
	 * actions, and the last is the one no other quest tool gives you.
	 */
	private static JPanel verdictBlock(QuestVerdicts verdicts)
	{
		JPanel panel = column();

		if (verdicts == null)
		{
			// Not an empty list. A missing bundle and a client that has not
			// loaded quest states are both "cannot answer", and rendering
			// either as "nothing to do" would be a confident wrong answer.
			panel.add(muted("Cannot judge quests yet. Log in and let the client"
				+ " load, or check the log if this persists."));
			return panel;
		}

		if (!verdicts.rulesKnown())
		{
			// Without rules nothing can be BUILD_BLOCKED, so every verdict
			// below is "as far as I know". A pure's quest list rendered as a
			// main's is exactly the mistake this plugin exists to prevent.
			JLabel warning = new JLabel(wrap("No build rules yet, so nothing can be"
				+ " judged against them. Turn on sync to get them from questpath."));
			warning.setFont(FontManager.getRunescapeSmallFont());
			warning.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			panel.add(warning);
			panel.add(Box.createVerticalStrut(6));
		}

		JLabel done = new JLabel(wrap(verdicts.count(QuestStatus.COMPLETE) + " done"));
		done.setFont(FontManager.getRunescapeSmallFont());
		done.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(done);

		for (QuestStatus status : QuestVerdicts.sections())
		{
			int count = verdicts.count(status);
			if (count == 0)
			{
				continue;
			}
			panel.add(Box.createVerticalStrut(8));

			JLabel groupHeading = new JLabel(QuestVerdicts.label(status) + "  ·  " + count);
			groupHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			groupHeading.setForeground(statusColor(status));
			panel.add(groupHeading);

			for (QuestGraph.Quest quest : verdicts.sample(status))
			{
				JLabel row = new JLabel(wrap(quest.name()));
				row.setFont(FontManager.getRunescapeSmallFont());
				row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				panel.add(row);
			}

			int hidden = verdicts.hidden(status);
			if (hidden > 0)
			{
				// Said rather than silently truncated: a list that stops at
				// eight without saying so reads as a complete list of eight.
				JLabel more = new JLabel(wrap("and " + hidden + " more"));
				more.setFont(FontManager.getRunescapeSmallFont());
				more.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				panel.add(more);
			}
		}

		return panel;
	}

	/**
	 * Green for what can be done, amber for what needs work, red for what the
	 * build has closed off. The last is the distinction that matters: "go and
	 * train" and "never, while this build stands" must not look the same.
	 */
	private static Color statusColor(QuestStatus status)
	{
		if (status.permanent())
		{
			return ColorScheme.PROGRESS_ERROR_COLOR;
		}
		if (status.doable())
		{
			return ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		return ColorScheme.PROGRESS_INPROGRESS_COLOR;
	}

	private void clearBodies()
	{
		for (JPanel bodyForTab : tabBodies.values())
		{
			bodyForTab.removeAll();
		}
	}

	/**
	 * Fills every tab's sections that do not exist yet.
	 *
	 * Driven off the enum rather than written out per tab, so a section that
	 * becomes live only has to change its status — it does not also have to be
	 * deleted from a hand-maintained list here.
	 */
	private void renderPlannedSections()
	{
		for (PanelSections.Tab tab : PanelSections.Tab.values())
		{
			JPanel bodyForTab = tabBodies.get(tab);
			boolean anyAlready = bodyForTab.getComponentCount() > 0;
			for (PanelSections section : PanelSections.values())
			{
				if (section.tab() != tab || section.status() != PanelSections.Status.NOT_BUILT)
				{
					continue;
				}
				if (anyAlready)
				{
					bodyForTab.add(Box.createVerticalStrut(14));
				}
				bodyForTab.add(heading(section.title()));
				bodyForTab.add(notBuiltBlock(section));
				anyAlready = true;
			}
		}
	}

	private void repaintAll()
	{
		for (JPanel bodyForTab : tabBodies.values())
		{
			bodyForTab.revalidate();
			bodyForTab.repaint();
		}
		display.revalidate();
		display.repaint();
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
		JLabel next = new JLabel(wrap(step == null
			? "No Strength level raises it further."
			: "Strength " + step.atStrength + " raises it to " + step.ceiling + " (+" + step.gained + ")"));
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
			panel.add(muted("Nothing raises combat at or below 99."));
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
	 * Nothing, deliberately. An earlier version priced every row from level
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
	private static JPanel syncBlock(SyncStatus sync, List<String> unmatchedQuests)
	{
		JPanel panel = column();

		JLabel line = new JLabel(wrap(sync.describe(Instant.now())));
		line.setForeground(sync.needsAttention()
			? ColorScheme.PROGRESS_ERROR_COLOR
			: ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(line);

		if (sync.state() == SyncStatus.State.NO_TOKEN)
		{
			JLabel hint = new JLabel(wrap("Run: questpath -plugin-token"));
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(hint);
		}

		// A quest name this plugin sends that questpath cannot resolve is a
		// real disagreement between the two. Counting them would hide which,
		// and which is the only part that leads anywhere.
		if (!unmatchedQuests.isEmpty())
		{
			panel.add(Box.createVerticalStrut(6));
			JLabel header = new JLabel(wrap(unmatchedQuests.size()
				+ " quest names questpath did not recognise:"));
			header.setFont(FontManager.getRunescapeSmallFont());
			header.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			panel.add(header);
			for (String name : unmatchedQuests)
			{
				JLabel row = new JLabel(wrap(name));
				row.setFont(FontManager.getRunescapeSmallFont());
				row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				panel.add(row);
			}
		}
		return panel;
	}

	/**
	 * The account's declared limits, and how close each one is.
	 *
	 * The headroom is the point. "Defence: no xp at all" is a rule; "Defence 1
	 * — no xp at all" is a rule you are currently obeying, and one is far more
	 * useful than the other while deciding what to train.
	 *
	 * Blocking rules are drawn as warnings and informational ones are not.
	 * Colouring a goal like a cap would turn "approaching a target" into
	 * "about to ruin your account", and the second warning stops meaning
	 * anything once it fires for the first.
	 */
	private static JPanel buildRulesBlock(CombatStats stats, List<BuildRule> rules)
	{
		JPanel panel = column();
		JPanel grid = grid();

		for (BuildRule rule : rules)
		{
			Skill skill = rule.skill();
			String label = skillNameOf(rule, skill);
			int level = skill == null ? -1 : stats.get(skill);
			if (level >= 0)
			{
				label = label + " " + level;
			}
			addRow(grid, label, rule.describe(), ruleColor(rule, level));
		}

		panel.add(grid);
		return panel;
	}

	/**
	 * Amber once a blocking rule is one level away, red once it is reached.
	 *
	 * Only for rules that actually block, and only for the seven combat skills
	 * this panel knows a level for — everything else is neutral rather than
	 * guessed at.
	 */
	private static Color ruleColor(BuildRule rule, int level)
	{
		if (!rule.blocking())
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		if (level < 0 || !rule.hasValue())
		{
			// NO_XP, or a skill outside the combat seven. It blocks, but there
			// is no distance to render.
			return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		if (level >= rule.value())
		{
			return ColorScheme.PROGRESS_ERROR_COLOR;
		}
		if (level >= rule.value() - 1)
		{
			return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
		return ColorScheme.PROGRESS_COMPLETE_COLOR;
	}

	/**
	 * The rule's skill name, falling back to whatever questpath called it.
	 *
	 * A skill questpath knows and this client does not is a real
	 * disagreement, and the rule is shown under questpath's name rather than
	 * dropped -- it might be the one ending the build.
	 */
	private static String skillNameOf(BuildRule rule, Skill skill)
	{
		return skill == null ? rule.skillName() : skillName(skill);
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

	private static JLabel muted(String text)
	{
		JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
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
