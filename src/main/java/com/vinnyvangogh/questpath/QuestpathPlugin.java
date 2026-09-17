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
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
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

	/**
	 * Used to find the running Quest Helper or Shortest Path plugin, if
	 * either is present -- see {@link QuestHelperBridge} and {@link
	 * ShortestPathBridge}.
	 */
	@Inject
	private PluginManager pluginManager;

	/**
	 * RuneLite's own shared event bus, used only to post a {@code
	 * PluginMessage} for Shortest Path -- see {@link ShortestPathBridge}.
	 */
	@Inject
	private EventBus eventBus;

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
	 * Persists named gear loadouts. Guice hands out the one {@code
	 * Singleton}-scoped instance -- see {@link LoadoutStore}'s own class doc
	 * for why every method on it is synchronized.
	 */
	@Inject
	private LoadoutStore loadoutStore;

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

	private BankNeedsOverlay bankOverlay;
	private BankListOverlay bankListOverlay;
	private BankButton bankButton;
	private BankTabLayout bankTab;

	/**
	 * questpath's own row in the Grand Exchange's item-search chatbox -- see
	 * that class's own doc for what it draws and where.
	 */
	private GrandExchangeButton geButton;

	/**
	 * Which of the account's eight Grand Exchange slots hold a live buy
	 * offer -- what turns "already asked for" into a state the shopping list
	 * can see, rather than one only the player is holding in their head.
	 *
	 * <p>Never null and never replaced: {@link #onGrandExchangeOfferChanged}
	 * writes into it and {@link #onGameStateChanged} clears it, so nothing
	 * has to null-check a field that is only ever empty or full.
	 */
	private final GrandExchangeOffers geOffers = new GrandExchangeOffers();

	/**
	 * Whether questpath's bank layout is the one on screen.
	 *
	 * <p>Off by default, and that is the important half. This is a tab, not a
	 * filter: switched on it replaces what the grid shows, so defaulting it on
	 * meant opening tab nine and being handed questpath's list with the title
	 * still claiming to be tab nine. A bank tab you did not choose is a broken
	 * bank.
	 *
	 * <p>Session state rather than config for the same reason a real tab is:
	 * you pick one for a trip, and a setting that survived logout would start
	 * every visit in whichever state the last one ended.
	 */
	private boolean bankNeedsShown;

	/**
	 * The six in-game overlays, held so they can be removed again.
	 *
	 * All six are registered whatever the config says: an Overlay that is
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
	 * The account as the last refresh saw it.
	 *
	 * Volatile because the Skills tab reads it from the event thread while
	 * refreshes write it from the client thread. Null until the first refresh
	 * with quests actually read -- which the panel renders as "log in",
	 * distinct from an account with nothing to train.
	 */
	private volatile PlayerState accountSnapshot;

	/**
	 * Watches for an outstanding item becoming fully held, so the chat can
	 * say so at the moment it happens rather than a panel turning green
	 * behind the player's back.
	 */
	private final ItemCompletionWatcher completions = new ItemCompletionWatcher();
	private final FloatingNotes floatingNotes = new FloatingNotes();
	private final ShopkeeperMemory shopkeeper = new ShopkeeperMemory();
	private Boosts cachedShopBoosts;
	private Map<String, WorldPoint> dropsByName;
	/**
	 * Where the player was at the last client-thread read. Volatile
	 * because the panel reads it from Swing -- see
	 * lastKnownPlayerLocation for why it cannot just ask the client.
	 */
	private volatile WorldPoint lastPlayerLocation;
	private Map<String, String> dropRegionsByName;
	private final ShopApproachWatcher shopApproach = new ShopApproachWatcher();

	/**
	 * Watches for a shop opening that sells something outstanding -- see its
	 * own class doc for why this is "at a shop" rather than the
	 * walking-distance proximity {@link #shopLocation} now makes buildable
	 * but that this class does not attempt.
	 */

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
	private FloatingNoteOverlay floatingNoteOverlay;

	/**
	 * The quest graph currently in use -- fetched live from questpath when
	 * {@link QuestpathConfig#fetchQuestGraph()} is on and the fetch
	 * succeeded, the bundled jar resource otherwise -- or null when neither
	 * is available.
	 *
	 * Null is rendered as a plain failure rather than as an empty quest list:
	 * a broken bundle and a player with nothing to do look identical
	 * otherwise, and only one of them is worth telling someone about.
	 */
	private QuestGraph questGraph;

	@Inject
	private QuestGraphFetcher graphFetcher;

	/**
	 * A quest-graph-source announcement waiting for a login to actually show
	 * it in.
	 *
	 * {@link #fetchLiveQuestGraph}'s fetch usually resolves before the
	 * client is logged in at all -- it starts from {@link #startUp}, and a
	 * chat line posted while sitting at the login screen is a message nobody
	 * saw. Held here and flushed by {@link #onGameStateChanged}'s own {@code
	 * LOGGED_IN} branch instead of being dropped, because "say once on load"
	 * has to mean the player actually sees it, not merely that this class
	 * attempted to say it.
	 */
	private String pendingGraphAnnouncement;

	private QuestpathPanel panel;
	private NavigationButton navButton;

	/**
	 * Built once in {@link #startUp}, not injected: it needs
	 * {@code pluginManager} wired into two lambdas, not a RuneLite instance
	 * of its own. See its class doc for why it exists at all.
	 */
	private QuestHelperBridge questHelperBridge;

	/**
	 * Built once in {@link #startUp}, same reason {@link #questHelperBridge}
	 * is: it needs {@code pluginManager} and {@code eventBus} wired into
	 * lambdas, not a RuneLite instance of its own. See its class doc.
	 */
	private ShortestPathBridge shortestPathBridge;

	/**
	 * Built once in {@link #startUp}, same shape as {@link
	 * #shortestPathBridge}: it needs {@code client} and {@code clientThread}
	 * wired into lambdas rather than a RuneLite instance of its own, so a
	 * test can stand in for both without constructing a real {@code Client}.
	 * Unlike {@link #shortestPathBridge} and {@link #questHelperBridge} it
	 * talks to no other plugin -- see its class doc.
	 */
	private GrandExchangeSearchBridge geSearchBridge;

	/**
	 * The account's own RuneLite account type, refreshed every game tick --
	 * see {@link #lastKnownPlayerLocation} for why a Swing caller cannot ask
	 * {@code client} for this directly, the same reasoning applies here.
	 * Null before the client has read one, e.g. at the login screen.
	 */
	private net.runelite.api.vars.AccountType accountType;

	/**
	 * Notices when the route's current step finishes, for {@link
	 * QuestpathConfig#autoAdvanceQuestHelper}.
	 *
	 * Reset in {@link #startUp} and on logout, same as every other piece of
	 * per-session route state -- see its class doc for why an unreset
	 * planner would misfire on the very sweep it exists to ignore.
	 */
	private final AutoAdvancePlanner autoAdvance = new AutoAdvancePlanner();

	/**
	 * Notices when the player arrives at the next route quest's start point,
	 * for {@link QuestpathConfig#questHelperArrivalPrompt}.
	 *
	 * Reset in {@link #startUp} and on logout, same triggers and same reason
	 * as {@link #autoAdvance} -- see that field's doc and {@link
	 * QuestStartArrivalWatcher}'s class doc for why an unreset watcher would
	 * misfire on the very sweep it exists to ignore.
	 */
	private final QuestStartArrivalWatcher questStartArrival = new QuestStartArrivalWatcher();

	/**
	 * The quests worth checking the player's distance to this tick, name to
	 * start point -- rebuilt once per {@link #refresh}, never per tick, by
	 * {@link QuestStartCandidates#of}.
	 *
	 * <p>Every remaining step of the saved route, capped to {@link
	 * QuestpathConfig#questStartArrivalDepth} route positions when that is
	 * non-zero, plus every quest {@link QuestVerdicts} currently calls {@link
	 * QuestStatus#ELIGIBLE} or {@link QuestStatus#BOOSTABLE} -- startable
	 * right now, boost genuinely covering any shortfall included -- and,
	 * either way, not already begun. The depth setting only narrows the
	 * route half; off-route ELIGIBLE/BOOSTABLE candidates are unaffected at
	 * any depth, see {@link QuestStartCandidates}'s own class doc. See that
	 * same doc for the bug this double filter closes: an earlier version
	 * added every route step with no eligibility check at all, which is how
	 * Shadow of the Storm started on an account well short of its levels. No
	 * arbitrary size limit on top of either filter, because neither half
	 * scales with the 218-quest graph on a real account -- a route is a few
	 * dozen quests at most and startable quests are bounded by how much of
	 * the prerequisite tree is actually open, typically far fewer than that.
	 * {@link #checkQuestStartArrival} spends one {@code WorldPoint#distanceTo}
	 * per entry here, per tick, and nothing else -- no classification, no
	 * graph lookup, no per-tick allocation beyond the watcher's own arrival
	 * list. Cleared to empty, never null, so {@link QuestStartArrivalWatcher}
	 * never has to special-case "not computed yet" versus "nothing to track".
	 */
	private Map<String, WorldPoint> questStartCandidates = Collections.emptyMap();

	/**
	 * Whether any quest on the account's saved route is currently {@code
	 * IN_PROGRESS}, per the client's own quest state -- the second signal
	 * {@link QuestStartArrivalGate} now checks alongside Quest Helper's own
	 * selection. See that class's doc for why the route, not the whole
	 * account, is what {@link #inProgressQuestNames()} is intersected
	 * against, and why that scoping is the point.
	 *
	 * <p>Recomputed once per {@link #refresh}, the same cadence as {@link
	 * #questStartCandidates}, and read every tick from {@link
	 * #checkQuestStartArrival} without redoing any of that work -- {@link
	 * QuestStartArrivalGate#allow} is a plain boolean check, not a scan.
	 */
	private boolean routeQuestInProgress;

	/**
	 * When the shop-route button last actually sent a route to Shortest Path
	 * ({@link ShortestPathBridge.RouteOutcome#SENT}), or null when none has
	 * been sent this session or the tracked send was already cleared. See
	 * {@link ShopRouteGuard}'s class doc for why anything tracks this at all,
	 * and {@link #checkShopArrival} and {@link #onGameStateChanged} for the
	 * two ways it clears before {@link ShopRouteGuard#ACTIVE_WINDOW} would.
	 */
	private Instant shopRouteSentAt;

	/**
	 * Whether a shop trip is still underway, as opposed to whether a route
	 * was sent recently.
	 *
	 * <p>These were one field and one clock, on the reasoning that "still
	 * walking a shop trip" is exactly the state that should hold an unrelated
	 * quest-start prompt back and exactly the state {@link ShopRouteOverlay}
	 * exists to describe. Play disagreed. {@link ShopRouteGuard#ACTIVE_WINDOW}
	 * is five minutes, which is a sane ceiling on "is this player still
	 * walking to a shop" and no ceiling at all on a trip whose current stop is
	 * a monster -- the drop that ends that leg arrives when it arrives, and a
	 * 10/128 roll off a level-92 tortoise is not a five-minute errand. The
	 * overlay stood down mid-trip, and because {@code ShopRouteAdvance} was
	 * gated on the same expired clock, nothing could bring it back.
	 *
	 * <p>So the timer stays where it was justified -- suppressing automatic
	 * Quest Helper starts, where expiring is the safe direction because the
	 * cost of being wrong is one prompt held back too long. This one has no
	 * timer: a trip ends when it ends. Cancelled, finished, arrived at a shop
	 * stop, or logged out; nothing else, and never merely because time passed
	 * while the player was doing the thing the trip told them to do.
	 */
	private boolean shopTripActive;

	/**
	 * Items skipped on the current shop trip -- see {@link ShopItemSkips}'s
	 * class doc. Handed to both {@link QuestpathPanel} (the skip button and
	 * its undo list) and {@link ShopRouteOverlay} (which stops must not be
	 * shown as "next"), the one instance so a skip made from either surface
	 * is immediately visible from the other.
	 */
	private final ShopItemSkips shopItemSkips = new ShopItemSkips();

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
	 * What this account has declared it will not do. questpath owns these
	 * exactly as it owns the build rules, and the push response is the only
	 * place the plugin learns them.
	 */
	private volatile List<PlayerConstraint> constraints = Collections.emptyList();

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
	 * The build's start-only quests, as questpath last sent them.
	 *
	 * <p>Same lifecycle as routeOrder -- volatile, replaced wholesale,
	 * cleared on logout and on a rejected push. A rejection means questpath
	 * is not confirming this account, and a build's partial runs shown
	 * against the wrong account would be advice to break a rule that account
	 * actually holds.
	 */
	private volatile List<PartialRun> partialRuns = Collections.emptyList();

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
	 * Per-account diary routing data, as questpath last reported it -- null
	 * both before the first accepted push and for a server old enough to
	 * predate the field; see {@link PushResponse#hasDiary()}'s own doc for
	 * why those two are the same case here rather than distinct ones. Same
	 * lifecycle as {@link #buildRules} -- volatile, replaced wholesale,
	 * cleared on logout and on a rejected push, because a stale verdict from
	 * another account is worse than none.
	 */
	private volatile PushResponse.Diary diaryPush;

	/**
	 * When each diary task ({@code (tier_id, ordinal)}) was last nudged
	 * about, for {@link DiaryRouteGuard#shouldNudge}'s cooldown parameter.
	 * Session state, not cleared on a rejected push -- a cooldown is about
	 * not repeating a chat line too often, not about which account is
	 * confirmed, so a push rejection has no bearing on it. Cleared on logout
	 * in {@link #onGameStateChanged} for the same reason {@link
	 * #questStartCandidates} is: the next login may be a different account
	 * standing anywhere at all, and its first nearby task must not be read
	 * as "already nudged."
	 */
	private final Map<DiaryRouteStops.TaskKey, Instant> diaryNudgedAt = new HashMap<>();

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
		// The plugin can be toggled off and on again within the same
		// session without a logout in between, and RuneLite reuses this
		// same instance across that toggle -- so without this, a quest
		// finished while the plugin was off would look like a completion
		// observed live the moment it comes back on. See AutoAdvancePlanner's
		// class doc.
		autoAdvance.reset();
		// Same reasoning, same class shape: toggling the plugin off and back
		// on must not read wherever the player is already standing as a
		// fresh arrival. See QuestStartArrivalWatcher's class doc.
		questStartArrival.reset();
		questHelperBridge = new QuestHelperBridge(pluginManager::getPlugins,
			plugin -> pluginManager.isPluginEnabled((Plugin) plugin),
			// Quest Helper's own startUpQuest defers through this same queue
			// when not already called from it, so a read-back posted here
			// runs after Quest Helper's own deferred write -- see
			// QuestHelperBridge's class doc.
			clientThread::invokeLater,
			// Answered by this plugin's own Client, not read reflectively out
			// of Quest Helper -- net.runelite.api.Client is RuneLite's shared
			// game state, not something private to Quest Helper.
			() -> client.getGameState() == GameState.LOGGED_IN);
		shortestPathBridge = new ShortestPathBridge(pluginManager::getPlugins,
			plugin -> pluginManager.isPluginEnabled((Plugin) plugin),
			// PluginMessage/EventBus#post is RuneLite's own public API, not
			// Shortest Path's, so no reflection is needed here -- but a
			// thread hand-off still is: Shortest Path's own onPluginMessage
			// handler asserts it is running on the client thread and does
			// nothing to get itself there if it is not, the opposite
			// convention from Quest Helper's startUpQuest, which defers
			// internally. This button is Swing, so route() is called from
			// the EDT, and a bare eventBus::post reference here used to run
			// Shortest Path's assertion on the EDT and throw AssertionError
			// on every click -- see ShortestPathBridge's own class doc and
			// clientThreadEventPoster's doc for the fix and why it works
			// from either thread.
			ShortestPathBridge.clientThreadEventPoster(clientThread::invoke, eventBus::post));
		geSearchBridge = new GrandExchangeSearchBridge(
			// InterfaceID.GeOffers.UNIVERSE (30474240) is the packed id of
			// the Grand Exchange window's own root widget -- group 465,
			// child 0, the same id the deprecated
			// WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER resolves to.
			// Not enough on its own, though: that window can be open while
			// showing the quantity/price screen rather than the item-search
			// chatbox, in which case there is nothing below for the action
			// to feed text into. Chatbox.MES_LAYER is that chatbox's own
			// root -- see GrandExchangeButton's class doc for how
			// that packed id was confirmed -- so both must be open for this
			// to report AVAILABLE rather than a click that quietly does
			// nothing. Only ever called from inside geSearchBridge's own
			// client-thread hand-off -- see GrandExchangeSearchBridge's
			// class doc for why that matters here the same way it does for
			// shortestPathBridge above.
			() ->
			{
				Widget window = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
				if (window == null || window.isHidden())
				{
					return false;
				}
				Widget search = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
				return search != null && !search.isHidden();
			},
			itemName ->
			{
				// searchText, not the name as questpath holds it: the wiki's
				// title case makes RuneLite's own case-sensitive filter miss
				// every time and fall back to a fuzzy search, which draws the
				// results underlined as though the spelling were wrong. See
				// GrandExchangeSearchBridge#searchText for the disassembly.
				client.setVarcStrValue(VarClientStr.INPUT_TEXT,
					GrandExchangeSearchBridge.searchText(itemName));
				// GE_ITEM_SEARCH (752) takes three int arguments and this
				// class has no verified call site for them -- see
				// GrandExchangeSearchBridge's own class doc for the
				// AssertionError that produced, and
				// GrandExchangeButton's for the fix applied here:
				// replay the search box's own key listener, already built
				// with whatever arguments it actually needs, rather than
				// guess a second time.
				Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
				if (input != null)
				{
					Object[] listener = input.getOnKeyListener();
					if (listener != null)
					{
						client.runScript(listener);
					}
				}
			},
			clientThread::invoke);
		geButton = new GrandExchangeButton(client, clientThread::invokeLater, this::grandExchangeList);
		panel = new QuestpathPanel(sprites, config, configManager,
			new OverlaySettingsPanel(config, configManager),
			this::skillPlan, questHelperBridge, shortestPathBridge, geSearchBridge,
			this::grandExchangeAvailable, this::shopLocation,
			this::dropLocation, this::lastKnownPlayerLocation, this::onShopRouteSent,
			this::cancelShopRoute,
			this::shopRouteActive, this::syncNow,
			shopItemSkips, this::diaryGraphTasks, this::diaryPush, lockedGearActions());
		navButton = NavigationButton.builder()
			.tooltip("Quest Path")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlays.add(new NextQuestOverlay(this, config, overlayIcons));
		overlays.add(new PickupsOverlay(this, config, overlayIcons));
		overlays.add(new NeededItemsOverlay(this, config, overlayIcons, client));
		overlays.add(new ShopRouteOverlay(this, config, overlayIcons, this::shopLocation,
			this::dropLocation, this::playerLocation, shopItemSkips));
		overlays.add(new DiaryRouteOverlay(this, config, overlayIcons, this::diaryGraphTasks,
			this::diaryPush, this::playerLocation));
		overlays.add(new GrandExchangeOverlay(this, config, overlayIcons, client, this::grandExchangeList));
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
		floatingNoteOverlay = new FloatingNoteOverlay(this, config, client, floatingNotes);
		overlayManager.add(floatingNoteOverlay);

		bankOverlay = new BankNeedsOverlay(this, config, client, tooltipManager);
		overlayManager.add(bankOverlay);

		bankListOverlay = new BankListOverlay(this, config, overlayIcons, client);
		overlayManager.add(bankListOverlay);

		bankTab = new BankTabLayout(client, clientThread, this);
		bankButton = new BankButton(client, this::bankNeedsShown, this::toggleBankNeeds);

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

		if (config.fetchQuestGraph())
		{
			fetchLiveQuestGraph();
		}

		// The plugin can be enabled mid-session, so there may already be a
		// logged-in account whose StatChanged burst has been and gone.
		refresh();
		log.debug("questpath started");
	}

	/**
	 * Asks the configured questpath instance for its current quest graph,
	 * replacing the bundled copy on success.
	 *
	 * <p>This is the self-updating half of {@link QuestGraph}'s own doc
	 * comment on why a bundle goes stale: the graph loaded above only
	 * changes when this plugin is released, and this session had four
	 * server-side corrections -- sub-area gates, shop reachability, the
	 * shop-source cap, the teleport rewrite -- each of which reached the web
	 * app immediately and would otherwise have waited for that. Off by
	 * default, opt-in the same way {@link QuestpathConfig#syncEnabled()} is:
	 * see that field's own doc for why a plugin that must talk to a
	 * third-party server to be useful is a harder plugin to justify than one
	 * where doing so is an extra.
	 *
	 * <p>Whatever happens is said exactly once, never silently: a fetched
	 * graph swaps in with an announcement naming it, and a failure --
	 * unreachable server, a version this build refuses either direction,
	 * malformed JSON -- leaves {@link #questGraph} exactly as {@link
	 * #startUp} already set it and says why. The player reported the same
	 * already-fixed bug twice before this existed, purely because nothing
	 * told them their bundle was stale; a fetch that fails back to the
	 * bundle without a word would recreate exactly that hole one layer
	 * further out.
	 */
	private void fetchLiveQuestGraph()
	{
		graphFetcher.fetch(config.serverUrl(), outcome -> clientThread.invokeLater(() ->
		{
			if (outcome.succeeded())
			{
				questGraph = outcome.graph();
				String message = "Quest Path: using the live quest graph from questpath ("
					+ questGraph.quests().size() + " quests).";
				log.info("questpath: {}", message);
				announceGraphSource(message);
				refresh();
				return;
			}

			String stillOn = questGraph == null
				? "no quest graph at all -- the Quests tab will say so"
				: questGraph.quests().size() + " quests bundled with this build";
			String message = "Quest Path: could not fetch the quest graph from questpath (" + outcome.reason()
				+ "); staying on " + stillOn + ".";
			log.info("questpath: {}", message);
			announceGraphSource(message);
		}));
	}

	/**
	 * Shows one quest-graph-source line in game chat, or holds it for the
	 * next login if there is no chat to show it in yet -- see {@link
	 * #pendingGraphAnnouncement}'s own doc for why this must not simply drop
	 * the message when the client is not logged in.
	 */
	private void announceGraphSource(String message)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			chat(message);
			return;
		}
		pendingGraphAnnouncement = message;
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
		if (floatingNoteOverlay != null)
		{
			overlayManager.remove(floatingNoteOverlay);
			floatingNoteOverlay = null;
		}
		// Emptied as well as unregistered: notes are timed from wall clock,
		// so a handful left over from a previous session would otherwise be
		// waiting, already expired, for the next one to read them.
		floatingNotes.clear();
		shopkeeper.forget();
		shopApproach.clear();
		if (groundOverlay != null)
		{
			overlayManager.remove(groundOverlay);
			groundOverlay = null;
		}
		if (bankOverlay != null)
		{
			overlayManager.remove(bankOverlay);
			bankOverlay = null;
		}
		if (bankListOverlay != null)
		{
			overlayManager.remove(bankListOverlay);
			bankListOverlay = null;
		}
		if (bankButton != null)
		{
			bankButton.detach();
			bankButton = null;
		}
		if (geButton != null)
		{
			geButton.detach();
			geButton = null;
		}
		if (bankTab != null)
		{
			bankTab.removeAdded();
			bankTab = null;
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
		// Cached every tick rather than read live from grandExchangeAvailable
		// -- see lastKnownPlayerLocation's own doc for why a Swing caller
		// (the panel's Items tab) must never read Client state directly.
		accountType = client.getAccountType();

		// Unconditional, unlike the refresh below: this is about whether a
		// shop interface is open right now, which has nothing to do with
		// whether the account's own state changed this tick.
		checkShopArrival();

		// Same reasoning: the player's position moves every tick regardless
		// of whether the account's own state changed, and questStartArrival
		// has to keep tracking it continuously -- see its class doc for why
		// this is safe to call unconditionally, unlike AutoAdvancePlanner.
		checkQuestStartArrival();

		// And the same reasoning again, which the shop approach was written
		// without. It lived inside refresh(), which runs only when the
		// account's own state changed -- and walking towards a shop changes
		// nothing about the account. So proximity was never tested while the
		// player moved: it was tested when something else caused a refresh,
		// which in practice meant opening the shop. The reminder to go
		// somewhere arrived once they were already standing there.
		checkShopApproach(overlaySnapshot == null ? null : overlaySnapshot.items());

		// Same reasoning as checkShopApproach: proximity to a diary task
		// changes every tick as the player walks, whether or not the
		// account's own state changed this tick, so this cannot live inside
		// the refreshPending-gated block below.
		checkDiaryApproach();

		if (!refreshPending)
		{
			return;
		}
		refreshPending = false;
		refresh();
	}

	/**
	 * Reads whether a shop is open and, if so, what it stocks, and hands
	 * both to {@link #shopArrival} -- see that class's doc for why this is
	 * "at a shop", not walking-distance proximity.
	 *
	 * <p>Checked on the game tick rather than {@code WidgetLoaded}
	 * deliberately: {@code WidgetLoaded} fires when the shop interface's
	 * widget tree is created, which is not guaranteed to be after the
	 * game's own script has finished populating it with the shop's actual
	 * stock, and this plugin has no way to run the real client and confirm
	 * the ordering either way -- see the plugin's own report on this
	 * feature. A game tick happens once the client has finished processing
	 * the tick's game logic, which is the conventional, lower-risk point to
	 * read widget content that a script populates.
	 *
	 * <p>Cost while no shop is open: one widget lookup that returns null.
	 * Cost while one is open: one widget lookup plus a scan of its own
	 * slots, bounded by a shop's own size (never more than a few dozen) --
	 * not the 218-quest or 577-item scale the rest of this plugin is
	 * careful about, and only paid while a shop interface is actually on
	 * screen.
	 */
	private void checkShopArrival()
	{
		Widget itemsWidget = client.getWidget(InterfaceID.Shopmain.ITEMS);
		boolean shopOpenNow = itemsWidget != null;

		if (shopOpenNow)
		{
			// A real "this leg of the trip is done" signal, ahead of
			// ShopRouteGuard#ACTIVE_WINDOW's own safety-net expiry -- see
			// that class's doc. Whichever shop it is: reaching any shop
			// interface at all is as much confirmation as this plugin can
			// read that the player is no longer walking a Shortest Path
			// route they sent, so automatic Quest Helper starts no longer
			// need to be held back for it.
			shopRouteSentAt = null;
			// The trip itself is a narrower question, and "any shop at all"
			// is the wrong test for it. A stop that is a monster is not
			// reached by opening a shop counter, so a player who stops at a
			// general store on the way to the tortoises has not finished
			// anything -- ending the trip there would stand the overlay down
			// mid-grind, which is the reported bug wearing a second hat.
			// Ended only when the stop actually being walked to is a shop.
			if (currentStopIsAShop())
			{
				shopTripActive = false;
			}
			rememberShopkeeper();
			// The reminder is spent once the counter is open. Leaving it up
			// behind the interface is a line telling you to go somewhere you
			// are already standing.
			floatingNotes.clear();
		}

	}

	/**
	 * Hands the player's current position and the precomputed {@link
	 * #questStartCandidates} to {@link #questStartArrival} -- see that
	 * class's doc for why this is safe and cheap every tick even though it
	 * now checks more than one quest.
	 *
	 * <p>Cost: one field read for the candidate map (already computed by the
	 * last {@link #refresh}) and one distance comparison per candidate.
	 * Nothing here classifies a quest or scans the graph -- that happened
	 * once, in {@link QuestStartCandidates#of}, the last time an
	 * account-state change ran {@link #refresh}. {@link
	 * QuestHelperBridge#hasSelectedQuest()} only runs when {@code arrived} is
	 * non-empty -- almost never -- so the reflective read it costs is not a
	 * per-tick expense either.
	 */
	private void checkQuestStartArrival()
	{
		// Through playerLocation() rather than the client directly, so the
		// cached copy the panel reads is refreshed every tick -- see
		// lastKnownPlayerLocation for why the panel cannot ask for itself.
		WorldPoint player = playerLocation();

		// Always observed, whatever the toggle says -- same discipline as
		// autoAdvance in refresh(): the watcher has to keep tracking the
		// player's real position even while this feature is off, or turning
		// it on mid-session would see a stale baseline and could fire the
		// moment it is switched on for wherever the player already happens
		// to be standing.
		List<String> arrived = questStartArrival.observe(questStartCandidates, player);
		if (!config.questHelperArrivalPrompt() || arrived.isEmpty())
		{
			return;
		}
		// Never hijack a quest already in progress on Quest Helper's own
		// screen, nor one the account is already working on the route --
		// see QuestStartArrivalGate's class doc for why both signals are
		// checked, and why neither one alone is the account-level
		// IN_PROGRESS check that was rejected outright.
		for (String questName : QuestStartArrivalGate.allow(
			arrived, questHelperBridge.hasSelectedQuest(), routeQuestInProgress))
		{
			handleQuestStartArrival(questName);
		}
	}

	/**
	 * Every quest worth checking the player's distance to this tick -- see
	 * {@link #questStartCandidates}'s own doc and {@link
	 * QuestStartCandidates}'s class doc for what is and is not included, and
	 * why that is the whole cap.
	 *
	 * @param route the account's saved route, or null when none is saved or
	 *     the client has not loaded quest states yet
	 * @param verdicts this refresh's judgement of every quest in the graph,
	 *     or null when the account is not known yet (no rules received, no
	 *     snapshot read)
	 */
	private Map<String, WorldPoint> questStartCandidates(NextQuestStatus route, QuestVerdicts verdicts)
	{
		return QuestStartCandidates.of(questGraph, route, verdicts, inProgressQuestNames(),
			config.questStartArrivalDepth());
	}

	/**
	 * Whether any quest on route is currently {@code IN_PROGRESS}, per the
	 * client's own quest state -- see {@link #routeQuestInProgress}'s own
	 * field doc and {@link QuestStartArrivalGate}'s class doc for why the
	 * route, not the whole account, is what {@link #inProgressQuestNames()}
	 * is intersected against.
	 *
	 * <p>Checked against every outstanding route step, not only the head:
	 * the account may have started a quest further down the route than
	 * {@code route.questName()} names, the same reason {@link
	 * QuestStartCandidates} itself walks {@code route.upcoming()} rather
	 * than stopping at the first entry.
	 *
	 * @param route the account's saved route, or null when none is saved or
	 *     the client has not loaded quest states yet -- answers false, the
	 *     same "nothing to gate on" answer an empty route would give
	 */
	private boolean routeQuestInProgress(NextQuestStatus route)
	{
		if (route == null)
		{
			return false;
		}
		Set<String> inProgress = inProgressQuestNames();
		for (NextQuestStatus.Step step : route.upcoming())
		{
			if (inProgress.contains(step.questName()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the stop currently being walked to is somewhere to buy from
	 * rather than somewhere to kill something.
	 *
	 * <p>Answered from the same {@link ShopRouteStops#tour} the overlay draws
	 * and {@link ShopRouteAdvance} now routes by, so all three agree about
	 * which stop is live. True when there is no trip to speak of, because the
	 * caller's question is "may I end the trip here" and there is nothing to
	 * protect: an unknown stop must not keep a trip alive forever.
	 */
	private boolean currentStopIsAShop()
	{
		OverlaySnapshot snapshot = overlaySnapshot;
		QuestItems items = snapshot == null ? null : snapshot.items();
		if (items == null)
		{
			return true;
		}
		for (ShopRouteStops.Stop stop : ShopRouteStops.tour(items.totals(), shopItemSkips,
			this::shopLocation, this::dropLocation, playerLocation()))
		{
			if (!stop.current())
			{
				continue;
			}
			ShopRouteStops.Destination destination = ShopRouteStops.destinationOf(
				stop.total(), this::shopLocation, this::dropLocation, playerLocation());
			return destination == null || !destination.monster();
		}
		return true;
	}

	/**
	 * Records that the shop-route button just sent a route -- see {@link
	 * ShopRouteGuard}'s class doc for why this plugin tracks that at all.
	 * Handed to the panel as {@code this::onShopRouteSent}; called only for
	 * {@link ShortestPathBridge.RouteOutcome#SENT}, never for any other
	 * outcome.
	 */
	private void onShopRouteSent()
	{
		shopRouteSentAt = Instant.now();
		shopTripActive = true;
	}

	/**
	 * Calls off a shop trip: clears the line on the ground and stands the
	 * shop-route overlay back down.
	 *
	 * <p>There was no way to stop one once started. It expired on its own
	 * after ShopRouteGuard's window or when a shop interface opened, neither
	 * of which is "I have changed my mind", and in the meantime it went on
	 * holding back automatic Quest Helper starts.
	 */
	private void cancelShopRoute()
	{
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}
		shopRouteSentAt = null;
		shopTripActive = false;
	}

	/**
	 * Whether a shop route sent to Shortest Path is still active enough that
	 * an automatic Quest Helper start should hold off -- see {@link
	 * ShopRouteGuard}'s class doc for the report this closes and why holding
	 * off, rather than starting anyway, is the chosen side of that trade.
	 */
	private boolean shopRouteBlocksQuestRouting()
	{
		return ShopRouteGuard.blocksQuestRouting(shopRouteSentAt, Instant.now());
	}

	/**
	 * Whether a shop route sent to Shortest Path is still active enough for
	 * {@link ShopRouteOverlay} to have something to say -- the identical
	 * window {@link #shopRouteBlocksQuestRouting} gates automatic Quest
	 * Helper starts on. One clock serves both: "still walking a shop trip" is
	 * exactly the state that should hold an unrelated quest-start prompt back
	 * and exactly the state this overlay exists to describe, so a second,
	 * independently tracked "is the overlay's trip still active" would only
	 * be free to drift from the one {@link ShopRouteGuard} already answers.
	 */
	boolean shopRouteActive()
	{
		return shopTripActive();
	}

	// ------------------------------------------------------------------
	// Locked gear: a saved loadout's max hit at live levels, independent of
	// whatever the account is currently wearing. See LockedGear's own class
	// doc for why the loadout is frozen and only the levels move.
	// ------------------------------------------------------------------

	/** The single config key the chosen loadout's name is persisted under, RSProfile-scoped like the loadouts themselves. */
	private static final String LOCKED_GEAR_SELECTED_KEY = "lockedGearSelected";

	/**
	 * Bundles every locked-gear callback the panel needs, built once in
	 * {@link #startUp()} alongside every other panel callback.
	 */
	private LockedGearActions lockedGearActions()
	{
		return new LockedGearActions(
			this::lockCurrentGear,
			this::selectLoadout,
			this::deleteLoadout,
			this::renameLoadout,
			(loadoutName, style, prayer) -> editLoadout(loadoutName, loadout -> loadout.withMelee(style, prayer)),
			(loadoutName, style, prayer) -> editLoadout(loadoutName, loadout -> loadout.withRanged(style, prayer)),
			(loadoutName, spell, prayer) -> editLoadout(loadoutName, loadout -> loadout.withMagic(spell, prayer)));
	}

	/**
	 * The RSProfile-scoped name of the loadout the Locked gear section
	 * should show, or null if none has ever been chosen (or no account is
	 * logged in). Read fresh from {@link ConfigManager} every time rather
	 * than cached on this plugin -- an RSProfile-scoped read already answers
	 * differently per account with no logout bookkeeping of its own needed,
	 * the same reasoning {@link LoadoutStore} itself relies on.
	 */
	private String selectedLoadoutName()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return null;
		}
		return configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, LOCKED_GEAR_SELECTED_KEY);
	}

	private void setSelectedLoadoutName(String name)
	{
		if (name == null)
		{
			configManager.unsetRSProfileConfiguration(QuestpathConfig.GROUP, LOCKED_GEAR_SELECTED_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, LOCKED_GEAR_SELECTED_KEY, name);
		}
	}

	/**
	 * Adapts {@code ItemManager.getItemStats(id, false)} into {@link
	 * LockedGear.ItemStatsView} without {@link LockedGear} ever seeing an
	 * {@code ItemManager} -- see runelite-api.md &sect;6-8. Client-thread
	 * only, like every {@code ItemManager} call in this plugin.
	 */
	private LockedGear.ItemStatsView itemStatsView(int itemId)
	{
		net.runelite.http.api.item.ItemStats stats = itemManager.getItemStats(itemId, false);
		if (stats == null || !stats.isEquipable())
		{
			return UNKNOWN_ITEM_STATS;
		}
		net.runelite.http.api.item.ItemEquipmentStats equipment = stats.getEquipment();
		if (equipment == null)
		{
			return UNKNOWN_ITEM_STATS;
		}
		return new LockedGear.ItemStatsView()
		{
			@Override
			public boolean known()
			{
				return true;
			}

			@Override
			public int str()
			{
				return equipment.getStr();
			}

			@Override
			public int rstr()
			{
				return equipment.getRstr();
			}

			@Override
			public int mdmg()
			{
				return equipment.getMdmg();
			}
		};
	}

	/** Shared, since every field on it is a constant -- no need for a fresh instance per unknown item. */
	private static final LockedGear.ItemStatsView UNKNOWN_ITEM_STATS = new LockedGear.ItemStatsView()
	{
		@Override
		public boolean known()
		{
			return false;
		}

		@Override
		public int str()
		{
			return 0;
		}

		@Override
		public int rstr()
		{
			return 0;
		}

		@Override
		public int mdmg()
		{
			return 0;
		}
	};

	/**
	 * "Lock current gear": snapshots whatever is currently worn under {@code
	 * name} and saves it, replacing any existing loadout of the same name.
	 * The panel has already confirmed an overwrite on the EDT before this
	 * runs -- see {@link LockedGearActions#lockCurrentGear}'s own doc.
	 *
	 * <p>Runs entirely on the client thread: {@link LoadoutCapture#fromWorn}
	 * needs {@code Client} and {@code ItemManager}, both client-thread-only.
	 * Called from the EDT (a button click), so {@code clientThread.invoke}
	 * queues this for the next tick rather than running it inline.
	 */
	private void lockCurrentGear(String name)
	{
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty())
		{
			panel.lockedGearActionFailed("Loadout name must not be blank.");
			return;
		}
		clientThread.invoke(() ->
		{
			Optional<Loadout> captured = LoadoutCapture.fromWorn(client, itemManager, trimmed);
			if (!captured.isPresent())
			{
				// Not logged in, or the worn-equipment container has not
				// loaded yet -- an honest failure, not a silent no-op.
				panel.lockedGearActionFailed("Could not read worn equipment.");
				return;
			}
			try
			{
				loadoutStore.save(captured.get());
			}
			catch (LoadoutStore.LoadoutStoreException e)
			{
				panel.lockedGearActionFailed(e.getMessage());
				return;
			}
			setSelectedLoadoutName(trimmed);
			refreshPending = true;
		});
	}

	/** Selects a different saved loadout to show. Config-only, like {@code buildRulesShowAll}'s own toggle -- no client-thread hop needed. */
	private void selectLoadout(String name)
	{
		setSelectedLoadoutName(name);
		refreshPending = true;
	}

	/**
	 * Deletes a saved loadout. The panel has already confirmed this on the
	 * EDT. If the deleted loadout was selected, its config key is left
	 * pointing at a name that no longer exists -- {@link
	 * LockedGear#resolve} already falls back to the first loadout by name
	 * whenever the selected name does not match one, which is exactly the
	 * right behaviour here too.
	 */
	private void deleteLoadout(String name)
	{
		try
		{
			loadoutStore.delete(name);
		}
		catch (LoadoutStore.LoadoutStoreException e)
		{
			panel.lockedGearActionFailed(e.getMessage());
			return;
		}
		refreshPending = true;
	}

	/**
	 * Renames a saved loadout. If the renamed loadout was the selected one,
	 * the selection is repointed at its new name -- otherwise it would stop
	 * matching and the section would silently jump to whichever loadout
	 * sorts first by name instead of staying on the one the player was just
	 * looking at.
	 */
	private void renameLoadout(String from, String to)
	{
		try
		{
			loadoutStore.rename(from, to);
		}
		catch (LoadoutStore.LoadoutStoreException | IllegalArgumentException e)
		{
			panel.lockedGearActionFailed(e.getMessage());
			return;
		}
		if (from != null && from.equals(selectedLoadoutName()))
		{
			setSelectedLoadoutName(to == null ? null : to.trim());
		}
		refreshPending = true;
	}

	/**
	 * Applies one edit (a style/prayer or spell/prayer change) to the named
	 * loadout and saves it. The loadout name always comes from the panel,
	 * which already knows exactly which loadout it is showing -- including
	 * before anything has ever been explicitly selected, the first-by-name
	 * fallback {@link LockedGear#resolve} applies on its own.
	 */
	private void editLoadout(String loadoutName, java.util.function.UnaryOperator<Loadout> edit)
	{
		if (loadoutName == null)
		{
			return;
		}
		Optional<Loadout> current;
		try
		{
			current = loadoutStore.byName(loadoutName);
		}
		catch (LoadoutStore.LoadoutStoreException e)
		{
			panel.lockedGearActionFailed(e.getMessage());
			return;
		}
		if (!current.isPresent())
		{
			return;
		}
		try
		{
			loadoutStore.save(edit.apply(current.get()));
		}
		catch (LoadoutStore.LoadoutStoreException e)
		{
			panel.lockedGearActionFailed(e.getMessage());
			return;
		}
		refreshPending = true;
	}

	/**
	 * Whether a shop trip is underway at all -- see {@link #shopTripActive}'s
	 * own doc for why this is not the same question as {@link
	 * #shopRouteBlocksQuestRouting}, and why answering both with one clock
	 * ended trips in the middle.
	 */
	boolean shopTripActive()
	{
		return shopTripActive;
	}

	/**
	 * Starts questName in Quest Helper on arrival, reusing {@link
	 * QuestHelperBridge}'s own availability check rather than calling {@link
	 * QuestHelperBridge#start} blind -- the same rule {@link
	 * #startNextOnAutoAdvance} and the panel's right-click menu both follow,
	 * so this can never attempt a handoff either of those would have refused.
	 */
	private void handleQuestStartArrival(String questName)
	{
		if (shopRouteBlocksQuestRouting())
		{
			// Said, not silently skipped -- see ShopRouteGuard's class doc:
			// a shop route the player deliberately sent must not be
			// silently abandoned, but the arrival itself must not vanish
			// with no trace either.
			chat("Quest Path: you're at the start of \"" + questName
				+ "\", but a shop route sent to Shortest Path is still active -- "
				+ "start it yourself in Quest Helper if you want it now.");
			return;
		}
		QuestHelperBridge.Availability availability = questHelperBridge.availability(questName);
		if (availability != QuestHelperBridge.Availability.AVAILABLE)
		{
			chat("Quest Path: you're at the start of \"" + questName
				+ "\", but it could not be opened in Quest Helper -- " + availability.menuLabel() + ".");
			return;
		}
		questHelperBridge.start(questName, outcome -> reportQuestStartArrivalOutcome(questName, outcome));
	}

	/**
	 * The one line the player sees for an arrival attempt. Silent automation
	 * is worse than none, so both a confirmed start and a failed one get
	 * said -- never only the good news. Mirrors {@link
	 * #reportAutoAdvanceOutcome}.
	 */
	private void reportQuestStartArrivalOutcome(String questName, QuestHelperBridge.StartOutcome outcome)
	{
		if (outcome == QuestHelperBridge.StartOutcome.CONFIRMED)
		{
			chat("Quest Path: you're at the start of \"" + questName + "\" -- opened it in Quest Helper.");
			return;
		}
		chat("Quest Path: you're at the start of \"" + questName
			+ "\", but it did not open in Quest Helper (" + outcome + "). Check the client log.");
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

		// Cleared only when the BANK container itself is what changed, never
		// merely because the bank has been seen at some point.
		//
		// The first version tested owned.bankSeen(), which is true for the
		// rest of the session once a bank has been opened -- so every
		// inventory change after that wiped the tally, and inventory changes
		// are constant. Collecting to bank would have gone back to un-buying
		// the item for anyone who had opened a bank, which is nearly
		// everyone. It survived its own tests because they call
		// forgetCollected() directly, and it survived the live trace only
		// because that run never opened a bank.
		//
		// Re-reading the bank is the one event that genuinely supersedes the
		// tally: at that moment the bank's own count includes whatever was
		// collected into it.
		net.runelite.api.ItemContainer changed = event.getItemContainer();
		if (changed != null && changed.getId() == net.runelite.api.gameval.InventoryID.BANK)
		{
			geOffers.forgetCollected();
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
			if (pendingGraphAnnouncement != null)
			{
				// The fetch in fetchLiveQuestGraph almost always resolves
				// before this first login, since it starts from startUp --
				// so this is usually where the one announcement the player
				// actually sees happens, not a rare edge case.
				chat(pendingGraphAnnouncement);
				pendingGraphAnnouncement = null;
			}
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
			partialRuns = Collections.emptyList();
			knownItems = Collections.emptyMap();
			// The next login may be a different account, with a different
			// diary verdict entirely -- see diaryPush's own doc.
			diaryPush = null;
			// Same reasoning: a cooldown recorded against this account's
			// nearby tasks has nothing to say about the next one's.
			diaryNudgedAt.clear();
			// The next login may be another account, whose bank is not this
			// one. Reporting the previous account's items would be worse than
			// reporting none.
			owned.clear();
			// Nor are the previous account's Grand Exchange slots this one's.
			// The client re-fires every slot on the next login, so this only
			// has to avoid answering with stale offers in between.
			geOffers.clear();
			completions.reset();
			// The next login may stand in front of a different shop, or none
			// at all -- the previous account's "just opened" state must not
			// carry over and suppress a genuine new opening.
			// The next login may be a different account on a different
			// route entirely; its first sighting of that route must not be
			// read as a completion of whatever this account's route last
			// pointed at.
			autoAdvance.reset();
			// Same reasoning: the next login may stand anywhere at all, on a
			// different account's route, and that first sighting must not be
			// read as an arrival.
			questStartArrival.reset();
			// The next login may be a different account entirely; this
			// account's route and eligible quests are not candidates for it.
			questStartCandidates = Collections.emptyMap();
			// Same reasoning: the next login's route, if any, has not been
			// read yet, so there is nothing to call "in progress" until the
			// first post-login refresh says otherwise.
			routeQuestInProgress = false;
			// The next login may stand at a different shop, or none, mid-way
			// through no trip of its own -- a route sent for this account
			// must not hold back that account's first genuine arrival.
			shopRouteSentAt = null;
			shopTripActive = false;
			// The next login's shopping list has nothing to do with this
			// account's -- a skip chosen against one account's items means
			// nothing for another account's, and could even hide a genuine
			// shortfall on it by coincidence of a shared item name.
			shopItemSkips.clear();
			// The next login may be a different account of a different type
			// entirely -- an ironman logging out and a main logging in on
			// the same client session must not keep seeing the previous
			// account's Grand Exchange availability until the first tick
			// re-reads it.
			accountType = null;
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
			accountSnapshot = null;
			// No account logged in to stand near anything as. Left non-empty
			// here, checkQuestStartArrival would keep comparing the previous
			// account's candidates against a player position that also reads
			// null -- harmless, but a stale set is still the wrong thing to
			// be holding onto.
			questStartCandidates = Collections.emptyMap();
			// No route has been read this pass, so nothing on it can be
			// called in progress.
			routeQuestInProgress = false;
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

		// Rebuilt here, once per account-state change, so the per-tick check
		// in checkQuestStartArrival never has to reclassify anything -- see
		// questStartCandidates' own doc.
		questStartCandidates = questStartCandidates(route, questVerdicts);
		// Same cadence, for the same reason -- see routeQuestInProgress's
		// own field doc and QuestStartArrivalGate's class doc for what this
		// gates.
		routeQuestInProgress = routeQuestInProgress(route);

		java.util.List<ItemCompletionWatcher.Completion> justCompleted = completions.observe(items);
		announce(justCompleted);
		advanceShopRoute(justCompleted, items);
		floatCompletions(justCompleted);

		if (route != null)
		{
			// Always observed, whether or not the toggle is on: the planner
			// has to keep tracking the route's real head even while this
			// feature is switched off, or turning it on mid-session would
			// see a stale head and misreport whatever finished while it was
			// off as a live completion. route == null (client not loaded
			// yet) is the one pass this must not see at all -- see
			// AutoAdvancePlanner#observe's doc.
			AutoAdvancePlanner.Decision decision = autoAdvance.observe(route);
			if (config.autoAdvanceQuestHelper())
			{
				handleAutoAdvance(decision);
			}
		}

		// Built here, on the client thread, exactly like every other read
		// this pass makes: LockedGear.resolve reads item stats through
		// itemStatsView, an ItemManager call that is client-thread-only.
		LockedGear lockedGear =
			LockedGear.resolve(loadoutStore.all(), selectedLoadoutName(), stats, this::itemStatsView);

		panel.update(stats, xp, syncStatus(), buildRules, unmatchedQuests,
			questVerdicts, items, route, routeWork(route), SyncSettings.from(config), report, lockedGear);

		// Keeps the Grand Exchange row in step with a shopping list that
		// changed while the search chatbox happened to already be open --
		// e.g. banking an item while shopping for something else. A cheap
		// no-op the rest of the time: attach() itself returns immediately
		// when that chatbox is not on screen.
		if (geButton != null)
		{
			geButton.attach();
		}
	}

	/**
	 * Acts on what {@link AutoAdvancePlanner} decided: starts the next route
	 * step in Quest Helper, or says why it could not, in one chat line
	 * either way. Never called more than once per transition -- {@link
	 * AutoAdvancePlanner#observe} already moved its own head forward before
	 * this runs, so there is nothing left here that could turn into a retry
	 * loop.
	 */
	private void handleAutoAdvance(AutoAdvancePlanner.Decision decision)
	{
		switch (decision.action())
		{
			case ADVANCE:
				startNextOnAutoAdvance(decision.finished(), decision.next());
				return;
			case ROUTE_FINISHED:
				chat("Quest Path: \"" + decision.finished()
					+ "\" finished -- that was the last quest on your route.");
				return;
			case NONE:
			default:
				// Nothing happened worth a line in the chat.
		}
	}

	/**
	 * Starts next in Quest Helper, reusing {@link QuestHelperBridge}'s own
	 * availability check rather than calling {@link QuestHelperBridge#start}
	 * blind -- the same rule the panel's right-click menu follows, so
	 * auto-advance can never attempt a handoff the manual path would have
	 * refused.
	 */
	private void startNextOnAutoAdvance(String finished, String next)
	{
		if (shopRouteBlocksQuestRouting())
		{
			// Same reasoning as handleQuestStartArrival's own guard -- see
			// ShopRouteGuard's class doc.
			chat("Quest Path: \"" + finished + "\" finished, and \"" + next
				+ "\" is next, but a shop route sent to Shortest Path is still active -- "
				+ "start it yourself in Quest Helper when you're done shopping.");
			return;
		}
		QuestHelperBridge.Availability availability = questHelperBridge.availability(next);
		if (availability != QuestHelperBridge.Availability.AVAILABLE)
		{
			chat("Quest Path: \"" + finished + "\" finished, but \"" + next
				+ "\" could not be started in Quest Helper -- " + availability.menuLabel() + ".");
			return;
		}
		questHelperBridge.start(next, outcome -> reportAutoAdvanceOutcome(finished, next, outcome));
	}

	/**
	 * The one line the player sees for an auto-advance attempt. Silent
	 * automation is worse than none, so both a confirmed start and a failed
	 * one get said -- never only the good news.
	 */
	private void reportAutoAdvanceOutcome(String finished, String next, QuestHelperBridge.StartOutcome outcome)
	{
		if (outcome == QuestHelperBridge.StartOutcome.CONFIRMED)
		{
			chat("Quest Path: \"" + finished + "\" finished -- started \"" + next + "\" in Quest Helper.");
			return;
		}
		chat("Quest Path: \"" + finished + "\" finished, but \"" + next
			+ "\" did not start in Quest Helper (" + outcome + "). Check the client log.");
	}

	/** One plain line in the game chat, questpath's own message colour. */
	private void chat(String message)
	{
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append(message);
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(line.build())
			.build());
	}

	/**
	 * Prints one chat line per item just completed.
	 *
	 * The toggle is read here rather than inside the watcher on purpose: the
	 * watcher keeps observing either way, so switching the setting on does not
	 * then announce a backlog of everything collected while it was off.
	 */

	/**
	 * Where the player is, or null before the client has anyone to ask
	 * about. Handed to {@link ShopRouteOverlay} so it can name the nearest
	 * of several shops selling the same item rather than whichever the
	 * bundle happened to list first.
	 */

	/**
	 * Floats a line for each item just finished -- see {@link FloatingNotes}
	 * for why the chat line alone was not enough.
	 *
	 * <p>Where it lands is {@link NotePlacement}'s call, and above the
	 * shopkeeper is the default because that is what the feature is for. The
	 * fallback matters as much as the placement: an item finished by chopping
	 * a tree has no NPC in the picture, and a note that simply failed to
	 * appear at those moments would read as the feature being broken.
	 */
	private void floatCompletions(java.util.List<ItemCompletionWatcher.Completion> completed)
	{
		if (completed.isEmpty() || config == null || !config.floatingNotes())
		{
			return;
		}
		Instant now = Instant.now();
		WorldPoint you = playerLocation();
		WorldPoint shopkeeperAt = shopkeeper.location(now);
		NotePlacement placement = config.floatingNotePlacement();

		for (ItemCompletionWatcher.Completion done : completed)
		{
			String praise = FloatingNotes.praiseFor(done.itemName());
			if (placement.overShopkeeper(shopkeeperAt != null))
			{
				floatingNotes.add(praise, shopkeeperAt, now, FloatingNotes.LIFETIME, done.itemName());
			}
			if (placement.overPlayer(shopkeeperAt != null))
			{
				floatingNotes.add(praise, you, now, FloatingNotes.LIFETIME, done.itemName());
			}
		}
	}


	/**
	 * Remembers whoever this shop belongs to, so a note can be put over them
	 * -- see {@link ShopkeeperMemory} for why by tile rather than by
	 * reference.
	 *
	 * <p>Read from what the player is interacting with rather than from the
	 * nearest NPC: opening a shop means talking to its owner, and "nearest"
	 * in a busy bank would pick somebody at random.
	 */
	private void rememberShopkeeper()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		Actor talkingTo = client.getLocalPlayer().getInteracting();
		if (talkingTo instanceof NPC)
		{
			shopkeeper.remember(talkingTo.getName(), talkingTo.getWorldLocation(), Instant.now());
		}
	}


	private WorldPoint playerLocation()
	{
		WorldPoint here = client.getLocalPlayer() == null
			? null
			: client.getLocalPlayer().getWorldLocation();
		lastPlayerLocation = here;
		return here;
	}

	/**
	 * The last position read on the client thread, for callers that are not
	 * on it.
	 *
	 * <p>{@code client.getLocalPlayer()} asserts it is on the client thread,
	 * and that assertion is an {@link AssertionError} rather than an
	 * exception -- so a Swing caller does not get a null or a wrong answer,
	 * it gets an Error thrown through whatever was rendering. Handing the
	 * panel {@code this::playerLocation} did exactly that: the shop-route
	 * button asked where the player was while rendering on the EDT, threw,
	 * and took the entire item list off the screen with it. Nothing was
	 * broken about the list; it simply never finished being drawn.
	 *
	 * <p>This is the same hazard {@link ShortestPathBridge}'s class doc
	 * documents from the other direction, where Shortest Path's own handler
	 * asserts the same thing about a call coming out of a button.
	 *
	 * <p>A tick-old position is the right answer for what reads this. The
	 * shop tour is deciding which of several shops across the map to walk to
	 * first, and no ordering that direction changes because the player moved
	 * one tile since the last tick.
	 */
	WorldPoint lastKnownPlayerLocation()
	{
		return lastPlayerLocation;
	}

	/**
	 * Whether this account has Grand Exchange access at all -- the gate
	 * {@link QuestpathPanel} uses to decide whether its Grand Exchange list
	 * appears, automatically and with no config item of its own: see
	 * {@link GrandExchangeAccounts}'s own doc for why the client's own
	 * account type is the right source, and this project's rule against
	 * ever suggesting something an account cannot act on for why an
	 * ironman must never see this section, toggle or not.
	 *
	 * <p>Reads the cached {@link #accountType} rather than the client
	 * directly, the same reasoning {@link #lastKnownPlayerLocation} documents
	 * at length: {@code Client#getAccountType()} is exactly the kind of call
	 * that must not be made from the panel's own Swing thread.
	 */
	boolean grandExchangeAvailable()
	{
		return GrandExchangeAccounts.canUseGrandExchange(accountType);
	}


	/**
	 * The account's whole Grand Exchange buy list, each line marked with the
	 * live offer already covering it -- {@link GrandExchangeOverlay}'s own
	 * supplier, and the list {@link #nextGrandExchangeSuggestion} picks from.
	 *
	 * <p>Null, not empty, when there is nothing to answer with at all. An
	 * empty list means "nothing left to buy", which is a real and good
	 * answer, and the two must not render the same -- this project's
	 * standing rule that a missing answer never gets to look like a
	 * satisfied one.
	 */
	java.util.List<GrandExchangeShoppingList.Line> grandExchangeList()
	{
		if (!grandExchangeAvailable())
		{
			return null;
		}
		OverlaySnapshot snapshot = overlaySnapshot;
		if (snapshot == null || snapshot.items() == null)
		{
			return null;
		}
		return GrandExchangeShoppingList.of(snapshot.items().totals(), geOffers, this::tradeability);
	}

	/**
	 * Whether the Grand Exchange trades an item at all, for {@link
	 * GrandExchangeShoppingList}'s own buy list.
	 *
	 * <p><b>Karamjan rum is the case this is here for.</b> The shopping list
	 * is built from every outstanding quest item, and a great many of those
	 * are never sold at a Grand Exchange -- they are found, stolen, or
	 * handed to you mid-quest. Offering one as the next thing to buy breaks
	 * this project's oldest rule: never suggest something the account cannot
	 * act on.
	 *
	 * <p>{@code isGeTradeable()} is the authority rather than a list of
	 * exceptions questpath would have to maintain -- it is the same flag
	 * RuneLite's own bundled Grand Exchange plugin filters its search
	 * results on, so an item this reports as tradeable is exactly an item
	 * that box can find.
	 *
	 * <p>Runs on the client thread only, which both callers already are:
	 * {@link GrandExchangeOverlay} renders there, and {@link
	 * #nextGrandExchangeSuggestion} is read from a widget callback.
	 */
	private GrandExchangeShoppingList.Availability tradeability(int itemId, String name)
	{
		if (itemId <= 0)
		{
			// No id to ask about. Overwhelmingly these are requirements the
			// wiki states as prose rather than named items -- see
			// GrandExchangeShoppingList.Availability#UNKNOWN.
			return GrandExchangeShoppingList.Availability.UNKNOWN;
		}
		ItemComposition item = itemManager.getItemComposition(itemId);
		if (item == null)
		{
			return GrandExchangeShoppingList.Availability.UNKNOWN;
		}
		return item.isGeTradeable()
			? GrandExchangeShoppingList.Availability.BUYABLE
			: GrandExchangeShoppingList.Availability.NOT_ON_GE;
	}

	/**
	 * Moves a running shop trip on to its next stop when the current item is
	 * finished -- see {@link ShopRouteAdvance}'s class doc for the trip that
	 * silently stopped being a trip without this.
	 *
	 * <p>Gated on the same toggle as the completion chat line rather than a
	 * toggle of its own. Both answer one question -- "tell me when an item
	 * is done" -- and a player who has switched that off is not asking for a
	 * route to move under them either.
	 */
	private void advanceShopRoute(java.util.List<ItemCompletionWatcher.Completion> completed,
		QuestItems items)
	{
		if (config == null || !config.chatOnItemComplete() || items == null
			|| shortestPathBridge == null)
		{
			return;
		}

		ShopRouteAdvance.Decision decision = ShopRouteAdvance.observe(shopTripActive(), completed,
			items.totals(), shopItemSkips, this::shopLocation, this::dropLocation, playerLocation());

		if (decision.action() == ShopRouteAdvance.Action.FINISHED)
		{
			// Ends the trip rather than leaving a route pointing at the shop
			// just bought from. Clearing this is also what stands
			// ShopRouteOverlay down and brings NeededItemsOverlay back --
			// see ShopRouteOverlay#isShowing.
			shopRouteSentAt = null;
			shopTripActive = false;
			shopTripChat("Quest Path: shopping done -- nothing left on this trip.");
			return;
		}

		if (decision.action() != ShopRouteAdvance.Action.REROUTE)
		{
			return;
		}

		shortestPathBridge.route(decision.destinations(), outcome ->
		{
			if (outcome != ShortestPathBridge.RouteOutcome.SENT)
			{
				// Said out loud rather than swallowed. A trip that stops
				// advancing looks exactly like one that finished, and the
				// player is standing in a shop either way with no way to
				// tell the difference.
				shopTripChat("Quest Path: could not move on to the next stop.");
				return;
			}
			shopRouteSentAt = Instant.now();
			shopTripActive = true;
			StringBuilder line = new StringBuilder("Quest Path: next up ")
				.append(decision.nextItem());
			if (!decision.nextShop().isEmpty())
			{
				line.append(" at ").append(decision.nextShop());
			}
			line.append(" -- ").append(decision.remaining()).append(" stop")
				.append(decision.remaining() == 1 ? "" : "s").append(" left.");
			shopTripChat(line.toString());
		});
	}

	/** One plain chat line about a shop trip. */
	private void shopTripChat(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append(text)
				.build())
			.build());
	}

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


	/** Whether the bank marks and list are currently switched on. */
	boolean bankNeedsShown()
	{
		return bankNeedsShown;
	}

	private void toggleBankNeeds()
	{
		setBankTab(!bankNeedsShown);
	}

	/**
	 * Selects or leaves questpath's bank tab.
	 *
	 * <p>Turning it on deselects whichever real tab was open, the same way
	 * clicking a real tab deselects the last one. Without that the bank still
	 * believes tab nine is showing -- it keeps tab nine's title, and its next
	 * rebuild puts tab nine's items back underneath.
	 */
	private void setBankTab(boolean on)
	{
		if (bankNeedsShown == on)
		{
			return;
		}
		bankNeedsShown = on;
		if (bankTab == null)
		{
			return;
		}
		bankTab.removeAdded();
		clientThread.invokeLater(() ->
		{
			if (on)
			{
				client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
			}
			// Re-run the client's own build. This class hooks the end of it,
			// so re-running is how the grid moves between questpath's order
			// and the bank's own.
			client.runScript(ScriptID.BANKMAIN_BUILD);
		});
	}

	/**
	 * Steps aside the moment a real tab is chosen.
	 *
	 * <p>questpath's layout is one tab among the bank's own, so picking any of
	 * the others has to mean leaving this one -- otherwise the tab strip stops
	 * working while the feature is on, which is a far worse bug than the
	 * feature is a good one.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!bankNeedsShown)
		{
			return;
		}
		String option = event.getMenuOption();
		if (option == null)
		{
			return;
		}
		String target = event.getMenuTarget();
		if (target != null && target.contains("Quest Path"))
		{
			// Our own button. It carries a "View tab" option so it reads like
			// the bank's tabs, which means without this it would match the
			// rule below and close the tab it had just opened.
			return;
		}
		if (option.startsWith("View tab") || option.startsWith("View tag tab")
			|| option.equals("View all items") || option.startsWith("Potion store"))
		{
			setBankTab(false);
			if (bankButton != null)
			{
				bankButton.refresh();
			}
		}
	}

	/**
	 * Whether the bank grid is currently laid out by questpath.
	 *
	 * Read by BankNeedsOverlay so it stops outlining slots that are already
	 * grouped by the thing the outline was there to say.
	 */
	boolean bankTabActive()
	{
		return bankTab != null && bankTab.active();
	}

	/**
	 * Prices a target level for the Skills tab.
	 *
	 * <p>Null rather than an empty plan when the graph or the account is
	 * missing: an empty plan reads as "nothing to train", which is the
	 * opposite of "nobody has told me anything yet". The panel renders the
	 * two differently and has to be able to tell them apart.
	 *
	 * <p>Runs on the event thread, from the panel. Everything it touches --
	 * the bundled graph, the account snapshot -- is already read, so it does
	 * no client work; the classifier memoises within one call.
	 */
	private SkillPlan skillPlan(String skill, int targetLevel)
	{
		PlayerState account = accountSnapshot;
		if (questGraph == null || account == null || skill == null)
		{
			return null;
		}
		return SkillPlan.of(questGraph, account,
			new QuestClassifier(questGraph, account), skill, targetLevel);
	}

	/**
	 * Where to walk for a shop named by an item's {@code shopNames()}, or
	 * null when the bundled graph carries no coordinate for it -- see
	 * {@link QuestGraph.Shop}'s own doc for why every name that does resolve
	 * is a genuine, walkable destination.
	 *
	 * <p>Handed to the panel as a callback, the same shape {@link #skillPlan}
	 * is: the panel has no graph of its own and should not learn to hold
	 * one, and this is the one question it needs answered from it.
	 */

	/**
	 * Where a monster that drops something can be found, or null when the
	 * bundle carries nowhere for it.
	 *
	 * <p>Answers "for something like Bat bones, if there is no shop, it
	 * should route me to the nearest bats". Only consulted once no shop can
	 * supply the item -- a shop is repeatable and a drop is a grind, so a
	 * shop wins whenever there is one.
	 *
	 * <p>Wilderness locations are not filtered out here even though this is a
	 * hardcore account, because that is a decision for whoever is reading the
	 * route rather than one to make silently: a dropper that only appears in
	 * the Wilderness would otherwise vanish with no explanation, which is the
	 * same quiet omission this whole feature exists to stop. The region is
	 * carried on the row for a caller that wants to say so.
	 */

	/**
	 * Nudges the player towards a shop stocking something they still need, as
	 * they come up on it.
	 *
	 * <p>Replaces announcing when the shop interface opened, which was the
	 * wrong moment twice over -- see {@link ShopApproachWatcher}. Standing in
	 * the shop being told the shop sells things is not a reminder, and the
	 * widget-based trigger fired the same line five times in two minutes
	 * while the player was fishing.
	 *
	 * <p>Only shops this account can actually trade at are candidates,
	 * because {@link #shopLocation} is the one place a shop name becomes a
	 * tile and it already filters -- so a guild nine Fishing levels away
	 * cannot shout about its stock.
	 */
	private void checkShopApproach(QuestItems items)
	{
		if (items == null || questGraph == null)
		{
			return;
		}
		// Its own toggle rather than the item-done one. They are different
		// events -- "you finished something" against "there is one for sale
		// right there" -- and the second is the one worth walking on.
		boolean wantChat = config != null && config.shopNearbyPrompt();
		boolean wantNote = config != null && config.shopNearbyPrompt()
			&& config.floatingNotes()
			&& config.floatingNotePlacement() != NotePlacement.ABOVE_YOU;
		if (!wantChat && !wantNote)
		{
			// Still not observed while both are off: the watcher's baseline
			// would go stale and switching one on mid-session would announce
			// every shop already in range at once.
			shopApproach.clear();
			return;
		}

		Map<String, WorldPoint> nearby = new HashMap<>();
		Map<String, QuestItems.Total> wantedAt = new HashMap<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.satisfied() || shopItemSkips.isSkipped(total.name()))
			{
				continue;
			}
			for (String shopName : total.shopNames())
			{
				WorldPoint at = shopLocation(shopName);
				if (at != null)
				{
					nearby.put(shopName, at);
					wantedAt.putIfAbsent(shopName, total);
				}
			}
		}

		for (String shopName : shopApproach.observe(nearby, playerLocation()))
		{
			QuestItems.Total total = wantedAt.get(shopName);
			if (total == null)
			{
				continue;
			}
			String offer = FloatingNotes.offerFor(total.name(), total.outstanding());
			if (wantChat)
			{
				// Highlighted rather than flat, and the shop's name is the
				// highlighted half: the offer text says what it wants, the
				// name says where to walk, and only one of those is worth
				// finding again after the line has scrolled.
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Quest Path: ")
						.append(ChatColorType.HIGHLIGHT)
						.append(shopName)
						.append(ChatColorType.NORMAL)
						.append(" -- " + offer)
						.build())
					.build());
			}
			if (wantNote)
			{
				// Over the shop, which is where the shopkeeper stands. At
				// this distance there is no NPC to read off the player's
				// interaction, and the tile is the honest answer rather than
				// a guess at which of the figures ahead is the one selling.
				// Held far longer than a completion note. That one confirms
				// something already done and can fade; this one is asking you
				// to change where you are walking, and four seconds is not
				// long enough to read a name, find the NPC and decide.
				floatingNotes.add(offer, nearby.get(shopName), Instant.now(),
					FloatingNotes.OFFER_LIFETIME, total.name());
			}
		}
	}


	/**
	 * Notices when the nearest actionable diary task is close enough, and
	 * this is not too soon after the last time this exact task was
	 * mentioned, to say so in chat -- {@link DiaryRouteGuard#shouldNudge}'s
	 * own doc has the four conditions in full.
	 *
	 * <p>Checked every tick, unconditionally, the same reasoning {@link
	 * #checkShopApproach} documents at length: proximity to a diary task
	 * changes as the player walks regardless of whether the account's own
	 * state changed, so this cannot wait for {@link #refreshPending}.
	 *
	 * <p>Shares {@link QuestpathConfig#overlayDiaryRoute()} with {@link
	 * DiaryRouteOverlay} rather than carrying a second toggle of its own --
	 * see that config item's own doc for why one switch governs both the
	 * always-on display and this occasional chat line.
	 */
	private void checkDiaryApproach()
	{
		if (config == null || !config.overlayDiaryRoute() || diaryPush == null || questGraph == null)
		{
			return;
		}

		List<DiaryRouteStops.Stop> stops =
			DiaryRouteStops.stops(questGraph.diaryTasks(), diaryPush, playerLocation());
		if (stops.isEmpty())
		{
			return;
		}

		DiaryRouteStops.Stop nearest = stops.get(0);
		DiaryRouteStops.TaskKey key =
			new DiaryRouteStops.TaskKey(nearest.task().tierId(), nearest.task().ordinal());
		Instant now = Instant.now();

		boolean nudge = DiaryRouteGuard.shouldNudge(config.overlayDiaryRoute(), nearest.distanceTiles(),
			shopRouteBlocksQuestRouting(), routeQuestInProgress, diaryNudgedAt.get(key), now);
		if (!nudge)
		{
			return;
		}

		diaryNudgedAt.put(key, now);
		chat("Quest Path: " + nearest.task().diary() + " " + nearest.task().tier()
			+ " nearby -- " + nearest.task().text());
	}

	/**
	 * The region a dropper lives in -- "general", "wilderness" -- or empty
	 * when the bundle carries none for it.
	 *
	 * <p>Kept beside {@link #dropLocation} because they read the same table
	 * and must agree about which row a name resolves to.
	 */
	private String dropRegion(String sourceName)
	{
		if (questGraph == null || sourceName == null)
		{
			return "";
		}
		if (dropRegionsByName == null)
		{
			Map<String, String> index = new HashMap<>();
			for (QuestGraph.DropLocation drop : questGraph.dropLocations())
			{
				index.putIfAbsent(drop.sourceName().toLowerCase(), drop.region());
			}
			dropRegionsByName = index;
		}
		return dropRegionsByName.getOrDefault(sourceName.toLowerCase(), "");
	}

	/**
	 * A region worth naming beside a destination, or empty. Handed to the
	 * overlay so a warned region is said out loud rather than walked into.
	 */
	String dropRegionNote(String sourceName)
	{
		return DropRegionPolicy.noteFor(dropRegion(sourceName), constraints);
	}

	private WorldPoint dropLocation(String sourceName)
	{
		if (questGraph == null || sourceName == null)
		{
			return null;
		}
		if (DropRegionPolicy.of(dropRegion(sourceName), constraints)
			== DropRegionPolicy.Verdict.BLOCKED)
		{
			// Declared off-limits outright. Filtered in the same place shop
			// reachability is filtered, so a route, a label and a stop cannot
			// disagree about whether somewhere is on the table.
			return null;
		}
		if (dropsByName == null)
		{
			Map<String, WorldPoint> index = new HashMap<>();
			for (QuestGraph.DropLocation drop : questGraph.dropLocations())
			{
				// First wins: the bundle lists a monster once per place it
				// appears, and they are in the order the wiki gave.
				index.putIfAbsent(drop.sourceName().toLowerCase(),
					new WorldPoint(drop.x(), drop.y(), drop.plane()));
			}
			dropsByName = index;
		}
		return dropsByName.get(sourceName.toLowerCase());
	}

	/**
	 * Every achievement diary task the current graph carries, account-agnostic
	 * -- handed to {@link DiaryRouteStops} and {@link DiaryRouteOverlay} as a
	 * supplier so both always read whichever graph ({@link #questGraph}) is
	 * live at the moment they are called, bundled or freshly fetched, rather
	 * than a copy taken once at construction time.
	 */
	List<QuestGraph.DiaryTask> diaryGraphTasks()
	{
		return questGraph == null ? Collections.emptyList() : questGraph.diaryTasks();
	}

	/**
	 * The account's per-account diary routing data, as questpath last
	 * reported it, or null when there is none -- see {@link #diaryPush}'s own
	 * doc.
	 */
	PushResponse.Diary diaryPush()
	{
		return diaryPush;
	}

	private WorldPoint shopLocation(String shopName)
	{
		if (questGraph == null || shopName == null)
		{
			return null;
		}
		QuestGraph.Shop shop = questGraph.shop(shopName);
		if (shop == null)
		{
			return null;
		}
		// A shop this account cannot be served at is not a destination, and
		// this is the one place every route, label and stop resolves a shop
		// name -- so filtering here is what keeps them from disagreeing.
		// See ShopReachability for the report: the Fishing Guild Shop was
		// named for a big fishing net behind 68 Fishing while a Catherby shop
		// selling the same net, that the account could walk into, went
		// unmentioned.
		//
		// Boostable counts as reachable. Walking somewhere with a fishing
		// potion is a real plan, and refusing to route there would hide a
		// shop the account can genuinely use.
		if (!ShopReachability.of(shop, accountSnapshot, questGraph, shopBoosts()).usable())
		{
			return null;
		}
		return new WorldPoint(shop.x(), shop.y(), shop.plane());
	}

	/**
	 * The boost table, for sizing a boostable entry requirement.
	 *
	 * Built from the same graph the quest classifier uses, so the two cannot
	 * disagree about whether a potion closes a gap.
	 */
	private Boosts shopBoosts()
	{
		if (questGraph == null)
		{
			return null;
		}
		if (cachedShopBoosts == null)
		{
			cachedShopBoosts = new Boosts(questGraph.boosts());
		}
		return cachedShopBoosts;
	}

	/** The overlays and the bank layout read config through here. */
	QuestpathConfig config()
	{
		return config;
	}

	/**
	 * Answers a search questpath's own button asked for, by handing the
	 * client the buy list's item ids instead of whatever it would have found.
	 *
	 * <p><b>Nothing is drawn.</b> The client renders those ids in its own
	 * result list, with its own widgets, its own scrolling and its own
	 * "Select &lt;item&gt;" option on every row -- which is why this is worth
	 * far more than the hand-drawn row it replaced. See {@link
	 * GrandExchangeButton}'s class doc for where the whole sequence was
	 * disassembled from.
	 *
	 * <p><b>The sentinel check is what keeps this from hijacking a real
	 * search.</b> Only a search whose text is exactly {@link
	 * GrandExchangeButton#SEARCH_SENTINEL} is ours; anything the player
	 * actually typed falls straight through. Quest Helper does the same with
	 * its own string, so both buttons work in the same box.
	 *
	 * <p>Consumed even when the list comes back empty. Letting an empty buy
	 * list fall through would search the game for the word "questpath" and
	 * fill the panel with whatever fuzzy-matched it, which reads as a broken
	 * feature rather than as "nothing to buy".
	 */
	@Subscribe
	public void onGrandExchangeSearched(GrandExchangeSearched event)
	{
		if (geButton == null
			|| !GrandExchangeButton.SEARCH_SENTINEL.equals(client.getVarcStrValue(VarClientStr.INPUT_TEXT)))
		{
			return;
		}
		event.consume();
		short[] ids = geButton.resultIds();
		client.setGeSearchResultIndex(0);
		client.setGeSearchResultCount(ids.length);
		client.setGeSearchResultIds(ids);
	}

	/**
	 * Tracks what the account's eight Grand Exchange slots are buying, so the
	 * shopping list can move on the moment an offer is <em>placed</em> rather
	 * than when it is finally collected.
	 *
	 * <p>Collecting is the only moment {@link QuestItems} can see anything
	 * change, because it counts what the client says you hold and an offer
	 * sitting in a slot is not held. Without this the list went on naming an
	 * item that was already bought and waiting, and the player had to
	 * remember which slots they had spent -- which is the bookkeeping the
	 * list exists to do for them.
	 *
	 * <p>The client fires this for every slot at login, which is what seeds
	 * the state; nothing has to walk the slots itself.
	 *
	 * <p><b>The item's name is resolved here rather than inside {@link
	 * GrandExchangeOffers}.</b> {@code ItemManager#getItemComposition} must
	 * run on the client thread, which this event already is, and keeping the
	 * lookup out of that class leaves it pure state that a test can drive
	 * without a client at all. A null composition narrows matching to the
	 * item id rather than dropping the offer -- see {@link
	 * GrandExchangeOffers#update}.
	 */
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null)
		{
			return;
		}
		String name = null;
		if (offer.getItemId() > 0)
		{
			ItemComposition item = itemManager.getItemComposition(offer.getItemId());
			name = item == null ? null : item.getName();
		}
		geOffers.update(event.getSlot(), offer.getState(), offer.getItemId(), name,
			offer.getTotalQuantity(), offer.getQuantitySold());

		// If questpath's own list is on screen right now, the item just put
		// on offer has to leave it -- otherwise the one surface whose job is
		// to say what to buy next goes on naming something already bought.
		// A no-op unless that list is actually showing.
		if (geButton != null)
		{
			geButton.refresh();
		}
	}

	/**
	 * Rebuilds the bank grid once the client has finished its own build, and
	 * redraws questpath's Grand Exchange button after a search runs.
	 *
	 * BANKMAIN_FINISHBUILDING is the only point at which the item widgets both
	 * exist and are final; earlier and there is nothing to move, later and the
	 * client has already drawn a frame of its own layout.
	 *
	 * <p>GE_ITEM_SEARCH (752) is handled first and unconditionally -- it has
	 * nothing to do with the bank, and gating it behind {@code bankTab != null}
	 * the way the branches below are would mean questpath's own Grand
	 * Exchange button goes stale if the bank tab feature ever failed to
	 * initialise for an unrelated reason. {@code
	 * GrandExchangePlugin.onScriptPostFired} reacts to the same script id for
	 * the same reason: it is what runs after the search chatbox's own
	 * content changes, which is exactly when a row drawn inside it needs
	 * redrawing. See {@link GrandExchangeButton}'s class doc.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.GE_ITEM_SEARCH)
		{
			if (geButton != null)
			{
				geButton.attach();
			}
			return;
		}
		if (bankTab == null)
		{
			return;
		}
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			bankTab.onBankBuilt();
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE && bankNeedsShown)
		{
			// Searching is the bank's own view of itself, which this layout is
			// not. Stand down rather than fight it for the grid.
			setBankTab(false);
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING && bankTab.active())
		{
			// Tells the bank it is in a search state, which stops its own tab
			// logic rebuilding the grid underneath this layout. The return
			// value sits on the int stack.
			int[] stack = client.getIntStack();
			stack[client.getIntStackSize() - 1] = 1;
		}
	}

	/**
	 * Rebuilds the bank button each time the bank interface loads, and draws
	 * questpath's Grand Exchange suggestion row each time the item-search
	 * chatbox loads.
	 *
	 * The client discards each interface's widget tree when it closes, so a
	 * widget held from a previous open is stale rather than reusable.
	 * Attaching on load is the only point at which a parent exists to hang
	 * one from.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CHATBOX)
		{
			if (geButton != null)
			{
				geButton.attach();
			}
			return;
		}
		if (event.getGroupId() != InterfaceID.BANKMAIN || bankButton == null)
		{
			return;
		}
		bankButton.attach();
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
		maybeSync(false);
	}

	/**
	 * Pushes now, whatever the change gate thinks.
	 *
	 * <p>The automatic path only sends when the payload actually moved, which
	 * is right for a background sync and useless as a button: the whole reason
	 * to press one is that you changed something on the questpath side -- a
	 * build rule, the plan itself -- which changes nothing about the payload
	 * this end and so is invisible to that gate. Waiting for the next level-up
	 * to find out whether your new plan arrived is not an answer.
	 *
	 * <p>Still refuses when sync is off or nobody is logged in. Those are not
	 * gates to override, they are the absence of anything to send.
	 */
	void syncNow()
	{
		maybeSync(true);
	}

	private void maybeSync(boolean force)
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

			if (!force && !shouldSend(structural, experience))
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
						partialRuns = Collections.emptyList();
						knownItems = Collections.emptyMap();
						// A rejection means questpath is not confirming this
						// account's state, and a stale diary verdict shown
						// against the wrong account is exactly the kind of
						// confident-wrong answer this project refuses
						// elsewhere -- see buildRules' own doc just above.
						diaryPush = null;
						clientThread.invokeLater(this::refresh);
					}
				},
				accepted ->
				{
					// questpath owns the build rules; this is the only place
					// the plugin learns them.
					buildRules = BuildRule.ordered(accepted.buildRules());
					constraints = accepted.constraints();
					unmatchedQuests = accepted.unmatched();
					// Same story for the saved route -- questpath owns the
					// plan, and this push response is the only place it is
					// handed over.
					routeOrder = accepted.routeOrder();
					partialRuns = accepted.partialRuns();
					knownItems = accepted.knownItems();
					// hasDiary() false (an older questpath) must leave this
					// null, never overwrite a genuine earlier value with a
					// misleading "no data" from a field that was never
					// there to begin with -- but a rejection already clears
					// it above, and a fresh accepted push from the same,
					// newer questpath is exactly when a real change should
					// land, so this reads accepted.diary() unconditionally
					// once hasDiary() is confirmed.
					diaryPush = accepted.hasDiary() ? accepted.diary() : diaryPush;

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

		// Kept for the Skills tab, which is asked its question from the event
		// thread and must not read the client to answer it. Built here rather
		// than there because this is already the pass that has the client's
		// levels, and because a snapshot the panel builds itself would be a
		// second, subtly different account.
		// In-progress quests as well as finished ones, because a prerequisite
		// the wiki states as "Started [[X]]" is satisfied by one of these.
		// Passing only the completed set would judge those on completion
		// alone and disagree with the Go planner, which has the distinction.
		PlayerState account = new PlayerState(levels, experience, completed,
			inProgressQuestNames(), buildRules);
		accountSnapshot = account;
		return QuestVerdicts.of(questGraph, account);
	}

	/**
	 * What the route list cannot say: the quests on the plan that still need
	 * something, and the ones the build says never to finish.
	 *
	 * <p>Built from accountSnapshot rather than from the client directly, so
	 * this pass and the Quests tab's verdicts are judging the same account.
	 * That snapshot is written by verdicts(), which runs earlier in the same
	 * refresh -- taking a second reading here would produce an account that
	 * differs from the one on screen by whatever happened in between.
	 *
	 * <p>An empty result when the snapshot or the route is missing, which
	 * {@link RouteWork} renders as no section rather than as "your plan is
	 * clear". The partial runs still travel in that case: they come from the
	 * push rather than from the client, so questpath having sent them does
	 * not depend on the client having loaded.
	 */
	private RouteWork routeWork(NextQuestStatus route)
	{
		return RouteWork.of(questGraph, accountSnapshot, route, partialRuns);
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
			new PlayerState(levels, experience, completed, inProgressQuestNames(), buildRules),
			heldIncludingCollected(), owned.bankSeen(), pickupScope(),
			new HashSet<>(routeOrder), knownItems, owned.carriedByName(),
			owned.bankPlaceholders());
	}

	/**
	 * What the account holds, plus whatever has been collected out of a Grand
	 * Exchange slot since the bank was last seen.
	 *
	 * <p>Reported from play: collecting a finished offer to the bank moved the
	 * item out of "Queued" and straight back into "Left", as though it had
	 * never been bought. It had -- the plugin just lost the evidence. While
	 * the offer sits in a slot the purchase is visible; collect it and the
	 * slot empties, and the items are now in a bank whose contents the plugin
	 * only knows if the bank has been opened this session.
	 *
	 * <p>Merged with max rather than added, the same way {@code
	 * QuestItems.effectiveHeld} merges the last sync's counts and for the same
	 * reason: collecting to the <em>inventory</em> is visible, so adding would
	 * count those items twice and drop something off the list that is still
	 * needed. See {@link GrandExchangeOffers#collected} for where max
	 * under-reports instead, and why that is the safer direction.
	 */
	private Map<String, Long> heldIncludingCollected()
	{
		Map<String, Long> held = owned.heldByName();
		Map<String, Long> collected = geOffers.collected();
		if (collected.isEmpty())
		{
			return held;
		}
		Map<String, Long> merged = new HashMap<>(held);
		for (Map.Entry<String, Long> entry : collected.entrySet())
		{
			merged.merge(entry.getKey(), entry.getValue(), Math::max);
		}
		return merged;
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
