package com.vinnyvangogh.questpath;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Settings for the questpath plugin.
 *
 * <p>Only one feature is configurable, and it is the only one that leaves the
 * client: pushing this account's state to a self-hosted questpath instance.
 * Everything the panel shows works with all of this untouched, which is
 * deliberate — a plugin whose entire functionality is "talk to a server you
 * type in" is a much harder thing to justify than one where that is an extra.
 *
 * <p>The group name is {@code questroute} and must never be renamed without a
 * migration once published: renaming a config group silently resets
 * everyone's saved settings. It was {@code questpath} before the first Hub
 * submission -- that name belongs to another author's live plugin -- and
 * {@link ConfigGroupMigration} carries a pre-rename profile over once.
 */
@ConfigGroup(QuestpathConfig.GROUP)
public interface QuestpathConfig extends Config
{
	String GROUP = "questroute";

	/**
	 * The group this plugin used before it was renamed to Quest Route.
	 * Read once, by {@link ConfigGroupMigration}, and never written.
	 */
	String LEGACY_GROUP = "questpath";

	@ConfigSection(
		name = "In-game overlays",
		description = "Which Quest Route panels to draw over the game, and how."
			+ " These are easier to read on the Quest Route sidebar's View tab, which groups"
			+ " them and does not truncate the labels.",
		position = 1,
		// Closed. There are more than twenty items in here and they are laid
		// out better on the sidebar's own View tab; leaving this open makes
		// the config screen a wall for something most people will set once.
		closedByDefault = true
	)
	String overlaySection = "overlays";

	@ConfigSection(
		name = "Shops",
		description = "Mark the things you need while a shop is open",
		position = 2,
		closedByDefault = true
	)
	String shopSection = "shops";

	@ConfigSection(
		name = "Ground items",
		description = "Mark things you need that are lying on the floor",
		position = 3,
		closedByDefault = true
	)
	String groundSection = "ground";

	@ConfigSection(
		name = "Bank",
		description = "Mark what your plan needs while the bank is open",
		position = 4,
		closedByDefault = true
	)
	String bankSection = "bank";

	@ConfigSection(
		name = "Chat",
		description = "What Quest Route says in the game chat",
		position = 4,
		closedByDefault = true
	)
	String chatSection = "chat";

	@ConfigSection(
		name = "Experience lamps",
		description = "Mark up the skill-choice interface when you rub a lamp",
		position = 5,
		closedByDefault = true
	)
	String lampSection = "lamps";

	@ConfigSection(
		name = "Quest Route sync",
		description = "Push this account's state to your own Quest Route server",
		position = 0,
		closedByDefault = true
	)
	String syncSection = "sync";

	@ConfigSection(
		name = "Quests tab",
		description = "Which half of the achievement-diary/quest split the Quests tab, and the"
			+ " route overlays it drives, currently shows.",
		position = 5,
		closedByDefault = false
	)
	String questTabSection = "questTab";

	@ConfigSection(
		name = "Group (party)",
		description = "Plan quests with your RuneLite party: Group Ironman, or anyone questing together.",
		position = 7,
		closedByDefault = true
	)
	String groupSection = "group";

	@ConfigSection(
		name = "Voice",
		description = "Quest Route saying what to buy as you near a shop, and naming needed items"
			+ " on the floor. Bundled recordings, played on your computer; nothing is sent anywhere.",
		position = 8,
		closedByDefault = true
	)
	String voiceSection = "voice";

