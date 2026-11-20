package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.GroupShoppingList;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import com.vinnyvangogh.questpath.groupshop.ItemSources;
import com.vinnyvangogh.questpath.groupshop.Transfer;
import com.vinnyvangogh.questpath.PanelSections.Tab;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JViewport;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;
import static com.vinnyvangogh.questpath.Ui.*;
import static net.runelite.client.ui.ColorScheme.*;
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

	/**
	 * The skill icons are around 25px; the tab strip has five of them across a
	 * 225px sidebar, so they are scaled down to leave room for the padding
	 * between them.
	 */
	private static final int TAB_ICON_SIZE = 20;

	private final Map<Tab, JPanel> tabBodies = new EnumMap<>(Tab.class);

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
	 * What the Combat tab last rendered -- including its Build rules section,
	 * since Task 4a folded Tab.BUILD into Tab.COMBAT.
	 *
	 * One signature now drives one clear/rebuild for the whole tab: {@code
	 * stats}, {@code xp.signature()}, {@code rules}, AND the "show all
	 * rules" toggle. The toggle has to be part of it -- it can change what
	 * the same rules render as with no new stat or rule ever arriving, and
	 * the two signatures this replaced could not have caught that between
	 * them: the old combat gate had no rules to watch and the old build gate
	 * had no toggle to watch, and one tab cannot go half-stale.
	 *
	 * They were torn down and rebuilt on every refresh while Quests and Items
	 * were signature-guarded, which is about seventy components thrown away
	 * and remade per tick for a picture that only changes when a level or a
	 * rule does. The Sync tab deliberately has no guard: it says "sent 3m
	 * ago", so it has to be rebuilt to stay true.
	 */
	private Object lastCombatSignature;

	/**
	 * The last combat inputs the tab actually rendered, cached so the "show
	 * all rules" toggle can rebuild the tab immediately from what is already
	 * known, rather than waiting for the next stat or rule push to arrive.
	 */
	private CombatStats lastStats;
	private SkillXp lastXp;
	private List<BuildRule> lastRules;
	/**
	 * The store behind the sidebar's own rules editor, and what to run after
	 * an edit. Null in a preview harness, which the editor block renders as
	 * "unavailable" rather than as a form that silently does nothing.
	 */
	private final LocalBuildRules localBuildRules;
	private final Runnable onRulesEdited;

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

	/**
	 * The Quests tab's own Quests/Diaries/All filter -- see {@link
	 * QuestJournalFilter}'s own class doc for what it drives beyond this
	 * panel. Built once, like {@link #questStatusFilter}, and its choice is
	 * read from {@link QuestpathConfig#questJournalFilter()} on first build
	 * and written back through {@link #configManager} on every change, so
	 * it survives both a panel rebuild and a client restart.
	 */
	private final JComboBox<QuestJournalFilter> journalFilterCombo = new JComboBox<>();

	private final JPanel questResults = new JPanel();
	private QuestVerdicts lastVerdicts;
	private NextQuestStatus lastNextQuest;
	private RouteWork lastRouteWork;

	/** The overlay settings tab's contents, built once and kept. */
	private final OverlaySettingsPanel overlaySettings;

	/** The most-used toggles, above {@link #overlaySettings}; re-read on every config change. */
	private final QuickSettingsPanel quickSettings;

	/** Re-reads the quick settings after a config change; safe from any thread. */
	void refreshQuickSettings()
	{
		SwingUtilities.invokeLater(quickSettings::refresh);
	}

	/** The quick settings' "All settings" link: opens the View tab and scrolls the full settings into view. */
	private void scrollToAllSettings()
	{
		if (overlaySettings == null)
		{
			return;
		}
		tabGroup.select(materialTabs.get(Tab.VIEW));
		Container viewport = SwingUtilities.getAncestorOfClass(JViewport.class, overlaySettings);
		if (viewport instanceof JViewport)
		{
			Point p = SwingUtilities.convertPoint(overlaySettings, 0, 0,
				((JViewport) viewport).getView());
			((JViewport) viewport).setViewPosition(new Point(0, p.y));
		}
	}

	/**
	 * Read for {@code buildRulesShowAll} and written by its checkbox in the
	 * Build rules section -- see {@link #buildRulesToggle()}. Everything
	 * else this panel shows is pushed in through {@link #update}; this is
	 * the one setting the panel itself both reads and writes, the same
	 * ConfigManager path {@link OverlaySettingsPanel} uses for its own
	 * controls.
	 */
	private final QuestpathConfig config;
	private final ConfigManager configManager;

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
	private final BiFunction<String, Integer, SkillPlan> planner;

	/** What the skill plan was last drawn for; see the Build tab's gate. */
	private Object lastSkillSignature;

	private final JTextField itemSearch = new JTextField();
	private final JPanel itemResults = new JPanel();
	private QuestItems lastItems;

	/**
	 * Answers whether the current shop route can be handed to Shortest
	 * Path, and does the handing-off. See {@link ShortestPathBridge}'s class
	 * doc for exactly how strong a claim {@link
	 * ShortestPathBridge.RouteOutcome#SENT} is.
	 */
	private final ShortestPathBridge shortestPathBridge;

	/**
	 * Shows a clicked item in the Grand Exchange's own search results.
	 * Only ever reachable for an account {@link #geAvailable} reports true
	 * for -- see {@link GrandExchangeAccounts}'s own doc.
	 */
	private final GrandExchangeSearchBridge geSearchBridge;

	/**
	 * Whether this account can use the Grand Exchange at all, cached by
	 * {@code QuestpathPlugin} from the client's own account type -- see
	 * {@code QuestpathPlugin#grandExchangeAvailable}'s doc for why this
	 * panel cannot ask the client directly. The one gate the Grand Exchange
	 * list is behind: automatic, not a config item, so an ironman never
	 * sees it however any setting is left.
	 */
	private final BooleanSupplier geAvailable;

	/**
	 * Where to walk for a shop by name, or null when the bundled graph
	 * carries no coordinate for it. A callback rather than a graph of its
	 * own for the same reason {@link #planner} is one: the panel should not
	 * learn to hold the bundle, only ask the one question it needs answered.
	 */
	private final Function<String, WorldPoint> shopLocator;

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
	/**
	 * Whether a shop route sent to Shortest Path is still live, so a skip
	 * knows whether there is a trip to redirect. Supplied by the plugin
	 * rather than tracked here: the panel does not own that window.
	 */
	/** Calls off a running shop trip -- see QuestpathPlugin#cancelShopRoute. */
	private final Runnable onCancelShopRoute;

	/**
	 * Pushes to questpath right now. See QuestpathPlugin#syncNow for why the
	 * automatic gate is the wrong thing to hang a button on.
	 */
	private final Runnable onSyncNow;

	/**
	 * Where the player is, so the trip can be ordered from there.
	 *
	 * A shopping route that does not know where you are standing can only
	 * sort by something that is not distance -- which is how it ended up
	 * alphabetical.
	 */
	private final Supplier<WorldPoint> playerLocation;

	/** Resolves a monster name to a tile, for an item no shop sells. */
	private final Function<String, WorldPoint> dropLocator;
	private final BooleanSupplier shopRouteActive;
	private final ShopItemSkips shopItemSkips;

	/**
	 * Every achievement diary task the current graph carries, account-agnostic
	 * -- see {@code QuestpathPlugin#diaryGraphTasks}. A callback rather than a
	 * graph of its own, the same reasoning {@link #shopLocator} follows: the
	 * panel should not learn to hold the bundle.
	 */
	private final Supplier<List<QuestGraph.DiaryTask>> diaryGraphTasks;

	/**
	 * The account's per-account diary routing data, as questpath last
	 * reported it, or null when there is none -- see {@code
	 * QuestpathPlugin#diaryPush}'s own doc for what "none" covers.
	 */
	private final Supplier<PushResponse.Diary> diaryPush;

	/** Every callback the Locked gear section calls back into the plugin for. See its own class doc. */
	private final LockedGearActions lockedGearActions;

	/** What the Locked gear section last rendered, cached only so a failed-action message can force an immediate redraw. */
	private LockedGear lastLockedGear;

	/**
	 * The last locked-gear action's failure message, or null. Shown in the
	 * section until the next action attempt, success or failure -- see
	 * {@link #lockedGearActionFailed}.
	 */
	private String lockedGearMessage;

	/** Whether the Locked gear section's item list is expanded. Not persisted -- a per-session UI convenience, like the search boxes' text. */
	private boolean lockedGearItemsExpanded;

	/** "Say again" on the current shop stop; null until the plugin sets it. */
	private volatile Consumer<String> sayShopAgain;

	/**
	 * Non-null while this account has no local profile: the Quests tab then
	 * shows first-run setup instead of the route. The text says why (first
	 * run, or a damaged file that was set aside). Set from the plugin.
	 */
	private volatile String setupNote;
	private volatile LocalProfile setupCurrent;
	private volatile BiConsumer<LocalProfile, List<BuildRule>> onSetupDone;
	private volatile Runnable onSetupCancel;
	private volatile Runnable onOpenSetup;
	/** Bumped on every setSetup, so reopening setup rebuilds the tab even with an unchanged note. */
	private volatile int setupRevision;
	private final Map<Tab, MaterialTab> materialTabs = new EnumMap<>(Tab.class);
	private MaterialTabGroup tabGroup;

	/** Shows ({@code note != null}) or hides first-run setup; takes effect on the next render. */
	void setSetup(String note, BiConsumer<LocalProfile, List<BuildRule>> onDone)
	{
		setSetup(note, null, onDone, null);
	}

	/**
	 * Shows setup, filled in from {@code current} when there is one, with a
	 * cancel that leaves the saved answers alone ({@code onCancel} null hides it).
	 */
	void setSetup(String note, LocalProfile current,
		BiConsumer<LocalProfile, List<BuildRule>> onDone, Runnable onCancel)
	{
		this.setupNote = note;
		this.setupCurrent = current;
		this.onSetupDone = onDone;
		this.onSetupCancel = onCancel;
		this.setupRevision++;
	}

	/** Wires the always-visible "Account setup" button. */
	void setOnOpenSetup(Runnable onOpenSetup)
	{
		this.onOpenSetup = onOpenSetup;
	}

	/** Wires the "What's new" button beside it. */
	void setOnWhatsNew(Runnable onWhatsNew)
	{
		this.onWhatsNew = onWhatsNew;
	}

	private volatile Runnable onWhatsNew;

	/**
	 * The player's own marks on quests -- see {@link LocalProfile.Mark} --
	 * read off the profile the plugin holds (replaced wholesale on every
	 * change, so its identity rides the Quests tab's signature) and written
	 * back through {@code onMark}.
	 */
	void setQuestMarks(Supplier<LocalProfile> profile, BiConsumer<String, LocalProfile.Mark> onMark)
	{
		this.questProfile = profile;
		this.onQuestMark = onMark;
	}

	private volatile Supplier<LocalProfile> questProfile;
	private volatile BiConsumer<String, LocalProfile.Mark> onQuestMark;

	private LocalProfile profile()
	{
		Supplier<LocalProfile> supplier = questProfile;
		return supplier == null ? null : supplier.get();
	}

	/** Makes the Quests tab the one on screen; setup lives there. */
	void showQuestsTab()
	{
		MaterialTab tab = materialTabs.get(Tab.QUESTS);
		if (tab != null && tabGroup != null)
		{
			tabGroup.select(tab);
		}
	}

	/** "Account setup" and "What's new", above the tabs so they are one click from every page. */
	private JPanel setupHeader()
	{
		JButton setup = button("Account setup", event -> run(onOpenSetup));
		setup.setToolTipText("Account type, build, skill caps and quest filters. Opens setup from any page.");
		JButton whatsNew = button("What's new", event -> run(onWhatsNew));
		whatsNew.setToolTipText("Print this version's update notes in the game chat again.");
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		row.add(setup);
		row.add(whatsNew);
		return row;
	}

	private static void run(Runnable action)
	{
		if (action != null)
		{
			action.run();
		}
	}

	/** Wires the shop stop's "Say again" button to the plugin's voice. */
	void setSayShopAgain(Consumer<String> sayShopAgain)
	{
		this.sayShopAgain = sayShopAgain;
	}

	QuestpathPanel(SpriteManager sprites, QuestpathConfig config, ConfigManager configManager,
		OverlaySettingsPanel overlaySettings,
		BiFunction<String, Integer, SkillPlan> planner,
		ShortestPathBridge shortestPathBridge,
		GrandExchangeSearchBridge geSearchBridge, BooleanSupplier geAvailable,
		Function<String, WorldPoint> shopLocator,
		Function<String, WorldPoint> dropLocator,
		Supplier<WorldPoint> playerLocation, Runnable onShopRouteSent,
		Runnable onCancelShopRoute,
		BooleanSupplier shopRouteActive, Runnable onSyncNow,
		ShopItemSkips shopItemSkips,
		Supplier<List<QuestGraph.DiaryTask>> diaryGraphTasks,
		Supplier<PushResponse.Diary> diaryPush,
		LockedGearActions lockedGearActions,
		LocalBuildRules localBuildRules, Runnable onRulesEdited)
	{
		this.localBuildRules = localBuildRules;
		this.onRulesEdited = onRulesEdited;
		this.lockedGearActions = lockedGearActions;
		this.onSyncNow = onSyncNow;
		this.config = config;
		this.configManager = configManager;
		this.overlaySettings = overlaySettings;
		this.quickSettings = new QuickSettingsPanel(QuickSettingsPanel.rows(config),
			(key, value) -> configManager.setConfiguration(QuestpathConfig.GROUP, key, value),
			this::scrollToAllSettings);
		this.planner = planner;
		this.shortestPathBridge = shortestPathBridge;
		this.geSearchBridge = geSearchBridge;
		this.geAvailable = geAvailable;
		this.shopLocator = shopLocator;
		this.dropLocator = dropLocator;
		this.playerLocation = playerLocation;
		this.onShopRouteSent = onShopRouteSent;
		this.onCancelShopRoute = onCancelShopRoute;
		this.shopRouteActive = shopRouteActive;
		this.shopItemSkips = shopItemSkips;
		this.diaryGraphTasks = diaryGraphTasks;
		this.diaryPush = diaryPush;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// Deliberately the no-display constructor. Handed a panel, the group
		// swaps content by removing it, which is the expensive path above;
		// left null it only manages the tab labels and select() skips the
		// swap entirely, leaving it to the card layout below.
		MaterialTabGroup tabGroup = new MaterialTabGroup();
		// One row, equal widths. Five labels at the sidebar's 225px is about
		// six characters each, which is why the tab names are one word.
		tabGroup.setLayout(new GridLayout(0, Tab.values().length, 2, 2));
		tabGroup.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		MaterialTab first = null;
		for (Tab tab : Tab.values())
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
			materialTab.setOnSelectEvent(() ->
			{
				cards.show(display, tab.name());
				return true;
			});
			tabGroup.addTab(materialTab);
			materialTabs.put(tab, materialTab);
			if (first == null)
			{
				first = materialTab;
			}
		}

		questResults.setLayout(new BoxLayout(questResults, BoxLayout.Y_AXIS));
		questResults.setAlignmentX(LEFT_ALIGNMENT);
		itemResults.setLayout(new BoxLayout(itemResults, BoxLayout.Y_AXIS));
		itemResults.setAlignmentX(LEFT_ALIGNMENT);
		styleSearch(questSearch, "Filter the quests below by name", this::refilterQuests);
		styleSearch(itemSearch, "Filter the items below by name", this::refilterItems);
		styleStatusFilter(questStatusFilter, this::refilterQuests);
		styleJournalFilter(journalFilterCombo);

		// The settings tab is built once and never refilled: it reads config,
		// not account state, so nothing an experience drop does can change it.
		// Adding it here rather than in render() also means a half-typed
		// interaction survives the panel rebuilding underneath everything
		// else.
		if (overlaySettings != null)
		{
			body(Tab.VIEW).add(overlaySettings);
		}

		buildSkillTab();
		quickenTooltips();

		// The setup buttons and the quick settings sit above the tabs, not
		// inside one, so they are the same one click from every page. The
		// quick settings start folded: eight rows on every page would push
		// the tabs off the top of a 225px sidebar.
		JLabel quickHeader = bold("Quick settings", BRAND_ORANGE);
		CollapsibleGroup quick = new CollapsibleGroup(quickHeader, quickSettings, false);
		quick.alsoToggleOn(quickHeader);
		JPanel top = column();
		top.add(setupHeader());
		top.add(quick);
		top.add(tabGroup);
		this.tabGroup = tabGroup;
		add(top, BorderLayout.NORTH);
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
		field.setBackground(DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		field.setAlignmentX(LEFT_ALIGNMENT);

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
		styled(combo, "Show only one verdict group").addActionListener(e -> onChange.run());
	}

	/**
	 * The Quests tab's own Quests/Diaries/All filter.
	 *
	 * Initial selection comes from {@link QuestpathConfig#questJournalFilter()}
	 * so a choice made in a previous session is still selected the first time
	 * this tab is built. {@code populatingJournalFilter} guards the write-back
	 * the same way {@link OverlaySettingsPanel}'s own {@code populating} flag
	 * does: without it, this initial {@code setSelectedItem} call would fire
	 * its own listener and write the just-read value straight back as though
	 * the player had chosen it.
	 */
	private boolean populatingJournalFilter = true;

	private void styleJournalFilter(JComboBox<QuestJournalFilter> combo)
	{
		for (QuestJournalFilter filter : QuestJournalFilter.values())
		{
			combo.addItem(filter);
		}
		combo.setSelectedItem(config.questJournalFilter());
		populatingJournalFilter = false;
		styled(combo, "Quests: the quest route only. Diaries: the diary journal and route only."
			+ " All: both, each in its own order. Also drives which of the two in-game route"
			+ " overlays can show, the same as the in-game quest journal's own filter.");
		combo.addActionListener(e ->
		{
			if (populatingJournalFilter)
			{
				return;
			}
			onJournalFilterChanged((QuestJournalFilter) combo.getSelectedItem());
		});
	}

	/**
	 * Writes the new filter through {@link ConfigManager}, exactly like
	 * every control in {@link OverlaySettingsPanel}, then rebuilds the
	 * Quests tab immediately from whatever {@link #lastVerdicts}, {@link
	 * #lastNextQuest} and {@link #lastRouteWork} currently hold -- the same
	 * "redraw now, do not wait for the next push" reasoning {@link
	 * #onBuildRulesShowAllToggled} already documents for its own tab.
	 */
	private void onJournalFilterChanged(QuestJournalFilter filter)
	{
		if (filter == null)
		{
			return;
		}
		setConfig("questJournalFilter", filter);
		if (lastVerdicts == null && lastNextQuest == null && lastRouteWork == null)
		{
			// Nothing has rendered yet -- the first real render already
			// reads the new value off config, so there is nothing to redraw.
			return;
		}
		rebuildQuestsTab(lastVerdicts, lastNextQuest, lastRouteWork);
		lastVerdictSignature = questsSignature(lastVerdicts, lastNextQuest, lastRouteWork, filter);
	}

	/** What the Quests tab is drawn from; a change in any of it is a rebuild. */
	private Object questsSignature(QuestVerdicts verdicts, NextQuestStatus nextQuest, RouteWork routeWork,
		QuestJournalFilter filter)
	{
		return Arrays.asList(
			verdicts == null ? null : verdicts.signature(),
			nextQuest == null ? null : nextQuest.hashCode(),
			routeWork == null ? null : routeWorkSignature(routeWork),
			diaryPush.get(),
			filter,
			setupNote, setupRevision, profile());
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
	private static MaterialTab tabFor(SpriteManager sprites, Tab tab,
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
		NextQuestStatus nextQuest, RouteWork routeWork, SyncSettings settings, AccountReport report,
		LockedGear lockedGear)
	{
		SwingUtilities.invokeLater(() ->
			renderBody(stats, xp, sync, rules, unmatchedQuests, verdicts, items, nextQuest, routeWork,
				settings, report, lockedGear));
	}

	/**
	 * Called by the plugin's locked-gear callbacks when an action fails --
	 * see {@link LockedGearActions}. Hops to the EDT itself, exactly like
	 * {@link #update}, since a failure can be reported from the client
	 * thread ({@code lockCurrentGear}) or straight from an EDT button
	 * handler. Redraws immediately rather than waiting for the next tick's
	 * {@link #update}, the same reasoning {@link #onBuildRulesShowAllToggled}
	 * already uses for its own config-only change.
	 */
	void lockedGearActionFailed(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			lockedGearMessage = message;
			if (lastStats == null)
			{
				// Nothing has rendered yet -- the first real render will
				// already show this once it arrives.
				return;
			}
			rebuildCombatTab();
		});
	}

	/**
	 * Says there is nothing to show rather than showing a combat level built
	 * from levels the client has not loaded. A logged-out client reports every
	 * skill as 1, which renders as a confident, wrong "combat 3".
	 *
	 * The unbuilt tabs keep their text: what they say does not depend on being
	 * logged in, and blanking them would make the plugin look broken rather
	 * than logged out.
	 *
	 * <p><b>This is the state that actually covers "no RuneScape account is
	 * logged in" for the whole Combat tab, Locked gear included.</b> It
	 * replaces the entire tab body with a single "Log in to see your
	 * levels." line and never calls {@code lockedGearSection} at all, since
	 * {@code QuestpathPlugin.refresh()} returns early into this method
	 * whenever {@code client.getGameState() != GameState.LOGGED_IN} --
	 * before {@code LockedGear.resolve} is ever invoked. See {@link
	 * LockedGear.Shape#NOT_LOGGED_IN}'s render site in {@link
	 * #lockedGearSection} for the narrower case that shape actually covers.
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
				body(Tab.VIEW).add(overlaySettings);
			}
			body(Tab.COMBAT).add(muted("Log in to see your levels."));
			body(Tab.SYNC).add(muted("Log in to sync."));
			body(Tab.QUESTS).add(muted("Log in to judge your quests."));
			body(Tab.ITEMS).add(muted("Log in to check your bank."));
			body(Tab.GROUP).add(muted("Log in to plan with your group."));
			lastGroupView = null;
			// Cleared with everything else, so put them back before asking
			// them to redraw. The instances are the same, so whatever was
			// chosen is still chosen.
			addSkillFurniture();
			lastSkillSignature = null;
			renderSkillPlan();
			renderPlannedSectionsFor(Tab.values());
			repaintAll();
		});
	}

	private void renderBody(CombatStats stats, SkillXp xp, SyncStatus sync,
		List<BuildRule> rules, List<String> unmatchedQuests, QuestVerdicts verdicts, QuestItems items,
		NextQuestStatus nextQuest, RouteWork routeWork, SyncSettings settings, AccountReport report,
		LockedGear lockedGear)
	{
		clearBodies(Tab.SYNC);

		lastStats = stats;
		lastXp = xp;
		lastRules = rules;
		lastLockedGear = lockedGear;

		Object combatSignature =
			Arrays.asList(stats, xp.signature(), rules, config.buildRulesShowAll(), lockedGear);
		if (!Objects.equals(combatSignature, lastCombatSignature))
		{
			rebuildCombatTab();
			lastCombatSignature = combatSignature;
		}

		JPanel syncBody = body(Tab.SYNC);
		syncBody.add(heading(PanelSections.SYNC.title()));
		syncBody.add(syncBlock(sync, unmatchedQuests, settings, report));

		// Independent of the combat signature above: the skill plan does not
		// care about xp.signature() (it prices levels, not current xp) or
		// about the "show all rules" toggle (that only changes what is
		// already-known rules render as, not the rules themselves), so
		// coupling it to the combat signature would rebuild it on changes it
		// has no reason to care about.
		Object skillSignature = Arrays.asList(stats, rules);
		if (!Objects.equals(skillSignature, lastSkillSignature))
		{
			lastSkillSignature = skillSignature;
			renderSkillPlan();
		}

		// Rebuilt only when their contents actually changed; see the
		// signature fields. NEXT_QUEST shares this gate with QUEST_VERDICT --
		// both live on the Quests tab and both are driven by the same
		// completed-quest read, so a quest finishing invalidates them
		// together.
		lastVerdicts = verdicts;
		lastNextQuest = nextQuest;
		lastRouteWork = routeWork;
		// routeWork rides the signature too. It is derived from the same
		// completed-quest read as the other two, but not only from it: a
		// levelled skill can clear an outstanding quest without any quest
		// finishing, and the partial runs arrive on a push rather than on
		// anything the client does. Left out, either change would sit
		// unrendered until something else happened to invalidate the tab.
		// diaryPush.get() rides this signature too, by reference: a rejected
		// or fresh push always replaces QuestpathPlugin#diaryPush wholesale
		// (see that field's own doc), never mutates it in place, so a new
		// object identity here reliably means "there is something new to
		// show" -- the same reasoning routeWork's own inclusion already
		// relies on for a push-delivered value with no cheaper signature to
		// compare. config.questJournalFilter() rides it too, the same way
		// config.buildRulesShowAll() rides the Combat tab's own signature
		// above -- so a filter change made through OverlaySettingsPanel or
		// RuneLite's own config panel (rather than journalFilterCombo,
		// which rebuilds immediately on its own) still reaches this tab on
		// the next tick instead of sitting stale until something else
		// invalidates it.
		Object questsSignature = questsSignature(verdicts, nextQuest, routeWork, config.questJournalFilter());
		if (!Objects.equals(questsSignature, lastVerdictSignature))
		{
			rebuildQuestsTab(verdicts, nextQuest, routeWork);
			lastVerdictSignature = questsSignature;
		}

		lastItems = items;
		Integer itemsSignature = items == null ? null : items.signature();
		if (!Objects.equals(itemsSignature, lastItemsSignature))
		{
			clearBodies(Tab.ITEMS);
			JPanel itemsBody = body(Tab.ITEMS);
			itemsBody.add(heading(PanelSections.ITEMS.title()));
			if (items != null && !items.isEmpty())
			{
				itemsBody.add(itemSearch);
				itemsBody.add(gap(6));
			}
			itemsBody.add(itemResults);
			fillItemResults(items);
			renderPlannedSectionsFor(Tab.ITEMS);
			lastItemsSignature = itemsSignature;
			showBodies(Tab.ITEMS);
		}

		renderPlannedSectionsFor(Tab.SYNC);
		showBodies(Tab.SYNC);
		repaintAll();
	}

	/**
	 * Tears down and rebuilds the whole Combat tab -- the combat level, the
	 * free Ranged/Magic ceiling, the cost of the next combat level, and the
	 * Build rules section underneath them -- from whatever {@link #lastStats},
	 * {@link #lastXp} and {@link #lastRules} currently hold.
	 *
	 * Called from {@link #renderBody} when the combined signature says
	 * something changed, and also directly from the "show all rules"
	 * checkbox's listener, which needs the tab to redraw immediately rather
	 * than waiting for the next stat or rule push -- see requirement 4 in the
	 * Task 4a brief. Both callers keep {@code lastCombatSignature} in sync
	 * themselves afterwards; this method only rebuilds the Swing tree.
	 */
	private void rebuildCombatTab()
	{
		CombatStats stats = lastStats;
		SkillXp xp = lastXp;
		List<BuildRule> rules = lastRules;
		int ceiling = CombatMath.freeRangedCeiling(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));

		clearBodies(Tab.COMBAT);
		JPanel combat = body(Tab.COMBAT);
		// The panel opens on this tab, so the button people reach for
		// most lives here as well as on Sync. Two call sites, one
		// behaviour -- see syncNowButton.
		combat.add(syncNowButton());
		combat.add(gap(10));
		section(combat, PanelSections.COMBAT, combatBlock(stats));
		section(combat, PanelSections.LOCKED_GEAR, lockedGearSection(lastLockedGear));
		section(combat, PanelSections.CEILING, ceilingBlock(stats, xp, ceiling));
		section(combat, PanelSections.COST, costBlock(stats, xp));
		combat.add(buildRulesSection(stats, rules));
		renderPlannedSectionsFor(Tab.COMBAT);
		showBodies(Tab.COMBAT);
	}

	/**
	 * Tears down and rebuilds the whole Quests tab: the filter combo itself,
	 * then whichever of the quest route and the diary route/journal {@link
	 * QuestpathConfig#questJournalFilter()} currently permits.
	 *
	 * <p>Called from {@link #renderBody} when the combined signature (which
	 * includes the filter) says something changed, and also directly from
	 * {@link #onJournalFilterChanged}, which needs the tab to redraw
	 * immediately rather than waiting for the next push -- the same
	 * reasoning {@link #onBuildRulesShowAllToggled} already documents for
	 * the Combat tab's own config-only change. Both callers keep {@code
	 * lastVerdictSignature} in sync themselves afterwards; this method only
	 * rebuilds the Swing tree.
	 */
	private void rebuildQuestsTab(QuestVerdicts verdicts, NextQuestStatus nextQuest, RouteWork routeWork)
	{
		QuestJournalFilter filter = config.questJournalFilter();

		clearBodies(Tab.QUESTS);
		JPanel quests = body(Tab.QUESTS);

		String setup = setupNote;
		if (setup != null)
		{
			quests.add(new SetupPanel(setup, setupCurrent,
				localBuildRules == null ? Collections.<BuildRule>emptyList() : localBuildRules.all(),
				onSetupDone, onSetupCancel));
			showBodies(Tab.QUESTS);
			// Setup is on the Quests tab; wherever the player was, take them to it.
			showQuestsTab();
			return;
		}

		quests.add(journalFilterCombo);
		quests.add(gap(10));

		if (filter.showsQuests())
		{
			// Search first: it filters the browser list further down, but a
			// player reaching for it should not have to scroll past "next on
			// your route" to find it every time.
			if (verdicts != null)
			{
				quests.add(questSearch);
				quests.add(gap(4));
				quests.add(questStatusFilter);
				quests.add(gap(10));
			}
			section(quests, PanelSections.NEXT_QUEST, nextQuestBlock(nextQuest));
			if (routeWork != null && !routeWork.outstanding().isEmpty())
			{
				section(quests, PanelSections.ROUTE_OUTSTANDING, routeOutstandingBlock(routeWork));
			}
			if (routeWork != null && !routeWork.partial().isEmpty())
			{
				section(quests, PanelSections.ROUTE_PARTIAL, routePartialBlock(routeWork));
			}
			quests.add(heading(PanelSections.QUEST_VERDICT.title()));
			quests.add(questResults);
			fillQuestResults(verdicts);
		}

		if (filter.showsDiaries())
		{
			section(quests, PanelSections.DIARY_ROUTE, diaryRouteBlock());
			section(quests, PanelSections.DIARY_JOURNAL, diaryJournalBlock());
		}

		renderPlannedSectionsFor(Tab.QUESTS);
		showBodies(Tab.QUESTS);
	}

	// ------------------------------------------------------------------
	// Group: party members' shared quest states, planned together. See
	// GroupPlan and ItemSplit for what each answer means.
	// ------------------------------------------------------------------

	/** The last view rendered, so the quest picker can redraw the split without waiting for a tick. */
	private GroupView lastGroupView;

	/** The quest the item split is for, by name, kept across rebuilds. */
	private String groupSplitQuest;

	/** How many quests each Group list shows before it says how many more there are. */
	private static final int GROUP_LIST_LIMIT = 12;

	/**
	 * Renders the Group tab. Safe from the client thread: hops to the EDT,
	 * like {@link #update}.
	 */
	void updateGroup(GroupView view)
	{
		SwingUtilities.invokeLater(() ->
		{
			lastGroupView = view;
			rebuildGroupTab();
		});
	}

	private void rebuildGroupTab()
	{
		GroupView view = lastGroupView;
		clearBodies(Tab.GROUP);
		JPanel body = body(Tab.GROUP);
		body.add(heading(PanelSections.GROUP_MEMBERS.title()));

		if (view == null)
		{
			body.add(muted("Log in to plan with your group."));
		}
		else
		{
			body.add(muted(view.sharing()
				? "Sharing your quests, levels, build rules, route, account type and quest-item counts with"
					+ " your party."
				: "Not sharing. Your party sees nothing from you; turn on \"Share with my party\" in the"
					+ " View tab's Sync section to let them plan with you."));
			body.add(gap(6));
			switch (view.state())
			{
				case NO_GRAPH:
					body.add(muted("No quest graph is loaded, so nothing can be planned. The Quests tab says why."));
					break;
				case NOT_IN_PARTY:
					body.add(muted("Not in a party. Join one with RuneLite's Party plugin; party members"
						+ " running Quest Route with sharing on show up here."));
					break;
				case LOADING:
					body.add(muted("Waiting for the game to load your quest states."));
					break;
				case READY:
				default:
					groupBody(body, view);
					break;
			}
		}
		showBodies(Tab.GROUP);
		body.revalidate();
		body.repaint();
	}

	private void groupBody(JPanel body, GroupView view)
	{
		GroupPlan plan = view.plan();
		int total = 0;
		JPanel members = grid();
		for (GroupMember member : plan.members())
		{
			if (!member.compatible())
			{
				addRow(members, member.name(), "other version", PROGRESS_ERROR_COLOR,
					"Runs a Quest Route that sends a different message shape (v" + member.version()
						+ "). Update both clients to the same version to plan together.");
				continue;
			}
			StringBuilder caps = new StringBuilder();
			for (BuildRule rule : member.rules())
			{
				if (!rule.blocking())
				{
					continue;
				}
				caps.append(caps.length() == 0 ? "" : ", ").append(rule.skillName()).append(' ')
					.append(rule.describe());
			}
			addRow(members, member.name(), plan.doneCount(member) + " quests done",
				Color.WHITE, caps.length() == 0 ? "No blocking build rules." : "Build: " + caps);
			total++;
		}
		for (String name : view.notSharing())
		{
			addRow(members, name, "not sharing", LIGHT_GRAY_COLOR,
				"Sends nothing: Quest Route is not running for them, or their sharing is off.");
		}
		body.add(members);

		if (total < 2)
		{
			body.add(gap(6));
			body.add(muted("Nobody else in the party is sharing yet, so the lists below are for you alone."));
		}

		body.add(gap(14));
		body.add(heading(PanelSections.GROUP_TOGETHER.title()));
		List<QuestGraph.Quest> together = plan.together();
		if (together.isEmpty())
		{
			body.add(muted("No quest that nobody has done is open to every member right now."));
		}
		else
		{
			questNameList(body, together);
		}

		body.add(gap(14));
		body.add(heading(PanelSections.GROUP_FLAGGED.title()));
		List<GroupPlan.Flag> flagged = plan.flagged();
		if (flagged.isEmpty())
		{
			body.add(muted("Nothing the group could start now breaks anyone's build rules."));
		}
		else
		{
			body.add(muted("Open to everyone, but would break a member's build. Plan around these."));
			int shown = 0;
			for (GroupPlan.Flag flag : flagged)
			{
				if (shown++ >= GROUP_LIST_LIMIT)
				{
					body.add(muted("... and " + (flagged.size() - GROUP_LIST_LIMIT) + " more."));
					break;
				}
				body.add(row(flag.quest().name(), PROGRESS_ERROR_COLOR));
				for (GroupPlan.MemberBreach breach : flag.breaches())
				{
					body.add(row("  " + breach.describe(), LIGHT_GRAY_COLOR));
				}
			}
		}

		body.add(gap(14));
		body.add(heading(PanelSections.GROUP_ITEMS.title()));
		itemSplitBlock(body, plan, together);

		body.add(gap(14));
		body.add(heading(PanelSections.GROUP_SHOPPING.title()));
		groupShoppingBlock(body, view);

		body.add(gap(14));
		body.add(heading(PanelSections.GROUP_DONE_BY_SOME.title()));
		List<GroupPlan.Partial> partial = plan.doneBySome();
		if (partial.isEmpty())
		{
			body.add(muted(total < 2 ? "Needs at least two sharing members."
				: "No quest is finished by some of you and not others."));
		}
		else
		{
			int shown = 0;
			for (GroupPlan.Partial entry : partial)
			{
				if (shown++ >= GROUP_LIST_LIMIT)
				{
					body.add(muted("... and " + (partial.size() - GROUP_LIST_LIMIT) + " more."));
					break;
				}
				JLabel quest = row(entry.quest().name() + " — needs " + String.join(", ", entry.stillNeed()));
				quest.setToolTipText("Done by " + String.join(", ", entry.doneBy())
					+ "; still needed by " + String.join(", ", entry.stillNeed()));
				body.add(quest);
			}
		}
	}

	private void questNameList(JPanel body, List<QuestGraph.Quest> quests)
	{
		int shown = 0;
		for (QuestGraph.Quest quest : quests)
		{
			if (shown++ >= GROUP_LIST_LIMIT)
			{
				body.add(muted("... and " + (quests.size() - GROUP_LIST_LIMIT) + " more."));
				break;
			}
			body.add(row(quest.name()));
		}
	}

	/** The quest picker and its split. Only quests on the "together" list can be picked. */
	private void itemSplitBlock(JPanel body, GroupPlan plan, List<QuestGraph.Quest> together)
	{
		if (together.isEmpty())
		{
			body.add(muted("Pick from \"Do together next\" -- there is nothing there yet."));
			return;
		}
		QuestGraph.Quest selected = together.get(0);
		List<String> names = new ArrayList<>();
		for (QuestGraph.Quest quest : together)
		{
			names.add(quest.name());
			if (quest.name().equals(groupSplitQuest))
			{
				selected = quest;
			}
		}
		body.add(groupPicker(names, selected.name(), "Which group quest to split the item list for", chosen ->
			groupSplitQuest = chosen));
		body.add(gap(6));

		ItemSplit split = plan.split(selected);
		if (split.lines().isEmpty())
		{
			body.add(muted(split.unsplit() == 0
				? "The wiki lists no items for this quest."
				: "Nothing on this quest's list can be split; see the Items tab."));
		}
		for (ItemSplit.Line line : split.lines())
		{
			String each = (line.quantityStated() ? "" : "at least ") + line.perMember() + " each";
			JLabel title = row(line.name() + " — " + each + (line.obtainable() ? " (found in quest)" : ""));
			body.add(title);
			transfers(body, line.transfers());
			for (Map.Entry<String, Long> get : line.toGet().entrySet())
			{
				body.add(row("  " + get.getKey() + " gets " + get.getValue(), PROGRESS_INPROGRESS_COLOR));
			}
			if (!line.unknown().isEmpty())
			{
				body.add(row("  unknown: " + String.join(", ", line.unknown()), LIGHT_GRAY_COLOR));
			}
			if (line.transfers().isEmpty() && line.toGet().isEmpty() && line.unknown().isEmpty())
			{
				body.add(row("  everyone has theirs", LIGHT_GRAY_COLOR));
			}
		}
		if (split.unsplit() > 0)
		{
			body.add(gap(4));
			body.add(muted(split.unsplit() + " more line" + (split.unsplit() == 1 ? "" : "s")
				+ " (optional, a category like \"any pickaxe\", or unmatched) not split -- see the Items tab."));
		}
		if (!split.membersWithoutItems().isEmpty())
		{
			body.add(muted("Not sharing item counts: " + String.join(", ", split.membersWithoutItems())
				+ ". They have opened no container yet this session."));
		}
		if (!split.membersWithoutBank().isEmpty())
		{
			body.add(muted("Bank not opened this session: " + String.join(", ", split.membersWithoutBank())
				+ " -- their counts are only what they carry."));
		}
		body.add(muted("Group storage is not counted here; Group shopping below counts it."));
	}

	/**
	 * A Group tab drop-down. Picking a different entry stores it through
	 * {@code onPick} and rebuilds the tab; restoring the selection on a
	 * rebuild does not, because the listener is attached after it.
	 */
	private JComboBox<String> groupPicker(List<String> options, String selected, String tooltip,
		Consumer<String> onPick)
	{
		JComboBox<String> picker = styled(new JComboBox<>(options.toArray(new String[0])), tooltip);
		picker.setSelectedItem(selected);
		picker.addActionListener(e ->
		{
			Object chosen = picker.getSelectedItem();
			if (chosen != null && !chosen.equals(selected))
			{
				onPick.accept((String) chosen);
				rebuildGroupTab();
			}
		});
		return picker;
	}

	// ------------------------------------------------------------------
	// Group shopping: what the whole party still has to get, across every
	// quest each member counts. See GroupShoppingList for the math and
	// GroupShoppingNeeds for which quests count.
	// ------------------------------------------------------------------

	private static final String ALL_QUESTS = "All quests";
	private static final String EVERYONE = "Everyone";
	private static final int GROUP_SHOP_LIMIT = 25;

	/** The Group shopping filters, kept across rebuilds. Null means "all". */
	private String groupShopQuest;
	private String groupShopMember;
	private boolean groupShopStillNeeded = true;

	private void groupShoppingBlock(JPanel body, GroupView view)
	{
		GroupShoppingList list = view.shopping();
		if (list == null || list.lines().isEmpty())
		{
			body.add(muted("No quest anyone counts asks for an item this list can shop for."));
			return;
		}
		body.add(muted("Counts each member's route, or every quest their build allows when they have none."
			+ " Build-blocked quests never count."));
		groupShopFilters(body, list);

		List<GroupShoppingList.Line> lines = list.filtered(groupShopQuest, groupShopMember, groupShopStillNeeded);
		if (lines.isEmpty())
		{
			body.add(muted(groupShopStillNeeded ? "Nothing left to get for this filter." : "Nothing for this filter."));
		}
		int shown = 0;
		for (GroupShoppingList.Line line : lines)
		{
			if (shown++ >= GROUP_SHOP_LIMIT)
			{
				body.add(muted("... and " + (lines.size() - GROUP_SHOP_LIMIT) + " more. Filter by quest to see them."));
				break;
			}
			groupShopLine(body, view, line);
		}
		body.add(gap(6));
		body.add(groupShopRouteButton(view, lines));
		groupShopFootnotes(body, list);
	}

	private void groupShopFilters(JPanel body, GroupShoppingList list)
	{
		List<String> quests = new ArrayList<>();
		quests.add(ALL_QUESTS);
		quests.addAll(list.quests());
		if (groupShopQuest != null && !quests.contains(groupShopQuest))
		{
			groupShopQuest = null;
		}
		body.add(groupPicker(quests, groupShopQuest == null ? ALL_QUESTS : groupShopQuest,
			"Show what the group needs for one quest, with every holding spent on that quest alone",
			chosen -> groupShopQuest = ALL_QUESTS.equals(chosen) ? null : chosen));

		List<String> members = new ArrayList<>();
		members.add(EVERYONE);
		members.addAll(list.memberNames());
		if (groupShopMember != null && !members.contains(groupShopMember))
		{
			groupShopMember = null;
		}
		body.add(groupPicker(members, groupShopMember == null ? EVERYONE : groupShopMember,
			"Only items this member needs; totals stay the group's",
			chosen -> groupShopMember = EVERYONE.equals(chosen) ? null : chosen));

		body.add(check("Still needed only", groupShopStillNeeded, Color.WHITE, e ->
		{
			groupShopStillNeeded = ((JCheckBox) e.getSource()).isSelected();
			rebuildGroupTab();
		}));
		body.add(gap(4));
	}

	/** One item: the headline count, then who, who holds what, and where to get it. */
	private void groupShopLine(JPanel body, GroupView view, GroupShoppingList.Line line)
	{
		body.add(groupShopTitle(line));
		for (GroupShoppingList.Share share : line.shares())
		{
			groupShopDetail(body, "  " + share.member() + ": " + describeShare(share)
				+ (share.stale() ? " (stale)" : ""));
		}
		transfers(body, line.transfers());
		String held = describeHolders(line);
		if (!held.isEmpty())
		{
			groupShopDetail(body, "  Held: " + held);
		}
		if (line.stillNeeded() > 0)
		{
			groupShopDetail(body, "  Get: " + describeSources(view.sources(line.name())));
		}
	}

	/** "Bucket — need 28 more (30 total)", coloured by whether anything is left to get. */
	private static JLabel groupShopTitle(GroupShoppingList.Line line)
	{
		String floor = line.quantityStated() ? "" : "at least ";
		boolean shortOf = line.stillNeeded() > 0;
		JLabel title = row(line.name() + " — " + (shortOf ? "need " + floor + line.stillNeeded() + " more" : "covered")
			+ " (" + floor + line.totalNeeded() + " total)" + (line.obtainable() ? " (found in quest)" : ""));
		title.setForeground(shortOf ? PROGRESS_INPROGRESS_COLOR : PROGRESS_COMPLETE_COLOR);
		title.setToolTipText("For: " + String.join(", ", line.quests())
			+ (line.tradeable() ? "" : ". Untradeable: only each member's own holding counts."));
		return title;
	}

	private void groupShopDetail(JPanel body, String text)
	{
		body.add(row(text, LIGHT_GRAY_COLOR));
	}

	/** "Alice gives Bob 3", one green row each. */
	private static void transfers(JPanel body, List<Transfer> transfers)
	{
		for (Transfer transfer : transfers)
		{
			body.add(row("  " + transfer.from() + " gives " + transfer.to() + " " + transfer.count(),
				PROGRESS_COMPLETE_COLOR));
		}
	}

	private static String describeShare(GroupShoppingList.Share share)
	{
		if (share.unknown())
		{
			return "needs " + share.need() + ", items not shared";
		}
		if (share.shortBy() == 0)
		{
			return "covered (needs " + share.need() + ")";
		}
		return share.shortBy() + " more (needs " + share.need() + ")";
	}

	private static String describeHolders(GroupShoppingList.Line line)
	{
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Long> holder : line.holders().entrySet())
		{
			parts.add(holder.getKey() + " " + holder.getValue());
		}
		if (line.storageHeld() > 0)
		{
			parts.add("group storage " + line.storageHeld()
				+ (line.storageUsed() > 0 ? " (" + line.storageUsed() + " used)" : ""));
		}
		return String.join(", ", parts);
	}

	private static String describeSources(ItemSources.Options options)
	{
		if (options == null)
		{
			return "not looked up";
		}
		List<String> parts = new ArrayList<>();
		for (ItemSources.Source source : options.sources())
		{
			parts.add(source.describe());
		}
		String hidden = options.unreachableShops() == 0 ? ""
			: " (" + options.unreachableShops() + " shop" + (options.unreachableShops() == 1 ? "" : "s")
				+ " you can't use yet)";
		return (parts.isEmpty() ? "nowhere you can use" : String.join(", ", parts)) + hidden;
	}

	/** Sends every usable shop on the shown, still-short lines to Shortest Path. */
	private JButton groupShopRouteButton(GroupView view, List<GroupShoppingList.Line> lines)
	{
		Set<WorldPoint> destinations = new LinkedHashSet<>();
		for (GroupShoppingList.Line line : lines)
		{
			ItemSources.Options options = view.sources(line.name());
			if (line.stillNeeded() == 0 || options == null)
			{
				continue;
			}
			for (String shop : options.shops())
			{
				WorldPoint at = shopLocator.apply(shop);
				if (at != null)
				{
					destinations.add(at);
				}
			}
		}
		if (destinations.isEmpty() || shortestPathBridge == null)
		{
			return disabled("No shop you can use sells what the group needs");
		}
		int count = destinations.size();
		return routeButton(" (group shops)", event -> shortestPathBridge.route(destinations, outcome ->
			SwingUtilities.invokeLater(() -> reportRouteOutcome(outcome, count))));
	}

	private static JButton disabled(String text)
	{
		JButton button = button(text, null);
		button.setEnabled(false);
		return button;
	}

	/** A Shortest Path button: its label is the bridge's own availability word, enabled only when AVAILABLE. */
	private JButton routeButton(String suffix, ActionListener onClick)
	{
		ShortestPathBridge.Availability availability = shortestPathBridge.availability();
		JButton button = button(availability.menuLabel() + suffix, onClick);
		button.setEnabled(availability == ShortestPathBridge.Availability.AVAILABLE);
		return button;
	}

	private void groupShopFootnotes(JPanel body, GroupShoppingList list)
	{
		if (!list.membersWithoutItems().isEmpty())
		{
			body.add(muted("Not sharing item counts: " + String.join(", ", list.membersWithoutItems())
				+ ". Their need is shown but not counted as still to get."));
		}
		if (!list.membersWithoutBank().isEmpty())
		{
			body.add(muted("Bank not opened this session: " + String.join(", ", list.membersWithoutBank())
				+ " -- their counts are only what they carry."));
		}
		GroupStorage storage = list.storage();
		body.add(muted(storage == null
			? "Group storage: not seen this session. A group ironman opening it shares what is there."
			: "Group storage as " + storage.seenBy() + " last saw it, " + minutesAgo(storage.seenAtMillis()) + "."));
	}

	private static String minutesAgo(long epochMillis)
	{
		long minutes = Math.max(0, (System.currentTimeMillis() - epochMillis) / 60_000L);
		return minutes == 0 ? "just now" : minutes + " min ago";
	}

	// ------------------------------------------------------------------
	// Locked gear: a saved loadout's melee/ranged/magic max hit at live
	// levels, while the account wears something else entirely. See
	// LockedGear's own class doc for why the gear is frozen and only the
	// levels move.
	// ------------------------------------------------------------------

	/**
	 * The whole Locked gear section, for every {@link LockedGear.Shape}.
	 * Rebuilt wholesale by {@link #rebuildCombatTab} along with the rest of
	 * the Combat tab -- there is no persisted widget state here beyond
	 * {@link #lockedGearItemsExpanded}, which this method reads back rather
	 * than owning a JPanel across rebuilds.
	 */
	private JPanel lockedGearSection(LockedGear gear)
	{
		JPanel panel = column();

		if (lockedGearMessage != null)
		{
			panel.add(tinted(wrap(lockedGearMessage), PROGRESS_ERROR_COLOR));
			panel.add(gap(6));
		}

		if (gear == null || gear.shape() == LockedGear.Shape.NOT_LOGGED_IN)
		{
			// This is NOT the "no account logged in" case in general --
			// that one never reaches here at all, because QuestpathPlugin's
			// refresh() returns early into showLoggedOut() (which replaces
			// the whole Combat tab with "Log in to see your levels.")
			// whenever GameState != LOGGED_IN, before LockedGear.resolve is
			// ever called. This branch instead covers the narrower window
			// where the client already reports LOGGED_IN but
			// ConfigManager.getRSProfileKey() has not yet been derived from
			// the account hash -- see LoadoutStore#all's own null-key check.
			// Kept even though that window may be brief or never observed
			// in practice: LoadoutStore's contract genuinely returns this
			// status, and a snapshot that silently mapped it to some other
			// message would be lying about which of the two states is true.
			panel.add(muted("Log in to see your saved loadouts."));
			return panel;
		}

		if (gear.shape() == LockedGear.Shape.CORRUPT)
		{
			panel.add(tinted(wrap("Could not read your saved loadouts: " + gear.corruptDetail()
				+ ". Nothing was overwritten."), PROGRESS_ERROR_COLOR));
			return panel;
		}

		if (gear.shape() == LockedGear.Shape.NO_LOADOUTS)
		{
			panel.add(muted("No loadout saved. Put on the gear you plan to fight in, then press Lock current gear."));
			panel.add(gap(6));
			panel.add(lockCurrentGearButton(gear));
			return panel;
		}

		// READY. Only the style blocks this loadout's weapon can actually
		// produce are shown -- see Loadout#showsMeleeBlock and its two
		// siblings. A loadout with no captured weapon styles (an old
		// loadout, or the weapon's stats were unknown at lock time) shows
		// all three, exactly as before this feature existed.
		Loadout selected = gear.selectedLoadout();
		panel.add(pickerRow(gear));
		boolean anyStyleShown = false;
		if (selected.showsMeleeBlock())
		{
			panel.add(gap(8));
			panel.add(styleRow("Melee", gear.melee(), meleeControls(gear)));
			anyStyleShown = true;
		}
		if (selected.showsRangedBlock())
		{
			panel.add(gap(8));
			panel.add(styleRow("Ranged", gear.ranged(), rangedControls(gear)));
			anyStyleShown = true;
		}
		if (selected.showsMagicBlock())
		{
			panel.add(gap(8));
			panel.add(styleRow("Magic", gear.magic(), magicControls(gear)));
			anyStyleShown = true;
		}
		if (!anyStyleShown)
		{
			// Unreachable in practice -- classifyWeaponStyles always falls
			// back to melee alone rather than an empty list -- but a
			// hand-edited config value could still produce an empty,
			// non-null weaponStyles list, and silently showing nothing
			// would look like the section rendered wrong rather than like
			// a state worth explaining.
			panel.add(gap(8));
			panel.add(muted("This loadout's captured weapon styles don't match any known style."));
		}
		panel.add(gap(6));
		panel.add(muted("At your real levels, in " + selected.name()));
		panel.add(gap(6));
		panel.add(itemListSection(selected));

		return panel;
	}

	/** The loadout picker combo, plus Lock current gear / Rename / Delete underneath it. */
	private JPanel pickerRow(LockedGear gear)
	{
		JPanel panel = column();

		JComboBox<String> picker = styled(new JComboBox<>(gear.loadoutNames().toArray(new String[0])),
			"Which saved loadout to show the max hit for");
		picker.setSelectedItem(gear.selectedLoadout().name());
		// Attached after setSelectedItem above, so restoring the current
		// selection on rebuild never fires a spurious select of itself.
		picker.addActionListener(e ->
		{
			String chosen = (String) picker.getSelectedItem();
			if (chosen == null || chosen.equals(gear.selectedLoadout().name()))
			{
				return;
			}
			lockedGearMessage = null;
			lockedGearActions.selectLoadout.accept(chosen);
		});
		panel.add(picker);
		panel.add(gap(4));

		// Two rows, not one: "Lock current gear" / "Rename" / "Delete" side
		// by side overflowed the standard PluginPanel width -- Delete showed
		// only a sliver. "Lock current gear" gets its own full-width row
		// (it's both the longest label and the button reached for most);
		// Rename and Delete split the second row evenly.
		JButton lockButton = lockCurrentGearButton(gear);
		lockButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, lockButton.getPreferredSize().height));
		panel.add(lockButton);
		panel.add(gap(4));

		JPanel secondRow = new JPanel(new GridLayout(1, 2, 4, 0));
		secondRow.setAlignmentX(LEFT_ALIGNMENT);
		secondRow.setOpaque(false);
		secondRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, lockButton.getPreferredSize().height));
		secondRow.add(renameButton(gear));
		secondRow.add(deleteButton(gear));
		panel.add(secondRow);

		return panel;
	}

	/**
	 * "Lock current gear". Prompts for a name on the EDT and, if it already
	 * names a saved loadout, confirms the overwrite there too -- both before
	 * ever handing off to the plugin, which is the only thing that hops to
	 * the client thread. Works from every shape: {@code gear} may be a
	 * {@link LockedGear.Shape#NO_LOADOUTS} snapshot with nothing to name yet.
	 */
	private JButton lockCurrentGearButton(LockedGear gear)
	{
		JButton button = button("Lock current gear", e ->
		{
			boolean ready = gear.shape() == LockedGear.Shape.READY;
			String defaultName = ready ? gear.selectedLoadout().name() : "";
			String name = JOptionPane.showInputDialog(this, "Name this loadout:", defaultName);
			if (name == null)
			{
				return;
			}
			String trimmed = name.trim();
			if (trimmed.isEmpty())
			{
				warn("Loadout name must not be blank.");
				return;
			}
			if (ready && gear.loadoutNames().contains(trimmed)
				&& !confirm("A loadout named \"" + trimmed + "\" already exists. Overwrite it?"))
			{
				return;
			}
			lockedGearMessage = null;
			lockedGearActions.lockCurrentGear.accept(trimmed);
		});
		button.setToolTipText("<html><body style='width:200px'>Snapshots the gear you are wearing"
			+ " right now and saves it under a name. Only your live levels move after this --"
			+ " the gear itself stays exactly as locked until you press this again.</body></html>");
		return button;
	}

	private JButton renameButton(LockedGear gear)
	{
		return button("Rename", e ->
		{
			String current = gear.selectedLoadout().name();
			String name = JOptionPane.showInputDialog(this, "Rename \"" + current + "\" to:", current);
			if (name == null)
			{
				return;
			}
			String trimmed = name.trim();
			if (trimmed.isEmpty() || trimmed.equals(current))
			{
				return;
			}
			lockedGearMessage = null;
			lockedGearActions.renameLoadout.accept(current, trimmed);
		});
	}

	private JButton deleteButton(LockedGear gear)
	{
		return button("Delete", e ->
		{
			String current = gear.selectedLoadout().name();
			if (!confirm("Delete loadout \"" + current + "\"? This cannot be undone."))
			{
				return;
			}
			lockedGearMessage = null;
			lockedGearActions.deleteLoadout.accept(current);
		});
	}

	/**
	 * One style's row: label, the big number or state text, its two compact
	 * combos underneath, and (when relevant) the muted "Not applied" line.
	 */
	private JPanel styleRow(String label, LockedGear.StyleLine line, JComponent controls)
	{
		JPanel panel = column();
		panel.add(row(label, LIGHT_GRAY_COLOR));

		switch (line.kind())
		{
			case NEEDS_SELECTION:
				panel.add(tinted("Choose a style and prayer", LIGHT_GRAY_COLOR));
				break;
			case UNKNOWN_STATS:
				panel.add(tinted(wrap("Stats unknown for: " + String.join(", ", line.unknownItemNames())),
					PROGRESS_ERROR_COLOR));
				break;
			case RESULT:
			default:
			{
				MaxHitResult result = line.result();
				if (result.isKnown())
				{
					JLabel value = tinted(String.valueOf(result.value()), Color.WHITE);
					value.setFont(BOLD.deriveFont(20f));
					panel.add(value);
					panel.add(nextStepLabel(line));
					for (JLabel comparison : withoutBuffLabels(line))
					{
						panel.add(comparison);
					}
				}
				else
				{
					panel.add(tinted(wrap(result.reason()), PROGRESS_ERROR_COLOR));
				}
				break;
			}
		}

		panel.add(gap(2));
		panel.add(controls);

		if (line.kind() == LockedGear.StyleLine.Kind.RESULT && line.result().isKnown()
			&& !line.result().notApplied().isEmpty())
		{
			StringBuilder names = new StringBuilder();
			for (GearEffect effect : line.result().notApplied())
			{
				if (names.length() > 0)
				{
					names.append(", ");
				}
				names.append(effect.label());
			}
			panel.add(gap(2));
			panel.add(muted("Not applied: " + names));
		}

		return panel;
	}

	/**
	 * The line under a known max hit saying which level raises it next.
	 *
	 * The question a frozen loadout is being asked is "what is the next
	 * level worth training to", and a max hit floors, so most levels move
	 * nothing. Where no level does -- a standard spell, or 99 -- that is
	 * said outright rather than left to look like a missing line.
	 */
	private static JLabel nextStepLabel(LockedGear.StyleLine line)
	{
		if (!line.hasNextStep())
		{
			return muted("No higher level raises it");
		}
		MaxHitStep next = line.nextStep();
		return muted(next.maxHit() + " at " + next.skill().getName() + " " + next.level());
	}

	/**
	 * "What if I dropped this buff" -- zero, one, or two muted lines, in
	 * that order: without the active prayer, without the active potion.
	 *
	 * <p>Only shown when the buff is actually on: {@link
	 * LockedGear.StyleLine#withoutPrayerValue()} and {@link
	 * LockedGear.StyleLine#withoutPotionValue()} are both null exactly when
	 * there is nothing to compare against, so an empty list here is the
	 * honest "no prayer, no potion" case rather than a missing feature.
	 */
	private static List<JLabel> withoutBuffLabels(LockedGear.StyleLine line)
	{
		List<JLabel> labels = new ArrayList<>(2);
		if (line.withoutPrayerValue() != null)
		{
			labels.add(muted("Without prayer: " + line.withoutPrayerValue()));
		}
		if (line.withoutPotionValue() != null)
		{
			labels.add(muted("Without potion: " + line.withoutPotionValue()));
		}
		return labels;
	}

	private JPanel meleeControls(LockedGear gear)
	{
		Loadout loadout = gear.selectedLoadout();
		JPanel column = column();
		column.add(styleControls(loadout, AttackStyle.values(), parseOrNull(AttackStyle.class, loadout.meleeStyle()),
			AttackStyle::label, StrengthPrayer.values(), parseOrNull(StrengthPrayer.class, loadout.strengthPrayer()),
			StrengthPrayer::label, lockedGearActions.meleeSelectionChanged));

		// Potion gets its own row and its own listener rather than a third
		// slot on the row above: comboRow is a fixed two-up layout every
		// other style block already relies on, and unlike style/prayer a
		// potion has a real "None" off state -- it can never be left
		// unselected the way an unmade style/prayer choice can, so it needs
		// no shared null guard with them.
		StrengthPotion potion = parseOrNull(StrengthPotion.class, loadout.meleePotion());
		JComboBox<StrengthPotion> potionCombo = enumCombo(StrengthPotion.values(),
			potion == null ? StrengthPotion.NONE : potion, StrengthPotion::label);
		potionCombo.addActionListener(e ->
		{
			StrengthPotion chosenPotion = (StrengthPotion) potionCombo.getSelectedItem();
			if (chosenPotion != null)
			{
				lockedGearMessage = null;
				lockedGearActions.meleePotionChanged.accept(loadout.name(), chosenPotion.name());
			}
		});
		column.add(gap(4));
		column.add(potionCombo);
		return column;
	}

	private JPanel rangedControls(LockedGear gear)
	{
		Loadout loadout = gear.selectedLoadout();
		return styleControls(loadout, RangedStyle.values(), parseOrNull(RangedStyle.class, loadout.rangedStyle()),
			RangedStyle::label, RangedPrayer.values(), parseOrNull(RangedPrayer.class, loadout.rangedPrayer()),
			RangedPrayer::label, lockedGearActions.rangedSelectionChanged);
	}

	private JPanel magicControls(LockedGear gear)
	{
		Loadout loadout = gear.selectedLoadout();
		return styleControls(loadout, Spell.values(), parseOrNull(Spell.class, loadout.spell()), Spell::label,
			MagicPrayer.values(), parseOrNull(MagicPrayer.class, loadout.magicPrayer()), MagicPrayer::label,
			lockedGearActions.magicSelectionChanged);
	}

	/**
	 * A style (or spell) combo beside a prayer combo. Either changing reports
	 * both to the plugin by enum name, once both have a value: an unmade
	 * choice on one side is not a selection to save.
	 */
	private <S extends Enum<S>, P extends Enum<P>> JPanel styleControls(Loadout loadout, S[] styles, S style,
		Function<S, String> styleLabel, P[] prayers, P prayer, Function<P, String> prayerLabel,
		LockedGearActions.TriConsumer<String, String, String> onChange)
	{
		JComboBox<S> styleCombo = enumCombo(styles, style, styleLabel);
		JComboBox<P> prayerCombo = enumCombo(prayers, prayer, prayerLabel);
		ActionListener listener = e ->
		{
			Object chosenStyle = styleCombo.getSelectedItem();
			Object chosenPrayer = prayerCombo.getSelectedItem();
			if (chosenStyle != null && chosenPrayer != null)
			{
				lockedGearMessage = null;
				onChange.accept(loadout.name(), ((Enum<?>) chosenStyle).name(), ((Enum<?>) chosenPrayer).name());
			}
		};
		styleCombo.addActionListener(listener);
		prayerCombo.addActionListener(listener);
		return comboRow(styleCombo, prayerCombo);
	}

	private static JPanel comboRow(Component first, Component second)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(first);
		row.add(Box.createHorizontalStrut(4));
		row.add(second);
		return row;
	}

	/**
	 * A compact combo over one of the six {@code MaxHit} selection enums,
	 * showing each constant's own {@code label()} rather than its enum name.
	 * {@code selected} may be null (nothing chosen, or an unrecognised
	 * stored name) -- the combo is left on whatever it defaults to, which is
	 * harmless since no listener is attached until after this returns.
	 */
	private static <E extends Enum<E>> JComboBox<E> enumCombo(E[] values, E selected,
		Function<E, String> labeler)
	{
		JComboBox<E> combo = new JComboBox<>(values);
		combo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				@SuppressWarnings("unchecked")
				E typed = (E) value;
				String text = typed == null ? "" : labeler.apply(typed);
				return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			}
		});
		if (selected != null)
		{
			combo.setSelectedItem(selected);
		}
		styled(combo, null).setMaximumSize(new Dimension(105, 22));
		return combo;
	}

	/** {@code Enum.valueOf}, but null instead of throwing for a blank or unrecognised name -- e.g. a since-renamed enum constant. */
	private static <E extends Enum<E>> E parseOrNull(Class<E> type, String name)
	{
		if (name == null)
		{
			return null;
		}
		try
		{
			return Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException unrecognised)
		{
			return null;
		}
	}

	/**
	 * The loadout's items, names only, behind a collapsible toggle -- 225px
	 * is not wide enough to show a dozen item names alongside three max-hit
	 * readouts without the toggle. Expansion state is a per-session UI
	 * convenience ({@link #lockedGearItemsExpanded}), not persisted config.
	 */
	private JPanel itemListSection(Loadout loadout)
	{
		JPanel panel = column();
		JPanel items = column();
		items.setVisible(lockedGearItemsExpanded);
		for (LoadoutItem item : loadout.items())
		{
			items.add(row(item.name().isEmpty() ? ("item " + item.itemId()) : item.name()));
		}
		panel.add(check("Items (" + loadout.items().size() + ")", lockedGearItemsExpanded, Color.WHITE, e ->
		{
			lockedGearItemsExpanded = ((JCheckBox) e.getSource()).isSelected();
			items.setVisible(lockedGearItemsExpanded);
			revalidate();
			repaint();
		}));
		panel.add(items);
		return panel;
	}

	/**
	 * The Build rules section, now rendered inside the Combat tab rather than
	 * a tab of its own -- see PanelSections.BUILD_RULES's tab.
	 *
	 * "Not received yet" versus "no blocking rules" is the same
	 * not-told-versus-nothing-to-tell distinction the rest of this panel
	 * keeps: an empty {@code rules} list means questpath has never pushed
	 * any, so the section says it has not been told. A non-empty list with
	 * the "blocking only" filter hiding every row is a different claim
	 * entirely -- questpath answered, and nothing in the answer blocks --
	 * and rendering that as an empty section would read as the first claim
	 * when it is actually the second.
	 */
	private JPanel buildRulesSection(CombatStats stats, List<BuildRule> rules)
	{
		JPanel panel = column();
		panel.add(heading(PanelSections.BUILD_RULES.title()));

		if (rules.isEmpty())
		{
			// Not "you have no rules". An empty list means nobody has told
			// the plugin what this account is holding, and telling someone
			// their build is unconstrained when nobody has asked would be the
			// worst thing this tab could say.
			//
			// The editor belongs here as well as below, because this is the
			// state where it matters most: with no rules at all the guard is
			// off everywhere and off silently, so the way to fix that has to
			// sit directly under the sentence admitting it.
			panel.add(muted("Quest Route has not been told your rules. Sync sends them"
				+ " when it is on, or write them here -- with none, nothing is guarded."));
			panel.add(buildRulesEditorBlock());
			return panel;
		}

		panel.add(gap(4));
		panel.add(buildRulesToggle());
		panel.add(gap(4));

		boolean showAll = config.buildRulesShowAll();
		List<BuildRule> shown = showAll ? rules : blockingRulesOf(rules);

		if (shown.isEmpty())
		{
			// The filter is on and nothing in the account's rules blocks --
			// good news, not missing data, so it gets its own muted line
			// rather than an empty section that would read like the "not
			// received yet" case above.
			panel.add(muted("No blocking rules"));
		}
		else
		{
			panel.add(buildRulesBlock(stats, shown));
		}

		int hidden = rules.size() - shown.size();
		if (hidden > 0)
		{
			panel.add(gap(4));
			panel.add(muted(hidden + (hidden == 1
				? " non-blocking rule hidden"
				: " non-blocking rules hidden")));
		}

		panel.add(gap(6));
		panel.add(buildRulesEditorBlock());
		return panel;
	}

	/**
	 * The editor for rules written here rather than pushed.
	 *
	 * <p>Always present, not only when the pushed list is empty. A player who
	 * has synced once can still want a rule questpath does not know about,
	 * and hiding the editor the moment a push lands would mean the way to add
	 * one disappears exactly when the tab starts looking useful.
	 */
	private JPanel buildRulesEditorBlock()
	{
		JPanel block = column();
		if (localBuildRules == null)
		{
			// No store -- a preview or a test harness. Saying so beats
			// rendering a form whose buttons quietly do nothing.
			block.add(muted("The rules editor is unavailable in this context."));
			return block;
		}
		block.add(muted("Your own rules"));
		block.add(new BuildRulesEditor(localBuildRules, rules ->
		{
			// Every verdict on every tab is derived from the rules, so a rule
			// saved against a panel still showing the old answers is a panel
			// confidently wrong about which quests the account can do --
			// which is the one thing it exists to be right about.
			if (onRulesEdited != null)
			{
				onRulesEdited.run();
			}
		}));
		return block;
	}

	/** The rules {@link BuildRule#blocking()} reports true for, in the order given. */
	private static List<BuildRule> blockingRulesOf(List<BuildRule> rules)
	{
		List<BuildRule> blocking = new ArrayList<>();
		for (BuildRule rule : rules)
		{
			if (rule.blocking())
			{
				blocking.add(rule);
			}
		}
		return blocking;
	}

	/**
	 * The "Show all rules" checkbox, unchecked by default: blocking-only is
	 * the question most people open this section to answer. Rebuilt with the
	 * rest of the Build rules section on every redraw, so it always reflects
	 * {@code config.buildRulesShowAll()} without needing the populating-flag
	 * dance {@link OverlaySettingsPanel} uses for its own, once-built
	 * controls -- {@link JCheckBox#setSelected} does not fire an
	 * {@code ActionListener} on its own.
	 */
	private JCheckBox buildRulesToggle()
	{
		JCheckBox box = check("Show all rules", config.buildRulesShowAll(), Color.WHITE,
			e -> onBuildRulesShowAllToggled(((JCheckBox) e.getSource()).isSelected()));
		box.setToolTipText("Off shows only rules that would end the build."
			+ " On also lists goals and soft caps, which only inform.");
		return box;
	}

	/**
	 * Writes the toggle through {@link ConfigManager}, exactly like every
	 * control in {@link OverlaySettingsPanel}, then rebuilds the Combat tab
	 * immediately from {@link #lastStats}/{@link #lastXp}/{@link #lastRules}
	 * rather than waiting for the next stat or rule push -- see requirement 4
	 * in the Task 4a brief.
	 */
	private void onBuildRulesShowAllToggled(boolean showAll)
	{
		setConfig("buildRulesShowAll", showAll);
		if (lastStats == null)
		{
			// Nothing has rendered yet -- the first real render will already
			// read the new value off config, so there is nothing to redraw.
			return;
		}
		rebuildCombatTab();
		lastCombatSignature = Arrays.asList(lastStats, lastXp.signature(), lastRules, showAll);
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
		// Hidden across the swap, for the reason clearBodies already documents
		// at length: removeAll() on a *showing* container pays AWT's
		// component-mixing pass once per child instead of once for the
		// subtree, and this rebuilds several hundred of them.
		//
		// Measured, not guessed. Three skips in one session each stalled the
		// EDT for about half a second, and the watchdog's own stack named this
		// method every time:
		//
		//   EDT stalled 496ms ... QuestpathPanel.fillItemResults(...:761)
		//                         QuestpathPanel.lambda$skipRow$10(...)
		//
		// The same cost was fixed once for switching tabs and never applied to
		// rebuilding the tab already on screen, which is the case a skip hits.
		boolean wasShowing = itemResults.isShowing();
		if (wasShowing)
		{
			itemResults.setVisible(false);
		}
		try
		{
			itemResults.removeAll();
			itemResults.add(itemsBlock(items, itemSearch.getText()));
		}
		finally
		{
			// In a finally so a throw part-way through the rebuild cannot
			// leave the Items tab permanently invisible -- a blank panel with
			// no error is the worst way for this to fail.
			if (wasShowing)
			{
				itemResults.setVisible(true);
			}
		}
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
	 * For a group ironman: whose storage snapshot the counts include and how
	 * old it is, or that storage has not been seen yet. Nothing otherwise.
	 */
	private void addGroupStorageNote(JPanel panel, QuestItems items)
	{
		String note = items.groupStorage().note(System.currentTimeMillis());
		if (note != null)
		{
			panel.add(muted(note));
			panel.add(gap(6));
		}
	}

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
			panel.add(note("Bank not read this session, so these"
				+ " counts are what you are carrying. Open your bank once.", PROGRESS_INPROGRESS_COLOR));
			panel.add(gap(6));
		}
		addGroupStorageNote(panel, items);

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
			panel.add(bold("GET WHILE YOU ARE HERE", PROGRESS_COMPLETE_COLOR));

			for (QuestItems.Pickup pickup : items.pickups())
			{
				JLabel row = small(pickup.itemName() + " — in " + pickup.questName(), LIGHT_GRAY_COLOR);
				row.setToolTipText("You are partway through " + pickup.questName()
					+ ", the wiki says this can be picked up during it, and "
					+ pickup.alsoWantedBy() + " quest(s) on your list want it.");
				panel.add(row);
			}
			panel.add(gap(12));
		}

		if (!filtering && geAvailable.getAsBoolean())
		{
			// Automatic, not a config item: see QuestpathPlugin
			// #grandExchangeAvailable's doc for why the account's own type
			// is the whole gate, and GrandExchangeAccounts for why an
			// ironman can never reach this branch regardless of anything a
			// player could toggle.
			panel.add(grandExchangeBlock(items));
			panel.add(gap(8));
		}

		if (!filtering)
		{
			panel.add(shopRouteButton(items));
			if (shopRouteActive.getAsBoolean())
			{
				// Only while a trip is running. A permanent "stop" beside a
				// "start" that has never been pressed is a control that does
				// nothing, and this panel already has enough to read.
				panel.add(cancelShopRouteButton());
			}
			panel.add(skipRow(items));
			panel.add(gap(8));
		}

		// One shopping list first, the per-quest breakdown after. Coins wanted
		// by two quests is one trip to the bank for the sum, not two lines to
		// add up in your head.
		panel.add(bold("EVERYTHING YOU NEED", BRAND_ORANGE));

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

		panel.add(gap(12));
		panel.add(bold("BY QUEST  ·  " + items.quests().size(), BRAND_ORANGE));
		panel.add(gap(4));

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
				panel.add(gap(10));
			}
			first = false;

			panel.add(bold(needs.questName() + "  ·  " + needs.have() + "/" + needs.needed(),
				needs.ready() ? PROGRESS_COMPLETE_COLOR : BRAND_ORANGE));

			JPanel grid = grid();
			for (QuestItems.Line line : shownLines)
			{
				String label = line.optional() ? line.name() + " (optional)" : line.name();
				addRow(grid, label, line.describe(), line.held()
					? PROGRESS_COMPLETE_COLOR
					: LIGHT_GRAY_COLOR,
					itemTooltip(line.members().size() > 1
						? "Any of: " + String.join(", ", line.members()) : null,
						RouteSources.dropsOnly(dropLocator).annotate(line.sources())));
			}
			panel.add(grid);
			questsShown++;
		}

		int questsLeft = items.quests().size() - questsShown;
		if (!filtering && questsLeft > 0)
		{
			// Said, not silently dropped. A list that stops at eight without
			// mentioning the other fifty-nine reads as the whole answer.
			panel.add(gap(8));
			panel.add(muted(questsLeft + " more quests on your plan need items."
				+ " Search above to see any of them."));
		}

		return panel;
	}

	/**
	 * One button: hand the shops selling what the plan still needs to
	 * Shortest Path as a target set.
	 *
	 * <p>Its label carries the reason whenever it is disabled -- a disabled
	 * Swing component receives no mouse events, so a tooltip on a disabled
	 * button would never show.
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
	/**
	 * The flat, ordered buy list for an account with a Grand Exchange -- see
	 * {@link GrandExchangeShoppingList}'s own class doc for why its order is
	 * plain alphabetical rather than {@link QuestItems.Total}'s own
	 * walking-tour order, and {@link GrandExchangeSearchBridge}'s for what a
	 * click actually does. This block never appears for an account {@link
	 * #geAvailable} reports false for -- see this method's own caller.
	 */
	private JPanel grandExchangeBlock(QuestItems items)
	{
		JPanel panel = column();
		panel.add(bold("GRAND EXCHANGE LIST", BRAND_ORANGE));

		boolean includeRecommended = includeRecommended();
		panel.add(check("Include recommended items", includeRecommended, LIGHT_GRAY_COLOR, e ->
		{
			setConfig("recommendedItems", ((JCheckBox) e.getSource()).isSelected());
			fillItemResults(lastItems);
		}));

		TeleportForm form = config == null ? TeleportForm.TABLET : config.recommendedTeleportForm();
		if (includeRecommended)
		{
			// The tablet-or-runes choice, next to the toggle it qualifies.
			// A checkbox rather than a two-item combo because the sidebar is
			// narrow and "runes instead" is the whole question; the Settings
			// tab carries the same key as a combo for anyone who looks there.
			JCheckBox runesBox = check("Runes instead of teleport tablets", form == TeleportForm.RUNES,
				LIGHT_GRAY_COLOR, e ->
				{
					setConfig("recommendedTeleportForm",
						((JCheckBox) e.getSource()).isSelected() ? TeleportForm.RUNES : TeleportForm.TABLET);
					fillItemResults(lastItems);
				});
			runesBox.setToolTipText("Camelot: 5 air and 1 law per cast. Offered only when your Magic"
				+ " level can cast the spell; otherwise the tablet stays listed.");
			panel.add(runesBox);

			if (!items.graphCarriesRecommended())
			{
				// "This graph cannot say", kept apart from "nothing is
				// recommended" -- the same line PanelSections draws between
				// unbuilt and empty. A v12 bundle has no recommended_items
				// on any quest, so the toggle above can only add nothing to
				// the list, and an empty section would read as the wiki
				// recommending nothing for every quest on the route.
				panel.add(muted("This quest graph carries no recommended items. Re-export it from"
					+ " Quest Route (graph version 13 or later) to list them here."));
			}
		}
		panel.add(gap(2));

		List<GrandExchangeShoppingList.Line> lines = GrandExchangeShoppingList.of(
			items.grandExchangeTotals(includeRecommended, form));
		if (lines.isEmpty())
		{
			panel.add(muted("Nothing outstanding is buyable -- the list below covers what is left."));
			addRecommendedCoins(panel, items, includeRecommended);
			return panel;
		}

		panel.add(note("Click an item to search for it at the Grand Exchange."
			+ " Only does anything while the Grand Exchange window is open.", LIGHT_GRAY_COLOR));
		panel.add(gap(4));

		List<GrandExchangeShoppingList.Line> required = GrandExchangeShoppingList.required(lines);
		List<GrandExchangeShoppingList.Line> recommended = GrandExchangeShoppingList.recommended(lines);

		if (!required.isEmpty())
		{
			panel.add(geGrid(required));
		}

		if (!recommended.isEmpty())
		{
			panel.add(gap(6));
			panel.add(bold("RECOMMENDED", BRAND_ORANGE));
			panel.add(geGrid(recommended));
		}
		addRecommendedCoins(panel, items, includeRecommended);

		return panel;
	}

	private JPanel geGrid(List<GrandExchangeShoppingList.Line> lines)
	{
		JPanel grid = grid();
		for (GrandExchangeShoppingList.Line line : lines)
		{
			JLabel left = addRow(grid, line.name(), String.valueOf(line.outstanding()), LIGHT_GRAY_COLOR, null);
			attachGrandExchangeSearch(left, line.itemId(), line.name());
		}
		return grid;
	}

	private boolean includeRecommended()
	{
		return config != null && config.recommendedItems();
	}

	private void setConfig(String key, Object value)
	{
		configManager.setConfiguration(QuestpathConfig.GROUP, key, value);
	}

	/**
	 * The coins the route's quests recommend bringing, as one muted line
	 * under the Grand Exchange list -- a balance to have, never a row to
	 * buy, because no counter sells coins. Nothing when the toggle is off or
	 * no quest recommends any.
	 */
	private void addRecommendedCoins(JPanel panel, QuestItems items, boolean includeRecommended)
	{
		if (!includeRecommended || items.recommendedCoins() <= 0)
		{
			return;
		}
		panel.add(gap(4));
		panel.add(muted("Also bring " + String.format("%,d", items.recommendedCoins())
			+ " coins -- recommended across the route, not something to buy."));
	}

	/**
	 * Left-click a Grand Exchange list row to search for it -- see {@link
	 * GrandExchangeSearchBridge#search} for exactly what that does and does
	 * not confirm.
	 *
	 * <p>The outcome always lands somewhere, on the EDT: a click that does
	 * nothing because the window is closed, or because the client call
	 * itself failed, must say so rather than read as "nothing needed" -- the
	 * rule this whole feature was built under.
	 */
	private void attachGrandExchangeSearch(JComponent row, int itemId, String itemName)
	{
		if (itemId <= 0)
		{
			// Nothing to show without an id: the search answers with ids,
			// never with a typed name -- see GrandExchangeSearchBridge.
			return;
		}
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText("Click to show \"" + itemName + "\" in the Grand Exchange search.");
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (event.getButton() == MouseEvent.BUTTON1)
				{
					geSearchBridge.search(itemId, outcome ->
						SwingUtilities.invokeLater(() -> reportGrandExchangeOutcome(outcome, itemName)));
				}
			}
		});
	}

	/**
	 * Turns a {@link GrandExchangeSearchBridge.Outcome} into what the player
	 * sees. {@link GrandExchangeSearchBridge.Outcome#SENT} is not
	 * confirmation the search box visibly updated -- see that constant's own
	 * doc -- so the toast says "searched", the same understated verb {@link
	 * #reportRouteOutcome} uses for {@code SENT}, never "found" or "opened".
	 */
	private void reportGrandExchangeOutcome(GrandExchangeSearchBridge.Outcome outcome, String itemName)
	{
		switch (outcome)
		{
			case SENT:
				showToast("Showing \"" + itemName + "\" in the Grand Exchange search.");
				return;
			case NOT_OPEN:
				warn("Open the Grand Exchange first -- \"" + itemName + "\" was not searched for.");
				return;
			case CALL_FAILED:
				warn("Searching the Grand Exchange for \"" + itemName + "\" failed. Check the client log.");
				return;
			default:
				// Exhaustive over a closed enum; nothing else to report.
		}
	}

	/**
	 * Stops a shop trip that is under way.
	 *
	 * <p>Asked for directly: "we need a way to disable the shop route after
	 * you turn it on." Until now a trip ended only by expiring or by a shop
	 * interface opening, and neither of those is the player changing their
	 * mind about the whole errand.
	 */
	/**
	 * "Sync with questpath" -- one press, both directions.
	 *
	 * <p>Both directions is the part worth saying on the button, because the
	 * reason to press it is almost always the second one: a push is answered
	 * with this account's build rules, its plan and what questpath holds for
	 * it, so pressing this is how a plan you just changed on the website
	 * reaches the client. Calling it "send" would describe half of what it
	 * does and the less useful half.
	 */
	private JButton syncNowButton()
	{
		JButton button = button("Sync with Quest Route", event -> onSyncNow.run());
		button.setToolTipText("<html><body style='width:220px'>Pushes this account to Quest Route"
			+ " and applies whatever comes back -- build rules, your saved plan, and the items"
			+ " Quest Route holds for you.<br><br>Automatic sync only fires when something changes"
			+ " in-game, so this is how a plan you edited on the website arrives without waiting"
			+ " for your next level-up.</body></html>");
		return button;
	}

	private JButton cancelShopRouteButton()
	{
		return button("Stop the shop route", event ->
		{
			onCancelShopRoute.run();
			repaintAll();
		});
	}

	private JButton shopRouteButton(QuestItems items)
	{
		if (shopDestinations(items).isEmpty())
		{
			return disabled("No known shop sells what you still need");
		}
		return routeButton("", event -> sendShopRoute(items, true));
	}

	/**
	 * Sends the current shopping list's destinations to Shortest Path.
	 *
	 * <p>One place, used by the button and by a skip. A skip that changed the
	 * list without re-sending left Shortest Path walking to a shop for an item
	 * no longer on the trip -- the panel said one thing and the line on the
	 * ground said another, which is the same mismatch this whole feature keeps
	 * producing in new places.
	 *
	 * @param announce whether to report the outcome in the panel. The button
	 *     does; a skip does not, because the path visibly changes on screen
	 *     and a line per skip is noise -- but a FAILURE is always reported,
	 *     since a skip that silently did not re-route is exactly the state
	 *     this method exists to prevent
	 */
	private void sendShopRoute(QuestItems items, boolean announce)
	{
		if (shortestPathBridge == null || items == null)
		{
			return;
		}
		Set<WorldPoint> destinations = shopDestinations(items);
		ShopRouteStops.Destination named = null;
		String stopName = "nothing";
		QuestItems.Total current = currentStop(items);
		if (current != null)
		{
			stopName = current.name();
			named = ShopRouteStops.destinationOf(current, shopLocator, dropLocator, null);
		}
		// Named in the log, not just counted. A count alone said "29" three
		// skips running while the stop never moved, which read as working.
		log.debug("quest-route shop route: stop \"{}\" -> {} ({} destination(s), {} skipped)",
			stopName, named == null ? "nowhere placeable" : named.shopName(),
			destinations.size(), shopItemSkips.size());
		if (destinations.isEmpty())
		{
			return;
		}
		int destinationCount = destinations.size();
		shortestPathBridge.route(destinations, outcome ->
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
			if (announce || outcome != ShortestPathBridge.RouteOutcome.SENT)
			{
				SwingUtilities.invokeLater(() -> reportRouteOutcome(outcome, destinationCount));
			}
		});
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
		// questpath's item plan: make steps, which member of an "X or Y" was
		// chosen, or why nothing reachable -- worded by QuestItems.PlanNote.
		String tiers = total.tiersNote();
		if (tiers != null)
		{
			sharedNote = sharedNote == null ? tiers : sharedNote + ". " + tiers;
		}
		String planNote = total.plan().describe();
		if (planNote != null)
		{
			sharedNote = sharedNote == null ? planNote : sharedNote + ". " + planNote;
		}
		// Make-from-owned reads as in progress: nothing to fetch, one thing
		// to do with what is already in the bank.
		Color totalColour = skipped
			? MEDIUM_GRAY_COLOR
			: total.satisfied()
				? PROGRESS_COMPLETE_COLOR
				: total.inProgress() || total.makeFromOwned()
					? PROGRESS_INPROGRESS_COLOR
					: LIGHT_GRAY_COLOR;
		String label = skipped ? total.name() + "  (skipped)" : total.displayName();
		addRow(totalsGrid, label, total.describe(), totalColour,
			itemTooltip(sharedNote, RouteSources.dropsOnly(dropLocator).annotate(total.sources())));
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

		// The stop the trip is actually walking to, not simply the first
		// outstanding item. Those are different whenever the first item
		// cannot be routed to, and this button used to name the second while
		// the line on the ground went to the first.
		//
		// That is what "I don't know where it's taking me for the black
		// candle" was. No shop in the database sells a black candle and
		// nothing is known to drop one, so it cannot be a destination; the
		// route quietly moved on to Bones, whose only known dropper is a cave
		// goblin in the Lumbridge Swamp Caves. The button said black candle,
		// the path went to the swamp, and skipping did nothing visible
		// because every following item was unroutable too and the route never
		// moved off Bones.
		QuestItems.Total current = currentStop(items);
		if (current != null)
		{
			String currentName = current.name();
			// Where this stop is and what it is for, before the buttons that
			// act on it: the shop with its town, and what else it supplies.
			JLabel where = tinted("<html>" + escape(ShopRouteStops.destinationLine(current,
				shopLocator, dropLocator, playerLocation.get(), items.totals(includeRecommended()),
				shopItemSkips)) + "</html>", LIGHT_GRAY_COLOR);
			where.setFont(SMALL);
			panel.add(where);
			panel.add(button("Skip \"" + currentName + "\" (move to back)",
				event -> reskip(() -> shopItemSkips.skip(currentName))));

			JButton again = sayAgainButton(current);
			if (again != null)
			{
				panel.add(again);
			}
		}

		List<String> skipped = shopItemSkips.names();
		if (!skipped.isEmpty())
		{
			panel.add(gap(4));
			panel.add(bold("SKIPPED THIS TRIP  ·  " + skipped.size(), LIGHT_GRAY_COLOR));
			// Each its own small button rather than one combined "restore
			// all": a skip is reversible one at a time because it was made
			// one at a time, and a player who skipped three items to get to
			// a fourth should not have to give all three back to undo one.
			for (String skippedName : skipped)
			{
				panel.add(button(skippedName + "  ·  restore",
					event -> reskip(() -> shopItemSkips.unskip(skippedName))));
			}
		}

		return panel;
	}

	/** The stop the trip is walking to: the first unsatisfied, unskipped, placeable total. */
	private QuestItems.Total currentStop(QuestItems items)
	{
		for (ShopRouteStops.Stop stop : ShopRouteStops.tour(items.totals(includeRecommended()), shopItemSkips,
			shopLocator, dropLocator, playerLocation.get()))
		{
			if (stop.current())
			{
				return stop.total();
			}
		}
		return null;
	}

	/**
	 * A skip or an undo: redraw now (a skip changes nothing the items
	 * signature covers), and re-send the route so the line on the ground
	 * agrees with the list.
	 */
	private void reskip(Runnable change)
	{
		change.run();
		fillItemResults(lastItems);
		repaintAll();
		if (shopRouteActive.getAsBoolean())
		{
			sendShopRoute(lastItems, false);
		}
	}

	/**
	 * "Say again": the approach line for the shop this stop walks to, for a
	 * player who missed it or wants to check the list. Null when the stop is
	 * a monster, not a shop, or the plugin has not wired the voice.
	 */
	private JButton sayAgainButton(QuestItems.Total current)
	{
		Consumer<String> say = sayShopAgain;
		ShopRouteStops.Destination dest = ShopRouteStops.destinationOf(current, shopLocator, dropLocator,
			playerLocation.get());
		if (say == null || dest == null || dest.monster())
		{
			return null;
		}
		String shopName = dest.shopName();
		JButton again = button("Say again: " + dest.label(), event -> say.accept(shopName));
		again.setToolTipText("Say every item still needed from " + shopName + ", out loud if the"
			+ " voice is on, in chat if not.");
		return again;
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
		return ShopRouteStops.destinations(items.totals(includeRecommended()), shopItemSkips, shopLocator,
			dropLocator, playerLocation.get());
	}

	/**
	 * Turns a {@link ShortestPathBridge.RouteOutcome} into what the player
	 * sees. {@link ShortestPathBridge.RouteOutcome#SENT} is not confirmation
	 * that Shortest Path acted on the route -- see that constant's own doc
	 * -- so the toast says "sent", never "started".
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
				panel.add(muted("No route saved in Quest Route. Save a plan there and it will show up here."));
				return panel;
			case ALL_DONE:
				panel.add(note("Nothing left to do \u2014 every quest on your route is finished.",
					PROGRESS_COMPLETE_COLOR));
				break;
			case NEXT:
				JLabel value = tinted(wrap(next.questName()), Color.WHITE);
				value.setFont(BOLD);
				panel.add(value);
				break;
			default:
				return panel;
		}
		panel.add(gap(6));
		panel.add(routeProgress(next));
		panel.add(routeList(next));
		return panel;
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
		panel.add(note(next.doneCount() + " of " + next.size()
			+ " done  \u00b7  " + next.remaining() + " to go", LIGHT_GRAY_COLOR));

		JProgressBar bar = new JProgressBar(0, Math.max(1, next.size()));
		bar.setValue(next.doneCount());
		bar.setForeground(PROGRESS_COMPLETE_COLOR);
		bar.setBackground(DARKER_GRAY_COLOR);
		bar.setBorderPainted(false);
		bar.setStringPainted(false);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		bar.setPreferredSize(new Dimension(0, 6));
		bar.setAlignmentX(LEFT_ALIGNMENT);
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
	private JPanel routeList(NextQuestStatus next)
	{
		JPanel panel = column();
		panel.add(gap(8));

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
				color = PROGRESS_COMPLETE_COLOR.darker();
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
				color = LIGHT_GRAY_COLOR;
			}

			panel.add(small(prefix + step.questName(), color));
		}

		return panel;
	}

	/**
	 * Quests on the plan that are not startable yet, and what each is waiting
	 * on.
	 *
	 * <p>The route list above already names these -- it names everything, in
	 * plan order, which is the right shape for "what now" and says nothing
	 * about whether the third row is something you could begin in ten seconds
	 * or something needing 41 Cooking first. That is the reading that sends
	 * someone walking to a quest start to find out there.
	 *
	 * <p>So this is the same quests, re-sorted by how close they are and
	 * carrying their reason. Closest first: a boost away, then a level away,
	 * then a quest away. Hovering a row gives the full chain, the same
	 * tooltip the quest browser uses.
	 *
	 * <p>Deliberately not a filter on the list above. Re-ordering the plan
	 * would be showing an order questpath did not save, and the plan's order
	 * is a real answer that this section is not entitled to overwrite.
	 */
	private JPanel routeOutstandingBlock(RouteWork work)
	{
		JPanel panel = column();

		List<RouteWork.Outstanding> outstanding = work.outstanding();
		panel.add(note(outstanding.size() + " of the "
			+ work.considered() + " unfinished on your plan "
			+ (outstanding.size() == 1 ? "needs" : "need") + " something first.", LIGHT_GRAY_COLOR));
		panel.add(gap(6));

		for (RouteWork.Outstanding entry : outstanding)
		{
			JLabel row = small(outstandingPrefix(entry) + entry.questName(), LIGHT_GRAY_COLOR);
			// Null when there is nothing to say, so a row never trails an
			// empty tooltip around -- see blockerTooltip.
			row.setToolTipText(blockerTooltip(entry.questName(), entry.blockers()));
			panel.add(row);
		}

		return panel;
	}

	/**
	 * What kind of waiting one outstanding quest is doing, in one glyph.
	 *
	 * <p>ASCII only, and for the reason the route list documents at length:
	 * FontManager's small RuneScape font is a bitmap font with holes in it,
	 * and a missing glyph draws as a box players read as a stray letter.
	 * Nothing here is worth a second gamble on that.
	 */
	private static String outstandingPrefix(RouteWork.Outstanding entry)
	{
		switch (entry.status())
		{
			case BOOSTABLE:
				// A drink away. Nearest thing to ready there is.
				return "+  ";
			case SKILL_BLOCKED:
				return "^  ";
			case QUEST_BLOCKED:
				return ".  ";
			default:
				return "?  ";
		}
	}

	/**
	 * The quests this build says to start and deliberately never finish.
	 *
	 * <p>These are not on the route and never can be: a route target is
	 * something the plan intends to complete, and completing one of these is
	 * exactly what breaks the build. Until this section existed the preset's
	 * advice -- "start Dragon Slayer I for the anti-dragon shield, do not
	 * finish it" -- lived in a string nothing rendered, while the plan it
	 * produced quietly reported the same quest as impossible.
	 *
	 * <p>Each row hovers to all three sentences, because someone looking at
	 * one is asking three questions at once: what am I getting, how far do I
	 * play, and why can I not just finish it.
	 */
	private JPanel routePartialBlock(RouteWork work)
	{
		JPanel panel = column();

		panel.add(note("Play these part of the way and stop."
			+ " Finishing one breaks your build, so they are not on the plan above.", LIGHT_GRAY_COLOR));
		panel.add(gap(6));

		for (PartialRun run : work.partial())
		{
			JLabel row = small("!  " + run.name(), Color.WHITE);
			row.setToolTipText(partialTooltip(run));
			panel.add(row);

			// The "for" line sits on the row rather than only in the tooltip.
			// It is the one of the three that decides whether to bother at
			// all, and a section whose rows are bare names is a section you
			// have to hover every line of to read.
			JLabel forWhat = small("      " + run.forWhat(), LIGHT_GRAY_COLOR);
			forWhat.setToolTipText(partialTooltip(run));
			panel.add(forWhat);
			panel.add(gap(4));
		}

		return panel;
	}

	/**
	 * How many actionable stops to list before summarising the rest -- the
	 * same "roughly a handful, not the whole list" judgment {@link
	 * ShopRouteOverlay#BASE_STOPS} makes for the overlay's own next-stops
	 * list, applied here to the sidebar instead.
	 */
	private static final int DIARY_ROUTE_ROWS = 5;

	/**
	 * The next few actionable diary tasks worth walking to, the ones this
	 * plugin's own graph has no location for, and any actionable task
	 * questpath reported that this graph does not recognise at all -- see
	 * {@link DiaryRouteStops}'s own class doc for why those are three
	 * separate lists rather than one that silently drops the last two kinds.
	 *
	 * <p>"Diary route needs a questpath sync" -- not an empty list -- is what
	 * this shows when {@link #diaryPush} has never delivered anything, per
	 * {@code PushResponse#hasDiary()}'s own doc: a questpath old enough to
	 * predate diary routing and an account with genuinely nothing targeted
	 * are different facts, and only the second is honestly "nothing to
	 * show."
	 */
	private JPanel diaryRouteBlock()
	{
		JPanel panel = column();

		PushResponse.Diary diary = diaryPush.get();
		if (diary == null)
		{
			panel.add(muted("Diary route needs a Quest Route sync."));
			return panel;
		}

		List<QuestGraph.DiaryTask> graphTasks = diaryGraphTasks.get();
		List<DiaryRouteStops.Stop> stops = DiaryRouteStops.stops(graphTasks, diary, playerLocation.get());
		List<QuestGraph.DiaryTask> locationUnknown = DiaryRouteStops.locationUnknown(graphTasks, diary);
		List<DiaryRouteStops.Unmatched> unmatched = DiaryRouteStops.unmatched(graphTasks, diary);

		if (stops.isEmpty() && locationUnknown.isEmpty() && unmatched.isEmpty())
		{
			// Two different facts behind the same empty result -- see
			// DiaryJournal#progressNeverSynced's own doc, and
			// DiaryRouteOverlay#rows for the same distinction made in the
			// overlay.
			panel.add(muted(DiaryJournal.progressNeverSynced(diary)
				? "Diary progress not synced yet."
				: "No outstanding diary tasks this account can currently act on."));
			return panel;
		}

		int shown = Math.min(stops.size(), DIARY_ROUTE_ROWS);
		for (int i = 0; i < shown; i++)
		{
			DiaryRouteStops.Stop stop = stops.get(i);
			QuestGraph.DiaryTask task = stop.task();
			String distance = stop.distanceTiles() == Integer.MAX_VALUE
				? ""
				: "  (" + stop.distanceTiles() + " tiles)";
			JLabel label = small(task.diary() + " " + task.tier() + ": " + task.text() + distance,
				i == 0 ? Color.WHITE : LIGHT_GRAY_COLOR);
			if (!stop.warnings().isEmpty())
			{
				label.setToolTipText(String.join("; ", stop.warnings()));
			}
			panel.add(label);
		}
		if (stops.size() > shown)
		{
			panel.add(muted("...and " + (stops.size() - shown) + " more."));
		}
		if (!stops.isEmpty())
		{
			panel.add(gap(6));
			panel.add(diaryRouteButton(stops.get(0)));
		}

		for (QuestGraph.DiaryTask task : locationUnknown)
		{
			panel.add(small(task.diary() + " " + task.tier() + ": " + task.text() + "  -- location unknown",
				LIGHT_GRAY_COLOR));
		}

		for (DiaryRouteStops.Unmatched entry : unmatched)
		{
			// A graph/server mismatch, not a location gap -- see
			// DiaryRouteStops.Unmatched's own class doc.
			panel.add(small("Tier " + entry.tierId() + " task #" + entry.ordinal()
				+ "  -- not in this plugin's quest graph (server/graph mismatch)", LIGHT_GRAY_COLOR));
		}

		return panel;
	}

	/**
	 * Hands the single nearest actionable diary task's destination -- exactly
	 * the point {@link DiaryRouteStops#destinationOf} chose, the same one
	 * named beside it above -- to Shortest Path. One place decides that
	 * point: see {@link DiaryRouteStops}'s class doc for why this button
	 * must never compute its own.
	 */
	private JButton diaryRouteButton(DiaryRouteStops.Stop stop)
	{
		QuestGraph.Location destination = stop.destination();
		JButton button = routeButton("", event ->
		{
			WorldPoint point = new WorldPoint(destination.x(), destination.y(), destination.plane());
			shortestPathBridge.route(Set.of(point), outcome -> repaintAll());
		});
		if (button.isEnabled())
		{
			button.setText("Send diary route to Shortest Path");
		}
		return button;
	}

	// ------------------------------------------------------------------
	// Diary journal: every bundled diary task, grouped Area > Tier, each
	// marked against this account's own progress -- the in-game diary
	// journal's own shape, per DiaryJournal's class doc. Distinct from
	// diaryRouteBlock() above: that one is "what to walk to next", this one
	// is "show me the whole diary, the way the game does."
	// ------------------------------------------------------------------

	/**
	 * The whole diary journal: one collapsible group per area, each holding
	 * its tiers and tasks, every task's row coloured and prefixed by {@link
	 * DiaryJournal.TaskState}.
	 *
	 * <p>Still renders the full Area/Tier structure even before a sync --
	 * {@link DiaryJournal#group} hands every task back as {@link
	 * DiaryJournal.TaskState#NOT_SYNCED} rather than nothing at all, so the
	 * journal stays something a player can browse ahead of ever syncing.
	 * The banner above it is what actually says "not synced yet"; see
	 * {@link DiaryJournal#progressNeverSynced}'s own doc for why that check
	 * lives there rather than being re-derived here.
	 */
	private JPanel diaryJournalBlock()
	{
		JPanel panel = column();

		PushResponse.Diary diary = diaryPush.get();
		if (DiaryJournal.progressNeverSynced(diary))
		{
			panel.add(muted(diary == null
				? "Diary progress not synced yet -- push this account's state from a Quest Route"
					+ " that supports diary routing."
				: "Diary progress not synced yet -- Quest Route has not read this account's"
					+ " per-task diary progress."));
			panel.add(gap(6));
		}

		List<QuestGraph.DiaryTask> graphTasks = diaryGraphTasks.get();
		List<DiaryJournal.Area> areas = DiaryJournal.group(graphTasks, diary);
		if (areas.isEmpty())
		{
			panel.add(muted("Quest Route has not bundled any diary tasks yet."));
			return panel;
		}

		for (DiaryJournal.Area area : areas)
		{
			panel.add(diaryAreaGroup(area));
			panel.add(gap(4));
		}
		return panel;
	}

	/**
	 * One area's collapsible group: its tiers as bold sub-headings, each
	 * tier's tasks underneath. Starts collapsed -- twelve areas' worth of
	 * tasks open at once would be several hundred rows, the same reasoning
	 * {@link OverlaySettingsPanel}'s own feature groups start collapsed for,
	 * just applied to areas instead of settings.
	 */
	private CollapsibleGroup diaryAreaGroup(DiaryJournal.Area area)
	{
		JLabel header = tinted(area.name(), Color.WHITE);
		header.setFont(BOLD);

		JPanel body = column();
		body.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 0));
		for (DiaryJournal.Tier tier : area.tiers())
		{
			JLabel tierLabel = row(tier.name(), LIGHT_GRAY_COLOR);
			tierLabel.setFont(BOLD);
			body.add(tierLabel);
			for (DiaryJournal.Entry entry : tier.entries())
			{
				body.add(diaryTaskRow(entry));
			}
			body.add(gap(6));
		}

		CollapsibleGroup group = new CollapsibleGroup(header, body, false);
		group.alsoToggleOn(header);
		return group;
	}

	/** One task's row: a state marker, its text, and "location unknown" when the graph has nowhere for it. */
	private JLabel diaryTaskRow(DiaryJournal.Entry entry)
	{
		QuestGraph.DiaryTask task = entry.task();
		StringBuilder text = new StringBuilder();
		text.append("  ").append(diaryStateGlyph(entry.state())).append(' ').append(task.text());
		if (entry.locationUnknown())
		{
			text.append("  -- location unknown");
		}

		JLabel label = small(text.toString(), diaryStateColor(entry.state()));
		String tooltip = diaryStateTooltip(entry);
		if (tooltip != null)
		{
			label.setToolTipText(tooltip);
		}
		return label;
	}

	/**
	 * A plain ASCII marker per state, never a unicode glyph -- the game's own
	 * font has no glyph for one, which used to render as an orange tofu box;
	 * see {@link CollapsibleGroup}'s own {@code Chevron} doc for the same
	 * lesson learned once already on this panel.
	 */
	private static String diaryStateGlyph(DiaryJournal.TaskState state)
	{
		switch (state)
		{
			case DONE:
				return "[x]";
			case DOABLE:
				return "[ ]";
			case BLOCKED:
				return "[!]";
			case DEFERRED:
				return "[-]";
			case UNKNOWN:
				return "[?]";
			case NOT_SYNCED:
			default:
				return "[.]";
		}
	}

	private static Color diaryStateColor(DiaryJournal.TaskState state)
	{
		switch (state)
		{
			case DONE:
				return PROGRESS_COMPLETE_COLOR;
			case DOABLE:
				return Color.WHITE;
			case BLOCKED:
				return PROGRESS_ERROR_COLOR;
			case UNKNOWN:
				return PROGRESS_INPROGRESS_COLOR;
			case DEFERRED:
			case NOT_SYNCED:
			default:
				return LIGHT_GRAY_COLOR;
		}
	}

	private static String diaryStateTooltip(DiaryJournal.Entry entry)
	{
		switch (entry.state())
		{
			case BLOCKED:
				return entry.reason().isEmpty() ? "Blocked." : entry.reason();
			case DEFERRED:
				return entry.reason().isEmpty()
					? "This diary tier is deferred -- never nudged, only listed here."
					: "Deferred. " + entry.reason();
			case UNKNOWN:
				return "Quest Route cannot yet tell whether this task is done for this account.";
			case NOT_SYNCED:
				return "No diary progress has been read for this account yet.";
			default:
				return null;
		}
	}

	/**
	 * All three sentences of a partial run, laid out as a tooltip.
	 *
	 * <p>Labelled rather than run together. "For", "stop at" and "why" are
	 * answers to different questions, and a paragraph makes the reader work
	 * out which sentence answers which.
	 */
	private static String partialTooltip(PartialRun run)
	{
		return "<html><body style='width:230px'>"
			+ "<b>" + escape(run.name()) + "</b>"
			+ "<br><br>For: " + escape(run.forWhat())
			+ "<br><br>Stop at: " + escape(run.upTo())
			+ "<br><br>Why: " + escape(run.why())
			+ "</body></html>";
	}

	/**
	 * What about the route work would change what is drawn.
	 *
	 * <p>Not the object itself: RouteWork is rebuilt every refresh and has no
	 * equals, so comparing instances would rebuild the Quests tab on every
	 * pass and undo the point of the signature. This is the rendered content
	 * -- each outstanding quest with its verdict, each partial run by slug --
	 * so a levelled skill that clears a row invalidates the tab and a pass
	 * that changed nothing does not.
	 */
	private static Object routeWorkSignature(RouteWork work)
	{
		List<String> parts = new ArrayList<>();
		for (RouteWork.Outstanding entry : work.outstanding())
		{
			parts.add(entry.questName() + "/" + entry.status());
		}
		for (PartialRun run : work.partial())
		{
			parts.add("partial:" + run.slug());
		}
		parts.add("considered:" + work.considered());
		return parts;
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
			panel.add(note("No build rules yet, so nothing can be"
				+ " judged against them. Turn on sync to get them from Quest Route.", PROGRESS_ERROR_COLOR));
			panel.add(gap(6));
		}

		String needle = filter == null ? "" : filter.trim().toLowerCase();
		boolean filtering = !needle.isEmpty();
		panel.add(note(verdicts.count(QuestStatus.COMPLETE) + " done", LIGHT_GRAY_COLOR));
		LocalProfile marks = profile();
		boolean hideBlocked = marks != null && marks.hideBlocked();
		if (marks != null)
		{
			JCheckBox hide = check("Hide quests your build has closed off", hideBlocked, LIGHT_GRAY_COLOR,
				e -> onQuestMark.accept("", LocalProfile.Mark.HIDE_BLOCKED));
			hide.setToolTipText("Leave out the \"Breaks your build\", \"Blocked by a prerequisite\" and"
				+ " refused groups. Right-click a quest to refuse it, pin it to the front or do it later.");
			panel.add(hide);
		}

		// A status picked from the combo narrows the sections shown to that one
		// group, on top of whatever text filter is also active. Left at "All
		// statuses", the grouping is the same as before: every section while
		// searching by name (so a finished quest is still findable), the
		// smaller default set otherwise.
		QuestStatus[] toRender = statusFilter != null
			? new QuestStatus[]{statusFilter}
			: (filtering ? QuestVerdicts.searchSections() : QuestVerdicts.sections());

		int matched = 0;
		List<QuestGraph.Quest> refused = new ArrayList<>();
		for (QuestStatus status : toRender)
		{
			if (hideBlocked && statusFilter == null && status.permanent())
			{
				continue;
			}
			List<QuestGraph.Quest> shown = new ArrayList<>();
			for (QuestGraph.Quest quest : verdicts.quests(status))
			{
				if (filtering && !quest.name().toLowerCase().contains(needle))
				{
					continue;
				}
				// A refused quest is listed under its own heading, not the
				// verdict it would otherwise have had: "never" outranks "can
				// do now", the same precedence the web app's classifier keeps.
				if (marks != null && marks.has(LocalProfile.Mark.REFUSED, quest.name()))
				{
					refused.add(quest);
				}
				else
				{
					shown.add(quest);
				}
			}
			if (shown.isEmpty())
			{
				continue;
			}
			matched += shown.size();
			// The heading shows how many matched out of the group when
			// filtering, so a filter never makes a group look smaller than it
			// is.
			String count = filtering
				? shown.size() + " of " + verdicts.count(status)
				: String.valueOf(verdicts.count(status));
			questGroup(panel, QuestVerdicts.label(status) + "  ·  " + count, statusColor(status), shown, marks, verdicts);
		}
		if (!refused.isEmpty() && !hideBlocked)
		{
			matched += refused.size();
			questGroup(panel, QuestVerdicts.label(QuestStatus.REFUSED) + "  ·  " + refused.size(),
				PROGRESS_ERROR_COLOR, refused, marks, verdicts);
		}

		if ((filtering || statusFilter != null) && matched == 0)
		{
			panel.add(gap(8));
			panel.add(muted("No quest matches that."));
		}

		return panel;
	}

	/**
	 * One verdict group: its heading, then every quest, not the first eight.
	 * The panel scrolls, and "and 49 more" was a list of 49 things you could
	 * not read. A pinned or deferred quest says so on its row.
	 */
	private void questGroup(JPanel panel, String heading, Color colour, List<QuestGraph.Quest> quests,
		LocalProfile marks, QuestVerdicts verdicts)
	{
		panel.add(gap(8));
		panel.add(bold(heading, colour));
		for (QuestGraph.Quest quest : quests)
		{
			String suffix = marks == null ? ""
				: marks.has(LocalProfile.Mark.PINNED, quest.name()) ? "  (pinned first)"
				: marks.has(LocalProfile.Mark.DEFERRED, quest.name()) ? "  (later)" : "";
			JLabel row = small(quest.name() + suffix, LIGHT_GRAY_COLOR);
			if (marks != null)
			{
				row.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mousePressed(MouseEvent e)
					{
						mouseReleased(e);
					}

					@Override
					public void mouseReleased(MouseEvent e)
					{
						if (e.isPopupTrigger())
						{
							questMenu(quest.name()).show(row, e.getX(), e.getY());
						}
					}
				});
			}
			panel.add(row);
			// Which skills are short, by how much, and what boosts them.
			String detail = verdicts.detail(quest);
			if (!detail.isEmpty())
			{
				panel.add(note("Short: " + detail, LIGHT_GRAY_COLOR));
			}
		}
	}

	/** Right-click on a quest: the web app's own per-quest choices, each a toggle. */
	private JPopupMenu questMenu(String quest)
	{
		JPopupMenu menu = new JPopupMenu();
		markItem(menu, quest, LocalProfile.Mark.PINNED, "Pin to the front of the route", "Unpin");
		markItem(menu, quest, LocalProfile.Mark.DEFERRED, "Do it later", "Do it in order again");
		markItem(menu, quest, LocalProfile.Mark.REFUSED, "Refuse: never do this quest", "Stop refusing it");
		return menu;
	}

	private void markItem(JPopupMenu menu, String quest, LocalProfile.Mark mark, String on, String off)
	{
		LocalProfile marks = profile();
		JMenuItem item = new JMenuItem(marks != null && marks.has(mark, quest) ? off : on);
		item.addActionListener(e -> onQuestMark.accept(quest, mark));
		menu.add(item);
	}

	private void warn(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Quest Route", JOptionPane.WARNING_MESSAGE);
	}

	/** An OK/Cancel question; true only on OK. */
	private boolean confirm(String question)
	{
		return JOptionPane.showConfirmDialog(this, question, "Quest Route", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
	}

	/** How long a toast stays up before it dismisses itself. */
	private static final int START_TOAST_MILLIS = 2500;

	/**
	 * A brief, non-modal toast for a "this went fine, no decision to make"
	 * line. Non-modal on purpose: good news is not a decision the player has
	 * to dismiss before doing anything else -- unlike {@link #warn}'s
	 * dialogs, which really are asking for that.
	 */
	private void showToast(String message)
	{
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog toast = new JDialog(owner);
		toast.setUndecorated(true);
		toast.setModal(false);
		toast.setFocusableWindowState(false);

		JLabel label = note(message, Color.WHITE);
		label.setOpaque(true);
		label.setBackground(PROGRESS_COMPLETE_COLOR.darker());
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
			return PROGRESS_ERROR_COLOR;
		}
		if (status.doable())
		{
			return PROGRESS_COMPLETE_COLOR;
		}
		return PROGRESS_INPROGRESS_COLOR;
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
	private void clearBodies(Tab... tabs)
	{
		for (Tab tab : tabs)
		{
			JPanel body = body(tab);
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
	private void showBodies(Tab... tabs)
	{
		for (Tab tab : tabs)
		{
			JPanel body = body(tab);
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
	private void renderPlannedSectionsFor(Tab... tabs)
	{
		for (Tab tab : tabs)
		{
			JPanel bodyForTab = body(tab);
			boolean anyAlready = bodyForTab.getComponentCount() > 0;
			for (PanelSections section : PanelSections.values())
			{
				if (section.tab() != tab || section.status() != PanelSections.Status.NOT_BUILT)
				{
					continue;
				}
				if (anyAlready)
				{
					bodyForTab.add(gap(14));
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
		addMouseListener(new MouseAdapter()
		{
			private int initial;
			private int dismiss;
			private boolean saved;

			@Override
			public void mouseEntered(MouseEvent event)
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
			public void mouseExited(MouseEvent event)
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
		skillChoice.setAlignmentX(LEFT_ALIGNMENT);
		skillChoice.addActionListener(event -> renderSkillPlan());

		skillTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		skillTarget.setAlignmentX(LEFT_ALIGNMENT);
		skillTarget.addChangeListener(event -> renderSkillPlan());

		skillResults.setLayout(new BoxLayout(skillResults, BoxLayout.Y_AXIS));
		skillResults.setAlignmentX(LEFT_ALIGNMENT);

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
		JPanel body = body(Tab.SKILLS);
		body.add(heading(PanelSections.SKILL_PLAN.title()));
		body.add(skillChoice);
		body.add(gap(4));
		body.add(labelled("Target level", skillTarget));
		body.add(gap(8));
		body.add(skillResults);
	}

	private static JPanel labelled(String text, Component control)
	{
		JPanel row = comboRow(small(text, LIGHT_GRAY_COLOR), control);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
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
			skillResults.add(muted("Log in, and Quest Route will price this out."));
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
	private JPanel skillPlanBlock(SkillPlan plan)
	{
		JPanel panel = column();

		if (plan.targetBreaksBuild())
		{
			// Said first and in red. Every number below it is arithmetic about
			// something this account has decided it will not do.
			panel.add(note("Your build caps " + plan.skill() + " at "
				+ plan.cappedAt() + ". Level " + plan.targetLevel() + " would end it.", PROGRESS_ERROR_COLOR));
			panel.add(gap(6));
		}

		if (plan.reached())
		{
			panel.add(muted("You are already " + plan.currentLevel() + " "
				+ plan.skill() + "."));
			return panel;
		}

		JPanel summary = grid();
		addRow(summary, "Now", plan.currentLevel() + " -> " + plan.targetLevel(),
			LIGHT_GRAY_COLOR);
		addRow(summary, "Experience", XpTable.format(plan.xpNeeded()),
			LIGHT_GRAY_COLOR);
		addRow(summary, "From quests now", XpTable.format(plan.readyXp()),
			PROGRESS_COMPLETE_COLOR);
		addRow(summary, "From quests later", XpTable.format(plan.laterXp()),
			BRAND_ORANGE);
		// Two training figures, and the first is the one that decides tonight.
		// "After everything" prices work the account has not agreed to do --
		// for a long prerequisite chain it is months out -- so it is shown
		// second and as the floor it is.
		addRow(summary, "Train, ready only", XpTable.format(plan.xpToTrainFromReadyOnly()),
			plan.readyQuestsAreEnough()
				? PROGRESS_COMPLETE_COLOR
				: LIGHT_GRAY_COLOR,
			"If you only do the quests you can start right now and nothing else,"
				+ " this is what is left to train.");
		addRow(summary, "Train, after all", XpTable.format(plan.xpToTrain()),
			plan.questsAreEnough()
				? PROGRESS_COMPLETE_COLOR
				: LIGHT_GRAY_COLOR,
			"The floor: what is left once every quest you could ever reach has"
				+ " paid out. The gap between these two is what unblocking the"
				+ " list below is worth.");
		panel.add(summary);

		if (plan.readyQuestsAreEnough())
		{
			panel.add(gap(6));
			panel.add(muted("The quests you can start today get you there on their own."));
		}
		else if (plan.questsAreEnough())
		{
			panel.add(gap(6));
			panel.add(muted("Quests alone get you there, but not without"
				+ " unblocking some first."));
		}

		if (plan.lostXp() > 0)
		{
			// The one number only this plugin can produce, and the reason the
			// training figure above is bigger than a skill guide would say.
			panel.add(gap(6));
			panel.add(note(XpTable.format(plan.lostXp()) + " is off the table for this build, across "
				+ plan.lost().size() + " quest(s).", PROGRESS_ERROR_COLOR));
		}

		band(panel, "READY NOW", plan.ready(), PROGRESS_COMPLETE_COLOR);
		band(panel, "AFTER MORE WORK", plan.later(), BRAND_ORANGE);
		band(panel, "OFF THE TABLE", plan.lost(), PROGRESS_ERROR_COLOR);

		return panel;
	}

	/** How many quests one band lists before it says how many more there are. */
	private static final int MAX_PLAN_ROWS = 8;

	private void band(JPanel panel, String title, List<SkillPlan.Entry> entries, Color colour)
	{
		if (entries.isEmpty())
		{
			return;
		}
		panel.add(gap(10));
		panel.add(bold(title + "  ·  " + entries.size(), colour));

		JPanel rows = grid();
		int shown = Math.min(entries.size(), MAX_PLAN_ROWS);
		for (int i = 0; i < shown; i++)
		{
			SkillPlan.Entry entry = entries.get(i);
			addRow(rows, entry.questName(), XpTable.format(Math.round(entry.xp())),
				LIGHT_GRAY_COLOR, blockerTooltip(entry));
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
		return blockerTooltip(entry.questName(), entry.blockers());
	}

	/**
	 * The same tooltip for anything that knows a quest's name and what stands
	 * in its way, whether or not it came from a skill plan.
	 *
	 * <p>Split out for the route's own outstanding list, which has both and
	 * has no experience figure to carry -- passing a zero through
	 * SkillPlan.Entry to reuse this would have put "0 xp" into the model to
	 * satisfy a signature.
	 */
	private static String blockerTooltip(String questName, QuestBlockers blockers)
	{
		if (blockers == null || blockers.isEmpty())
		{
			return null;
		}

		StringBuilder text = new StringBuilder("<html><body style='width:230px'>");
		text.append("<b>").append(escape(questName)).append("</b>");

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
		JLabel value = tinted(String.valueOf(level), Color.WHITE);
		value.setFont(BOLD.deriveFont(24f));
		panel.add(value);

		int percent = (int) Math.round(CombatMath.combatProgress(stats) * 100.0);
		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue(percent);
		bar.setStringPainted(true);
		bar.setString(percent + "% to " + (level + 1));
		bar.setForeground(BRAND_ORANGE);
		bar.setBackground(DARKER_GRAY_COLOR);
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

		panel.add(tinted("Ceiling " + ceiling + " — at Attack " + stats.get(Skill.ATTACK) + ", Strength "
			+ stats.get(Skill.STRENGTH), LIGHT_GRAY_COLOR));
		panel.add(gap(4));

		JPanel grid = grid();
		addCeilingRow(grid, xp, Skill.RANGED, stats.get(Skill.RANGED), ceiling);
		addCeilingRow(grid, xp, Skill.MAGIC, stats.get(Skill.MAGIC), ceiling);
		panel.add(grid);

		CombatMath.CeilingStep step = CombatMath.nextCeilingStep(stats.get(Skill.ATTACK), stats.get(Skill.STRENGTH));
		panel.add(gap(4));
		panel.add(note(step == null
			? "No Strength level raises it further."
			: "Strength " + step.atStrength + " raises it to " + step.ceiling + " (+" + step.gained + ")",
			LIGHT_GRAY_COLOR));

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
			color = PROGRESS_COMPLETE_COLOR;
		}
		else if (headroom == 0)
		{
			detail = "at the ceiling";
			color = PROGRESS_INPROGRESS_COLOR;
		}
		else
		{
			detail = (-headroom) + " past the ceiling";
			color = PROGRESS_ERROR_COLOR;
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
	/**
	 * Non-static so it can carry the sync button, which needs the plugin's own
	 * callback. Everything else in here is still a pure function of its
	 * arguments.
	 */
	private JPanel syncBlock(SyncStatus sync, List<String> unmatchedQuests,
		SyncSettings settings, AccountReport report)
	{
		JPanel panel = column();

		panel.add(syncNowButton());
		panel.add(gap(6));

		panel.add(tinted(wrap(sync.describe(Instant.now())),
			sync.needsAttention() ? PROGRESS_ERROR_COLOR : LIGHT_GRAY_COLOR));

		if (sync.state() == SyncStatus.State.NO_TOKEN)
		{
			panel.add(note("Get this token from your Quest Route server", LIGHT_GRAY_COLOR));
		}

		// The settings themselves, not only their effect. RuneLite's config
		// panel is several clicks away and behind a different icon, and
		// "sent 3m ago" does not say which server it went to -- which is the
		// first thing anyone checks when it went somewhere unexpected.
		if (settings != null)
		{
			panel.add(gap(8));
			JPanel grid = grid();
			addRow(grid, "Sending", settings.enabled() ? "on" : "off",
				settings.enabled() ? PROGRESS_COMPLETE_COLOR : LIGHT_GRAY_COLOR);
			addRow(grid, "Server", settings.serverUrl(), LIGHT_GRAY_COLOR);
			addRow(grid, "Token", settings.hasToken() ? "set" : "not set",
				settings.hasToken() ? PROGRESS_COMPLETE_COLOR : PROGRESS_ERROR_COLOR);
			panel.add(grid);
			panel.add(note("Change these under Settings, Quest Route.", MEDIUM_GRAY_COLOR));
		}

		// Who is being sent, and what. The tab used to be able to say only
		// whether the last push worked, which answers "is it on" and nothing
		// about which account it wrote -- and that is the question that
		// matters. Three pushes once wrote one character's quest states over
		// another account's, and every one of them looked like an ordinary
		// sync from inside the game.
		if (report != null)
		{
			panel.add(gap(10));
			JLabel who = tinted(wrap(report.characterName()), Color.WHITE);
			who.setFont(BOLD);
			panel.add(who);
			panel.add(note("This is the account every push is attributed to.", MEDIUM_GRAY_COLOR));
			panel.add(gap(6));
			JPanel sent = grid();
			addRow(sent, "Combat", String.valueOf(report.combatLevel()), LIGHT_GRAY_COLOR);
			addRow(sent, "Quest points", String.valueOf(report.questPoints()), LIGHT_GRAY_COLOR);
			addRow(sent, "Total level", String.valueOf(report.totalLevel()), LIGHT_GRAY_COLOR);
			addRow(sent, "Total xp", XpTable.format(report.totalXp()), LIGHT_GRAY_COLOR);
			addRow(sent, "Quests done", report.questsComplete() + " of " + report.questsKnown(),
				LIGHT_GRAY_COLOR);
			addRow(sent, "Quests started", String.valueOf(report.questsInProgress()),
				LIGHT_GRAY_COLOR);
			// Two facts, not one. Zero stacks with the bank seen is an empty
			// bank; zero stacks without it is a bank nobody has opened, and
			// questpath's item answers are working from an older upload until
			// they are.
			addRow(sent, "Item stacks", String.valueOf(report.itemStacks()),
				report.itemStacks() > 0 ? LIGHT_GRAY_COLOR : MEDIUM_GRAY_COLOR);
			addRow(sent, "Bank", report.bankSeen() ? "seen this session" : "not opened yet",
				report.bankSeen() ? PROGRESS_COMPLETE_COLOR : PROGRESS_INPROGRESS_COLOR);
			panel.add(sent);

			if (!report.bankSeen())
			{
				panel.add(note("Open your bank once and the item lists fill in.", MEDIUM_GRAY_COLOR));
			}
		}

		// A quest name this plugin sends that questpath cannot resolve is a
		// real disagreement between the two. Counting them would hide which,
		// and which is the only part that leads anywhere.
		if (!unmatchedQuests.isEmpty())
		{
			panel.add(gap(6));
			panel.add(note(unmatchedQuests.size() + " quest names Quest Route did not recognise:",
				PROGRESS_INPROGRESS_COLOR));
			for (String name : unmatchedQuests)
			{
				panel.add(small(name, LIGHT_GRAY_COLOR));
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
			return LIGHT_GRAY_COLOR;
		}
		if (level < 0 || !rule.hasValue())
		{
			// NO_XP, or a skill outside the combat seven. It blocks, but there
			// is no distance to render.
			return PROGRESS_INPROGRESS_COLOR;
		}
		if (level >= rule.value())
		{
			return PROGRESS_ERROR_COLOR;
		}
		if (level >= rule.value() - 1)
		{
			return PROGRESS_INPROGRESS_COLOR;
		}
		return PROGRESS_COMPLETE_COLOR;
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
		panel.add(bold("Not built yet", BRAND_ORANGE.darker()));
		panel.add(note(section.plan(), LIGHT_GRAY_COLOR));
		return panel;
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
			return PROGRESS_ERROR_COLOR;
		}
		if (delta <= 3)
		{
			return PROGRESS_INPROGRESS_COLOR;
		}
		return PROGRESS_COMPLETE_COLOR;
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
			html.append(escape(lines.get(i)));
		}
		html.append("</html>");
		return html.toString();
	}

	private static void addRow(JPanel grid, String label, String detail, Color detailColor)
	{
		addRow(grid, label, detail, detailColor, null);
	}

	/**
	 * @return the label cell, so a caller that needs to wire it up further --
	 *     the Grand Exchange list attaches its search click to it -- does not
	 *     have to rebuild the row itself.
	 */
	private static JLabel addRow(JPanel grid, String label, String detail, Color detailColor, String tooltip)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(1, 0, 1, 6);

		JLabel left = new JLabel(label);
		left.setForeground(LIGHT_GRAY_COLOR);
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

	private JPanel body(Tab tab)
	{
		return tabBodies.get(tab);
	}

	/** A heading, then its block, then the gap before the next section. */
	private static void section(JPanel into, PanelSections section, Component block)
	{
		into.add(heading(section.title()));
		into.add(block);
		into.add(gap(14));
	}
}
