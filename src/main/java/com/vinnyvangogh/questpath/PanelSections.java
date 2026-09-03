package com.vinnyvangogh.questpath;

import net.runelite.api.Skill;

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
		Tab.BUILD,
		"Build rules",
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
		// The icon is the game's own skill-tab art, via SkillIconManager --
		// no asset to ship, no sprite id to guess wrong, and it already looks
		// like the rest of the client.
		//
		// Two of these are honest metaphors and three are borrowed. A sword
		// for combat and a hammer for build rules read on sight; a slayer
		// helm for quests, a needle for items and a rune for sync are the
		// least-bad skill icons for concepts that have no skill. The label
		// survives as the tooltip precisely because of that -- an icon that
		// has to be learned is worse than a word until it is.
		//
		// Do not reach for Skill.OVERALL here. It is declared in
		// net.runelite.api.Skill and is null at runtime in 1.12.37 -- the
		// field exists for binary compatibility and the static initialiser
		// never gives it a value. It compiles, and the tab silently drops to
		// its text fallback. PanelSectionsTest.everyTabHasAnIcon is what
		// found that, and is what will find it again.
		COMBAT("Combat", Skill.ATTACK),
		BUILD("Build", Skill.CONSTRUCTION),
		QUESTS("Quests", Skill.SLAYER),
		ITEMS("Items", Skill.CRAFTING),
		SYNC("Sync", Skill.MAGIC);

		private final String label;
		private final Skill icon;

		Tab(String label, Skill icon)
		{
			this.label = label;
			this.icon = icon;
		}

		String label()
		{
			return label;
		}

		/** The skill whose tab icon stands for this tab. */
		Skill icon()
		{
			return icon;
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
