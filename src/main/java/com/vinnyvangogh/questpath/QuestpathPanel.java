package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.SpriteManager;
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
@lombok.extern.slf4j.Slf4j
class QuestpathPanel extends PluginPanel
{
	/**
	 * Timing for the two things that were reported as slow: building the
	 * panel, and switching a tab.
	 *
	 * Instrumentation rather than another guess. Two rounds of reasoning about
	 * what "ought" to be expensive found real problems and did not fix the
	 * symptom, and the remaining complaint -- that it degrades the more you
	 * switch back and forth -- is the shape of something accumulating, which
	 * inference is bad at finding and a millisecond count is not.
	 *
	 * Logged only when a pass is slow enough to notice, so an idle client
	 * stays quiet.
	 */
	private static final long SLOW_MILLIS = 4;

	/** Counts how many switches have happened, to expose growth over time. */
	private int tabSwitches;

	/**
	 * The skill icons are around 25px; the tab strip has five of them across a
	 * 225px sidebar, so they are scaled down to leave room for the padding
	 * between them.
	 */
	private static final int TAB_ICON_SIZE = 20;

	private final Map<PanelSections.Tab, JPanel> tabBodies = new EnumMap<>(PanelSections.Tab.class);

	/**
	 * Switches tabs by changing which one is visible, never by removing one.
	 *
	 * MaterialTabGroup's own display panel does the swap as
	 * {@code removeAll(); add(next);} and that is what made switching away
	 * from the Items tab take four seconds. RuneLite's game canvas is a
	 * heavyweight AWT component, so every lightweight component removed from
	 * that window runs AWT's mixing pass -- {@code removeNotify} into
	 * {@code mixOnHiding} into {@code recursiveApplyCurrentShape}, walking
	 * the tree to recompute what overlaps the canvas. Removing one panel
	 * recurses into every descendant, so a tab holding fourteen hundred
	 * components paid that walk fourteen hundred times.
	 *
	 * <p>The cost therefore tracked the tab being left, not the one being
	 * opened, which is why a thirty-five component tab measured four seconds
	 * and a hundred and sixty-seven component tab measured twelve
	 * milliseconds. CardLayout hides with {@code setVisible} instead: one
	 * mixing pass for the card, not one per component inside it.
	 */
	private final CardLayout cards = new CardLayout();
	private final JPanel display = new JPanel(cards);

	/**
	 * What the Quests and Items tabs last rendered.
	 *
	 * They list in full -- around 200 labels between them -- and the panel
	 * refreshes on every experience drop. Rebuilding them several times a
	 * second while training is work for a picture that did not change, and it
	 * throws away scroll position with it.
	 *
	 * lastVerdictSignature covers both QUEST_VERDICT and NEXT_QUEST, which
	 * share one cache gate: a two-element list of (verdicts signature, next-
	 * quest hash), compared with Objects.equals rather than typed as Integer,
	 * because it now carries two independent pieces of state.
	 */
	private Object lastVerdictSignature;
	private Integer lastItemsSignature;

	/**
	 * What the Combat and Build tabs last rendered.
	 *
	 * They were torn down and rebuilt on every refresh while Quests and Items
	 * were signature-guarded, which is about seventy components thrown away
	 * and remade per tick for a picture that only changes when a level or a
	 * rule does. The Sync tab deliberately has no guard: it says "sent 3m
	 * ago", so it has to be rebuilt to stay true.
	 */
	private Object lastCombatSignature;
	private Object lastBuildSignature;

	/**
	 * The quest search box, and the last verdicts it filters.
	 *
	 * The box is built once and never cleared, so typing in it survives the
	 * panel rebuilding underneath -- which happens on every quest completion
	 * and every build-rule arrival. A search field that lost its text and
	 * focus mid-type would be worse than no search field.
	 */
	private final JTextField questSearch = new JTextField();

	/**
	 * The verdict/status filter beside the search box.
	 *
	 * "All statuses" plus one entry per group a search would show -- COMPLETE
	 * included, because filtering to "Done" is a real thing to want even
	 * outside a text search. Built once, like questSearch, so a chosen filter
	 * survives the panel rebuilding underneath it on every quest completion.
	 */
	private final JComboBox<String> questStatusFilter = new JComboBox<>();
	private static final String STATUS_FILTER_ALL = "All statuses";

	private final JPanel questResults = new JPanel();
	private QuestVerdicts lastVerdicts;

	/** The overlay settings tab's contents, built once and kept. */
	private final OverlaySettingsPanel overlaySettings;

	/**
	 * The skill planner's controls and its results, kept apart.
	 *
	 * The controls are built once and never rebuilt, for the same reason the
	 * search boxes are: a half-chosen target that reset itself every time an
	 * experience drop landed would be unusable. Only the results below them
	 * are refilled.
	 */
	private final JComboBox<String> skillChoice = new JComboBox<>();
	private final JSpinner skillTarget = new JSpinner(new SpinnerNumberModel(50, 2, 99, 1));
	private final JPanel skillResults = new JPanel();

	/**
	 * Answers "what would this level cost", or null when nothing is known yet.
	 *
	 * A callback rather than the graph and the account themselves: the panel
	 * has neither and should not learn to build a classifier. The plugin owns
	 * that, and hands down the one question this tab asks.
	 */
	private final java.util.function.BiFunction<String, Integer, SkillPlan> planner;

	/** What the skill plan was last drawn for; see the Build tab's gate. */
	private Object lastSkillSignature;

	private final JTextField itemSearch = new JTextField();
	private final JPanel itemResults = new JPanel();
	private QuestItems lastItems;

	/**
	 * Answers whether a quest can be handed to Quest Helper, and does the
	 * handing-off. Queried lazily, on the right-click that needs it, never at
	 * render time -- the quest browser lists all 218 quests and rebuilds on
	 * every quest completion, and none of them should pay for a plugin scan
	 * and two reflective calls when the popup for the other 217 will never
	 * open. See {@link QuestHelperBridge}.
	 */
	private final QuestHelperBridge questHelperBridge;

	/**
	 * Answers whether the current shop route can be handed to Shortest
	 * Path, and does the handing-off. See {@link ShortestPathBridge}'s class
	 * doc for exactly how strong a claim {@link
	 * ShortestPathBridge.RouteOutcome#SENT} is.
	 */
	private final ShortestPathBridge shortestPathBridge;

	/**
	 * Where to walk for a shop by name, or null when the bundled graph
	 * carries no coordinate for it. A callback rather than a graph of its
	 * own for the same reason {@link #planner} is one: the panel should not
	 * learn to hold the bundle, only ask the one question it needs answered.
	 */
	private final java.util.function.Function<String, WorldPoint> shopLocator;

	/**
	 * Told the moment the shop-route button actually sends a route -- see
	 * {@link ShopRouteGuard}'s class doc for why {@code QuestpathPlugin}
	 * needs to know this at all. Never told about any other outcome: {@link
	 * ShopRouteGuard} only ever needs to know a route went out, the same
	 * distinction {@link ShortestPathBridge.RouteOutcome#SENT} itself draws.
	 */
	private final Runnable onShopRouteSent;

	/**
	 * Which items the player has chosen to skip on the current shop trip, and
	 * the one place both the skip button and its undo list act -- see
	 * {@link ShopItemSkips}'s own doc. Shared with {@link ShopRouteOverlay},
	 * which is handed the identical instance by {@code QuestpathPlugin}, so a
	 * skip made here is reflected there without either polling the other.
	 */
	private final ShopItemSkips shopItemSkips;

	QuestpathPanel(SpriteManager sprites, OverlaySettingsPanel overlaySettings,
		java.util.function.BiFunction<String, Integer, SkillPlan> planner,
		QuestHelperBridge questHelperBridge, ShortestPathBridge shortestPathBridge,
		java.util.function.Function<String, WorldPoint> shopLocator, Runnable onShopRouteSent,
		ShopItemSkips shopItemSkips)
	{
		this.overlaySettings = overlaySettings;
		this.planner = planner;
		this.questHelperBridge = questHelperBridge;
		this.shortestPathBridge = shortestPathBridge;
		this.shopLocator = shopLocator;
		this.onShopRouteSent = onShopRouteSent;
		this.shopItemSkips = shopItemSkips;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// Deliberately the no-display constructor. Handed a panel, the group
		// swaps content by removing it, which is the expensive path above;
		// left null it only manages the tab labels and select() skips the
		// swap entirely, leaving it to the card layout below.
		MaterialTabGroup tabGroup = new MaterialTabGroup();
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

			// Every tab's wrapper is added once and stays. Only its visibility
			// changes after this.
			display.add(wrapper, tab.name());

			MaterialTab materialTab = tabFor(sprites, tab, tabGroup, wrapper);
			// Times the switch itself. MaterialTabGroup.select swaps the
			// displayed wrapper and validates it, which is where the cost of
			// a big tab actually lands -- laying out and painting everything
			// it holds, every time it comes back on screen.
			materialTab.setOnSelectEvent(() ->
			{
				long startedAt = System.nanoTime();
				tabSwitches++;
				cards.show(display, tab.name());
				SwingUtilities.invokeLater(() ->
				{
					long millis = (System.nanoTime() - startedAt) / 1_000_000L;
					if (millis >= SLOW_MILLIS)
					{
						log.info("questpath perf: switch #{} to {} took {}ms; components {}",
							tabSwitches, tab, millis, componentCounts());
					}
				});
				return true;
			});
			tabGroup.addTab(materialTab);
			if (first == null)
			{
				first = materialTab;
			}
		}

