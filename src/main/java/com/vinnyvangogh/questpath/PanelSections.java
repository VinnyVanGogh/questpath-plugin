package com.vinnyvangogh.questpath;


/**
 * Every section the sidebar will carry, and what is behind each one today.
 *
 * The panel shows all of them from the start rather than growing one at a
 * time. A section that is not built yet says so in those words — not "no
 * data", not an empty list, and never a plausible-looking placeholder number.
 * Someone reading this panel has to be able to tell "questpath does not know"
 * from "questpath has not been taught this yet", because the first is
 * something to fix in the app and the second is something to wait for.
 *
 * Every section is LIVE as of the bundled quest graph landing (see
 * docs/questpath-plugin-plan.md in the Go repo): NEXT_QUEST and QUEST_BROWSER
 * were the last two waiting on it. A future section that is not built yet
 * still says so in those words here rather than rendering an empty state.
 */
enum PanelSections
{
	COMBAT(
		Tab.COMBAT,
		"Combat",
		Status.LIVE,
		null),

	LOCKED_GEAR(
		Tab.COMBAT,
		"Locked gear",
		Status.LIVE,
		null),

	CEILING(
		Tab.COMBAT,
		"Free Ranged / Magic",
		Status.LIVE,
		null),

	COST(
		Tab.COMBAT,
		"Cost of the next combat level",
		Status.LIVE,
		null),

	SYNC(
		Tab.SYNC,
		"questpath sync",
		Status.LIVE,
		null),

	BUILD_RULES(
		Tab.COMBAT,
		"Build rules",
		Status.LIVE,
		null),

	SKILL_PLAN(
		Tab.SKILLS,
		"What a level costs",
		Status.LIVE,
		null),

	NEXT_QUEST(
		Tab.QUESTS,
		"Next on your route",
		Status.LIVE,
		null),

	QUEST_VERDICT(
		Tab.QUESTS,
		"Can I do this quest?",
		Status.LIVE,
		null),

	ITEMS(
		Tab.ITEMS,
		"Quest items",
		Status.LIVE,
		null),

	QUEST_BROWSER(
		Tab.QUESTS,
		"Quest browser",
		Status.LIVE,
		null),

	ROUTE_OUTSTANDING(
		Tab.QUESTS,
		"Still needs work",
		Status.LIVE,
		null),

	ROUTE_PARTIAL(
		Tab.QUESTS,
		"Start, never finish",
		Status.LIVE,
		null),

	DIARY_ROUTE(
		Tab.QUESTS,
		"Diary route",
		Status.LIVE,
		null),

	DIARY_JOURNAL(
		Tab.QUESTS,
		"Diary journal",
		Status.LIVE,
		null),

	OVERLAY_SETTINGS(
		Tab.VIEW,
		"Overlays",
		Status.LIVE,
		null),

	GROUP_MEMBERS(
		Tab.GROUP,
		"Your group",
		Status.LIVE,
		null),

	GROUP_TOGETHER(
		Tab.GROUP,
		"Do together next",
		Status.LIVE,
		null),

	GROUP_FLAGGED(
		Tab.GROUP,
		"Breaks someone's build",
		Status.LIVE,
		null),

	GROUP_ITEMS(
		Tab.GROUP,
		"Who brings what",
		Status.LIVE,
		null),

	GROUP_DONE_BY_SOME(
		Tab.GROUP,
		"Done by some of you",
		Status.LIVE,
		null);

	/**
	 * Which tab a section lives under.
	 *
	 * Grouped rather than one tab per section: the sidebar is 225px wide, and
	 * nine tabs would each be four characters of unreadable label. The
	 * groupings are by the question being asked, not by what happens to be
	 * built — "can I do this quest", "what is next", and "show me every quest"
	 * are three views of one thing.
	 */
	enum Tab
	{
		// The game's own interface art, via SpriteManager -- no asset to ship,
		// no sprite to draw badly, and it already looks like the rest of the
		// client because it is the rest of the client.
		//
		// These replaced a set of borrowed skill icons. Four of the six were
		// metaphors that had to be learned -- a slayer helm for quests, a
		// needle for items, a rune for sync -- which is the one thing an icon
		// must not be. Where the game has the actual icon for a concept, that
		// is the icon: the quest diary, the inventory, the options wrench.
		//
		// COMBAT carries the three bars (RS2_TAB_STATS, sprite 775) rather
		// than the crossed swords (TAB_COMBAT, sprite 168), because Tab.BUILD
		// merged into it. "What is my build" and "what can I hit" turned out
		// to be one question, not two, once the build rules sit in the same
		// tab as the combat math -- the three bars read as both at once,
		// where crossed swords would have read as combat alone with the
		// build rules stranded underneath a picture that never mentions
		// them. Sprite 168 is deliberately unused as a result: left named
		// here, rather than reassigned to something else, so the choice
		// reads as intentional and nobody goes looking for where "combat"
		// went.
		//
		// Sync is the only one with no interface equivalent, because pushing
		// your account somewhere is not a thing the game does. Wind surge is
		// the stand-in: a surge is the closest the spellbook comes to "send
		// this outward", and it is at least a picture nobody will mistake for
		// one of the others. SPELL_TELE_BLOCK (352) is the alternative and is
		// one constant away.
		//
		// The label survives as the tooltip either way -- an icon that has to
		// be learned needs a way to be learned, and it is also the fallback
		// when a sprite cannot be produced.
		// Sprite ids given as numbers rather than through
		// net.runelite.api.SpriteID, which is deprecated in favour of a
		// gameval namespace that splits every sprite into its own nested
		// class. These six ids are stable game data, they are named right
		// here, and PanelSectionsTest pins that each is real and distinct --
		// which is more than the constant was buying.
		COMBAT("Combat", 775),          // RS2_TAB_STATS, the three bars -- combat and build are one question
		SKILLS("Skills", 1197),         // OrbXp.ACTIVATED, the experience orb
		QUESTS("Quests", 776),          // TAB_QUESTS, the quest diary
		ITEMS("Items", 777),            // RS2_TAB_INVENTORY, the backpack
		SYNC("Sync", 362),              // SPELL_WIND_SURGE
		GROUP("Group", 781),            // RS2_TAB_FRIENDS_CHAT, the party/chat-channel tab
		VIEW("View", 785);              // RS2_TAB_OPTIONS, the wrench

		private final String label;
		private final int sprite;

		Tab(String label, int sprite)
		{
			this.label = label;
			this.sprite = sprite;
		}

		String label()
		{
			return label;
		}

		/** The game sprite drawn on this tab. */
		int sprite()
		{
			return sprite;
		}
	}

	enum Status
	{
		/** Built, showing real data. */
		LIVE,
		/** Planned, not written. Says so rather than rendering an empty state. */
		NOT_BUILT
	}

	private final Tab tab;
	private final String title;
	private final Status status;
	private final String plan;

	PanelSections(Tab tab, String title, Status status, String plan)
	{
		this.tab = tab;
		this.title = title;
		this.status = status;
		this.plan = plan;
	}

	Tab tab()
	{
		return tab;
	}

	String title()
	{
		return title;
	}

	Status status()
	{
		return status;
	}

	/** What this section will show once it exists, or null when it already does. */
	String plan()
	{
		return plan;
	}
}
