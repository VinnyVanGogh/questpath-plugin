package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * questpath — build-rule-aware quest planning, in the client.
 *
 * <p>v1 scope. The panel answers what the next combat level costs in every
 * stat, which needs no quest data, no bundled graph, and no server. The quest
 * planner and the state push to the questpath web app follow; the full plan is
 * docs/questpath-plugin-plan.md in the Go repo.
 *
 * <p>There is no config class yet. The first genuine setting is the state-push
 * server URL, which arrives with the feature that needs it — a stub config
 * would only invite guessing at options early.
 */
@Slf4j
@PluginDescriptor(
	name = "Quest Path",
	description = "What the next combat level costs, and which quests your build can legally do",
	tags = {"quest", "combat", "pure", "ironman", "planner", "build"}
)
public class QuestpathPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private QuestpathConfig config;

	@Inject
	private AccountStateSender sender;

	@Inject
	private SkillIconManager skillIcons;

	/**
	 * The client's own interface art, for the sidebar tabs. Skill icons are
	 * still used inside the overlays, where a row really is about a skill.
	 */
	@Inject
	private net.runelite.client.game.SpriteManager sprites;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private OverlayIcons overlayIcons;

	/**
	 * Used by the settings tab to write config the same way RuneLite's own
	 * panel does, so the two surfaces cannot disagree about a stored value.
	 */
	@Inject
	private ConfigManager configManager;

	@Inject
	private net.runelite.client.game.ItemManager itemManager;

	/**
	 * RuneLite's own hover-tooltip queue, shared with every other plugin.
	 *
	 * Overlays add to it during a render pass and it draws whatever was added
	 * that frame, so nothing here has to track the pointer or clear a stale
	 * tooltip -- not adding one is how a tooltip goes away.
	 */
	@Inject
	private net.runelite.client.ui.overlay.tooltip.TooltipManager tooltipManager;

	/** Prints questpath's own lines into the game chat. */
	@Inject
	private net.runelite.client.chat.ChatMessageManager chatMessageManager;

	/** RuneLite's shared scheduler. The watchdog samples on it. */
	@Inject
	private java.util.concurrent.ScheduledExecutorService scheduler;

	/**
	 * Held so shutDown can stop it. A poller nobody can cancel would outlive
	 * every plugin toggle, one per toggle, for the life of the client.
	 */
	private EdtWatchdog watchdog;

	/**
	 * The five in-game overlays, held so they can be removed again.
	 *
	 * All five are registered whatever the config says: an Overlay that is
	 * switched off returns null from render and costs a method call per
	 * frame, whereas adding and removing them as toggles change would mean
	 * reacting to config events and getting the ordering right for no gain.
	 */
	private final List<QuestpathOverlay> overlays = new java.util.ArrayList<>();

	/**
	 * What the overlays draw, rebuilt whenever the panel is.
	 *
	 * Written on the client thread and read on the client thread during
	 * render, but across a refresh boundary, so it is volatile and always
	 * replaced wholesale. Null means logged out, which every overlay renders
	 * as nothing at all rather than as an empty panel.
	 */
	private volatile OverlaySnapshot overlaySnapshot;

	/**
	 * Watches for an outstanding item becoming fully held, so the chat can
	 * say so at the moment it happens rather than a panel turning green
	 * behind the player's back.
	 */
	private final ItemCompletionWatcher completions = new ItemCompletionWatcher();

	/**
	 * Something happened that the panel and overlays should be rebuilt for,
	 * and the rebuild is waiting for the next tick.
	 *
	 * refresh() is not cheap: it reads roughly 180 quest varbits three times
	 * over and runs two classify passes across 218 quests. That is fine a few
	 * times a second and ruinous per event -- and the events arrive in
	 * bursts. Logging in fires a StatChanged for every skill; banking a full
	 * inventory fires an ItemContainerChanged for every slot. Refreshing on
	 * each meant twenty to thirty full rebuilds in a fraction of a second,
	 * every time, which is exactly when the client is already busiest.
	 *
	 * Coalescing to the game tick makes a burst cost one rebuild instead of
	 * thirty, at the price of up to 600ms of staleness on a readout nobody
	 * reads that fast.
	 */
	private boolean refreshPending;

	/**
	 * The finished-quest set for the rebuild currently in flight.
	 *
	 * Three separate parts of a refresh ask for this -- the verdicts, the item
	 * lists and the route -- and each ask walked all ~180 entries of RuneLite's
	 * Quest enum calling getState, which reads a varbit apiece. Same answer
	 * every time, three times over, on a method already being called too
	 * often. Held for the duration of one refresh and dropped after, so it
	 * can never serve a stale answer into the next one.
	 */
	private Set<String> completedThisPass;

	/**
	 * The shop marker, held separately from the panel overlays.
	 *
	 * It is a WidgetItemOverlay rather than a QuestpathOverlay: it draws into
	 * a game interface's own item slots instead of owning a panel, so it
	 * shares none of their layout and cannot sit in their list. It also has
	 * its own toggle deliberately -- someone who wants no panel on screen all
	 * day may still want the shop marked when they open one.
	 */
	private ShopItemOverlay shopOverlay;

	/**
	 * The ground marker. Also outside the panel overlays, and for the same
	 * reason: it draws into the world rather than owning a panel.
	 */
	private GroundItemOverlay groundOverlay;

	/**
	 * The bundled quest graph, or null when it could not be loaded.
	 *
	 * Null is rendered as a plain failure rather than as an empty quest list:
	 * a broken bundle and a player with nothing to do look identical
	 * otherwise, and only one of them is worth telling someone about.
	 */
	private QuestGraph questGraph;

	private QuestpathPanel panel;
	private NavigationButton navButton;

	/**
	 * The bank, inventory and equipment as last seen. Not re-read on demand:
	 * the client does not hold the bank's contents until it has been opened.
	 */
	private final OwnedItems owned = new OwnedItems();

	/**
	 * What was last sent, so an unchanged account is not pushed again. Reset
	 * on logout because the next login may be a different account, and its
	 * state must not be mistaken for unchanged.
	 */
	private int lastStructural;
	private int lastExperience;
	private Instant lastPushAt = Instant.EPOCH;
	private boolean haveSent;

	/**
	 * The last thing the push did, for the panel to report.
	 *
	 * Written from an OkHttp callback thread and read from the client thread,
	 * so it is volatile. A SyncStatus is immutable, so publishing the
	 * reference safely publishes everything in it.
	 */
	private volatile SyncStatus lastSyncOutcome;

	/**
	 * The account's declared limits, as questpath last reported them.
	 *
	 * Written from an OkHttp callback and read on the client thread, so it is
	 * volatile and always replaced wholesale rather than mutated. Empty until
	 * the first accepted push: the plugin has no other way to learn them, and
	 * inventing a default set would be inventing someone's build.
	 */
	private volatile List<BuildRule> buildRules = Collections.emptyList();

	/**
	 * Quest names this plugin sent that questpath could not resolve. A real
	 * disagreement between the two, worth showing rather than counting.
	 */
	private volatile List<String> unmatchedQuests = Collections.emptyList();

	/**
	 * The account's saved route from questpath, as quest names in order.
	 *
	 * Written from an OkHttp callback and read on the client thread, so it is
	 * volatile and always replaced wholesale. Empty until the first accepted
	 * push, same as buildRules and for the same reason -- there is no other
	 * way to learn it, and an account with a genuinely empty saved route
	 * looks identical to one that has not synced yet, which is the honest
	 * answer in both cases: "no route saved, as far as questpath has told
	 * this session". Cleared with buildRules on logout and on a rejected
	 * push, because on a rejection questpath is not confirming this
	 * account's state, and a stale route could belong to another account.
	 */
	private volatile List<String> routeOrder = Collections.emptyList();

	/**
	 * What questpath holds for this account, by lowercased item name.
	 *
	 * The fallback for every item answer before the player opens a bank. Same
	 * lifecycle as buildRules and routeOrder -- volatile, replaced wholesale,
	 * and cleared on logout and on a rejected push, because a stale map from
	 * another account is worse than none.
	 */
	private volatile Map<String, Long> knownItems = Collections.emptyMap();

	/**
	 * How often a push may be sent for experience alone, with no quest or
	 * level change behind it.
	 *
	 * Deliberately shorter than the five minutes questpath treats a push as
	 * live for. While the game is open the plugin has to keep re-asserting
	 * itself, or that window lapses and questpath falls back to polling
	 * WikiSync -- which would overwrite the plugin's own fresher data with an
	 * older upload, the exact problem the freshness window exists to stop.
	 */
	private static final Duration EXPERIENCE_PUSH_INTERVAL = Duration.ofMinutes(2);

	@Override
	protected void startUp()
	{
		watchdog = new EdtWatchdog(scheduler);
		watchdog.start();
		panel = new QuestpathPanel(sprites, new OverlaySettingsPanel(config, configManager));
		navButton = NavigationButton.builder()
			.tooltip("Quest Path")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlays.add(new NextQuestOverlay(this, config, overlayIcons));
		overlays.add(new PickupsOverlay(this, config, overlayIcons));
		overlays.add(new NeededItemsOverlay(this, config, overlayIcons));
		overlays.add(new CombatXpOverlay(this, config, overlayIcons));
		overlays.add(new BuildRulesOverlay(this, config, overlayIcons));
		for (QuestpathOverlay overlay : overlays)
		{
			overlayManager.add(overlay);
		}

		shopOverlay = new ShopItemOverlay(this, config, itemManager, client, tooltipManager);
		overlayManager.add(shopOverlay);

		groundOverlay = new GroundItemOverlay(this, config, client, tooltipManager);
		overlayManager.add(groundOverlay);

		try
		{
			questGraph = QuestGraph.load(gson);
			log.debug("questpath: quest graph loaded, {} quests", questGraph.quests().size());
		}
		catch (IOException e)
		{
			// Not fatal. The combat and sync tabs need no graph, and a plugin
			// that refuses to start because one tab cannot work is worse than
			// one tab saying why.
			questGraph = null;
			log.warn("questpath: bundled quest graph unavailable; the Quests tab will say so", e);
		}

		// The plugin can be enabled mid-session, so there may already be a
		// logged-in account whose StatChanged burst has been and gone.
		refresh();
		log.debug("questpath started");
	}

	@Override
	protected void shutDown()
	{
		for (QuestpathOverlay overlay : overlays)
		{
			overlayManager.remove(overlay);
		}
		overlays.clear();
		if (shopOverlay != null)
		{
			overlayManager.remove(shopOverlay);
			shopOverlay = null;
		}
		if (groundOverlay != null)
		{
			overlayManager.remove(groundOverlay);
			groundOverlay = null;
		}
		overlayIcons.clear();
		overlaySnapshot = null;

		if (watchdog != null)
		{
			watchdog.stop();
			watchdog = null;
		}

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		haveSent = false;
		owned.clear();
		log.debug("questpath stopped");
	}

	/**
	 * Levels arrive as a burst of StatChanged at login and one at a time
	 * afterwards. Recomputing on each is cheap — seven integer reads and a
	 * bounded search per skill — and it keeps the panel correct without a
	 * timer.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Coalesced: login fires one of these per skill.
		refreshPending = true;
		maybeSync();
	}

	/**
	 * Where every coalesced rebuild actually happens.
	 *
	 * One per tick at most, and only when something asked for one.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!refreshPending)
		{
			return;
		}
		refreshPending = false;
		refresh();
	}

	/**
	 * Quest completion moves varbits, so this is where a finished quest is
	 * noticed. There is no "quest completed" event to subscribe to.
	 *
	 * Every varbit in the game arrives here, which is why the push is gated on
	 * the payload actually differing rather than on the event firing.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		maybeSync();
	}

	/**
	 * The bank, inventory and equipment as the client reports them.
	 *
	 * This is the event the whole plugin exists for: nothing outside the
	 * client can see a bank, which is why questpath's item guide has been
	 * ticked by hand.
	 *
	 * Already on the client thread, so the container is read here rather than
	 * deferred -- an ItemContainer read later is a read of whatever it holds
	 * later.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!owned.record(client, event.getItemContainer()))
		{
			return;
		}

		// Coalesced to the next tick; see refreshPending. Banking a full
		// inventory fires one of these per slot.
		//
		// Redraw before pushing, not after.
		//
		// This used to only sync. Picking up the banana the panel had just
		// told you to pick up therefore changed nothing on screen: `owned`
		// knew, and every surface reading it -- the item lists, the pickups
		// prompt, the shop marks -- was built from a snapshot nobody had
		// rebuilt. It cleared on the next trip to a bank, because that
		// happened to fire the events that do refresh, which made the bug
		// look like it was about banking.
		//
		// The cost is a classify pass over 218 quests per container change.
		// That is a real cost and it is the right one: the whole point of
		// reading a bank from inside the client is answering "do I have this
		// yet" the moment it changes, and an answer that lags until you walk
		// to a bank is the answer the web app already gives.
		refreshPending = true;
		maybeSync();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refresh();
			maybeSync();
			return;
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// A logged-out client reports every skill as level 1, which would
			// render as a confident "combat 3" for an account that is nothing
			// of the sort.
			panel.showLoggedOut();
			// The next login may be a different account, whose state must not
			// be mistaken for "unchanged since last push".
			haveSent = false;
			lastSyncOutcome = null;
			// The next login may be another account with different limits.
			// Showing the previous one's would be worse than showing none.
			buildRules = Collections.emptyList();
			unmatchedQuests = Collections.emptyList();
			routeOrder = Collections.emptyList();
			knownItems = Collections.emptyMap();
			// The next login may be another account, whose bank is not this
			// one. Reporting the previous account's items would be worse than
			// reporting none.
			owned.clear();
			completions.reset();
		}
	}

	private void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			panel.showLoggedOut();
			// No account, so nothing for an overlay to be about. Cleared
			// rather than left standing: the next login may be a different
			// character, and one account's route drawn over another's game is
			// the failure this plugin has already had once.
			overlaySnapshot = null;
			// The next login may be another account. Keeping the baseline
			// would announce the new one's items against the old one's
			// shortfalls the first time anything changed.
			completions.reset();
			return;
		}

		// Held for this pass only. Every read below wants the same answer and
		// each was paying ~180 varbit reads for it; dropped in the finally so
		// the next refresh cannot be served last refresh's quests.
		completedThisPass = null;
		CombatStats stats;
		SkillXp xp;
		QuestItems items;
		NextQuestStatus route;
		AccountReport report;
		QuestVerdicts questVerdicts;
		try
		{
			stats = currentStats();
			xp = currentXp();
			items = questItems();
			route = nextQuest();
			report = accountReport();
			questVerdicts = verdicts();
		}
		finally
		{
			completedThisPass = null;
		}

		// Built from the same reads the panel is given, in the same pass. Two
		// passes would let the sidebar and the overlay disagree about the
		// same account for a frame, which is exactly the kind of small lie
		// that is impossible to reproduce later.
		overlaySnapshot = new OverlaySnapshot(stats, xp, route, items, buildRules,
			characterName(), questPoints());

		announce(completions.observe(items));

		panel.update(stats, xp, syncStatus(), buildRules, unmatchedQuests,
			questVerdicts, items, route, SyncSettings.from(config), report);
	}

	/**
	 * Prints one chat line per item just completed.
	 *
	 * The toggle is read here rather than inside the watcher on purpose: the
	 * watcher keeps observing either way, so switching the setting on does not
	 * then announce a backlog of everything collected while it was off.
	 */
	private void announce(java.util.List<ItemCompletionWatcher.Completion> completed)
	{
		if (completed.isEmpty() || config == null || !config.chatOnItemComplete())
		{
			return;
		}
		for (ItemCompletionWatcher.Completion done : completed)
		{
			ChatMessageBuilder line = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("Quest Path: you now have all ")
				.append(ChatColorType.HIGHLIGHT)
				.append(done.required() + " " + done.itemName())
				.append(ChatColorType.NORMAL);
			String quests = done.quests();
			if (quests.isEmpty())
			{
				// questpath knows the item is wanted but not by what -- an
				// unresolved line, or a total merged from a quest with no
				// name. Saying nothing is better than saying "for ".
				line.append(".");
			}
			else
			{
				line.append(" -- for ").append(ChatColorType.HIGHLIGHT).append(quests)
					.append(ChatColorType.NORMAL).append(".");
			}
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(line.build())
				.build());
		}
	}

	/**
	 * What the overlays draw. Read on the client thread during render.
	 *
	 * Package-private rather than exposed through an interface: the overlays
	 * are the only callers and they live beside this class.
	 */
	OverlaySnapshot overlaySnapshot()
	{
		return overlaySnapshot;
	}

	/** The logged-in character, or null when there is not one. */
	private String characterName()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}
		return client.getLocalPlayer().getName();
	}

	/**
	 * What the Sync tab reports about this account, or null when there is no
	 * account to report on.
	 *
	 * Read from the client rather than from questpath's answer, deliberately:
	 * the tab is showing what this client believes it is sending, so that a
	 * disagreement with what questpath received shows up as a disagreement
	 * rather than as agreement with itself.
	 */
	private AccountReport accountReport()
	{
		String name = characterName();
		if (name == null || name.isEmpty())
		{
			return null;
		}

		int complete = 0;
		int inProgress = 0;
		int known = 0;
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			if (state == null)
			{
				// The client has said nothing about this one, and the push
				// skips it for the same reason: "not started" is a claim, and
				// this is an absence of one.
				continue;
			}
			known++;
			if (state == QuestState.FINISHED)
			{
				complete++;
			}
			else if (state == QuestState.IN_PROGRESS)
			{
				inProgress++;
			}
		}

		int totalLevel = 0;
		long totalXp = 0;
		for (Skill skill : Skill.values())
		{
			if (skill.getName() == null)
			{
				// Skill.OVERALL is declared for binary compatibility and is
				// null at runtime; it is also an aggregate, so counting it
				// would double every total below.
				continue;
			}
			totalLevel += client.getRealSkillLevel(skill);
			totalXp += client.getSkillExperience(skill);
		}

		return new AccountReport(name,
			client.getLocalPlayer().getCombatLevel(),
			questPoints(),
			totalLevel,
			totalXp,
			complete,
			inProgress,
			known,
			owned.stackCount(),
			owned.bankSeen());
	}

	/**
	 * Quest points, as the game itself counts them.
	 *
	 * Read from the varp rather than counted from the quest graph: the game's
	 * own number is the one on the player's quest tab, and a count derived
	 * from questpath's 218 quests would differ from it by every quest
	 * questpath has not ingested -- a discrepancy with no explanation on
	 * screen.
	 */
	private int questPoints()
	{
		return client.getVarpValue(VarPlayerID.QP);
	}

	/**
	 * Real levels, not boosted ones. A combat level is computed from the
	 * account's actual stats, so a dose of super strength must not appear to
	 * move it.
	 */
	private CombatStats currentStats()
	{
		return new CombatStats(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.PRAYER));
	}

	/**
	 * Real experience for the combat seven, validated against the levels the
	 * client reports in the same read.
	 *
	 * Reading XP rather than deriving it from level thresholds is what makes
	 * every figure on the panel exact instead of an upper bound. The
	 * validation matters because LOGGED_IN can fire before the stats packet
	 * arrives, and getSkillExperience answers 0 until it does — see SkillXp.
	 */
	private SkillXp currentXp()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : CombatStats.COMBAT_SKILLS)
		{
			levels.put(skill, client.getRealSkillLevel(skill));
			xp.put(skill, client.getSkillExperience(skill));
		}
		return SkillXp.of(levels, xp);
	}

	/**
	 * Pushes account state to questpath when it has actually changed.
	 *
	 * The gate is the payload, not the event. onVarbitChanged fires for every
	 * varbit in the game and StatChanged for every experience drop, so a push
	 * per event would be a request per tick while training. Comparing what
	 * would be sent means the request happens exactly when something the
	 * server cares about moved — a quest finishing, a level going up.
	 *
	 * Reading the state must happen on the client thread (quest varbits), and
	 * the request must not, which is why the read is wrapped and the send is
	 * an enqueue inside it.
	 */
	private void maybeSync()
	{
		if (!config.syncEnabled() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			Map<String, Object> payload = AccountState.read(client, owned);
			if (payload == null)
			{
				// LOGGED_IN without a local player yet. Nothing here can be
				// attributed to a character, and questpath refuses an
				// unattributed push anyway -- see AccountState.read.
				return;
			}
			int structural = AccountState.fingerprint(payload);
			int experience = AccountState.experienceFingerprint(payload);

			if (!shouldSend(structural, experience))
			{
				return;
			}
				if (sender.send(config.serverUrl(), config.pairingToken(), payload,
				outcome ->
				{
					lastSyncOutcome = outcome;
					// A refusal means questpath is not confirming this
					// account's state, so anything still on the Build tab is
					// unconfirmed at best and another account's at worst --
					// which is exactly what a 409 says it is. Holding it on
					// screen next to this account's live levels is the most
					// convincing way to be wrong available to this panel, so
					// the rules go and the status line says why.
					//
					// Unreachable deliberately does not clear: a questpath
					// that is not running says nothing about whether the
					// rules already in hand belong to this account.
					if (outcome.state() == SyncStatus.State.REJECTED)
					{
						buildRules = Collections.emptyList();
						unmatchedQuests = Collections.emptyList();
						routeOrder = Collections.emptyList();
						knownItems = Collections.emptyMap();
						clientThread.invokeLater(this::refresh);
					}
				},
				accepted ->
				{
					// questpath owns the build rules; this is the only place
					// the plugin learns them.
					buildRules = BuildRule.ordered(accepted.buildRules());
					unmatchedQuests = accepted.unmatched();
					// Same story for the saved route -- questpath owns the
					// plan, and this push response is the only place it is
					// handed over.
					routeOrder = accepted.routeOrder();
					knownItems = accepted.knownItems();

					// Repaint now rather than waiting for the next game
					// event. An earlier version left it to the next
					// StatChanged, on the reasoning that a Swing hop from a
					// network callback is work for a panel nobody may be
					// looking at -- but the first push is the only time rules
					// arrive, and a player standing still generates no events,
					// so the Build tab sat on "not received yet" indefinitely
					// while the rules were already in hand.
					clientThread.invokeLater(this::refresh);
				}))
			{
				// Recorded on dispatch rather than on a successful response:
				// a questpath that is not running would otherwise mean a
				// retry on every varbit for as long as it stays down. The
				// next real change re-sends anyway.
				lastStructural = structural;
				lastExperience = experience;
				lastPushAt = Instant.now();
				haveSent = true;
			}
		});
	}

	/**
	 * Whether this reading is worth a request.
	 *
	 * Two rules, because the payload has two tempos. A quest finishing or a
	 * level landing is rare and worth sending the instant it happens.
	 * Experience moves every few seconds while training, so it is sent on a
	 * timer instead — often enough that questpath's stored xp is never far
	 * behind and its freshness window never lapses, rarely enough that
	 * training is not a request per tick.
	 */
	private boolean shouldSend(int structural, int experience)
	{
		if (!haveSent)
		{
			return true;
		}
		if (structural != lastStructural)
		{
			return true;
		}
		if (experience == lastExperience)
		{
			return false;
		}
		return Duration.between(lastPushAt, Instant.now()).compareTo(EXPERIENCE_PUSH_INTERVAL) >= 0;
	}

	/**
	 * What the sidebar says about the push.
	 *
	 * Configuration states are computed fresh each time rather than cached,
	 * because a token pasted into settings must show as fixed on the next
	 * render rather than on the next successful request.
	 */
	private SyncStatus syncStatus()
	{
		if (!config.syncEnabled())
		{
			return SyncStatus.of(SyncStatus.State.DISABLED);
		}
		if (config.pairingToken() == null || config.pairingToken().isEmpty())
		{
			return SyncStatus.of(SyncStatus.State.NO_TOKEN);
		}
		SyncStatus outcome = lastSyncOutcome;
		return outcome == null ? SyncStatus.of(SyncStatus.State.WAITING) : outcome;
	}

	/**
	 * Judges every quest against this account, or null when it cannot be done.
	 *
	 * Null when the graph is missing or the client has not loaded quest states
	 * yet. The panel distinguishes those from "nothing to do", because a blank
	 * list is an answer and neither of these is one.
	 *
	 * Runs on the client thread: Quest.getState reads varbits.
	 */
	private QuestVerdicts verdicts()
	{
		if (questGraph == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Set<String> completed = completedQuestNames();
		if (completed.isEmpty() && client.getGameState() == GameState.LOGGED_IN)
		{
			// A logged-in account with no finished quests is possible but
			// rare, and an account whose varbits have not arrived looks
			// identical. Judging against it would report every quest as
			// available, which is the confident wrong answer to avoid.
			return null;
		}

		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill.getName() == null)
			{
				continue;
			}
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
			experience.put(skill.getName(), (long) client.getSkillExperience(skill));
		}

		return QuestVerdicts.of(questGraph, new PlayerState(levels, experience, completed, buildRules));
	}

	/**
	 * What the doable quests need, against what is in the bank.
	 *
	 * Null when the graph is missing, the client has not loaded, or no
	 * container has been opened yet -- all of which are "cannot answer", not
	 * "you need nothing".
	 *
	 * Runs on the client thread: it classifies, which reads quest varbits.
	 */
	/**
	 * The names of every quest the client reports as finished.
	 *
	 * Names, not ids: the client's quest ids are the game's and questpath's
	 * are its own database rowids, and an earlier version compared the two
	 * directly. The overlap was coincidence, so quests the account had never
	 * started were judged COMPLETE and finished ones were not.
	 *
	 * Must run on the client thread -- getState reads varbits.
	 */
	private Set<String> completedQuestNames()
	{
		if (completedThisPass != null)
		{
			return completedThisPass;
		}
		Set<String> completed = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				completed.add(quest.getName());
			}
		}
		return completed;
	}

	/**
	 * Quests the client reports as started but not finished.
	 *
	 * This is what "the quest you are doing" resolves to. Quest Helper's own
	 * selected quest would be more precise and is not readable from here --
	 * it lives in memory in another plugin, with no config key, no event and
	 * no shared class to reach it through, and reflection is forbidden.
	 *
	 * Must run on the client thread.
	 */
	private Set<String> inProgressQuestNames()
	{
		Set<String> started = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.IN_PROGRESS)
			{
				started.add(quest.getName());
			}
		}
		return started;
	}

	/**
	 * Which quest on the saved route to do next, or why that cannot be
	 * answered yet.
	 *
	 * Null for the same reason verdicts() is null: a logged-out client, or one
	 * whose quest varbits have not loaded, reports zero completed quests
	 * indistinguishably from an account that genuinely has none, and the
	 * first quest on a real route would then be reported "next" even if it
	 * was finished months ago. The panel renders null as "cannot judge yet",
	 * not as "nothing to do".
	 */
	private NextQuestStatus nextQuest()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Set<String> completed = completedQuestNames();
		if (completed.isEmpty())
		{
			return null;
		}
		return NextQuestStatus.of(routeOrder, completed);
	}

	/**
	 * The quests the pickup list is drawn from: the next few on the route,
	 * still undone.
	 *
	 * "The next few" rather than "started". A started flag never clears on an
	 * umbrella quest -- Recipe for Disaster has been in progress for hundreds
	 * of hours -- so it recommended an item from a Feldip Hills subquest
	 * permanently, wherever the player actually was. What you are about to
	 * walk through is the honest reading of "here".
	 */
	private java.util.Set<String> pickupScope()
	{
		NextQuestStatus route = nextQuest();
		if (route == null)
		{
			return Collections.emptySet();
		}
		java.util.Set<String> scope = new HashSet<>();
		for (NextQuestStatus.Step step : route.upcoming())
		{
			if (scope.size() >= Math.max(1, config.pickupsLookahead()))
			{
				break;
			}
			scope.add(step.questName());
		}
		return scope;
	}

	private QuestItems questItems()
	{
		if (questGraph == null || client.getGameState() != GameState.LOGGED_IN || owned.isEmpty())
		{
			return null;
		}

		Set<String> completed = completedQuestNames();

		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill.getName() == null)
			{
				continue;
			}
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
			experience.put(skill.getName(), (long) client.getSkillExperience(skill));
		}

		return QuestItems.of(questGraph,
			new PlayerState(levels, experience, completed, buildRules),
			owned.heldByName(), owned.bankSeen(), pickupScope(),
			new HashSet<>(routeOrder), knownItems, owned.carriedByName());
	}

	@Provides
	QuestpathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuestpathConfig.class);
	}

	/**
	 * Drawn rather than loaded from a resource, deliberately: a placeholder
	 * PNG committed now would be an asset nobody designed, and AGENTS.md warns
	 * about shipping images that are not what they claim to be. Replace with a
	 * real icon.png when there is one.
	 */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(0xC8, 0x8B, 0x2E));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.drawString("qp", 2, 18);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
