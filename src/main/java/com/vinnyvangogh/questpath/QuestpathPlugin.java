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
import com.vinnyvangogh.questpath.groupshop.AccountKind;
import com.vinnyvangogh.questpath.groupshop.GroupShopAlert;
import com.vinnyvangogh.questpath.groupshop.GroupShoppingList;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import com.vinnyvangogh.questpath.groupshop.ItemSources;
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
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
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
import net.runelite.api.events.PostMenuSort;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

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
	name = "Quest Route",
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
	 * RuneLite's own party relay: the only channel the Group tab uses. No
	 * questpath server and no third party is involved, which is why the
	 * opt-in carries no third-party warning.
	 */
	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	/**
	 * What each other party member last sent, by party member id. Written
	 * from the party socket's thread, read from the client thread.
	 */
	private final Map<Long, GroupMember> partyMembers = new java.util.concurrent.ConcurrentHashMap<>();

	/** When each entry in {@link #partyMembers} last arrived; see {@link GroupBroadcaster#stale}. */
	private final Map<Long, Instant> partyHeardAt = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Group storage as this account last saw it open, lower-cased name to
	 * count, or null when it has not been opened this session. Client
	 * thread only. Never folded into {@link #owned}: storage is the group's,
	 * and counting it as this member's own would count it once per member.
	 */
	private Map<String, Long> groupStorageSeen;
	private long groupStorageAt;

	/**
	 * Whether {@link #groupStorageSeen} has been read back from this RS
	 * profile since login. The profile is not known until logged in, so the
	 * read is lazy; see {@link #loadGroupStorage}.
	 */
	private boolean groupStorageLoaded;

	/**
	 * Shops this account can use that sell something the party is still
	 * short of, from the last group shopping list. Built with the list, not
	 * per tick, so the per-tick approach check judges no reachability.
	 * Client thread.
	 */
	private Map<String, List<GroupShoppingList.Line>> lastGroupShopAt = Collections.emptyMap();

	/** Quest item lines by lower-cased name, for the group list's sources. */
	private Map<String, QuestGraph.Item> questItemsByName = Collections.emptyMap();
	private QuestGraph questItemsByNameFor;

	/** Gates every message this client sends to the party; see its class doc. */
	private GroupBroadcaster groupBroadcaster;

	/**
	 * Lower-cased names of every item any quest asks for -- the only item
	 * counts ever shared. Rebuilt when the graph changes identity.
	 */
	private Set<String> groupRelevantItems = Collections.emptySet();
	private QuestGraph groupRelevantItemsFor;

	/**
	 * Used to find the running Shortest Path plugin, if present -- see
	 * {@link ShortestPathBridge}.
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
	private LampOverlay lampOverlay;
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
	 * The wanted-ground-item id lookup {@link #onPostMenuSort} reads, built
	 * from the same {@link QuestItems#totals()} {@link GroundItemOverlay}
	 * marks the tile with -- one source of truth, two small per-consumer
	 * caches, the same duplication {@link GroundItemOverlay#lookupFor}
	 * already carries and explains: this and that overlay are read by
	 * different code under the one-file-per-agent rule, so neither may share
	 * the other's private cache field.
	 */
	private Map<Integer, QuestItems.Total> groundTakeLookup = Collections.emptyMap();
	private int groundTakeLookupSignature;
	private boolean haveGroundTakeLookup;

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
	 * {@code pluginManager} and {@code eventBus} wired into lambdas, not a
	 * RuneLite instance of its own. See its class doc.
	 */
	private ShortestPathBridge shortestPathBridge;

	/**
	 * Built once in {@link #startUp}, same shape as {@link
	 * #shortestPathBridge}: it needs {@code client} and {@code clientThread}
	 * wired into lambdas rather than a RuneLite instance of its own, so a
	 * test can stand in for both without constructing a real {@code Client}.
	 * Unlike {@link #shortestPathBridge} it talks to no other plugin -- see
	 * its class doc.
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
	 * Whether any quest on the account's saved route is currently {@code
	 * IN_PROGRESS}, per the client's own quest state -- one of the signals
	 * {@link DiaryRouteGuard#shouldNudge} holds a diary nudge back on. The
	 * route, not the whole account, is what {@link #inProgressQuestNames()}
	 * is intersected against: a quest half-done years ago says nothing about
	 * what the player is working on now.
	 *
	 * <p>Recomputed once per {@link #refresh} and read every tick from
	 * {@link #checkDiaryApproach} without redoing any of that work.
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
	 * <p>So the timer stays where it was justified -- holding back the diary
	 * nudge, where expiring is the safe direction because the cost of being
	 * wrong is one chat line held back too long. This one has no
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
	 * The rules the player wrote here, merged over the pushed ones wherever
	 * both name a skill.
	 *
	 * <p>Read through {@link #effectiveBuildRules()} rather than used
	 * directly, because every consumer of a rule -- the verdict engine, the
	 * lamp advisor, the panel -- has to see the same merged set. A caller
	 * that reached for {@link #buildRules} alone would be a guard that is
	 * silently off for anyone who has never run questpath, which is the
	 * situation this whole store exists to fix.
	 */
	@Inject
	private LocalBuildRules localBuildRules;
	/**
	 * Every lamp questpath can price, loaded once from the bundle.
	 *
	 * <p>Never null after startUp: a bundle that fails to load leaves an
	 * empty catalogue, which makes every lamp unmodelled rather than making
	 * the advisor throw. An unmodelled lamp still greys NO_XP skills, so the
	 * part that prevents an irreversible mistake survives a broken bundle.
	 */
	private volatile LampCatalogue lampCatalogue = LampCatalogue.empty();
	private final LampWatcher lampWatcher = new LampWatcher();
	/**
	 * What the lamp overlay draws, recomputed when the interface opens rather
	 * than per frame.
	 *
	 * <p>Per frame would mean re-running the advisor sixty times a second for
	 * an answer that cannot change while one interface is open -- and
	 * AGENTS.md is explicit that overlay work is kept to a minimum because it
	 * runs every frame.
	 */
	private volatile List<LampVerdict> lampVerdicts = Collections.emptyList();
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
	 * questpath's per-account item plan from the last accepted push, or null
	 * when there is none -- before the first push, from an older questpath,
	 * or when questpath could not work it out. Null keeps every graph line
	 * as it is and claims no choice. Same lifecycle as {@link #knownItems}:
	 * replaced wholesale, cleared on logout and on a rejected push.
	 */
	private volatile ItemPlan itemPlan;

	/**
	 * When each diary task ({@code (tier_id, ordinal)}) was last nudged
	 * about, for {@link DiaryRouteGuard#shouldNudge}'s cooldown parameter.
	 * Session state, not cleared on a rejected push -- a cooldown is about
	 * not repeating a chat line too often, not about which account is
	 * confirmed, so a push rejection has no bearing on it. Cleared on logout
	 * in {@link #onGameStateChanged}: the next login may be a different account
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
		migrateDiaryRouteToggle();
		watchdog = new EdtWatchdog(scheduler);
		watchdog.start();
		shortestPathBridge = new ShortestPathBridge(pluginManager::getPlugins,
			plugin -> pluginManager.isPluginEnabled((Plugin) plugin),
			// PluginMessage/EventBus#post is RuneLite's own public API, not
			// Shortest Path's, so no reflection is needed here -- but a
			// thread hand-off still is: Shortest Path's own onPluginMessage
			// handler asserts it is running on the client thread and does
			// nothing to get itself there if it is not. This button is
			// Swing, so route() is called from
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
			// The sentinel path, never the item's name: writing a real
			// search term into the chatbox input is the autotyping the Hub
			// rejects. GrandExchangeButton#showOne writes the button's own
			// sentinel, which onGrandExchangeSearched answers with this one
			// id -- see GrandExchangeSearchBridge's class doc.
			itemId -> geButton.showOne(itemId),
			clientThread::invoke);
		geButton = new GrandExchangeButton(client, clientThread::invokeLater, this::grandExchangeList);
		panel = new QuestpathPanel(sprites, config, configManager,
			new OverlaySettingsPanel(config, configManager),
			this::skillPlan, shortestPathBridge, geSearchBridge,
			this::grandExchangeAvailable, this::shopLocation,
			this::dropLocation, this::lastKnownPlayerLocation, this::onShopRouteSent,
			this::cancelShopRoute,
			this::shopRouteActive, this::syncNow,
			shopItemSkips, this::diaryGraphTasks, this::diaryPush, lockedGearActions(),
			localBuildRules, this::onLocalRulesEdited);
		navButton = NavigationButton.builder()
			.tooltip("Quest Route")
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
		// The lamp catalogue is bundled, so a failure here is a broken jar
		// rather than a missing server. Logged and carried on with an empty
		// catalogue: every lamp then reads as unmodelled, which still greys
		// NO_XP skills and still refuses to rank. A broken bundle must not
		// take the advisor's only irreversible-mistake guard down with it.
		try
		{
			lampCatalogue = LampCatalogue.load(gson);
		}
		catch (IOException e)
		{
			log.debug("lamp catalogue unavailable; every lamp will read as unmodelled", e);
			lampCatalogue = LampCatalogue.empty();
		}
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

		lampOverlay = new LampOverlay(this, config, client, tooltipManager);
		overlayManager.add(lampOverlay);

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

		// Registered whether or not sharing is on: receiving what other
		// members chose to send is how the Group tab works for a player who
		// has not opted in themselves.
		partyMembers.clear();
		wsClient.registerMessage(QuestpathGroupState.class);
		groupBroadcaster = new GroupBroadcaster(partyService::send, Instant::now);

		// The plugin can be enabled mid-session, so there may already be a
		// logged-in account whose StatChanged burst has been and gone.
		refresh();
		log.debug("questpath started");
	}

	/**
	 * One-shot migration for the old {@code overlayDiaryRoute} on/off
	 * switch, folded into {@link QuestpathConfig#questJournalFilter()} --
	 * see {@link QuestJournalFilter}'s own class doc for why the switch was
	 * replaced rather than kept alongside the new one.
	 *
	 * <p>Never renames or reuses a key without this: {@code AGENTS.md} is
	 * explicit that a renamed or removed config key needs a migration or it
	 * silently resets a saved setting. Read directly through {@link
	 * ConfigManager} rather than {@code QuestpathConfig#overlayDiaryRoute()},
	 * because that accessor no longer exists on the interface -- removing it
	 * is what stopped RuneLite's own config panel from showing a dead
	 * checkbox, and this is the one place still allowed to know the old key
	 * name.
	 *
	 * <p><b>The mapping.</b> An account that had the old switch off keeps
	 * the diary route overlay off, via {@code questJournalFilter}'s own
	 * default of {@code QUESTS} -- nothing to do. An account that had it on
	 * is moved to {@code ALL} rather than {@code DIARIES}, so a player who
	 * also had the quest route overlay on (a fully independent switch, never
	 * touched by this migration) does not lose it out from under them the
	 * first time this version starts. Either way the old key is cleared
	 * once read, which is what makes this a one-shot: the next startup finds
	 * nothing there and returns immediately.
	 */
	private void migrateDiaryRouteToggle()
	{
		String oldValue = configManager.getConfiguration(QuestpathConfig.GROUP, "overlayDiaryRoute");
		if (oldValue == null)
		{
			return;
		}
		if (Boolean.parseBoolean(oldValue)
			&& configManager.getConfiguration(QuestpathConfig.GROUP, "questJournalFilter") == null)
		{
			configManager.setConfiguration(QuestpathConfig.GROUP, "questJournalFilter", QuestJournalFilter.ALL);
			log.info("questpath: migrated the old \"Diary route: nearest task\" overlay switch to the new"
				+ " Quests tab filter (set to All, so both route overlays keep showing).");
		}
		configManager.unsetConfiguration(QuestpathConfig.GROUP, "overlayDiaryRoute");
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
				String message = "Quest Route: using the live quest graph from questpath ("
					+ questGraph.quests().size() + " quests).";
				log.info("questpath: {}", message);
				announceGraphSource(message);
				refresh();
				return;
			}

			String stillOn = questGraph == null
				? "no quest graph at all -- the Quests tab will say so"
				: questGraph.quests().size() + " quests bundled with this build";
			String message = "Quest Route: could not fetch the quest graph from questpath (" + outcome.reason()
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
		if (lampOverlay != null)
		{
			overlayManager.remove(lampOverlay);
			lampOverlay = null;
		}
		lampWatcher.interfaceClosed();
		lampVerdicts = Collections.emptyList();
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

		// Tell the party to forget this member before going quiet -- a no-op
		// unless this client was actually sharing. offer(false, ...) never
		// reads the account; see GroupBroadcaster.
		if (groupBroadcaster != null)
		{
			groupBroadcaster.offer(false, partyService.isInParty(), () -> null, false);
			groupBroadcaster = null;
		}
		wsClient.unregisterMessage(QuestpathGroupState.class);
		partyMembers.clear();

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		haveSent = false;
		owned.clear();
		log.debug("questpath stopped");
	}

	// ------------------------------------------------------------------
	// Group: party members running Quest Route share quest state over
	// RuneLite's PartyService. See GroupBroadcaster, GroupMember, GroupPlan.
	// ------------------------------------------------------------------

	/**
	 * Another member's state. Arrives on the party socket's thread, so it
	 * only touches the concurrent map and hands the rest to the client
	 * thread.
	 */
	@Subscribe
	public void onQuestpathGroupState(QuestpathGroupState message)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId())
		{
			return;
		}
		GroupMember member = GroupMember.decode(message.getMemberId(), memberName(message.getMemberId()), message);
		if (member == null)
		{
			partyMembers.remove(message.getMemberId());
			partyHeardAt.remove(message.getMemberId());
		}
		else
		{
			partyMembers.put(message.getMemberId(), member);
			partyHeardAt.put(message.getMemberId(), Instant.now());
		}
		clientThread.invokeLater(this::refreshGroup);
	}

	/** A newcomer has heard nothing yet: re-announce, if sharing. */
	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		clientThread.invokeLater(() ->
		{
			offerGroupState(true);
			refreshGroup();
		});
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		partyMembers.remove(event.getMemberId());
		partyHeardAt.remove(event.getMemberId());
		clientThread.invokeLater(this::refreshGroup);
	}

	/** A different party, or none: nothing anyone sent before applies. */
	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		partyMembers.clear();
		partyHeardAt.clear();
		clientThread.invokeLater(() ->
		{
			if (groupBroadcaster != null)
			{
				groupBroadcaster.reset();
			}
			refreshGroup();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!QuestpathConfig.GROUP.equals(event.getGroup()) || !"partyShare".equals(event.getKey()))
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			offerGroupState(false);
			refreshGroup();
		});
	}

	private String memberName(long memberId)
	{
		PartyMember member = partyService.getMemberById(memberId);
		String name = member == null ? null : member.getDisplayName();
		return name == null || name.isEmpty() ? "Party member" : name;
	}

	/** Client thread. Sends only when opted in, in a party, and something changed (or forced). */
	private void offerGroupState(boolean force)
	{
		if (groupBroadcaster == null)
		{
			return;
		}
		groupBroadcaster.offer(config.partyShare(), partyService.isInParty(), this::groupSnapshot, force);
	}

	/**
	 * This account as the party message carries it, or null when there is
	 * nothing honest to say yet: logged out, or quest states not loaded.
	 * Client thread: quest states are varbits.
	 */
	private QuestpathGroupState groupSnapshot()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Set<String> completed = completedQuestNames();
		if (completed.isEmpty())
		{
			// Indistinguishable from varbits that have not arrived; see
			// verdicts(). Sending it would tell the party this account has
			// done nothing.
			return null;
		}
		Map<String, Integer> levels = new java.util.LinkedHashMap<>();
		Map<String, Integer> experience = new java.util.LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill.getName() == null)
			{
				continue;
			}
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
			experience.put(skill.getName(), client.getSkillExperience(skill));
		}
		QuestpathGroupState message = GroupMember.encode(completed, inProgressQuestNames(), levels, experience,
			effectiveBuildRules(), owned.isEmpty() ? null : heldIncludingCollected(), owned.bankSeen(),
			groupRelevantItems());
		// A snapshot from an earlier session is still worth sharing: a member
		// with none of their own counts it, and freshest() lets a newer one win.
		loadGroupStorage();
		return GroupMember.withShopping(message, routeOrder, accountKind(), groupStorageSeen, groupStorageAt,
			groupRelevantItems());
	}

	/** The account's trading rules, from the type cached each tick. */
	private AccountKind accountKind()
	{
		return GrandExchangeAccounts.kindOf(accountType);
	}

	/**
	 * Keeps the group storage contents when its interface is open. Client
	 * thread. Returns whether the container was group storage, so the caller
	 * does not also count it as this account's own.
	 *
	 * <p>Only a group ironman has storage, and only quest items are kept --
	 * the same filter {@link #groupRelevantItems} puts on what is shared.
	 */
	private boolean recordGroupStorage(net.runelite.api.ItemContainer container)
	{
		if (container == null || container.getId() != net.runelite.api.gameval.InventoryID.INV_GROUP_TEMP)
		{
			return false;
		}
		if (accountKind() == AccountKind.GROUP_IRONMAN)
		{
			groupStorageSeen = questItemCounts(container);
			groupStorageAt = System.currentTimeMillis();
			groupStorageLoaded = true;
			configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, GroupStorageStore.KEY,
				GroupStorageStore.encode(gson, groupStorageSeen, groupStorageAt));
			refreshPending = true;
		}
		return true;
	}

	/**
	 * Reads this account's last storage snapshot back from its RS profile,
	 * once per login. Client thread. A profile is one account, so another
	 * account logging in reads its own snapshot or none -- never this one's.
	 */
	private void loadGroupStorage()
	{
		if (groupStorageLoaded || client.getGameState() != GameState.LOGGED_IN
			|| configManager.getRSProfileKey() == null)
		{
			return;
		}
		groupStorageLoaded = true;
		GroupStorageStore.Snapshot stored = GroupStorageStore.decode(gson,
			configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, GroupStorageStore.KEY));
		if (stored != null && groupStorageSeen == null)
		{
			groupStorageSeen = stored.counts;
			groupStorageAt = stored.at;
		}
	}

	/**
	 * Group storage as this account's item lists count it: the freshest of
	 * its own snapshot and every party member's, for a group ironman only.
	 */
	private GroupStorageView groupStorageView()
	{
		loadGroupStorage();
		GroupStorage own = groupStorageSeen == null ? null
			: new GroupStorage(groupStorageSeen, groupStorageAt, "you");
		return GroupStorageView.of(accountKind(), GroupStorageView.snapshots(own, partyMembers.values()));
	}

	/** A container's quest items, lower-cased name to count. Client thread. */
	private Map<String, Long> questItemCounts(net.runelite.api.ItemContainer container)
	{
		Map<String, Long> counts = new HashMap<>();
		Set<String> relevant = groupRelevantItems();
		for (net.runelite.api.Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(item.getId()).getName();
			String key = name == null ? "" : name.toLowerCase();
			if (relevant.contains(key))
			{
				counts.merge(key, (long) item.getQuantity(), Long::sum);
			}
		}
		return counts;
	}

	private Set<String> groupRelevantItems()
	{
		QuestGraph graph = questGraph;
		if (graph == null)
		{
			return Collections.emptySet();
		}
		if (graph != groupRelevantItemsFor)
		{
			Set<String> names = new HashSet<>();
			for (QuestGraph.Quest quest : graph.quests())
			{
				for (QuestGraph.Item item : quest.items())
				{
					if (!item.unresolved() && !item.grouped() && !item.name().isEmpty())
					{
						names.add(item.name().toLowerCase());
					}
				}
			}
			groupRelevantItems = names;
			groupRelevantItemsFor = graph;
		}
		return groupRelevantItems;
	}

	/**
	 * Rebuilds the Group tab. Client thread. The local account is judged
	 * from the client whether or not sharing is on -- reading your own state
	 * for your own panel shares nothing.
	 */
	private void refreshGroup()
	{
		// Rebuilt below only when there is a party to shop for.
		lastGroupShopAt = Collections.emptyMap();
		if (panel == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		boolean sharing = config.partyShare();
		if (questGraph == null)
		{
			panel.updateGroup(GroupView.of(GroupView.State.NO_GRAPH, sharing));
			return;
		}
		if (!partyService.isInParty())
		{
			panel.updateGroup(GroupView.of(GroupView.State.NOT_IN_PARTY, sharing));
			return;
		}
		PartyMember local = partyService.getLocalMember();
		GroupMember self = GroupMember.decode(local == null ? -1L : local.getMemberId(), "You", groupSnapshot());
		if (self == null)
		{
			panel.updateGroup(GroupView.of(GroupView.State.LOADING, sharing));
			return;
		}

		List<GroupMember> others = new java.util.ArrayList<>(partyMembers.values());
		others.sort(java.util.Comparator.comparing(GroupMember::name, String.CASE_INSENSITIVE_ORDER));
		List<GroupMember> members = new java.util.ArrayList<>();
		members.add(self);
		members.addAll(others);

		List<String> notSharing = new java.util.ArrayList<>();
		for (PartyMember member : partyService.getMembers())
		{
			if ((local != null && member.getMemberId() == local.getMemberId())
				|| partyMembers.containsKey(member.getMemberId()))
			{
				continue;
			}
			String name = member.getDisplayName();
			notSharing.add(name == null || name.isEmpty() ? "Party member" : name);
		}
		GroupPlan plan = GroupPlan.of(questGraph, members);
		panel.updateGroup(withGroupShopping(GroupView.ready(sharing, plan, notSharing), plan, self.memberId()));
	}

	/**
	 * Builds the group shopping list for a ready view, and the near-shop
	 * candidates the per-tick check reads. Client thread: sources and
	 * tradeability read client state.
	 *
	 * @param localId this client's own member id, which is never stale
	 */
	private GroupView withGroupShopping(GroupView view, GroupPlan plan, long localId)
	{
		Instant now = Instant.now();
		GroupShoppingList shopping = GroupShoppingList.of(
			GroupShoppingNeeds.of(questGraph, plan.planned(),
				m -> m.memberId() != localId && GroupBroadcaster.stale(partyHeardAt.get(m.memberId()), now)),
			GroupShoppingNeeds.storage(plan.planned()), this::itemTradeable);
		ItemSources.Lookup lookup = groupSourceLookup();
		lastGroupShopAt = GroupShopAlert.byShop(shopping.lines(), lookup);
		return view.withShopping(shopping, groupItemSources(shopping, lookup));
	}

	/**
	 * Whether the game lets an item change hands at all -- not the Grand
	 * Exchange question, which {@link #tradeability} answers. An item with no
	 * id, or no answer, is untradeable: pooling it would hand a member
	 * something nobody can give them. Client thread.
	 */
	private boolean itemTradeable(int itemId, String name)
	{
		if (itemId <= 0)
		{
			return false;
		}
		ItemComposition item = itemManager.getItemComposition(itemId);
		return item != null && item.isTradeable();
	}

	/** Where this account can get each line's item, keyed by lower-cased name. Client thread. */
	private Map<String, ItemSources.Options> groupItemSources(GroupShoppingList shopping, ItemSources.Lookup lookup)
	{
		Map<String, ItemSources.Options> out = new HashMap<>();
		AccountKind viewer = accountKind();
		for (GroupShoppingList.Line line : shopping.lines())
		{
			out.put(line.name().toLowerCase(), ItemSources.forViewer(viewer, line.name(), line.itemId(), lookup));
		}
		return out;
	}

	/**
	 * The quest graph's shops and drops, judged for this account through the
	 * same {@link #shopLocation} and {@link #dropLocation} every route and
	 * label uses, so the group list cannot name a shop the rest of the
	 * plugin would refuse to send you to. Client thread.
	 */
	private ItemSources.Lookup groupSourceLookup()
	{
		return new ItemSources.Lookup()
		{
			@Override
			public List<String> shops(String item)
			{
				QuestGraph.Item known = questItemNamed(item);
				return known == null ? Collections.emptyList() : known.shopNames();
			}

			@Override
			public boolean shopUsable(String shop)
			{
				return shopLocation(shop) != null;
			}

			@Override
			public List<String> drops(String item)
			{
				QuestGraph.Item known = questItemNamed(item);
				List<String> out = new java.util.ArrayList<>();
				for (String source : known == null ? Collections.<String>emptyList() : known.dropSourceNames())
				{
					if (dropLocation(source) != null)
					{
						out.add(source);
					}
				}
				return out;
			}

			@Override
			public boolean onGrandExchange(int itemId, String item)
			{
				return tradeability(itemId, item) == GrandExchangeShoppingList.Availability.BUYABLE;
			}
		};
	}

	/** The first quest item line with this name, any case, or null. Rebuilt when the graph changes. */
	private QuestGraph.Item questItemNamed(String name)
	{
		QuestGraph graph = questGraph;
		if (graph == null || name == null)
		{
			return null;
		}
		if (graph != questItemsByNameFor)
		{
			Map<String, QuestGraph.Item> index = new HashMap<>();
			for (QuestGraph.Quest quest : graph.quests())
			{
				for (QuestGraph.Item item : quest.items())
				{
					if (!item.name().isEmpty())
					{
						index.putIfAbsent(item.name().toLowerCase(), item);
					}
				}
			}
			questItemsByName = index;
			questItemsByNameFor = graph;
		}
		return questItemsByName.get(name.toLowerCase());
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

		// Re-announce an unchanged state now and then, so the party can tell
		// a quiet member from a gone one; heartbeatDue reads no account
		// state, so asking every tick costs nothing. The Group tab is redrawn
		// with it, which is also when a silent member turns stale on screen.
		if (groupBroadcaster != null && groupBroadcaster.heartbeatDue())
		{
			offerGroupState(false);
			refreshGroup();
		}

		// Unconditional, unlike the refresh below: this is about whether a
		// shop interface is open right now, which has nothing to do with
		// whether the account's own state changed this tick.
		checkShopArrival();

		// Whether the lamp interface is still up. There is no reliable
		// "widget closed" event for this group, and a stale verdict list
		// would keep the overlay drawing boxes over whatever interface
		// happened to open next.
		checkLampInterfaceClosed();

		// Refreshes the cached position the panel reads -- see
		// lastKnownPlayerLocation for why the panel cannot ask for itself.
		// Every tick, unconditionally: the player moves whether or not the
		// account's own state changed.
		playerLocation();

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
			// route they sent, so the diary nudge no longer needs to be
			// held back for it.
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
	 * Whether any quest on route is currently {@code IN_PROGRESS}, per the
	 * client's own quest state -- see {@link #routeQuestInProgress}'s own
	 * field doc for why the route, not the whole account, is what {@link
	 * #inProgressQuestNames()} is intersected against.
	 *
	 * <p>Checked against every outstanding route step, not only the head:
	 * the account may have started a quest further down the route than
	 * {@code route.questName()} names.
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
		boolean includeRecommended = config != null && config.recommendedItems();
		for (ShopRouteStops.Stop stop : ShopRouteStops.tour(items.totals(includeRecommended), shopItemSkips,
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
	 * holding back the diary nudge.
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
	 * the diary nudge should hold off -- see {@link ShopRouteGuard}'s class
	 * doc.
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
			(loadoutName, potion) -> editLoadout(loadoutName, loadout -> loadout.withMeleePotion(potion)),
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
		if (recordGroupStorage(event.getItemContainer()))
		{
			offerGroupState(false);
			refreshGroup();
			return;
		}
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
			itemPlan = null;
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
			// The next login's route, if any, has not been read yet, so
			// there is nothing to call "in progress" until the first
			// post-login refresh says otherwise.
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
			// Another account's group, if it has one at all.
			// Only the in-memory copy: the stored one belongs to this
			// account's RS profile and is read back when it logs in again.
			groupStorageSeen = null;
			groupStorageAt = 0L;
			groupStorageLoaded = false;
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
		overlaySnapshot = new OverlaySnapshot(stats, xp, route, items, effectiveBuildRules(),
			characterName(), questPoints());

		// Rebuilt here, once per account-state change, so the per-tick diary
		// check never has to rescan the route -- see routeQuestInProgress's
		// own field doc for what this gates.
		routeQuestInProgress = routeQuestInProgress(route);

		java.util.List<ItemCompletionWatcher.Completion> justCompleted = completions.observe(items);
		announce(justCompleted);
		advanceShopRoute(justCompleted, items);
		floatCompletions(justCompleted);

		// Built here, on the client thread, exactly like every other read
		// this pass makes: LockedGear.resolve reads item stats through
		// itemStatsView, an ItemManager call that is client-thread-only.
		LockedGear lockedGear =
			LockedGear.resolve(loadoutStore.all(), selectedLoadoutName(), stats, this::itemStatsView);

		panel.update(stats, xp, syncStatus(), effectiveBuildRules(), unmatchedQuests,
			questVerdicts, items, route, routeWork(route), SyncSettings.from(config), report, lockedGear);

		// The party hears about the same account state the panel just drew,
		// and only when the player opted in; the Group tab is rebuilt from it.
		offerGroupState(false);
		refreshGroup();

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
		boolean includeRecommended = config != null && config.recommendedItems();
		TeleportForm form = config == null ? TeleportForm.TABLET : config.recommendedTeleportForm();
		return GrandExchangeShoppingList.of(snapshot.items().grandExchangeTotals(includeRecommended, form),
			geOffers, this::tradeability);
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
			// GrandExchangeShoppingList.Availability#GENERIC.
			return GrandExchangeShoppingList.Availability.GENERIC;
		}
		ItemComposition item = itemManager.getItemComposition(itemId);
		if (item == null)
		{
			return GrandExchangeShoppingList.Availability.UNKNOWN;
		}
		if (!item.isGeTradeable())
		{
			return GrandExchangeShoppingList.Availability.NOT_ON_GE;
		}
		if (item.isMembers() && !onMembersWorld())
		{
			// Tradeable, and not by this account right now. The game refuses
			// the offer outright -- "You can't buy members-only items on this
			// world" -- so listing it as the next thing to buy is the same
			// failure Karamjan rum is here to prevent: suggesting something
			// the account cannot act on. It moves to "Not sold here" with its
			// own reason rather than vanishing, because the item is still
			// genuinely needed and is one world hop away.
			return GrandExchangeShoppingList.Availability.MEMBERS_ONLY;
		}
		return GrandExchangeShoppingList.Availability.BUYABLE;
	}

	/**
	 * Whether the world the account is on right now sells members items.
	 *
	 * <p>Read live rather than cached: it is the one input to
	 * {@link #tradeability} that changes without the item or the account
	 * changing, and a cached answer survives a world hop as a buy list that
	 * is confidently wrong in whichever direction it was cached.
	 *
	 * <p>An empty world-type set means the client has not told us yet. Treated
	 * as members, deliberately: the cost of being wrong that way is a listed
	 * item the player cannot buy this second, and the cost of being wrong the
	 * other way is silently hiding most of the buy list from a paying member.
	 * The second is much worse and much harder to notice.
	 */
	private boolean onMembersWorld()
	{
		java.util.EnumSet<net.runelite.api.WorldType> types = client.getWorldType();
		return types == null || types.isEmpty() || types.contains(net.runelite.api.WorldType.MEMBERS);
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
			shopTripChat("Quest Route: shopping done -- nothing left on this trip.");
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
				shopTripChat("Quest Route: could not move on to the next stop.");
				return;
			}
			shopRouteSentAt = Instant.now();
			shopTripActive = true;
			StringBuilder line = new StringBuilder("Quest Route: next up ")
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
				.append("Quest Route: " + done.leadIn())
				.append(ChatColorType.HIGHLIGHT)
				.append(done.subject())
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
	/**
	 * Makes "Take" the left-click default for a ground item questpath's plan
	 * still counts as short, and leaves every other entry alone.
	 *
	 * <p><b>Hook and verification.</b> {@code PostMenuSort} fires once per
	 * menu build, after the client's own native sort has already applied any
	 * {@code MenuEntry#setDeprioritized(true)} a plugin such as core's own
	 * Ground Items set while the menu was being assembled, and before the
	 * menu is shown -- it is the last point anything can still move an
	 * entry, and it is the hook RuneLite's own {@code MenuEntrySwapperPlugin}
	 * uses for its own swaps (confirmed by reading
	 * {@code MenuEntrySwapperPlugin#onPostMenuSort} and
	 * {@code GroundItemsPlugin#onMenuEntryAdded} from the actual RuneLite
	 * source at this build's tag, not from memory: the ground item's id
	 * comes back as {@code MenuEntry#getIdentifier()}, not
	 * {@code getItemId()} -- core's own handler reads it the same way).
	 * {@code client.isMenuOpen()} is checked first for the same reason
	 * {@code MenuEntrySwapperPlugin} checks it: the menu is not rebuilt while
	 * it is already open, so acting again here would just re-swap the same
	 * two entries back and forth every subsequent frame.
	 *
	 * <p><b>Ordering.</b> Deciding which entries move, and where, is
	 * {@link GroundItemTakePriority#reorder} -- kept free of every RuneLite
	 * type so it can be unit-tested directly. This method's only job is
	 * reducing the live menu down to {@link GroundItemTakePriority.Candidate}s
	 * and applying the permutation it returns.
	 *
	 * <p><b>Scope.</b> Only entries whose type is one of the five
	 * {@code GROUND_ITEM_*_OPTION} actions and whose option text is
	 * literally "Take" (tags stripped) are ever touched -- never an
	 * {@code EXAMINE_ITEM_GROUND}, a spell cast on a ground item, or
	 * anything belonging to an NPC or object that happens to share the tile.
	 * An item not on the wanted list is never inspected at all: its entry,
	 * and its position relative to every other untouched entry, cannot move.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (config == null || !config.groundTakeLeftClick())
		{
			return;
		}
		if (client.isMenuOpen())
		{
			// Re-sorting an already-open menu would just swap the same two
			// entries back and forth every subsequent frame -- see
			// MenuEntrySwapperPlugin#onPostMenuSort for the same guard.
			return;
		}

		OverlaySnapshot snapshot = overlaySnapshot;
		QuestItems items = snapshot == null ? null : snapshot.items();
		if (items == null || items.isEmpty())
		{
			return;
		}

		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return;
		}

		Map<Integer, QuestItems.Total> needed = groundTakeLookupFor(items);
		if (needed.isEmpty())
		{
			return;
		}

		List<GroundItemTakePriority.Candidate> candidates = new java.util.ArrayList<>();
		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			MenuAction type = entry.getType();
			if (type != MenuAction.GROUND_ITEM_FIRST_OPTION && type != MenuAction.GROUND_ITEM_SECOND_OPTION
				&& type != MenuAction.GROUND_ITEM_THIRD_OPTION && type != MenuAction.GROUND_ITEM_FOURTH_OPTION
				&& type != MenuAction.GROUND_ITEM_FIFTH_OPTION)
			{
				continue;
			}
			if (!"take".equalsIgnoreCase(Text.removeTags(entry.getOption())))
			{
				// Ground items can carry other primary ops besides Take
				// (a plugin-added one, for instance); this feature is about
				// picking the item up, nothing else that happens to share
				// the same handful of MenuAction slots.
				continue;
			}
			QuestItems.Total total = needed.get(entry.getIdentifier());
			if (total == null || total.covered())
			{
				continue;
			}
			long shortfall = total.outstanding();
			candidates.add(new GroundItemTakePriority.Candidate(i, entry.getIdentifier(), shortfall));
		}

		int[] order = GroundItemTakePriority.reorder(entries.length, candidates);
		if (order == null)
		{
			return;
		}

		MenuEntry[] reordered = new MenuEntry[entries.length];
		for (int i = 0; i < entries.length; i++)
		{
			reordered[i] = entries[order[i]];
		}
		for (GroundItemTakePriority.Candidate candidate : candidates)
		{
			entries[candidate.index()].setDeprioritized(false);
		}
		client.setMenuEntries(reordered);
	}

	/**
	 * The wanted-ground-item id lookup, rebuilt only when {@link
	 * QuestItems#signature()} changes. Mirrors {@link
	 * GroundItemOverlay#lookupFor} -- see {@link #groundTakeLookup}'s own doc
	 * for why this is a second small cache rather than a shared one.
	 */
	private Map<Integer, QuestItems.Total> groundTakeLookupFor(QuestItems items)
	{
		int signature = items.signature();
		if (haveGroundTakeLookup && signature == groundTakeLookupSignature)
		{
			return groundTakeLookup;
		}

		Map<Integer, QuestItems.Total> built = new HashMap<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.itemId() <= 0)
			{
				continue;
			}
			built.putIfAbsent(total.itemId(), total);
		}

		groundTakeLookup = built;
		groundTakeLookupSignature = signature;
		haveGroundTakeLookup = true;
		return groundTakeLookup;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Lamp tracking first, and before the bank guard below, because a
		// lamp is rubbed from the inventory with the bank shut. RuneLite
		// delivers one MenuOptionClicked per class, so the two features
		// share this method rather than each having their own -- see
		// OSRS-RUNELITE.md on subscriber naming.
		//
		// Any item click is recorded, with no list of menu verbs: most lamps
		// are "Rub" and a Book of Knowledge is "Read", and a hardcoded verb
		// list goes quietly out of date the first time a lamp ships with a
		// fourth word on it. It is the interface opening that decides one of
		// these clicks was a lamp.
		if (event.getItemId() > 0)
		{
			lampWatcher.itemClicked(event.getItemId(), event.getMenuTarget(), client.getTickCount());
		}

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
		if (target != null && target.contains("Quest Route"))
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
		if (questGraph == null)
		{
			return;
		}
		// Its own toggle rather than the item-done one. They are different
		// events -- "you finished something" against "there is one for sale
		// right there" -- and the second is the one worth walking on.
		boolean wantOwn = config != null && config.shopNearbyPrompt();
		boolean wantGroup = config != null && config.groupShopAlert();
		if (!wantOwn && !wantGroup)
		{
			// Still not observed while both are off: the watcher's baseline
			// would go stale and switching one on mid-session would announce
			// every shop already in range at once.
			shopApproach.clear();
			return;
		}

		// The party's shops ride the same watcher as this account's own, so a
		// shop that stocks something for you and something for the group
		// says it once, and "once per visit" means the same thing for both.
		Map<String, WorldPoint> nearby = new HashMap<>();
		Map<String, QuestItems.Total> wantedAt = wantOwn && items != null
			? ownShopCandidates(items, nearby) : Collections.emptyMap();
		Map<String, List<GroupShoppingList.Line>> groupAt = wantGroup
			? groupShopCandidates(nearby) : Collections.emptyMap();

		for (String shopName : shopApproach.observe(nearby, playerLocation()))
		{
			announceShopApproach(shopName, nearby.get(shopName), wantedAt.get(shopName),
				GroupShopAlert.describe(groupAt.get(shopName)));
		}
	}

	/**
	 * Says one shop just come into range: this account's own item if it has
	 * one there, the group's still-needed count if the party has one, both
	 * on one line when both.
	 */
	private void announceShopApproach(String shopName, WorldPoint at, QuestItems.Total total, String group)
	{
		if (total == null)
		{
			if (!group.isEmpty())
			{
				shopChat(shopName, group);
			}
			return;
		}
		String offer = FloatingNotes.offerFor(total.name(), total.outstanding());
		shopChat(shopName, group.isEmpty() ? offer : offer + "; " + group);
		if (config.floatingNotes() && config.floatingNotePlacement() != NotePlacement.ABOVE_YOU)
		{
			// Over the shop, which is where the shopkeeper stands. At
			// this distance there is no NPC to read off the player's
			// interaction, and the tile is the honest answer rather than
			// a guess at which of the figures ahead is the one selling.
			// Held far longer than a completion note. That one confirms
			// something already done and can fade; this one is asking you
			// to change where you are walking, and four seconds is not
			// long enough to read a name, find the NPC and decide.
			floatingNotes.add(offer, at, Instant.now(), FloatingNotes.OFFER_LIFETIME, total.name());
		}
	}

	/**
	 * The party's near-shop candidates from the last group list: adds each
	 * shop's tile to {@code nearby} (this account's own entry wins) and
	 * returns what the group is short of there.
	 */
	private Map<String, List<GroupShoppingList.Line>> groupShopCandidates(Map<String, WorldPoint> nearby)
	{
		for (String shopName : lastGroupShopAt.keySet())
		{
			WorldPoint at = shopLocation(shopName);
			if (at != null)
			{
				nearby.putIfAbsent(shopName, at);
			}
		}
		return lastGroupShopAt;
	}

	/**
	 * This account's own near-shop candidates: shops it can use that sell an
	 * item still short and not skipped. Fills {@code nearby} with their tiles
	 * and returns the item each shop is announced for.
	 */
	private Map<String, QuestItems.Total> ownShopCandidates(QuestItems items, Map<String, WorldPoint> nearby)
	{
		Map<String, QuestItems.Total> wantedAt = new HashMap<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.covered() || shopItemSkips.isSkipped(total.name()))
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
		return wantedAt;
	}

	/** One near-shop chat line: "Quest Route: <shop> -- <what>". */
	private void shopChat(String shopName, String what)
	{
		// Highlighted rather than flat, and the shop's name is the
		// highlighted half: the offer text says what it wants, the
		// name says where to walk, and only one of those is worth
		// finding again after the line has scrolled.
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("Quest Route: ")
				.append(ChatColorType.HIGHLIGHT)
				.append(shopName)
				.append(ChatColorType.NORMAL)
				.append(" -- " + what)
				.build())
			.build());
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
	 * <p>Shares {@link QuestpathConfig#questJournalFilter()} with {@link
	 * DiaryRouteOverlay} rather than carrying a second toggle of its own --
	 * see {@link QuestJournalFilter}'s own doc for why one switch governs
	 * both the always-on display and this occasional chat line.
	 */
	private void checkDiaryApproach()
	{
		if (config == null || !config.questJournalFilter().showsDiaries() || diaryPush == null
			|| questGraph == null)
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

		boolean nudge = DiaryRouteGuard.shouldNudge(config.questJournalFilter().showsDiaries(),
			nearest.distanceTiles(),
			shopRouteBlocksQuestRouting(), routeQuestInProgress, diaryNudgedAt.get(key), now);
		if (!nudge)
		{
			return;
		}

		diaryNudgedAt.put(key, now);
		chat("Quest Route: " + nearest.task().diary() + " " + nearest.task().tier()
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
		if (event.getGroupId() == LampSkillWidgets.group())
		{
			// A lamp's skill-choice interface has opened. Which lamp it is
			// comes from the item the player just clicked, never from this
			// interface: every lamp opens this same group with this same
			// title, and the amounts differ by two orders of magnitude.
			lampWatcher.interfaceOpened(client.getTickCount());
			refreshLampAdvice();
			return;
		}
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

	// -----------------------------------------------------------------------
	// Experience lamps
	// -----------------------------------------------------------------------

	/**
	 * Every rule that applies to this account: the ones questpath pushed,
	 * with the ones the player wrote here laid over the top.
	 *
	 * <p>Local wins on a skill both name, and the direction is the decision.
	 * A pushed rule can be minutes stale and arrives without the player doing
	 * anything; a local rule is something they typed. When the two disagree
	 * the typed one is the more recent statement of intent, and it is the one
	 * they would be surprised to find ignored.
	 *
	 * <p>Everything that consults a rule goes through here, so the guard
	 * cannot be accidentally half-applied -- a caller reading the pushed list
	 * alone would be silently unprotected for anyone who has never run
	 * questpath at all.
	 */
	List<BuildRule> effectiveBuildRules()
	{
		List<BuildRule> local = localBuildRules == null
			? Collections.emptyList() : localBuildRules.all();
		if (local.isEmpty())
		{
			return buildRules;
		}
		return BuildRuleCodec.merge(buildRules, local);
	}

	/** The store behind the sidebar's build-rules editor. */
	LocalBuildRules localBuildRules()
	{
		return localBuildRules;
	}

	/**
	 * Recomputes everything after the player writes a rule in the sidebar.
	 *
	 * <p>Called from the EDT (a button click), so the work is queued onto the
	 * client thread rather than run inline -- {@link #refresh()} reads levels
	 * and quest varbits straight from {@code Client}, and reading those from
	 * Swing is the threading bug this indirection exists to avoid.
	 *
	 * <p>It has to run at all because a rule changes every verdict on every
	 * tab. A saved rule against a stale panel is a panel confidently telling
	 * the player a quest is fine when they have just declared it is not.
	 */
	private void onLocalRulesEdited()
	{
		clientThread.invoke(this::refresh);
	}

	/**
	 * Drops the lamp advice once its interface is gone.
	 *
	 * <p>Checked on the tick rather than from a close event, because the
	 * interface is torn down without one this plugin can rely on. Leaving the
	 * verdicts in place would have the overlay draw boxes at the coordinates
	 * of squares that no longer exist -- and those coordinates belong to
	 * whatever interface opened next.
	 */
	private void checkLampInterfaceClosed()
	{
		if (!lampWatcher.open())
		{
			return;
		}
		Widget root = client.getWidget(LampSkillWidgets.componentFor("attack"));
		if (root == null || root.isHidden())
		{
			lampWatcher.interfaceClosed();
			lampVerdicts = Collections.emptyList();
		}
	}

	/**
	 * What {@link LampOverlay} draws. Read on the client thread during
	 * render, so it is computed elsewhere and only read here.
	 */
	List<LampVerdict> lampVerdicts()
	{
		return lampVerdicts;
	}

	/**
	 * Recomputes lamp advice for the interface that has just opened.
	 *
	 * <p>Once per opening rather than per frame. The answer cannot change
	 * while one interface is up -- the account's levels are fixed for the
	 * duration of the decision -- and AGENTS.md is explicit that overlay work
	 * runs every frame and must stay minimal.
	 *
	 * <p>Runs on the client thread, from the widget-loaded event, because it
	 * reads levels and experience straight from the client.
	 */
	private void refreshLampAdvice()
	{
		Lamp lamp = lampWatcher.lamp(lampCatalogue);
		if (lamp == null)
		{
			lampVerdicts = Collections.emptyList();
			return;
		}

		Map<String, Integer> levels = new HashMap<>();
		Map<String, Long> experience = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
			experience.put(skill.getName(), (long) client.getSkillExperience(skill));
		}

		PlayerState account = new PlayerState(levels, experience,
			completedQuestNames(), inProgressQuestNames(), effectiveBuildRules());

		LampAdvisor.Inputs inputs = new LampAdvisor.Inputs();
		// The free Ranged/Magic ceiling, from the same CombatMath the combat
		// panel uses -- so the advisor and the panel can never disagree about
		// where the ceiling is. Supplied only when the levels are real:
		// passing a ceiling derived from a not-yet-loaded account would flag
		// every Ranged square on a false premise.
		int attack = client.getRealSkillLevel(Skill.ATTACK);
		int strength = client.getRealSkillLevel(Skill.STRENGTH);
		if (attack > 0 && strength > 0)
		{
			inputs.freeCeiling(CombatMath.freeRangedCeiling(attack, strength));
		}
		inputs.routeNeeds(routeSkillNeeds());

		lampVerdicts = LampAdvisor.advise(lamp, account, LampSkillWidgets.skills(), inputs);
	}

	/**
	 * The levels the saved route is still waiting on, by skill.
	 *
	 * <p>Empty without a sync, and empty is the correct answer then rather
	 * than a problem to work around: with no route there is no gate for a
	 * lamp to remove, so every legal skill is simply ranked on what the lamp
	 * buys. The advice is weaker and still true.
	 */
	private Map<String, Integer> routeSkillNeeds()
	{
		Map<String, Integer> needs = new HashMap<>();
		QuestGraph graph = questGraph;
		PlayerState account = accountSnapshot;
		if (graph == null || account == null || routeOrder.isEmpty())
		{
			return needs;
		}
		// routeOrder carries quest names, not ids -- the two id spaces (the
		// client's and questpath's) have nothing to do with each other, and
		// the name is the one identity both sides genuinely share. See
		// PlayerState's own note on why comparing the ids produced a
		// completed set that was pure coincidence.
		for (String questName : routeOrder)
		{
			QuestGraph.Quest quest = graph.byName(questName);
			if (quest == null || account.isComplete(quest.name()))
			{
				continue;
			}
			for (QuestGraph.Requirement requirement : quest.requirements())
			{
				int have = account.levelIn(requirement.skill());
				if (have >= requirement.level())
				{
					continue;
				}
				// The highest level any outstanding route quest asks for, so
				// a lamp is credited against the gate that is actually
				// furthest away rather than whichever quest came first.
				String key = Lamp.normalise(requirement.skill());
				Integer existing = needs.get(key);
				if (existing == null || requirement.level() > existing)
				{
					needs.put(key, requirement.level());
				}
			}
		}
		return needs;
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
						itemPlan = null;
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
					// Replaced unconditionally, null included: a push answered
					// without a plan means questpath made no decision this time,
					// and the previous one is about holdings that have moved.
					itemPlan = accepted.itemPlan();
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
			inProgressQuestNames(), effectiveBuildRules());
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
			new PlayerState(levels, experience, completed, inProgressQuestNames(), effectiveBuildRules()),
			heldIncludingCollected(), owned.bankSeen(), pickupScope(),
			new HashSet<>(routeOrder), knownItems, owned.carriedByName(),
			owned.bankPlaceholders(), itemPlan, groupStorageView());
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

	/**
	 * Also where the pre-rename settings are carried over: this runs before
	 * RuneLite writes the group's defaults, which is the only moment "the new
	 * group has no value for this key" still means the player never set it.
	 * See {@link ConfigGroupMigration}'s class doc.
	 */
	@Provides
	QuestpathConfig provideConfig(ConfigManager configManager)
	{
		ConfigGroupMigration.run(configManager);
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
