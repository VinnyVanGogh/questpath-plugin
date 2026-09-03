package com.vinnyvangogh.questpath;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
		description = "Which questpath panels to draw over the game, and how",
		position = 1,
		closedByDefault = false
	)
	String overlaySection = "overlays";

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
		keyName = "overlayNextQuest",
		name = "Next on your route",
		description = "The next quests on your saved questpath route, and how far through it you are.",
		section = overlaySection,
		position = 2
	)
	default boolean overlayNextQuest()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayPickups",
		name = "Get while you're here",
		description = "Items you can pick up during a quest you are already on, that other quests also want.",
		section = overlaySection,
		position = 3
	)
	default boolean overlayPickups()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayItems",
		name = "Everything you need",
		description = "Every item your doable quests need, ticked against what the client can see you hold.",
		section = overlaySection,
		position = 4
	)
	default boolean overlayItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayCombatXp",
		name = "Combat XP to next level",
		description = "What each stat would cost to buy the next combat level, cheapest first.",
		section = overlaySection,
		position = 5
	)
	default boolean overlayCombatXp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayBuildRules",
		name = "Build rules",
		description = "Your declared caps and no-xp skills, and how much headroom is left in each.",
		section = overlaySection,
		position = 6
	)
	default boolean overlayBuildRules()
	{
		return false;
	}
}