	// ------------------------------------------------------------------
	// Voice. Keys freeze at the first published version: never rename.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "voiceEnabled",
		name = "Voice",
		description = "Speak Quest Route's reminders out loud. Off silences every spoken line below.",
		section = voiceSection,
		position = 1
	)
	default boolean voiceEnabled()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "voiceVolume",
		name = "Volume",
		description = "How loud the voice is, 0 to 100.",
		section = voiceSection,
		position = 2
	)
	default int voiceVolume()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "voiceShops",
		name = "Say what to buy near a shop",
		description = "As you come up on a shop, say every item your plan still needs from it:"
			+ " \"Looking for 30 air runes and an air talisman at Aubury's Rune Shop\".",
		section = voiceSection,
		position = 3
	)
	default boolean voiceShops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "voiceFloorItems",
		name = "Say needed items on the floor",
		description = "Say \"Bucket nearby\" once when an item your plan still needs is lying"
			+ " within the floor-item range (Ground items section).",
		section = voiceSection,
		position = 4
	)
	default boolean voiceFloorItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "partyShare",
		name = "Share with my party",
		description = "Send your finished quests, levels, build rules, route, account type and counts of quest"
			+ " items you hold (and in group storage, for Group Ironman) to members of your RuneLite party who"
			+ " also run Quest Route, so the sidebar's Group tab can plan with you. Travels over RuneLite's own"
			+ " party service only, never to a Quest Route server or any other server. Nothing is sent while this is off.",
		section = groupSection,
		position = 1
	)
	default boolean partyShare()
	{
		// Off by default: what an account has done and holds is the player's
		// to share, and joining a party for some other plugin must not start
		// broadcasting it.
		return false;
	}

	@ConfigItem(
		keyName = "groupShopAlert",
		name = "Group needs in shop alerts",
		description = "When you come up on a shop that sells something your party is still short of, say so in"
			+ " chat with how many more the group needs. Uses what party members share; once per visit.",
		section = groupSection,
		position = 2
	)
	default boolean groupShopAlert()
	{
		// Off by default: it writes to chat, and chat output is opt-in here.
		return false;
	}

	@ConfigItem(
		keyName = "questJournalFilter",
		name = "Quests tab shows",
		description = "Quests: the quest route only, on both the Quests tab and the \"Next on your"
			+ " route\" overlay (still gated by that overlay's own on/off switch above). Diaries:"
			+ " the diary journal and nearest-task route only, on both the tab and the \"Diary"
			+ " route\" overlay -- this is what used to be that overlay's own separate on/off"
			+ " switch, folded into this one setting instead. All: both, each in its own order."
			+ " Same idea as the in-game quest journal's own Quests/Diaries/All filter.",
		section = questTabSection,
		position = 1
	)
	default QuestJournalFilter questJournalFilter()
	{
		// QUESTS, not ALL: this is also the diary route overlay's entire
		// on/off switch now (see this item's own description), and that
		// overlay used to default off. Defaulting here to QUESTS keeps a
		// fresh install's diary overlay off exactly like before, without
		// this plugin having to carry a second, now-redundant toggle for it.
		return QuestJournalFilter.QUESTS;
	}

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Send my account state",
		description = "Push quest completion, levels and diaries to your Quest Route server when they change."
			+ " Quest Route then stops polling WikiSync for this account.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = syncSection,
		position = 1
	)
	default boolean syncEnabled()
	{
		// Off by default, and it stays that way. RuneLite requires opt-in for
		// anything touching a third-party server, and the panel is fully
		// useful without this.
		return false;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Quest Route address",
		description = "Optional. Where your Quest Route server is listening, e.g. an address on your own machine or network."
			+ " Leave blank (the default) to use Quest Route on its own: nothing is sent anywhere.",
		section = syncSection,
		position = 2
	)
	default String serverUrl()
	{
		// Blank on purpose. The plugin is local-first: with no address it
		// never opens a connection, and no chat line complains about it.
		return "";
	}

	@ConfigItem(
		keyName = "pairingToken",
		name = "Pairing token",
		description = "From your Quest Route server. It both authenticates the push and selects which account it writes.",
		secret = true,
		section = syncSection,
		position = 3
	)
	default String pairingToken()
	{
		// No default. There is no such thing as a sensible shared secret, and
		// an empty token means the push is off however the toggle is set.
		return "";
	}

	@ConfigItem(
		keyName = "resetAccountSetup",
		name = "Reset account setup",
		description = "Tick to wipe this account's saved Quest Route setup (account type, quest types, route)"
			+ " and run first-run setup again. You are asked to confirm; the box un-ticks itself.",
		position = 0
	)
	default boolean resetAccountSetup()
	{
		// A momentary switch, not a setting: the plugin turns it back off
		// as soon as it has been handled.
		return false;
	}

	// ------------------------------------------------------------------
	// Overlays.
	//
	// Every one is off by default. Five panels appearing over the game the
	// moment the plugin is installed is a plugin nobody keeps, and each of
	// these answers a question only some accounts are asking -- a main has no
	// build rules to watch and someone with no saved route has no route to be
	// next on. Turning one on is one click; turning four unwanted ones off is
	// how a plugin gets uninstalled.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "overlayDisplayMode",
		name = "Show",
		description = "Whether overlay rows carry the item pictures, the words, or both."
			+ " Rows that have no picture to show keep their text in every mode.",
		section = overlaySection,
		position = 1
	)
	default OverlayDisplayMode overlayDisplayMode()
	{
		return OverlayDisplayMode.TEXT_AND_ICONS;
	}

	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "How wide the overlay panels may be. Wider is denser in every mode,"
			+ " and dramatically so with images: the same twenty items go from a stripe"
			+ " down the screen to two rows of a grid.",
		section = overlaySection,
		position = 2
	)
	default OverlayWidth overlayWidth()
	{
		return OverlayWidth.REGULAR;
	}

	@ConfigItem(
		keyName = "overlayListLength",
		name = "Length",
		description = "How much of a capped list each overlay draws. \"Everything\" really is"
			+ " everything, however tall that gets -- a shopping list you can only see the top"
			+ " of is one you still have to finish somewhere else.",
		section = overlaySection,
		position = 3
	)
	default OverlayListLength overlayListLength()
	{
		return OverlayListLength.NORMAL;
	}

	@ConfigItem(
		keyName = "overlayCompactText",
		name = "Smaller text",
		description = "Set overlay rows in the game's small font, fitting about a third more"
			+ " of them in the same panel. Harder to read over bright scenery, which is why"
			+ " it is a choice rather than the default.",
		section = overlaySection,
		position = 4
	)
	default boolean overlayCompactText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayShowOverflow",
		name = "Say when a list is cut short",
		description = "Show the \"...and 58 more\" line at the end of a capped list."
			+ " Turning it off reclaims a row per section; the caps themselves do not move,"
			+ " so the panel simply stops explaining that it is showing you the top of a list.",
		section = overlaySection,
		position = 5
	)
	default boolean overlayShowOverflow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayNextQuest",
		name = "Next on your route",
		description = "The next quests on your saved Quest Route route, and how far through it you are.",
		section = overlaySection,
		position = 10
	)
	default boolean overlayNextQuest()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayNextQuestWidth",
		name = "- width",
		description = "Width for the route panel alone. Leave it matching the shared setting"
			+ " unless you want this one narrower or wider than the rest.",
		section = overlaySection,
		position = 11
	)
	default PanelWidth overlayNextQuestWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayNextQuestLength",
		name = "- length",
		description = "How much of the route preview to list. Short is usually right here -- it answers \"what now\", not \"show me the whole plan\".",
		section = overlaySection,
		position = 13
	)
	default PanelListLength overlayNextQuestLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayNextQuestItems",
		name = "- list its items",
		description = "List the next quest's own items under it, ticked against what you hold."
			+ " Answers \"can I start this now\" without opening anything.",
		section = overlaySection,
		position = 12
	)
	default boolean overlayNextQuestItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayPickups",
		name = "Get while you're here",
		description = "Items you can pick up during a quest you are already on, that other quests also want.",
		section = overlaySection,
		position = 20
	)
	default boolean overlayPickups()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayPickupsWidth",
		name = "- width",
		description = "Width for the pickups panel alone.",
		section = overlaySection,
		position = 21
	)
	default PanelWidth overlayPickupsWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayPickupsLength",
		name = "- length",
		description = "How many pickups to list.",
		section = overlaySection,
		position = 22
	)
	default PanelListLength overlayPickupsLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@Range(min = 1, max = 12)
	@ConfigItem(
		keyName = "pickupsLookahead",
		name = "- how far ahead",
		description = "How many quests down the route count as \"here\": the one in hand plus"
			+ " the next few. Higher catches more detours worth making; lower keeps the list"
			+ " about where you actually are, which is what it is for.",
		section = overlaySection,
		position = 23
	)
	default int pickupsLookahead()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "overlayItems",
		name = "Everything you need",
		description = "Every item your doable quests need, ticked against what the client can see you hold.",
		section = overlaySection,
		position = 30
	)
	default boolean overlayItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayItemsWidth",
		name = "- width",
		description = "Width for the shopping list alone. This is the panel that most wants"
			+ " the wider settings -- it is the one with sixty-odd rows in it.",
		section = overlaySection,
		position = 31
	)
	default PanelWidth overlayItemsWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayItemsLength",
		name = "- length",
		description = "How much of the shopping list to show. This is the panel that wants Everything: a list you can only see the top of is one you still have to finish elsewhere.",
		section = overlaySection,
		position = 33
	)
	default PanelListLength overlayItemsLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayItemsHideHeld",
		name = "- hide what you have",
		description = "Drop the \"already held\" section, leaving only what you are short of."
			+ " The running total at the top still counts them, so nothing is hidden without"
			+ " being accounted for.",
		section = overlaySection,
		position = 32
	)
	default boolean overlayItemsHideHeld()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayShopRoute",
		name = "Shop route: where and why",
		description = "While a shop route sent to Shortest Path is active, the next few stops:"
			+ " the item, how many short you are, the shop it sends you to, and which quest"
			+ " wants it. Replaces \"Everything you need\" for as long as the route is active.",
		section = overlaySection,
		position = 34
	)
	default boolean overlayShopRoute()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayShopRouteWidth",
		name = "- width",
		description = "Width for the shop route panel alone.",
		section = overlaySection,
		position = 35
	)
	default PanelWidth overlayShopRouteWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayShopRouteLength",
		name = "- length",
		description = "How many stops ahead to show. Three is the baseline at Normal --"
			+ " enough to say where you are heading without turning into the whole shopping list.",
		section = overlaySection,
		position = 36
	)
	default PanelListLength overlayShopRouteLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	/**
	 * <b>On by default, unlike every other overlay here.</b> The others are
	 * opt-in because they are on screen the whole time once ticked and only
	 * some players want each. This one draws only while the Grand Exchange
	 * window is actually open, and only for an account that has a Grand
	 * Exchange at all -- so left off it is not a quiet default, it is a
	 * feature nobody ever finds. The quantity to buy is on it and nowhere
	 * else, which makes a hidden-by-default version actively misleading:
	 * questpath's row in the search box fills in an item name and never a
	 * number.
	 */
	@ConfigItem(
		keyName = "overlayGrandExchange",
		name = "Grand Exchange: queued, next, left",
		description = "While the Grand Exchange is open: the one item to buy next and how many,"
			+ " what you already have on offer and how far along it is, and what is still left"
			+ " after that. Never shown to an ironman, who has no Grand Exchange to use.",
		section = overlaySection,
		position = 37
	)
	default boolean overlayGrandExchange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayGrandExchangeWidth",
		name = "- width",
		description = "Width for the Grand Exchange panel alone.",
		section = overlaySection,
		position = 38
	)
	default PanelWidth overlayGrandExchangeWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayGrandExchangeLength",
		name = "- length",
		description = "How much of what is left to list under the next item. \"Next\" and"
			+ " \"Queued\" are barely affected -- one item and eight slots are the whole"
			+ " point of the panel, and both are small however this is set.",
		section = overlaySection,
		position = 39
	)
	default PanelListLength overlayGrandExchangeLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "recommendedItems",
		name = "Recommended items",
		description = "Include each quest's recommended items (teleports, potions, gear) in the"
			+ " shopping lists and routes, after the required ones. Recommended coins are shown"
			+ " as a balance to bring, never as something to buy. Needs a quest graph that carries"
			+ " them (version 13 or later).",
		section = overlaySection,
		position = 40
	)
	default boolean recommendedItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recommendedTeleportForm",
		name = "- teleports as",
		description = "Whether a recommended teleport goes on the Grand Exchange list as the tablet"
			+ " or as the runes to cast it (Camelot: 5 air, 1 law). Runes are only offered"
			+ " when your Magic level can cast the spell; otherwise the tablet is listed"
			+ " whatever this says. The walking shop route always lists runes, because no"
			+ " shop sells tablets.",
		section = overlaySection,
		position = 41
	)
	default TeleportForm recommendedTeleportForm()
	{
		return TeleportForm.TABLET;
	}

	@ConfigItem(
		keyName = "overlayCombatXp",
		name = "Combat XP to next level",
		description = "What each stat would cost to buy the next combat level, cheapest first.",
		section = overlaySection,
		position = 40
	)
	default boolean overlayCombatXp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayCombatXpWidth",
		name = "- width",
		description = "Width for the combat panel alone.",
		section = overlaySection,
		position = 41
	)
	default PanelWidth overlayCombatXpWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayCombatXpLength",
		name = "- length",
		description = "How many stats to price. There are only seven, so this rarely needs changing.",
		section = overlaySection,
		position = 42
	)
	default PanelListLength overlayCombatXpLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayDiaryRouteWidth",
		name = "Diary route -- width",
		description = "Width for the diary route panel alone. Whether the panel shows at all is"
			+ " questJournalFilter, above -- see that item's own description.",
		section = overlaySection,
		position = 44
	)
	default PanelWidth overlayDiaryRouteWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayDiaryRouteLength",
		name = "Diary route -- length",
		description = "How much of the current task's detail to show.",
		section = overlaySection,
		position = 45
	)
	default PanelListLength overlayDiaryRouteLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayBuildRules",
		name = "Build rules",
		description = "Your declared caps and no-xp skills, and how much headroom is left in each.",
		section = overlaySection,
		position = 50
	)
	default boolean overlayBuildRules()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayBuildRulesWidth",
		name = "- width",
		description = "Width for the build rules panel alone.",
		section = overlaySection,
		position = 51
	)
	default PanelWidth overlayBuildRulesWidth()
	{
		return PanelWidth.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayBuildRulesLength",
		name = "- length",
		description = "How many rules to list per section.",
		section = overlaySection,
		position = 53
	)
	default PanelListLength overlayBuildRulesLength()
	{
		return PanelListLength.MATCH_SHARED;
	}

	@ConfigItem(
		keyName = "overlayBuildRulesSections",
		name = "- which rules",
		description = "Blocking rules are walls -- cross one and the build is over."
			+ " Informational ones are goals and soft caps: worth tracking, never urgent.",
		section = overlaySection,
		position = 52
	)
	default BuildRuleSections overlayBuildRulesSections()
	{
		return BuildRuleSections.BOTH;
	}

	@ConfigItem(
		keyName = "buildRulesShowAll",
		name = "Show all rules",
		description = "The Combat tab's own Build rules list, not the in-game overlay above:"
			+ " off shows only the rules that would end the build (NO_XP, HARD_CAP), which is"
			+ " what most people are checking for. On also lists goals and soft caps.",
		section = overlaySection,
		position = 54
	)
	default boolean buildRulesShowAll()
	{
		return false;
	}

	// ------------------------------------------------------------------
	// Shops.
	//
	// Deliberately independent of the "Everything you need" overlay. The
	// shopping list answers "what do I need"; this answers "is any of it in
	// front of me", and someone who does not want a panel on screen all day
	// may well still want the shop marked when they open one.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "shopHighlight",
		name = "Mark what you need in shops",
		description = "While a shop is open, outline the items your quests still need and show"
			+ " how many to buy. Works whether or not the \"Everything you need\" overlay is on.",
		section = shopSection,
		position = 1
	)
	default boolean shopHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopHighlightCounts",
		name = "Show how many to buy",
		description = "Print the outstanding count on the marked item, rather than only"
			+ " outlining it.",
		section = shopSection,
		position = 2
	)
	default boolean shopHighlightCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shopHighlightColour",
		name = "Outline colour",
		description = "The colour a needed item is outlined in. The buy count is drawn in green"
			+ " above the slot regardless, because green is the one colour a shop's own"
			+ " interface never uses.",
		section = shopSection,
		position = 3
	)
	default java.awt.Color shopHighlightColour()
	{
		return OverlayTheme.WARN;
	}

	@ConfigItem(
		keyName = "floatingNotes",
		name = "Float a note where it happened",
		description = "Show a short line in the world -- above you, where you are standing --"
			+ " when you finish gathering something your plan needs. The chat box already says"
			+ " this, and the chat box is exactly where nobody is looking at the moment it"
			+ " happens. Fades after a few seconds and never shows more than three at once.",
		section = shopSection,
		position = 5
	)
	default boolean floatingNotes()
	{
		// Off by default, like every other notification-shaped toggle in this
		// plugin -- see chatOnItemComplete and shopArrivalPrompt.
		return false;
	}

	@ConfigItem(
		keyName = "shopNearbyPrompt",
		name = "Say when a shop nearby has what you need",
		description = "As you come up on a shop stocking something your plan still needs, once,"
			+ " in chat and over the shopkeeper. This is the reminder one -- it fires whether or"
			+ " not that item is the next stop on your route, because a shop you are walking past"
			+ " is worth knowing about either way.",
		section = shopSection,
		position = 3
	)
	default boolean shopNearbyPrompt()
	{
		// Off by default like every other notification here, but this is the
		// one asked for by name: "there should also be an option for when an
		// item is available nearby -- that's the one I really want."
		return false;
	}

	@ConfigItem(
		keyName = "floatingNotePlacement",
		name = "Float notes",
		description = "Where a floating note appears. Above the shopkeeper reads as that NPC"
			+ " speaking, which is the point -- but items get finished by chopping trees as"
			+ " often as by buying things, and there is no shopkeeper then, so both shopkeeper"
			+ " options fall back to above you rather than showing nothing.",
		section = shopSection,
		position = 6
	)
	default NotePlacement floatingNotePlacement()
	{
		// Above the shopkeeper is what this was asked for; the fallback above
		// means it still says something when there is nobody to say it.
		return NotePlacement.ABOVE_SHOPKEEPER;
	}

	@ConfigItem(
		keyName = "shopArrivalPrompt",
		name = "Say when a shop sells what you need",
		description = "One chat line the moment you open a shop selling something your plan"
			+ " still needs. Not distance-based proximity -- this only notices a shop once its"
			+ " interface is open, never before you walk up to it -- and it says so once per"
			+ " visit, not every tick.",
		section = shopSection,
		position = 4
	)
	default boolean shopArrivalPrompt()
	{
		// Off by default, like every notification-shaped toggle in this
		// plugin -- see chatOnItemComplete.
		return false;
	}

	// ------------------------------------------------------------------
	// Ground items.
	//
	// Strictly things already lying on the floor. questpath knows where items
	// drop from, and turning that into instructions would carpet the screen
	// with kill orders for a shopping list -- so the rule is simply that the
	// item is physically there. A pile of bones on the ground is worth
	// marking; "go and make some bones" is not a thing this will ever say.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "groundHighlight",
		name = "Mark what you need on the ground",
		description = "Outline items lying on the floor that your plan still needs, so you"
			+ " pick them up in passing instead of buying them later.",
		section = groundSection,
		position = 1
	)
	default boolean groundHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groundHighlightCounts",
		name = "Show how many to pick up",
		description = "Print the outstanding count beside the item name.",
		section = groundSection,
		position = 2
	)
	default boolean groundHighlightCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groundHighlightColour",
		name = "Marker colour",
		description = "The colour a needed item on the floor is marked in.",
		section = groundSection,
		position = 3
	)
	default java.awt.Color groundHighlightColour()
	{
		return OverlayTheme.WARN;
	}

	@ConfigItem(
		keyName = "groundTakeLeftClick",
		name = "Left-click take items you need",
		description = "Make \"Take\" the left-click option for a ground item your plan still"
			+ " needs, so picking it up in passing does not need a right click. Never hides or"
			+ " reorders anything for an item you do not need.",
		section = groundSection,
		position = 4
	)
	default boolean groundTakeLeftClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "floorItemArrow",
		name = "Bouncing arrow over needed items",
		description = "Draw a bouncing arrow above a needed item lying within the floor-item range,"
			+ " on top of the outline.",
		section = groundSection,
		position = 5
	)
	default boolean floorItemArrow()
	{
		return true;
	}

	@Range(min = 1, max = 15)
	@ConfigItem(
		keyName = "floorItemRadius",
		name = "Floor-item range (tiles)",
		description = "How close a needed item on the floor must be for the arrow and for"
			+ " \"<item> nearby\" to be said.",
		section = groundSection,
		position = 6
	)
	default int floorItemRadius()
	{
		return 10;
	}

	// ------------------------------------------------------------------
	// Chat.
	//
	// One line, once, at the moment a thing stops being outstanding. The
	// panels can only be read by looking at them, and the moment worth
	// knowing about -- the eleventh log going into your bags -- is the one
	// moment you are looking at the ground instead.
	// ------------------------------------------------------------------

	// ------------------------------------------------------------------
	// Bank.
	//
	// The one screen where the shopping list stops being a list and becomes
	// a series of withdrawals. Marks the real slots rather than replacing the
	// grid with a drawn one: a drawn list cannot be withdrawn from, and
	// withdrawing is the whole reason to be standing here.
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "bankHighlight",
		name = "Mark what you need in the bank",
		description = "Outline the items your plan needs while the bank is open. Toggle it"
			+ " from the Quest Route button in the bank's title bar.",
		section = bankSection,
		position = 1
	)
	default boolean bankHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankHighlightCounts",
		name = "Show how many more to find",
		description = "Print the outstanding count above a marked slot.",
		section = bankSection,
		position = 2
	)
	default boolean bankHighlightCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankHighlightShortOnly",
		name = "Only mark what you are short of",
		description = "Leave items you already have enough of unmarked. Off, they are"
			+ " outlined in green so you can see the plan is covered without counting.",
		section = bankSection,
		position = 3
	)
	default boolean bankHighlightShortOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "bankHighlightColour",
		name = "Marker colour",
		description = "The colour an item you still need is outlined in.",
		section = bankSection,
		position = 4
	)
	default java.awt.Color bankHighlightColour()
	{
		return OverlayTheme.WARN;
	}

	@ConfigItem(
		keyName = "bankTab",
		name = "Rearrange the bank itself",
		description = "Group the bank's own grid by what your plan needs -- what you ran out"
			+ " of, then what you are short of, then what is ready, then everything else."
			+ " Items you own stay withdrawable; ones you own none of are shown greyed so"
			+ " you can see they are missing at all.",
		section = bankSection,
		position = 5
	)
	default boolean bankTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankButton",
		name = "Show the bank title-bar button",
		description = "The button on the bank's title row that toggles the needs marks. Turn it"
			+ " off if another plugin needs that space.",
		section = bankSection,
		position = 6
	)
	default boolean bankButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankList",
		name = "Show the ordered list too",
		description = "Alongside the marks, list what the plan needs beside the bank:"
			+ " what you are short of first, then what you already have.",
		section = bankSection,
		position = 6
	)
	default boolean bankList()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatOnItemComplete",
		name = "Say when an item is fully collected",
		description = "Print a line in the chat the moment you have all of something your"
			+ " plan needs, naming the quest it is for.",
		section = chatSection,
		position = 1
	)
	default boolean chatOnItemComplete()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lampAdvisor",
		name = "Mark up experience lamps",
		description = "When a lamp asks which skill to put its experience in, grey the skills"
			+ " your build rules close and box the ones worth choosing. The choice cannot be"
			+ " taken back and the interface gives you no help with it.",
		section = lampSection,
		position = 1
	)
	default boolean lampAdvisor()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lampGreyBlocked",
		name = "Grey out skills a rule closes",
		description = "Wash out any skill your build rules forbid: no experience at all, a cap"
			+ " this lamp would cross, a cap already reached, or a level the lamp itself"
			+ " requires. This is the part that stops a mistake, so it is on by default and"
			+ " kept separate from the suggestions.",
		section = lampSection,
		position = 2
	)
	default boolean lampGreyBlocked()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lampSuggestions",
		name = "Suggest where to put it",
		description = "How many targets to box. Turning this off leaves the greying on --"
			+ " deciding for yourself is not the same as wanting no warnings.",
		section = lampSection,
		position = 3
	)
	default LampSuggestions lampSuggestions()
	{
		return LampSuggestions.TOP_THREE;
	}

	@ConfigItem(
		keyName = "lampBestColour",
		name = "Best target colour",
		description = "The colour the single best skill is boxed in.",
		section = lampSection,
		position = 4
	)
	default java.awt.Color lampBestColour()
	{
		return OverlayTheme.GOOD;
	}

	@ConfigItem(
		keyName = "lampNextColour",
		name = "Runner-up colour",
		description = "The colour the second and third choices are boxed in. A different colour"
			+ " from the best one, so the top pick stays identifiable with three boxes on"
			+ " screen at once.",
		section = lampSection,
		position = 5
	)
	default java.awt.Color lampNextColour()
	{
		return OverlayTheme.ACCENT;
	}
}