		questResults.setLayout(new BoxLayout(questResults, BoxLayout.Y_AXIS));
		questResults.setAlignmentX(Component.LEFT_ALIGNMENT);
		itemResults.setLayout(new BoxLayout(itemResults, BoxLayout.Y_AXIS));
		itemResults.setAlignmentX(Component.LEFT_ALIGNMENT);
		styleSearch(questSearch, "Filter the quests below by name", this::refilterQuests);
		styleSearch(itemSearch, "Filter the items below by name", this::refilterItems);
		styleStatusFilter(questStatusFilter, this::refilterQuests);

		// The settings tab is built once and never refilled: it reads config,
		// not account state, so nothing an experience drop does can change it.
		// Adding it here rather than in render() also means a half-typed
		// interaction survives the panel rebuilding underneath everything
		// else.
		if (overlaySettings != null)
		{
			tabBodies.get(PanelSections.Tab.VIEW).add(overlaySettings);
		}

		buildSkillTab();
		quickenTooltips();

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		if (first != null)
		{
			tabGroup.select(first);
		}
		showLoggedOut();
	}

	/**
	 * The quest search box.
	 *
	 * Filters what is already computed rather than reclassifying: the verdicts
	 * are the expensive part and they do not depend on the text. Typing is
	 * therefore a relayout, not a graph walk.
	 */
	private void styleSearch(JTextField field, String tooltip, Runnable onChange)
	{
		field.setToolTipText(tooltip);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		field.setAlignmentX(Component.LEFT_ALIGNMENT);

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onChange.run();
			}
		});
	}

	/**
	 * The verdict/status combo beside the search box.
	 *
	 * Populated from {@link QuestVerdicts#searchSections()} rather than
	 * hand-listed, so a status the classifier gains automatically gets a
	 * filter entry instead of silently being unreachable through this box.
	 */
	private void styleStatusFilter(JComboBox<String> combo, Runnable onChange)
	{
		combo.addItem(STATUS_FILTER_ALL);
		for (QuestStatus status : QuestVerdicts.searchSections())
		{
			combo.addItem(QuestVerdicts.label(status));
		}
		combo.setSelectedItem(STATUS_FILTER_ALL);
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);
		combo.setToolTipText("Show only one verdict group");
		combo.addActionListener(e -> onChange.run());
	}

	/**
	 * The status this panel's combo is currently narrowed to, or null for
	 * "All statuses". Null means the caller falls back to the usual
	 * sections()/searchSections() grouping.
	 */
	private QuestStatus selectedStatusFilter()
	{
		Object selected = questStatusFilter.getSelectedItem();
		if (selected == null || STATUS_FILTER_ALL.equals(selected))
		{
			return null;
		}
		for (QuestStatus status : QuestVerdicts.searchSections())
		{
			if (QuestVerdicts.label(status).equals(selected))
			{
				return status;
			}
		}
		return null;
	}

	/**
	 * Redraws a list for its current filter.
	 *
	 * Called from a search box's document listener, which is already on the
	 * EDT, so there is no hop here.
	 */
	private void refilterQuests()
	{
		if (lastVerdicts == null)
		{
			return;
		}
		fillQuestResults(lastVerdicts);
		questResults.revalidate();
		questResults.repaint();
	}

	private void refilterItems()
	{
		if (lastItems == null)
		{
			return;
		}
		fillItemResults(lastItems);
		itemResults.revalidate();
		itemResults.repaint();
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
	private static MaterialTab tabFor(SpriteManager sprites, PanelSections.Tab tab,
		MaterialTabGroup group, JPanel content)
	{
		// Built with its label first and given the picture when the picture
		// arrives.
		//
		// SpriteManager cannot always answer immediately -- the client's
		// sprite cache may not be loaded when a plugin starts, and asking
		// synchronously then returns null forever, which would strand every
		// tab on its text fallback for the whole session. The async form
		// answers when it can, and the label in the meantime is the same
		// thing the tab falls back to permanently if the sprite never comes.
		MaterialTab materialTab = new MaterialTab(tab.label(), group, content);
		materialTab.setToolTipText(tab.label());

		if (sprites != null)
		{
			sprites.getSpriteAsync(tab.sprite(), 0, image ->
			{
				if (image == null)
				{
					return;
				}
				// Swing, so the swap happens on the EDT rather than on
				// whichever thread the sprite finished loading on.
				SwingUtilities.invokeLater(() ->
				{
					materialTab.setIcon(new ImageIcon(
						ImageUtil.resizeImage(image, TAB_ICON_SIZE, TAB_ICON_SIZE)));
					// The label becomes the tooltip alone once there is a
					// picture; six words across a 225px strip is unreadable.
					materialTab.setText(null);
					materialTab.revalidate();
					materialTab.repaint();
				});
			});
		}

		return materialTab;
	}

	/**
	 * Renders the readout for one set of levels. Safe to call from the client
	 * thread: it hops to the EDT itself, because Swing state must only be
	 * touched there and every caller here is a game event.
	 */
	void update(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items,
		NextQuestStatus nextQuest, SyncSettings settings, AccountReport report)
	{
		SwingUtilities.invokeLater(() ->
			render(stats, xp, sync, rules, unmatchedQuests, verdicts, items, nextQuest, settings, report));
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
			clearAllBodies();
			if (overlaySettings != null)
			{
				// Settings are about the client, not the account, so they stay
				// readable and editable while logged out -- which is exactly
				// when someone is most likely to be setting them up.
				tabBodies.get(PanelSections.Tab.VIEW).add(overlaySettings);
			}
			tabBodies.get(PanelSections.Tab.COMBAT).add(muted("Log in to see your levels."));
			tabBodies.get(PanelSections.Tab.SYNC).add(muted("Log in to sync."));
			tabBodies.get(PanelSections.Tab.QUESTS).add(muted("Log in to judge your quests."));
			tabBodies.get(PanelSections.Tab.ITEMS).add(muted("Log in to check your bank."));
			// Cleared with everything else, so put them back before asking
			// them to redraw. The instances are the same, so whatever was
			// chosen is still chosen.
			addSkillFurniture();
			lastSkillSignature = null;
			renderSkillPlan();
			renderPlannedSections();
			repaintAll();
		});
	}

	private void render(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items,
		NextQuestStatus nextQuest, SyncSettings settings, AccountReport report)
	{
		long startedAt = System.nanoTime();
		try
		{
			renderBody(stats, xp, sync, rules, unmatchedQuests, verdicts, items, nextQuest,
				settings, report);
		}
		finally
		{
			long millis = (System.nanoTime() - startedAt) / 1_000_000L;
			if (millis >= SLOW_MILLIS)
			{
				log.info("questpath perf: render took {}ms; components {}", millis, componentCounts());
			}
		}
	}

	/**
	 * How many Swing components each tab is currently holding.
	 *
	 * The number that matters for the "worse the more you switch" report: if
	 * it climbs across switches, something is accumulating rather than being
	 * replaced.
	 */
	private String componentCounts()
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<PanelSections.Tab, JPanel> entry : tabBodies.entrySet())
		{
			if (out.length() > 0)
			{
				out.append(' ');
			}
			out.append(entry.getKey()).append('=').append(countDeep(entry.getValue()));
		}
		return out.toString();
	}

	private static int countDeep(Component component)
	{
		int total = 1;
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) component).getComponents())
			{
				total += countDeep(child);
			}
		}
		return total;
	}

	private void renderBody(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items,
		NextQuestStatus nextQuest, SyncSettings settings, AccountReport report)
	{
		clearBodies(PanelSections.Tab.SYNC);

		int ceiling = CombatMath.freeRangedCeiling(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));

		Object combatSignature = java.util.Arrays.asList(stats, xp.signature());
		if (!java.util.Objects.equals(combatSignature, lastCombatSignature))
		{
			clearBodies(PanelSections.Tab.COMBAT);
			JPanel combat = tabBodies.get(PanelSections.Tab.COMBAT);
			combat.add(heading(PanelSections.COMBAT.title()));
			combat.add(combatBlock(stats));
			combat.add(Box.createVerticalStrut(14));
			combat.add(heading(PanelSections.CEILING.title()));
			combat.add(ceilingBlock(stats, xp, ceiling));
			combat.add(Box.createVerticalStrut(14));
			combat.add(heading(PanelSections.COST.title()));
			combat.add(costBlock(stats, xp));
			renderPlannedSectionsFor(PanelSections.Tab.COMBAT);
			lastCombatSignature = combatSignature;
			showBodies(PanelSections.Tab.COMBAT);
		}

		JPanel syncBody = tabBodies.get(PanelSections.Tab.SYNC);
		syncBody.add(heading(PanelSections.SYNC.title()));
		syncBody.add(syncBlock(sync, unmatchedQuests, settings, report));

		Object buildSignature = java.util.Arrays.asList(stats, rules);
		if (java.util.Objects.equals(buildSignature, lastBuildSignature))
		{
			// Unchanged, so the tab keeps what it has.
		}
		else
		{
		lastBuildSignature = buildSignature;
		clearBodies(PanelSections.Tab.BUILD);
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
		renderPlannedSectionsFor(PanelSections.Tab.BUILD);
		showBodies(PanelSections.Tab.BUILD);
		}

		// Shares the Build tab's gate: both are driven by levels and rules,
		// and both go stale for the same reasons -- an experience drop, or a
		// quest finishing and freeing the next one up.
		if (!java.util.Objects.equals(buildSignature, lastSkillSignature))
		{
			lastSkillSignature = buildSignature;
			renderSkillPlan();
		}

		// Rebuilt only when their contents actually changed; see the
		// signature fields. NEXT_QUEST shares this gate with QUEST_VERDICT --
		// both live on the Quests tab and both are driven by the same
		// completed-quest read, so a quest finishing invalidates them
		// together.
		lastVerdicts = verdicts;
		Object questsSignature = java.util.Arrays.asList(
			verdicts == null ? null : verdicts.signature(),
			nextQuest == null ? null : nextQuest.hashCode());
		if (!java.util.Objects.equals(questsSignature, lastVerdictSignature))
		{
			clearBodies(PanelSections.Tab.QUESTS);
			JPanel quests = tabBodies.get(PanelSections.Tab.QUESTS);
			// Search first: it filters the browser list further down, but a
			// player reaching for it should not have to scroll past "next on
			// your route" to find it every time.
			if (verdicts != null)
			{
				quests.add(questSearch);
				quests.add(Box.createVerticalStrut(4));
				quests.add(questStatusFilter);
				quests.add(Box.createVerticalStrut(10));
			}
			quests.add(heading(PanelSections.NEXT_QUEST.title()));
			quests.add(nextQuestBlock(nextQuest));
			quests.add(Box.createVerticalStrut(14));
			quests.add(heading(PanelSections.QUEST_VERDICT.title()));
			quests.add(questResults);
			fillQuestResults(verdicts);
			renderPlannedSectionsFor(PanelSections.Tab.QUESTS);
			lastVerdictSignature = questsSignature;
			showBodies(PanelSections.Tab.QUESTS);
		}

		lastItems = items;
		Integer itemsSignature = items == null ? null : items.signature();
		if (!java.util.Objects.equals(itemsSignature, lastItemsSignature))
		{
			clearBodies(PanelSections.Tab.ITEMS);
			JPanel itemsBody = tabBodies.get(PanelSections.Tab.ITEMS);
			itemsBody.add(heading(PanelSections.ITEMS.title()));
			if (items != null && !items.isEmpty())
			{
				itemsBody.add(itemSearch);
				itemsBody.add(Box.createVerticalStrut(6));
			}
			itemsBody.add(itemResults);
			fillItemResults(items);
			renderPlannedSectionsFor(PanelSections.Tab.ITEMS);
			lastItemsSignature = itemsSignature;
			showBodies(PanelSections.Tab.ITEMS);
		}

		renderPlannedSectionsFor(PanelSections.Tab.SYNC);
		showBodies(PanelSections.Tab.SYNC);
		repaintAll();
	}

	/**
	 * What the next doable quests need, ticked against the bank.
	 *
	 * The plugin's whole reason to exist: nothing outside the client can see a
	 * bank, so nothing outside the client can tell you that you already have
	 * four of the five things.
	 */
	private void fillItemResults(QuestItems items)
	{
		itemResults.removeAll();
		itemResults.add(itemsBlock(items, itemSearch.getText()));
	}

	/**
	 * Quest sections drawn on the Items tab before it stops and says how many
	 * more there are.
	 *
	 * Not a display preference: this is the tab's component count, and the
	 * component count is what a rebuild costs inside a window that holds the
	 * game canvas.
	 */
	private static final int MAX_QUEST_SECTIONS = 8;

	/**
	 * Not static, unlike the rest of this method's history -- it needs
	 * {@link #shortestPathBridge} and {@link #reportRouteOutcome} to draw and
	 * wire the route button below.
	 */
	private JPanel itemsBlock(QuestItems items, String filter)
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
		if (!items.bankSeen())
		{
			// The client does not hold the bank until it has been opened once,
			// so every count below is inventory and equipment only. Without
			// saying so, a row reading "none" claims you do not own something
			// that may be sitting in a bank nobody has looked in.
			JLabel warning = new JLabel(wrap("Bank not read this session, so these"
				+ " counts are what you are carrying. Open your bank once."));
			warning.setFont(FontManager.getRunescapeSmallFont());
			warning.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			panel.add(warning);
			panel.add(Box.createVerticalStrut(6));
		}

		if (items.isEmpty())
		{
			panel.add(muted("No doable quest has an item list."));
			return panel;
		}

		String needle = filter == null ? "" : filter.trim().toLowerCase();
		boolean filtering = !needle.isEmpty();

		// What is worth grabbing inside a quest already under way, before the
		// shopping list -- it is the only part that stops being useful the
		// moment you leave.
		if (!filtering && !items.pickups().isEmpty())
		{
			JLabel pickupHeading = new JLabel("GET WHILE YOU ARE HERE");
			pickupHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			pickupHeading.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			panel.add(pickupHeading);

			for (QuestItems.Pickup pickup : items.pickups())
			{
				JLabel row = row(pickup.itemName() + " — in " + pickup.questName());
				row.setFont(FontManager.getRunescapeSmallFont());
				row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.setToolTipText("You are partway through " + pickup.questName()
					+ ", the wiki says this can be picked up during it, and "
					+ pickup.alsoWantedBy() + " quest(s) on your list want it.");
				panel.add(row);
			}
			panel.add(Box.createVerticalStrut(12));
		}

		if (!filtering)
		{
			panel.add(shopRouteButton(items));
			panel.add(skipRow(items));
			panel.add(Box.createVerticalStrut(8));
		}

		// One shopping list first, the per-quest breakdown after. Coins wanted
		// by two quests is one trip to the bank for the sum, not two lines to
		// add up in your head.
		JLabel totalsHeading = new JLabel("EVERYTHING YOU NEED");
		totalsHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		totalsHeading.setForeground(ColorScheme.BRAND_ORANGE);
		panel.add(totalsHeading);

		JPanel totalsGrid = grid();
		int shownTotals = 0;
		// Skipped items are drawn last within this list rather than in their
		// ordinary shopping-priority place -- "move to the back", not merely
		// "mark differently" -- so the two-pass split below, not a single
		// pass appending straight into totalsGrid.
		List<QuestItems.Total> skippedTotals = new ArrayList<>();
		for (QuestItems.Total total : items.totals())
		{
			if (filtering && !total.name().toLowerCase().contains(needle))
			{
				continue;
			}
			if (!filtering && shopItemSkips.isSkipped(total.name()))
			{
				skippedTotals.add(total);
				continue;
			}
			shownTotals++;
			addTotalRow(totalsGrid, total);
		}
		for (QuestItems.Total total : skippedTotals)
		{
			shownTotals++;
			addTotalRow(totalsGrid, total);
		}
		panel.add(totalsGrid);
		if (filtering && shownTotals == 0)
		{
			panel.add(muted("No item matches that."));
		}

		panel.add(Box.createVerticalStrut(12));
		JLabel perQuest = new JLabel("BY QUEST  ·  " + items.quests().size());
		perQuest.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		perQuest.setForeground(ColorScheme.BRAND_ORANGE);
		panel.add(perQuest);
		panel.add(Box.createVerticalStrut(4));

		boolean first = true;
		int questsShown = 0;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			// Sixty-seven quest sections is around eight hundred and thirty
			// Swing components, most of the tab, for a list nobody scrolls to
			// the bottom of in a two-hundred-and-twenty-five pixel sidebar.
			// It also has to be torn down and rebuilt every time a stack
			// changes, and rebuilding a subtree that size inside a window
			// holding the game canvas is what made withdrawing an item from
			// the bank stutter. Capped to the nearest few, which is the part
			// that answers "what do I need for what I am about to do"; the
			// filter below still searches every quest, so nothing is
			// unreachable.
			if (!filtering && questsShown >= MAX_QUEST_SECTIONS)
			{
				break;
			}
			// A filter searches items, so a quest survives it by wanting one.
			// Filtering on the quest name instead would make searching "bones"
			// hide the very quest that needs them.
			List<QuestItems.Line> shownLines = new ArrayList<>();
			for (QuestItems.Line line : needs.lines())
			{
				if (!filtering || line.name().toLowerCase().contains(needle))
				{
					shownLines.add(line);
				}
			}
			if (shownLines.isEmpty())
			{
				continue;
			}
			if (!first)
			{
				panel.add(Box.createVerticalStrut(10));
			}
			first = false;

			JLabel title = row((needs.questName()
				+ "  ·  " + needs.have() + "/" + needs.needed()));
			title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			title.setForeground(needs.ready()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.BRAND_ORANGE);
			panel.add(title);

			JPanel grid = grid();
			for (QuestItems.Line line : shownLines)
			{
				String label = line.optional() ? line.name() + " (optional)" : line.name();
				addRow(grid, label, line.describe(), line.held()
					? ColorScheme.PROGRESS_COMPLETE_COLOR
					: ColorScheme.LIGHT_GRAY_COLOR, itemTooltip(null, line.sources()));
			}
			panel.add(grid);
			questsShown++;
		}

		int questsLeft = items.quests().size() - questsShown;
		if (!filtering && questsLeft > 0)
		{
			// Said, not silently dropped. A list that stops at eight without
			// mentioning the other fifty-nine reads as the whole answer.
			panel.add(Box.createVerticalStrut(8));
			panel.add(muted(questsLeft + " more quests on your plan need items."
				+ " Search above to see any of them."));
		}

		return panel;
	}

	/**
	 * One button: hand the shops selling what the plan still needs to
	 * Shortest Path as a target set.
	 *
	 * <p>Its label carries the reason whenever it is disabled, same as
	 * {@link #questHelperMenu}'s items -- a disabled Swing component
	 * receives no mouse events, so a tooltip on a disabled button would
	 * never show either.
	 *
	 * <p>Destinations are drawn from every unsatisfied {@link
	 * QuestItems.Total} on the current shopping list, resolved through
	 * {@link #shopLocator} -- which is null-safe by name, not by shop: an
	 * item whose {@code shopNames()} is empty, or whose names do not resolve
	 * against the bundled graph's own shop table, simply contributes
	 * nothing, so a wholly unplaceable list degrades to the same disabled,
	 * honest state {@link ShortestPathBridge}'s class doc describes for a
	 * bundle version too old to carry shop data at all.
	 */
	private JButton shopRouteButton(QuestItems items)
	{
		Set<WorldPoint> destinations = shopDestinations(items);

		JButton button = new JButton();
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (destinations.isEmpty())
		{
			button.setText("No known shop sells what you still need");
			button.setEnabled(false);
			return button;
		}

		ShortestPathBridge.Availability availability = shortestPathBridge.availability();
		button.setText(availability.menuLabel());
		button.setEnabled(availability == ShortestPathBridge.Availability.AVAILABLE);
		int destinationCount = destinations.size();
		button.addActionListener(event -> shortestPathBridge.route(destinations, outcome ->
		{
			if (outcome == ShortestPathBridge.RouteOutcome.SENT)
			{
				// Told before the Swing hop below, not after: onShopRouteSent
				// only records an Instant on the client thread (see
				// QuestpathPlugin#onShopRouteSent), so it costs nothing to do
				// immediately, and doing it here keeps this the one place
				// that reacts to a route actually going out.
				onShopRouteSent.run();
			}
			SwingUtilities.invokeLater(() -> reportRouteOutcome(outcome, destinationCount));
		}));
		return button;
	}

	/**
	 * One row of the "EVERYTHING YOU NEED" grid: the item, its count, and
	 * whichever colour its state earns -- including a fourth state beyond
	 * the usual three, for an item {@link ShopItemSkips#isSkipped}.
	 *
	 * <p>A skip does not change what the item needs or how much of it is
	 * held, so it earns a colour and a tooltip note on top of whichever of
	 * the ordinary three still applies, never in place of it -- "skipped"
	 * beside "3 / 5 -- still short" is a different fact from the count
	 * itself.
	 */
	private void addTotalRow(JPanel totalsGrid, QuestItems.Total total)
	{
		// The count of quests goes in the tooltip, not the label. "Ball of
		// wool x3" beside "5 / 24" read as a multiplication rather than as
		// "three quests want this", which is what it meant. Where to buy
		// or find it rides in the same tooltip -- one hover answers both
		// "why do I need several" and "where do I get it".
		String sharedNote = total.sharedAcrossQuests()
			? "Needed by " + total.quests() + " quests you can do"
			: null;
		boolean skipped = shopItemSkips.isSkipped(total.name());
		if (skipped)
		{
			sharedNote = sharedNote == null
				? "Skipped for this shop trip -- moved to the back, not routed to."
				: sharedNote + ". Skipped for this shop trip -- moved to the back, not routed to.";
		}
		// Three colours for three states, not two. Orange is the one you
		// are part way through -- some in your bags and still short --
		// which the sort has already floated to the top of the list; the
		// colour is what stops it reading as just another grey row that
		// happens to be first. Skipped overrides all three with the same
		// muted grey the rest of this panel already uses for "set aside" --
		// visible as different without inventing a fifth colour nobody else
		// on this panel means the same thing by.
		Color totalColour = skipped
			? ColorScheme.MEDIUM_GRAY_COLOR
			: total.satisfied()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: total.inProgress()
					? ColorScheme.PROGRESS_INPROGRESS_COLOR
					: ColorScheme.LIGHT_GRAY_COLOR;
		String label = skipped ? total.name() + "  (skipped)" : total.name();
		addRow(totalsGrid, label, total.describe(), totalColour,
			itemTooltip(sharedNote, total.sources()));
	}

	/**
	 * "Skip this item (move to back)" for whichever item {@link
	 * ShopRouteOverlay} would currently call the current stop, plus the undo
	 * list for anything already skipped.
	 *
	 * <p>Right under the shop route button, per the brief this answers: a
	 * skip is a decision made in the moment of looking at that button, not a
	 * separate settings-style control buried elsewhere. "Current" is defined
	 * exactly the way {@link ShopRouteOverlay} defines its own first stop --
	 * the first unsatisfied, unskipped {@link QuestItems.Total} in {@link
	 * QuestItems#totals()}'s own order -- so the button always names the
	 * thing the overlay is actually pointing at, never a different item the
	 * two surfaces disagree about.
	 *
	 * <p>Skipping and undoing both call {@link #fillItemResults} and {@link
	 * #repaintAll} directly rather than waiting for the next natural refresh:
	 * a skip changes nothing {@link QuestItems#signature()} covers -- no
	 * quantity, no held count -- so the tab's own signature gate would never
	 * notice one on its own, and a click that visibly did nothing until the
	 * next inventory change would read as broken.
	 */
	private JPanel skipRow(QuestItems items)
	{
		JPanel panel = column();

		QuestItems.Total current = null;
		for (QuestItems.Total total : items.totals())
		{
			if (!total.satisfied() && !shopItemSkips.isSkipped(total.name()))
			{
				current = total;
				break;
			}
		}

		if (current != null)
		{
			String currentName = current.name();
			JButton skip = new JButton("Skip \"" + currentName + "\" (move to back)");
			skip.setFont(FontManager.getRunescapeSmallFont());
			skip.setAlignmentX(Component.LEFT_ALIGNMENT);
			skip.addActionListener(event ->
			{
				shopItemSkips.skip(currentName);
				fillItemResults(lastItems);
				repaintAll();
			});
			panel.add(skip);
		}

		List<String> skipped = shopItemSkips.names();
		if (!skipped.isEmpty())
		{
			panel.add(Box.createVerticalStrut(4));
			JLabel heading = new JLabel("SKIPPED THIS TRIP  ·  " + skipped.size());
			heading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(heading);
			// Each its own small button rather than one combined "restore
			// all": a skip is reversible one at a time because it was made
			// one at a time, and a player who skipped three items to get to
			// a fourth should not have to give all three back to undo one.
			for (String skippedName : skipped)
			{
				JButton undo = new JButton(skippedName + "  → restore");
				undo.setFont(FontManager.getRunescapeSmallFont());
				undo.setAlignmentX(Component.LEFT_ALIGNMENT);
				undo.addActionListener(event ->
				{
					shopItemSkips.unskip(skippedName);
					fillItemResults(lastItems);
					repaintAll();
				});
				panel.add(undo);
			}
		}

		return panel;
	}

	/**
	 * Every shop worth walking to for the current shopping list: the shops
	 * selling something still outstanding, resolved to a tile.
	 *
	 * <p>Computed only when {@link #itemsBlock} rebuilds -- which is gated
	 * on {@link QuestItems#signature()}, not on every render -- so this never
	 * scales with how often the panel repaints, only with how often the
	 * shopping list actually changes.
	 *
	 * <p>A skipped item ({@link ShopItemSkips#isSkipped}) contributes no shop
	 * of its own -- "skip this item" means routing past it, not merely
	 * hiding it from the overlay while still sending Shortest Path there.
	 * Its shop can still appear in the set if some other, unskipped item also
	 * sells there: skipping is per item, not per shop, so a stop still worth
	 * making for a different reason is not removed along with it.
	 */
	private Set<WorldPoint> shopDestinations(QuestItems items)
	{
		if (items == null)
		{
			return Collections.emptySet();
		}
		return ShopRouteStops.destinations(items.totals(), shopItemSkips, shopLocator);
	}

	/**
	 * Turns a {@link ShortestPathBridge.RouteOutcome} into what the player
	 * sees. {@link ShortestPathBridge.RouteOutcome#SENT} is not confirmation
	 * that Shortest Path acted on the route -- see that constant's own doc
	 * -- so the toast says "sent", never "started" the way Quest Helper's
	 * confirmed start does.
	 *
	 * <p>The count rides along on every branch that has one to give, not just
	 * SENT: "0 destinations, so nothing was sent" and "sent 4 destinations"
	 * are different reports of an otherwise identical-looking button click,
	 * and a player who clicked twice with a different shopping list deserves
	 * to be able to tell them apart without opening the client log. {@link
	 * ShortestPathBridge#route} logs the same number at info for exactly the
	 * report a silent toast could never leave a trace of afterwards.
	 */
	private void reportRouteOutcome(ShortestPathBridge.RouteOutcome outcome, int destinationCount)
	{
		switch (outcome)
		{
			case SENT:
				showToast("Sent " + destinationCount + " shop stop" + (destinationCount == 1 ? "" : "s")
					+ " to Shortest Path.");
				return;
			case UNAVAILABLE:
				warn("Shortest Path could not take the route -- it was no longer available"
					+ " by the time the click landed.");
				return;
			case NO_DESTINATIONS:
				warn("There is nothing to send -- no known shop sells what you still need.");
				return;
			case CALL_FAILED:
				warn("Sending the route to Shortest Path failed. Check the client log.");
				return;
			default:
				// Exhaustive over a closed enum; nothing else to report.
		}
	}

	/**
	 * The saved route: where the account is on it, and what is left.
	 *
	 * Three distinct pictures, not one: a route nobody has saved in
	 * questpath, a route that is entirely finished, and a route with real
	 * work left on it. Collapsing any two of those into the same message
	 * would tell the player something false about their plan.
	 *
	 * The whole route is listed, not only its head. One name answers "what
	 * now" and nothing else -- not how much is left, not whether the plan is
	 * nearly done -- and a single line here beside a browser listing 218
	 * quests read as though the route were one quest long, which is what sent
	 * someone looking for the list that was not there. The head keeps its own
	 * prominence because "what now" is still the question asked most often.
	 */
	private JPanel nextQuestBlock(NextQuestStatus next)
	{
		JPanel panel = column();

		if (next == null)
		{
			// Not "no route saved". The client may not have loaded quest
			// states yet, which looks identical to "zero quests finished" --
			// see QuestpathPlugin.nextQuest().
			panel.add(muted("Cannot judge your route yet. Log in and let the client load."));
			return panel;
		}

		switch (next.state())
		{
			case NO_ROUTE:
				panel.add(muted("No route saved in questpath. Save a plan there and it will show up here."));
				return panel;
			case ALL_DONE:
				JLabel done = new JLabel(wrap("Nothing left to do \u2014 every quest on your route is finished."));
				done.setFont(FontManager.getRunescapeSmallFont());
				done.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				panel.add(done);
				panel.add(Box.createVerticalStrut(6));
				panel.add(routeProgress(next));
				panel.add(routeList(next));
				return panel;
			case NEXT:
				// The headline quest, left-clickable the same way its own row
				// further down the list is -- see attachQuestHelperInteractions.
				// It is the one name most players actually look at, which is
				// exactly the row Defect 2 reported as dead.
				JLabel value = new JLabel(wrap(next.questName()));
				value.setFont(FontManager.getRunescapeBoldFont());
				value.setForeground(Color.WHITE);
				attachQuestHelperInteractions(value, next.questName());
				panel.add(value);
				panel.add(Box.createVerticalStrut(6));
				panel.add(routeProgress(next));
				panel.add(routeList(next));
				return panel;
			default:
				return panel;
		}
	}

	/**
	 * How far through the plan the account is, as a bar and a count.
	 *
	 * The count alone is the number people quote and the bar is what makes it
	 * mean something at a glance: "4 of 21" and "18 of 21" read identically
	 * as text and not at all as pictures.
	 */
	private static JPanel routeProgress(NextQuestStatus next)
	{
		JPanel panel = column();

		JLabel count = new JLabel(wrap(next.doneCount() + " of " + next.size()
			+ " done  \u00b7  " + next.remaining() + " to go"));
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(count);

		JProgressBar bar = new JProgressBar(0, Math.max(1, next.size()));
		bar.setValue(next.doneCount());
		bar.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorderPainted(false);
		bar.setStringPainted(false);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		bar.setPreferredSize(new Dimension(0, 6));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(bar);

		return panel;
	}

	/**
	 * Every quest on the route, in the order questpath saved them.
	 *
	 * Finished ones are shown rather than filtered out: they are the
	 * progress, and a route nearly finished would otherwise look identical to
	 * one nobody has started. They are dimmed and ticked, which is enough to
	 * make the eye skip them without pretending they were never there.
	 *
	 * The next quest is marked in the list as well as named above it, because
	 * the list is what gets scrolled and the headline scrolls away.
	 *
	 * Every row is left- and right-clickable the same way the headline is --
	 * see attachQuestHelperInteractions -- including finished ones, which
	 * Quest Helper is content to be asked to start again.
	 */
	private JPanel routeList(NextQuestStatus next)
	{
		JPanel panel = column();
		panel.add(Box.createVerticalStrut(8));

		boolean marked = false;
		for (NextQuestStatus.Step step : next.route())
		{
			String prefix;
			Color color;
			if (step.done())
			{
				// "x" rather than a check mark: FontManager's small
				// RuneScape font is a bitmap font with no glyph for \u2713,
				// which drew as a tofu box players read as a stray "F".
				// Verified against the font's own cmap, not assumed --
				// plain ASCII is certain to be there, another Unicode pick
				// would only be a second gamble.
				prefix = "x  ";
				color = ColorScheme.PROGRESS_COMPLETE_COLOR.darker();
			}
			else if (!marked)
			{
				// Same reasoning as the check mark above: \u25b8 is missing
				// from the font too. ">" is the ASCII stand-in.
				prefix = ">  ";
				color = Color.WHITE;
				marked = true;
			}
			else
			{
				// \u00b7 (middle dot) is Latin-1 and the font does carry
				// it -- verified, not just assumed innocent because it
				// looked simpler than the other two.
				prefix = "\u00b7  ";
				color = ColorScheme.LIGHT_GRAY_COLOR;
			}

			JLabel row = row(prefix + step.questName());
			row.setFont(FontManager.getRunescapeSmallFont());
			row.setForeground(color);
			attachQuestHelperInteractions(row, step.questName());
			panel.add(row);
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
	private void fillQuestResults(QuestVerdicts verdicts)
	{
		questResults.removeAll();
		questResults.add(verdictBlock(verdicts, questSearch.getText(), selectedStatusFilter()));
	}

	private JPanel verdictBlock(QuestVerdicts verdicts, String filter, QuestStatus statusFilter)
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

		String needle = filter == null ? "" : filter.trim().toLowerCase();
		boolean filtering = !needle.isEmpty();

		JLabel done = new JLabel(wrap(verdicts.count(QuestStatus.COMPLETE) + " done"));
		done.setFont(FontManager.getRunescapeSmallFont());
		done.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(done);

		// A status picked from the combo narrows the sections shown to that one
		// group, on top of whatever text filter is also active. Left at "All
		// statuses", the grouping is the same as before: every section while
		// searching by name (so a finished quest is still findable), the
		// smaller default set otherwise.
		QuestStatus[] toRender = statusFilter != null
			? new QuestStatus[]{statusFilter}
			: (filtering ? QuestVerdicts.searchSections() : QuestVerdicts.sections());

		int matched = 0;
		for (QuestStatus status : toRender)
		{
			List<QuestGraph.Quest> shown = new ArrayList<>();
			for (QuestGraph.Quest quest : verdicts.quests(status))
			{
				if (!filtering || quest.name().toLowerCase().contains(needle))
				{
					shown.add(quest);
				}
			}
			if (shown.isEmpty())
			{
				continue;
			}
			matched += shown.size();
			panel.add(Box.createVerticalStrut(8));

			// The heading shows how many matched out of the group when
			// filtering, so a filter never makes a group look smaller than it
			// is.
			String count = filtering
				? shown.size() + " of " + verdicts.count(status)
				: String.valueOf(verdicts.count(status));
			JLabel groupHeading = new JLabel(QuestVerdicts.label(status) + "  ·  " + count);
			groupHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
			groupHeading.setForeground(statusColor(status));
			panel.add(groupHeading);

			// Every quest, not the first eight. The panel scrolls, and "and 49
			// more" was a list of 49 things you could not read -- the group
			// counts already gave the summary, so a truncated list gave
			// nothing the heading did not.
			for (QuestGraph.Quest quest : shown)
			{
				JLabel row = row(quest.name());
				row.setFont(FontManager.getRunescapeSmallFont());
				row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				attachQuestHelperMenu(row, quest.name());
				panel.add(row);
			}
		}

		if ((filtering || statusFilter != null) && matched == 0)
		{
			panel.add(Box.createVerticalStrut(8));
			panel.add(muted("No quest matches that."));
		}

		return panel;
	}

	/**
	 * Right-click a quest row to send it to Quest Helper, when Quest Helper
	 * can be reached at all.
	 *
	 * <p>The popup is built in {@link #questHelperMenu}, on the click --
	 * never here, and never once per row at render time. See
	 * {@link #questHelperBridge}'s own doc for why that matters at 218 rows.
	 */
	private void attachQuestHelperMenu(JLabel row, String questName)
	{
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent event)
			{
				maybeShow(event);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent event)
			{
				maybeShow(event);
			}

			private void maybeShow(java.awt.event.MouseEvent event)
			{
				// The popup trigger is mousePressed on macOS and
				// mouseReleased on Windows/Linux; checking both is the
				// portable way to catch either.
				if (!event.isPopupTrigger())
				{
					return;
				}
				questHelperMenu(questName).show(event.getComponent(), event.getX(), event.getY());
			}
		});
	}

	/**
	 * Left-click a route row to send it straight to Quest Helper; right-click
	 * for the same menu the quest browser gets. This is what "Next on your
	 * route" was missing entirely -- the right-click handler only ever
	 * reached the quest-browser rows, so the one place most players actually
	 * look had no affordance at all.
	 *
	 * <p>Availability, and the reason behind it, is computed on hover for the
	 * tooltip and again on click -- never at render time, for the same
	 * reason {@link #attachQuestHelperMenu} builds its popup lazily. The
	 * route is short, but the rule guarding it is the same rule.
	 */
	private void attachQuestHelperInteractions(JComponent row, String questName)
	{
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		// A cheap placeholder so ToolTipManager registers this component --
		// registration happens the first time setToolTipText is called with
		// non-null text -- before the real, reflection-backed text below
		// ever has to run. quickenTooltips(), called once on the whole
		// panel, still governs how fast that text appears from here.
		row.setToolTipText(questName);
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent event)
			{
				row.setToolTipText(questHelperBridge.availability(questName).menuLabel());
			}

			@Override
			public void mousePressed(java.awt.event.MouseEvent event)
			{
				maybeShowPopup(event);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent event)
			{
				maybeShowPopup(event);
			}

			@Override
			public void mouseClicked(java.awt.event.MouseEvent event)
			{
				if (event.getButton() == java.awt.event.MouseEvent.BUTTON1 && !event.isPopupTrigger())
				{
					startFromRoute(questName);
				}
			}

			private void maybeShowPopup(java.awt.event.MouseEvent event)
			{
				// The popup trigger is mousePressed on macOS and
				// mouseReleased on Windows/Linux; checking both is the
				// portable way to catch either.
				if (!event.isPopupTrigger())
				{
					return;
				}
				questHelperMenu(questName).show(event.getComponent(), event.getX(), event.getY());
			}
		});
	}

	/**
	 * One menu item, its label doubling as the reason when it is disabled.
	 *
	 * <p>A disabled Swing component receives no mouse events, so a tooltip on
	 * a disabled {@code JMenuItem} never shows -- which is why the reason
	 * lives in the label itself rather than behind a hover that would never
	 * fire.
	 */
	private JPopupMenu questHelperMenu(String questName)
	{
		JPopupMenu menu = new JPopupMenu();
		QuestHelperBridge.Availability availability = questHelperBridge.availability(questName);
		JMenuItem item = new JMenuItem(availability.menuLabel());
		item.setEnabled(availability == QuestHelperBridge.Availability.AVAILABLE);
		item.addActionListener(event -> startFromRoute(questName));
		menu.add(item);
		return menu;
	}

	/**
	 * Hands questName to {@link #questHelperBridge} and reports what
	 * actually happened. See {@link QuestHelperBridge}'s class doc for why
	 * the reflective call not throwing was never proof of anything -- this
	 * is the fix for that: the outcome reported here is always the verified
	 * one, never the fact that a method returned.
	 *
	 * <p>The callback can land on Quest Helper's own client thread rather
	 * than the EDT -- see {@link QuestHelperBridge#start}'s doc -- so it is
	 * always marshalled back before touching Swing, even for the two
	 * outcomes that in practice arrive synchronously.
	 */
	private void startFromRoute(String questName)
	{
		questHelperBridge.start(questName,
			outcome -> SwingUtilities.invokeLater(() -> reportStartOutcome(questName, outcome)));
	}

	/**
	 * Turns a {@link QuestHelperBridge.StartOutcome} into what the player
	 * sees. A confirmed start gets a brief, non-modal toast: silence on
	 * success was the entire bug this exists to fix, and good news does not
	 * need a decision dismissed before the player can keep going. Every
	 * other outcome gets a plain warning, naming the reason where one is
	 * actually known rather than guessed at.
	 */
	private void reportStartOutcome(String questName, QuestHelperBridge.StartOutcome outcome)
	{
		switch (outcome)
		{
			case CONFIRMED:
				showStartConfirmation(questName);
				return;
			case UNAVAILABLE:
				warn("Quest Helper could not start \"" + questName
					+ "\" -- it was no longer available by the time the click landed.");
				return;
			case CALL_FAILED:
				// Availability said AVAILABLE a moment ago; if the actual
				// handoff still failed, that is Quest Helper's integration
				// breaking between the click and the call, not a state this
				// menu item can pre-empt. Said plainly rather than swallowed.
				warn("Quest Helper could not start \"" + questName + "\". Check the client log.");
				return;
			case NOT_LOGGED_IN:
				warn("Quest Helper did not start \"" + questName + "\" -- you are not logged in.");
				return;
			case NOT_SELECTED:
				// The two known silent-return paths that land here --
				// Quest Helper's own account for "already effectively
				// complete" and any other reason it declined -- are not
				// distinguishable from outside QuestManager. Said honestly
				// rather than picking one to guess at.
				warn("Quest Helper did not start \"" + questName
					+ "\". It may already be effectively complete, or something else stopped it"
					+ " -- check the client log.");
				return;
			case UNVERIFIABLE:
				warn("Sent \"" + questName + "\" to Quest Helper, but questpath could not confirm"
					+ " whether it actually started -- the integration may be out of date."
					+ " Check the client log.");
				return;
			default:
				// Exhaustive over a closed enum; nothing else to report.
		}
	}

	private void warn(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Quest Path", JOptionPane.WARNING_MESSAGE);
	}

	/** How long a toast stays up before it dismisses itself. */
	private static final int START_TOAST_MILLIS = 2500;

	/**
	 * A brief, non-modal confirmation that Quest Helper actually selected
	 * the quest. Non-modal on purpose: this is good news, not a decision the
	 * player has to dismiss before doing anything else -- unlike the warning
	 * dialogs above, which really are asking for that.
	 */
	private void showStartConfirmation(String questName)
	{
		showToast("Started \"" + questName + "\" in Quest Helper.");
	}

	/**
	 * The same brief, non-modal toast {@link #showStartConfirmation} shows,
	 * generalised for any other "this went fine, no decision to make" line
	 * -- currently also used by {@link #reportRouteOutcome}.
	 */
	private void showToast(String message)
	{
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog toast = new JDialog(owner);
		toast.setUndecorated(true);
		toast.setModal(false);
		toast.setFocusableWindowState(false);

		JLabel label = new JLabel(wrap(message));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		label.setOpaque(true);
		label.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
		label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

		toast.getContentPane().add(label);
		toast.pack();
		toast.setLocationRelativeTo(this);
		toast.setVisible(true);

		Timer dismiss = new Timer(START_TOAST_MILLIS, event -> toast.dispose());
		dismiss.setRepeats(false);
		dismiss.start();
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

	/**
	 * Clears tabs' bodies for a rebuild, hiding each one first if it is
	 * currently the on-screen card.
	 *
	 * <p><b>Why:</b> {@code Container#removeAll()} calls {@code
	 * removeNotify()} on every direct child individually, and {@code
	 * removeNotify()} on a lightweight component showing over RuneLite's
	 * heavyweight game canvas runs AWT's own mixing pass -- {@code
	 * Component#mixOnHiding} into {@code Container#recursiveApplyCurrentShape}
	 * -- which walks that child's own subtree. Paid once per direct child
	 * rather than once for the whole tab, that is what turned an Items-tab
	 * rebuild (428 components, the current shopping list, changing on every
	 * bank or inventory event) into multi-second stalls -- measured live, not
	 * inferred: {@code questpath perf: render took 2489ms}, immediately
	 * preceded by an {@link EdtWatchdog} dump whose stack sat inside {@code
	 * removeAll -> removeNotify -> mixOnHiding -> recursiveApplyCurrentShape},
	 * called from this method at the Items tab's own {@code clearBodies} site.
	 * Three more stalls in the same 400-599ms-to-2.4-2.5s pattern followed in
	 * the same log, each preceded by its own {@code EdtWatchdog} dump landing
	 * in the identical stack.
	 *
	 * <p>This is the same fix {@link #cards}'s own class doc already
	 * describes for switching tabs -- {@code setVisible} instead of {@code
	 * removeAll}/{@code add} -- applied here to rebuilding a tab's contents
	 * in place rather than to switching away from it: hiding the container
	 * first means {@code isShowing()} is already false for it and everything
	 * under it before a single child is removed, which is what lets AWT skip
	 * or collapse the per-child walk. {@link #showBodies} is the other half
	 * -- restoring visibility only once a tab's entire new content is in
	 * place, never partway through, or the container is shown again only to
	 * have hundreds of new children added into it one at a time, which is the
	 * identical per-child cost on the add side instead of the remove side.
	 */
	private void clearBodies(PanelSections.Tab... tabs)
	{
		for (PanelSections.Tab tab : tabs)
		{
			JPanel body = tabBodies.get(tab);
			if (body.isShowing())
			{
				body.setVisible(false);
			}
			body.removeAll();
		}
	}

	/**
	 * Restores visibility for tabs {@link #clearBodies} hid for their own
	 * rebuild. Call once a tab's whole new content -- including its {@link
	 * #renderPlannedSectionsFor} call -- has been added, never in between;
	 * see {@link #clearBodies}'s own doc for why.
	 *
	 * <p>Only a tab {@link #clearBodies} actually hid is touched, identified
	 * by its own {@code isVisible()} still reading false: a tab that was
	 * never the on-screen card had nothing to fix and nothing to restore, and
	 * forcing it visible here would show it underneath whichever tab
	 * CardLayout actually has selected.
	 */
	private void showBodies(PanelSections.Tab... tabs)
	{
		for (PanelSections.Tab tab : tabs)
		{
			JPanel body = tabBodies.get(tab);
			if (!body.isVisible())
			{
				body.setVisible(true);
			}
		}
	}

	private void clearAllBodies()
	{
		for (JPanel bodyForTab : tabBodies.values())
		{
			bodyForTab.removeAll();
		}
		lastVerdictSignature = null;
		lastItemsSignature = null;
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
		renderPlannedSectionsFor(PanelSections.Tab.values());
	}

	private void renderPlannedSectionsFor(PanelSections.Tab... tabs)
	{
		for (PanelSections.Tab tab : tabs)
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

	/** How long a hover waits before this panel's tooltips appear. */
	private static final int TOOLTIP_DELAY_MILLIS = 150;

	/** How long they stay up. Long enough to read forty quest names. */
	private static final int TOOLTIP_DISMISS_MILLIS = 60_000;

	/**
	 * Makes this panel's tooltips appear promptly, and only this panel's.
	 *
	 * <p>Swing waits 750ms before showing one, which on a row whose whole
	 * purpose is the tooltip reads as the hover not working at all. The
	 * dismiss delay matters as much: the default four seconds is not long
	 * enough to read a twelve-quest prerequisite chain, and a tooltip that
	 * vanishes mid-sentence is worse than a slow one.
	 *
	 * <p>ToolTipManager is a process-wide singleton shared with every other
	 * plugin's sidebar, so the timings are changed on the way in and put back
	 * on the way out rather than set once at startup. Retuning the whole
	 * client's tooltips to suit one panel would be a side effect nobody asked
	 * for and nobody could find.
	 */
	private void quickenTooltips()
	{
		ToolTipManager tooltips = ToolTipManager.sharedInstance();
		addMouseListener(new java.awt.event.MouseAdapter()
		{
			private int initial;
			private int dismiss;
			private boolean saved;

			@Override
			public void mouseEntered(java.awt.event.MouseEvent event)
			{
				if (!saved)
				{
					initial = tooltips.getInitialDelay();
					dismiss = tooltips.getDismissDelay();
					saved = true;
				}
				tooltips.setInitialDelay(TOOLTIP_DELAY_MILLIS);
				tooltips.setDismissDelay(TOOLTIP_DISMISS_MILLIS);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event)
			{
				if (saved)
				{
					tooltips.setInitialDelay(initial);
					tooltips.setDismissDelay(dismiss);
				}
			}
		});
	}

	/**
	 * The skill planner's fixed furniture: pick a skill, pick a level.
	 *
	 * Built once. Everything below it is rebuilt whenever the answer changes,
	 * but the two controls keep whatever the player last chose -- including
	 * across the experience drops that make this panel refresh in the first
	 * place.
	 */
	private void buildSkillTab()
	{
		for (Skill skill : Skill.values())
		{
			// OVERALL is an aggregate, not a skill. A target level for it is
			// not a thing the game has.
			if (skill != Skill.OVERALL)
			{
				skillChoice.addItem(skill.getName());
			}
		}
		skillChoice.setFocusable(false);
		skillChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		skillChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
		skillChoice.addActionListener(event -> renderSkillPlan());

		skillTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		skillTarget.setAlignmentX(Component.LEFT_ALIGNMENT);
		skillTarget.addChangeListener(event -> renderSkillPlan());

		skillResults.setLayout(new BoxLayout(skillResults, BoxLayout.Y_AXIS));
		skillResults.setAlignmentX(Component.LEFT_ALIGNMENT);

		addSkillFurniture();
	}

	/**
	 * Puts the controls back on the tab.
	 *
	 * Separate from building them because logging out clears every tab body,
	 * and a control built once and cleared once is a control gone for the
	 * session. These are the same component instances every time -- re-adding
	 * moves them rather than making new ones, so the chosen skill and target
	 * survive a logout.
	 */
	private void addSkillFurniture()
	{
		JPanel body = tabBodies.get(PanelSections.Tab.SKILLS);
		body.add(heading(PanelSections.SKILL_PLAN.title()));
		body.add(skillChoice);
		body.add(Box.createVerticalStrut(4));
		body.add(labelled("Target level", skillTarget));
		body.add(Box.createVerticalStrut(8));
		body.add(skillResults);
	}

	private static JPanel labelled(String text, Component control)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel caption = new JLabel(text);
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(caption);
		row.add(Box.createHorizontalStrut(6));
		row.add(control);
		return row;
	}

	/**
	 * Recomputes the plan for whatever is selected and redraws the answer.
	 *
	 * Called both by the controls and by every refresh, because the answer
	 * changes for two different reasons: you picked a different target, or you
	 * finished a quest that used to be on the "later" list.
	 */
	private void renderSkillPlan()
	{
		skillResults.removeAll();

		Object chosen = skillChoice.getSelectedItem();
		SkillPlan plan = planner == null || chosen == null
			? null
			: planner.apply(chosen.toString(), (Integer) skillTarget.getValue());

		if (plan == null)
		{
			// No graph, or logged out. Not "nothing to train" -- there is no
			// account to answer about, which is a different statement.
			skillResults.add(muted("Log in, and questpath will price this out."));
		}
		else
		{
			skillResults.add(skillPlanBlock(plan));
		}

		skillResults.revalidate();
		skillResults.repaint();
	}

	/**
	 * What the level costs, and how much of it questing pays for.
	 *
	 * The headline is deliberately the training figure rather than the total.
	 * The total is what a skill guide already tells you; the number nobody
	 * else can produce is what is left after the quests this account can
	 * actually still do have paid out.
	 */
	/**
	 * Not static, unlike the rest of this method's history -- {@link #band}
	 * below needs {@link #attachQuestHelperInteractions} to wire its rows up
	 * to Quest Helper, the same reason {@link #itemsBlock} stopped being
	 * static.
	 */
	private JPanel skillPlanBlock(SkillPlan plan)
	{
		JPanel panel = column();

		if (plan.targetBreaksBuild())
		{
			// Said first and in red. Every number below it is arithmetic about
			// something this account has decided it will not do.
			JLabel warning = new JLabel(wrap("Your build caps " + plan.skill() + " at "
				+ plan.cappedAt() + ". Level " + plan.targetLevel() + " would end it."));
			warning.setFont(FontManager.getRunescapeSmallFont());
			warning.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			panel.add(warning);
			panel.add(Box.createVerticalStrut(6));
		}

		if (plan.reached())
		{
			panel.add(muted("You are already " + plan.currentLevel() + " "
				+ plan.skill() + "."));
			return panel;
		}

		JPanel summary = grid();
		addRow(summary, "Now", plan.currentLevel() + " -> " + plan.targetLevel(),
			ColorScheme.LIGHT_GRAY_COLOR);
		addRow(summary, "Experience", XpTable.format(plan.xpNeeded()),
			ColorScheme.LIGHT_GRAY_COLOR);
		addRow(summary, "From quests now", XpTable.format(plan.readyXp()),
			ColorScheme.PROGRESS_COMPLETE_COLOR);
		addRow(summary, "From quests later", XpTable.format(plan.laterXp()),
			ColorScheme.BRAND_ORANGE);
		// Two training figures, and the first is the one that decides tonight.
		// "After everything" prices work the account has not agreed to do --
		// for a long prerequisite chain it is months out -- so it is shown
		// second and as the floor it is.
		addRow(summary, "Train, ready only", XpTable.format(plan.xpToTrainFromReadyOnly()),
			plan.readyQuestsAreEnough()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.LIGHT_GRAY_COLOR,
			"If you only do the quests you can start right now and nothing else,"
				+ " this is what is left to train.");
		addRow(summary, "Train, after all", XpTable.format(plan.xpToTrain()),
			plan.questsAreEnough()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.LIGHT_GRAY_COLOR,
			"The floor: what is left once every quest you could ever reach has"
				+ " paid out. The gap between these two is what unblocking the"
				+ " list below is worth.");
		panel.add(summary);

		if (plan.readyQuestsAreEnough())
		{
			panel.add(Box.createVerticalStrut(6));
			panel.add(muted("The quests you can start today get you there on their own."));
		}
		else if (plan.questsAreEnough())
		{
			panel.add(Box.createVerticalStrut(6));
			panel.add(muted("Quests alone get you there, but not without"
				+ " unblocking some first."));
		}

		if (plan.lostXp() > 0)
		{
			// The one number only this plugin can produce, and the reason the
			// training figure above is bigger than a skill guide would say.
			panel.add(Box.createVerticalStrut(6));
			JLabel lost = new JLabel(wrap(XpTable.format(plan.lostXp())
				+ " is off the table for this build, across "
				+ plan.lost().size() + " quest(s)."));
			lost.setFont(FontManager.getRunescapeSmallFont());
			lost.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			panel.add(lost);
		}

		// Only READY NOW is wired to Quest Helper. SkillPlan has already done
		// the split that matters: ready() holds only ELIGIBLE/BOOSTABLE
		// entries, and every later() or lost() entry exists precisely because
		// it is not startable right now, for reasons this tab already states
		// as the blocker chain or the build-break warning above. Wiring
		// those to Quest Helper would only ever open a popup whose one item
		// is disabled, saying nothing this tab has not already said better.
		band(panel, "READY NOW", plan.ready(), ColorScheme.PROGRESS_COMPLETE_COLOR, true);
		band(panel, "AFTER MORE WORK", plan.later(), ColorScheme.BRAND_ORANGE, false);
		band(panel, "OFF THE TABLE", plan.lost(), ColorScheme.PROGRESS_ERROR_COLOR, false);

		return panel;
	}

	/** How many quests one band lists before it says how many more there are. */
	private static final int MAX_PLAN_ROWS = 8;

	/**
	 * Not static, unlike the rest of this method's history -- a {@code
	 * questHelperLinked} band needs {@link #attachQuestHelperInteractions}
	 * to wire its rows up, the same left-click-to-start, right-click-for-a-
	 * reason handoff the route panel already gets.
	 *
	 * @param questHelperLinked whether this band's rows should be wired to
	 *     Quest Helper. True only for {@code entries} that are actually
	 *     startable -- see the call site in {@link #skillPlanBlock} for why
	 *     that is READY NOW alone.
	 */
	private void band(JPanel panel, String title, List<SkillPlan.Entry> entries,
		Color colour, boolean questHelperLinked)
	{
		if (entries.isEmpty())
		{
			return;
		}
		panel.add(Box.createVerticalStrut(10));
		JLabel bandHeading = new JLabel(title + "  ·  " + entries.size());
		bandHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		bandHeading.setForeground(colour);
		panel.add(bandHeading);

		JPanel rows = grid();
		int shown = Math.min(entries.size(), MAX_PLAN_ROWS);
		for (int i = 0; i < shown; i++)
		{
			SkillPlan.Entry entry = entries.get(i);
			JLabel nameLabel = addRow(rows, entry.questName(), XpTable.format(Math.round(entry.xp())),
				ColorScheme.LIGHT_GRAY_COLOR, blockerTooltip(entry));
			if (questHelperLinked)
			{
				attachQuestHelperInteractions(nameLabel, entry.questName());
			}
		}
		panel.add(rows);

		int remaining = entries.size() - shown;
		if (remaining > 0)
		{
			// The biggest payouts are listed; the tail is counted rather than
			// dropped, so the totals above never look unaccounted for.
			panel.add(muted("...and " + remaining + " more, worth "
				+ XpTable.format(tail(entries, shown)) + "."));
		}
	}

	/**
	 * How much of a blocker chain one tooltip shows before counting the rest.
	 *
	 * While Guthix Sleeps on a fresh account is forty-one quests and eighteen
	 * skills, which is a fifty-nine line tooltip -- taller than the client and
	 * unreadable at any height. Both lists lead with what matters (deepest
	 * prerequisite first, biggest shortfall first), so a cut tail is the least
	 * useful part, and it is counted rather than dropped so the tooltip never
	 * understates the work.
	 */
	private static final int MAX_TOOLTIP_QUESTS = 12;
	private static final int MAX_TOOLTIP_SKILLS = 8;

	/**
	 * Spells out what "after more work" actually means for one quest.
	 *
	 * <p>The label names a category; this names the work. Everything in the
	 * chain, not just the immediate blocker -- a quest behind one quest that
	 * is itself behind three is not one quest away, and saying so would send
	 * someone off to start something they cannot finish.
	 *
	 * <p>Null when there is nothing to say, so ready quests get no empty
	 * tooltip following the pointer around.
	 */
	private static String blockerTooltip(SkillPlan.Entry entry)
	{
		QuestBlockers blockers = entry.blockers();
		if (blockers == null || blockers.isEmpty())
		{
			return null;
		}

		StringBuilder text = new StringBuilder("<html><body style='width:230px'>");
		text.append("<b>").append(escape(entry.questName())).append("</b>");

		List<String> questsFirst = blockers.quests();
		if (!questsFirst.isEmpty())
		{
			text.append("<br><br>Do these first, in order:");
			int shown = Math.min(questsFirst.size(), MAX_TOOLTIP_QUESTS);
			for (int i = 0; i < shown; i++)
			{
				text.append("<br>&nbsp;&nbsp;").append(escape(questsFirst.get(i)));
			}
			if (questsFirst.size() > shown)
			{
				text.append("<br>&nbsp;&nbsp;...and ")
					.append(questsFirst.size() - shown).append(" more");
			}
		}

		List<QuestBlockers.Gap> gaps = blockers.skills();
		if (!gaps.isEmpty())
		{
			text.append("<br><br>Levels needed along the way:");
			int shown = Math.min(gaps.size(), MAX_TOOLTIP_SKILLS);
			for (int i = 0; i < shown; i++)
			{
				QuestBlockers.Gap gap = gaps.get(i);
				text.append("<br>&nbsp;&nbsp;").append(escape(gap.skill()))
					.append(' ').append(gap.have()).append(" to ").append(gap.need());
				text.append(boostNote(gap));
			}
			if (gaps.size() > shown)
			{
				text.append("<br>&nbsp;&nbsp;...and ")
					.append(gaps.size() - shown).append(" more");
			}
		}

		return text.append("</body></html>").toString();
	}

	/**
	 * What a boost does for one shortfall, named and sized.
	 *
	 * <p>This line used to read "(a boost covers this)" for any gap the wiki
	 * permitted a boost on, whatever its size -- which reported a nine-level
	 * Cooking shortfall as solved by a boost that exists at no level in the
	 * game. It now says which item and how many levels, so the claim can be
	 * checked rather than taken.
	 *
	 * <p>A boost that falls short is still worth naming. "Best is +3, you are
	 * 9 down" tells you both that drinking will not do it and how far off it
	 * is, which is more than silence and more than a false yes.
	 */
	private static String boostNote(QuestBlockers.Gap gap)
	{
		if (!gap.boostable())
		{
			// The quest refuses boosted levels outright. Saying nothing here
			// is right: there is no item to name.
			return "";
		}
		Boosts.Best boost = gap.boost();
		if (boost.levels() <= 0)
		{
			return " (no boost reaches this)";
		}
		if (gap.covered())
		{
			return " (+" + boost.levels() + " from " + escape(boost.item()) + " covers it)";
		}
		return " (best is +" + boost.levels() + " from " + escape(boost.item())
			+ ", " + gap.shortfall() + " short)";
	}

	private static long tail(List<SkillPlan.Entry> entries, int from)
	{
		double total = 0;
		for (int i = from; i < entries.size(); i++)
		{
			total += entries.get(i).xp();
		}
		return Math.round(total);
	}

	/**
	 * Lays out and repaints what is actually on screen.
	 *
	 * It used to walk all six tab bodies. Five of them are not in the
	 * displayed hierarchy at any moment, so validating them bought nothing --
	 * and it happened on every refresh, which is every game tick that anything
	 * changed. The one that is showing gets validated through `display`, which
	 * is its ancestor, and the other five validate themselves when a tab
	 * switch puts them on screen.
	 */
	private void repaintAll()
	{
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
	private static JPanel syncBlock(SyncStatus sync, List<String> unmatchedQuests,
		SyncSettings settings, AccountReport report)
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

		// The settings themselves, not only their effect. RuneLite's config
		// panel is several clicks away and behind a different icon, and
		// "sent 3m ago" does not say which server it went to -- which is the
		// first thing anyone checks when it went somewhere unexpected.
		if (settings != null)
		{
			panel.add(Box.createVerticalStrut(8));
			JPanel grid = grid();
			addRow(grid, "Sending", settings.enabled() ? "on" : "off",
				settings.enabled() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			addRow(grid, "Server", settings.serverUrl(), ColorScheme.LIGHT_GRAY_COLOR);
			addRow(grid, "Token", settings.hasToken() ? "set" : "not set",
				settings.hasToken() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
			panel.add(grid);

			JLabel where = new JLabel(wrap("Change these under Settings, questpath."));
			where.setFont(FontManager.getRunescapeSmallFont());
			where.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			panel.add(where);
		}

		// Who is being sent, and what. The tab used to be able to say only
		// whether the last push worked, which answers "is it on" and nothing
		// about which account it wrote -- and that is the question that
		// matters. Three pushes once wrote one character's quest states over
		// another account's, and every one of them looked like an ordinary
		// sync from inside the game.
		if (report != null)
		{
			panel.add(Box.createVerticalStrut(10));
			JLabel who = new JLabel(wrap(report.characterName()));
			who.setFont(FontManager.getRunescapeBoldFont());
			who.setForeground(Color.WHITE);
			panel.add(who);

			JLabel whoNote = new JLabel(wrap("This is the account every push is attributed to."));
			whoNote.setFont(FontManager.getRunescapeSmallFont());
			whoNote.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			panel.add(whoNote);

			panel.add(Box.createVerticalStrut(6));
			JPanel sent = grid();
			addRow(sent, "Combat", String.valueOf(report.combatLevel()), ColorScheme.LIGHT_GRAY_COLOR);
			addRow(sent, "Quest points", String.valueOf(report.questPoints()), ColorScheme.LIGHT_GRAY_COLOR);
			addRow(sent, "Total level", String.valueOf(report.totalLevel()), ColorScheme.LIGHT_GRAY_COLOR);
			addRow(sent, "Total xp", XpTable.format(report.totalXp()), ColorScheme.LIGHT_GRAY_COLOR);
			addRow(sent, "Quests done", report.questsComplete() + " of " + report.questsKnown(),
				ColorScheme.LIGHT_GRAY_COLOR);
			addRow(sent, "Quests started", String.valueOf(report.questsInProgress()),
				ColorScheme.LIGHT_GRAY_COLOR);
			// Two facts, not one. Zero stacks with the bank seen is an empty
			// bank; zero stacks without it is a bank nobody has opened, and
			// questpath's item answers are working from an older upload until
			// they are.
			addRow(sent, "Item stacks", String.valueOf(report.itemStacks()),
				report.itemStacks() > 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
			addRow(sent, "Bank", report.bankSeen() ? "seen this session" : "not opened yet",
				report.bankSeen() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR);
			panel.add(sent);

			if (!report.bankSeen())
			{
				JLabel hint = new JLabel(wrap("Open your bank once and the item lists fill in."));
				hint.setFont(FontManager.getRunescapeSmallFont());
				hint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				panel.add(hint);
			}
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
				JLabel row = row(name);
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

	/**
	 * Builds a row's tooltip from an optional note plus its item's obtain
	 * sources, or null when there is neither.
	 *
	 * Null, not an empty string: Swing renders a tooltip with an empty
	 * string as a visible blank balloon, which is worse than no tooltip at
	 * all, and an item questpath knows no source for must show no tooltip
	 * rather than an empty or invented one.
	 */
	private static String itemTooltip(String note, List<String> sources)
	{
		List<String> lines = new ArrayList<>();
		if (note != null && !note.isEmpty())
		{
			lines.add(note);
		}
		if (sources != null)
		{
			lines.addAll(sources);
		}
		if (lines.isEmpty())
		{
			return null;
		}

		StringBuilder html = new StringBuilder("<html>");
		for (int i = 0; i < lines.size(); i++)
		{
			if (i > 0)
			{
				html.append("<br>");
			}
			html.append(escapeHtml(lines.get(i)));
		}
		html.append("</html>");
		return html.toString();
	}

	/** Swing tooltips render as HTML, so a source line has to escape it. */
	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void addRow(JPanel grid, String label, String detail, Color detailColor)
	{
		addRow(grid, label, detail, detailColor, null);
	}

	/**
	 * @return the label cell, so a caller that needs to wire it up further --
	 *     {@link #band} attaches {@link #attachQuestHelperInteractions} to
	 *     it -- does not have to rebuild the row itself. Every other caller
	 *     ignores the return value, which is why every one of them still
	 *     compiles as a bare statement unchanged.
	 */
	private static JLabel addRow(JPanel grid, String label, String detail, Color detailColor, String tooltip)
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
		if (tooltip != null)
		{
			left.setToolTipText(tooltip);
			right.setToolTipText(tooltip);
		}
		grid.add(right, c);
		return left;
	}

	private static String skillName(Skill skill)
	{
		String name = skill.getName();
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}

	/**
	 * A one-line row label, in plain text rather than HTML.
	 *
	 * This exists because every label in this panel used to be an HTML one.
	 * wrap() returns "<html><body style='width:150px'>...", which makes Swing
	 * build a whole document model per label -- fine for a sentence, ruinous
	 * in a loop. The quest browser lists all 218 quests, the route lists 91,
	 * and the item lists hundreds more, so switching to those tabs was laying
	 * out and painting several hundred HTML documents at once. That is what
	 * made the tab strip stutter.
	 *
	 * A plain JLabel clips with an ellipsis on its own when the column is too
	 * narrow, so nothing is lost visually; the full text goes on the tooltip,
	 * which is better than wrapping for a list you are scanning anyway.
	 *
	 * wrap() is still right for prose -- warnings, explanations, the states
	 * that need a sentence. Those are counted in ones, not hundreds.
	 */
	private static JLabel row(String text)
	{
		JLabel label = new JLabel(text);
		label.setToolTipText(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
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
