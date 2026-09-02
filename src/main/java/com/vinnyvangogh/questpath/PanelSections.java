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
 * The blocked ones are all blocked on the same thing: the quest graph. Those
 * answers need every quest's requirements, rewards and prerequisites, which
 * questpath ingests from the wiki and the plugin does not carry yet. The plan
 * for getting it here is docs/questpath-plugin-plan.md in the Go repo — a
 * bundled export, plus the planner ported to Java against generated golden
 * vectors.
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
		Status.NOT_BUILT,
		"Your saved route order from questpath, rather than every doable quest at once. The order "
			+ "lives in questpath and is not sent yet."),

	QUEST_VERDICT(
		Tab.QUESTS,
		"Can I do this quest?",
		Status.LIVE,
		null),

	ITEMS(
		Tab.ITEMS,
		"Quest items",
		Status.NOT_BUILT,
		"What the next quest needs, ticked off against what is actually in your bank. The bank half "
			+ "is done — questpath is being sent your bank, inventory and equipment — but matching "
			+ "them to a quest's requirements needs the quest graph, which is not here yet."),

	QUEST_BROWSER(
		Tab.QUESTS,
		"Quest browser",
		Status.NOT_BUILT,
		"Search and filter the list above, rather than the first eight of each group. Quest Helper "
			+ "already does walkthroughs better than this ever would; the point here is the verdict.");

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
