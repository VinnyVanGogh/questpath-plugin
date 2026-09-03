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
 * <p>The group name is {@code questpath} and must never be renamed without a
 * migration: renaming a config group silently resets everyone's saved
 * settings.
 */
@ConfigGroup(QuestpathConfig.GROUP)
public interface QuestpathConfig extends Config
{
	String GROUP = "questpath";

	@ConfigSection(
		name = "In-game overlays",
		description = "Which Quest Path panels to draw over the game, and how."
			+ " These are easier to read on the Quest Path sidebar's View tab, which groups"
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
		description = "What Quest Path says in the game chat",
		position = 4,
		closedByDefault = true
	)
	String chatSection = "chat";

	@ConfigSection(
		name = "questpath sync",
		description = "Push this account's state to your own questpath instance",
		position = 0,
		closedByDefault = true
	)
	String syncSection = "sync";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Send my account state",
		description = "Push quest completion, levels and diaries to your questpath instance when they change."
			+ " questpath then stops polling WikiSync for this account.",
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
		name = "questpath address",
		description = "Where your questpath instance is listening. The default is what `make run` serves.",
		section = syncSection,
		position = 2
	)
	default String serverUrl()
	{
		return "http://localhost:8080";
	}

	@ConfigItem(
		keyName = "pairingToken",
		name = "Pairing token",
		description = "From `questpath -plugin-token`. It both authenticates the push and selects which account it writes.",
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
		description = "The next quests on your saved questpath route, and how far through it you are.",
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
			+ " from the Quest Path button in the bank's title bar.",
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
		keyName = "bankList",
		name = "Show the ordered list too",
		description = "Alongside the marks, list what the plan needs beside the bank:"
			+ " what you are short of first, then what you already have.",
		section = bankSection,
		position = 5
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
}
