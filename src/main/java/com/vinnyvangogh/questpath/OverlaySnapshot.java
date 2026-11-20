package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Everything the overlays draw, read once and frozen.
 *
 * <p>Overlays render every frame; the things they show -- a bank's contents, a
 * quest verdict, the account's route -- cost a graph walk each to work out.
 * Recomputing per frame would be a walk of 218 quests fifty times a second for
 * a picture that changes when a quest finishes. So the plugin computes this
 * when something actually happens and the overlays only ever read it.
 *
 * <p>Immutable, and published through a volatile field: it is built on the
 * client thread and read on the client thread, but the reference crosses a
 * refresh boundary, and a half-built snapshot on screen would be worse than a
 * stale one.
 *
 * <p>Any field may be null, and every overlay must say so rather than draw an
 * empty state. "The client has not loaded quest states yet" and "you have
 * nothing to do" are different sentences, and only one of them is ever true.
 */
@Getter
@Accessors(fluent = true)
final class OverlaySnapshot
{
	/** Real levels, never boosted. Null before the client has reported any. */
	private final CombatStats stats;
	/** Experience per skill, as the client reports it. */
	private final SkillXp xp;
	/**
	 * The saved route and where along it the account is, or null when that
	 * cannot be judged yet -- a client whose quest varbits have not loaded
	 * reports zero finished quests, which is indistinguishable from an
	 * account that has genuinely finished none.
	 */
	private final NextQuestStatus route;
	/**
	 * What the doable quests need, ticked against the bank, or null when there
	 * is no bundle or the client has shown no containers yet.
	 */
	private final QuestItems items;
	/**
	 * The account's declared limits. Empty means questpath has not sent them,
	 * never that the account is unconstrained -- the plugin cannot learn them
	 * any other way.
	 */
	private final List<BuildRule> buildRules;
	/** The logged-in character, or null when there is not one. */
	private final String characterName;
	/** Quest points as the game itself counts them. */
	private final int questPoints;

	OverlaySnapshot(CombatStats stats, SkillXp xp, NextQuestStatus route, QuestItems items,
		List<BuildRule> buildRules, String characterName, int questPoints)
	{
		this.stats = stats;
		this.xp = xp;
		this.route = route;
		this.items = items;
		this.buildRules = buildRules == null ? Collections.emptyList()
			: Collections.unmodifiableList(buildRules);
		this.characterName = characterName;
		this.questPoints = questPoints;
	}
}
