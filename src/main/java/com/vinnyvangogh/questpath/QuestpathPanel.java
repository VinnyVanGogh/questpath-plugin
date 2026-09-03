package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
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

	private final JTextField itemSearch = new JTextField();
	private final JPanel itemResults = new JPanel();
	private QuestItems lastItems;

	QuestpathPanel(SpriteManager sprites, OverlaySettingsPanel overlaySettings)
	{
		this.overlaySettings = overlaySettings;
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
			quests.add(heading(PanelSections.NEXT_QUEST.title()));
			quests.add(nextQuestBlock(nextQuest));
			quests.add(Box.createVerticalStrut(14));
			quests.add(heading(PanelSections.QUEST_VERDICT.title()));
			if (verdicts != null)
			{
				quests.add(questSearch);
				quests.add(Box.createVerticalStrut(4));
				quests.add(questStatusFilter);
				quests.add(Box.createVerticalStrut(6));
			}
			quests.add(questResults);
			fillQuestResults(verdicts);
			renderPlannedSectionsFor(PanelSections.Tab.QUESTS);
			lastVerdictSignature = questsSignature;
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
		}

		renderPlannedSectionsFor(PanelSections.Tab.SYNC);
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

	private static JPanel itemsBlock(QuestItems items, String filter)
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

		// One shopping list first, the per-quest breakdown after. Coins wanted
		// by two quests is one trip to the bank for the sum, not two lines to
		// add up in your head.
		JLabel totalsHeading = new JLabel("EVERYTHING YOU NEED");
		totalsHeading.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		totalsHeading.setForeground(ColorScheme.BRAND_ORANGE);
		panel.add(totalsHeading);

		JPanel totalsGrid = grid();
		int shownTotals = 0;
		for (QuestItems.Total total : items.totals())
		{
			if (filtering && !total.name().toLowerCase().contains(needle))
			{
				continue;
			}
			shownTotals++;
			// The count of quests goes in the tooltip, not the label. "Ball of
			// wool x3" beside "5 / 24" read as a multiplication rather than as
			// "three quests want this", which is what it meant. Where to buy
			// or find it rides in the same tooltip -- one hover answers both
			// "why do I need several" and "where do I get it".
			String sharedNote = total.sharedAcrossQuests()
				? "Needed by " + total.quests() + " quests you can do"
				: null;
			// Three colours for three states, not two. Orange is the one you
			// are part way through -- some in your bags and still short --
			// which the sort has already floated to the top of the list; the
			// colour is what stops it reading as just another grey row that
			// happens to be first.
			Color totalColour = total.satisfied()
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: total.inProgress()
					? ColorScheme.PROGRESS_INPROGRESS_COLOR
					: ColorScheme.LIGHT_GRAY_COLOR;
			addRow(totalsGrid, total.name(), total.describe(), totalColour,
				itemTooltip(sharedNote, total.sources()));
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
	private static JPanel nextQuestBlock(NextQuestStatus next)
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
				JLabel value = new JLabel(wrap(next.questName()));
				value.setFont(FontManager.getRunescapeBoldFont());
				value.setForeground(Color.WHITE);
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
	 */
	private static JPanel routeList(NextQuestStatus next)
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
				prefix = "\u2713  ";
				color = ColorScheme.PROGRESS_COMPLETE_COLOR.darker();
			}
			else if (!marked)
			{
				prefix = "\u25b8  ";
				color = Color.WHITE;
				marked = true;
			}
			else
			{
				prefix = "\u00b7  ";
				color = ColorScheme.LIGHT_GRAY_COLOR;
			}

			JLabel row = row(prefix + step.questName());
			row.setFont(FontManager.getRunescapeSmallFont());
			row.setForeground(color);
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

	private static JPanel verdictBlock(QuestVerdicts verdicts, String filter, QuestStatus statusFilter)
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

	private void clearBodies(PanelSections.Tab... tabs)
	{
		for (PanelSections.Tab tab : tabs)
		{
			tabBodies.get(tab).removeAll();
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

	private static void addRow(JPanel grid, String label, String detail, Color detailColor, String tooltip)
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
