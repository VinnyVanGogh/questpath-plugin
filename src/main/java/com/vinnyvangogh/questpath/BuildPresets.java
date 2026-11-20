package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The three read-only starter builds offered when Build Guard is not
 * installed. Quest verdicts need some idea of what to protect, and these cover
 * the common cases without an editor: anything else is Build Guard's job.
 *
 * <p>The slugs are stored in the profile ({@code build_preset}) and are frozen.
 * Slugs from the old preset list that no longer exist read as "none chosen",
 * and the rules saved under them are kept as {@link #SAVED}.
 */
final class BuildPresets
{
	/** The player's own pre-Build-Guard rules, kept read-only. Not a member of {@link #ALL}. */
	static final String SAVED = "saved";

	/** One starter build: a stored slug, a shown label and its rules. */
	static final class Preset
	{
		private final String slug;
		private final String label;
		private final List<BuildRule> rules;

		Preset(String slug, String label, BuildRule... rules)
		{
			this.slug = slug;
			this.label = label;
			this.rules = Collections.unmodifiableList(Arrays.asList(rules));
		}

		String slug()
		{
			return slug;
		}

		String label()
		{
			return label;
		}

		List<BuildRule> rules()
		{
			return rules;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	static final Preset ZERKER = new Preset("zerker_60", "60 Attack zerker",
		BuildRule.local("Attack", BuildRule.HARD_CAP, 60, ""),
		BuildRule.local("Defence", BuildRule.HARD_CAP, 45, ""));

	static final Preset PURE = new Preset("pure_60", "60 Attack pure",
		BuildRule.local("Attack", BuildRule.HARD_CAP, 60, ""),
		BuildRule.local("Defence", BuildRule.NO_XP, 0, ""));

	static final Preset MAXED = new Preset("maxed_main", "Maxed main");

	static final List<Preset> ALL = Collections.unmodifiableList(Arrays.asList(ZERKER, PURE, MAXED));

	private BuildPresets()
	{
	}

	/** The starter with this slug, or null (none chosen, {@link #SAVED}, or a slug from another build). */
	static Preset find(String slug)
	{
		for (Preset preset : ALL)
		{
			if (preset.slug().equals(slug))
			{
				return preset;
			}
		}
		return null;
	}

	/**
	 * The rules the verdicts use when Build Guard is not supplying any.
	 *
	 * @param slug the profile's stored build, possibly null
	 * @param saved rules written in this plugin before Build Guard took over
	 */
	static List<BuildRule> localRules(String slug, List<BuildRule> saved)
	{
		Preset starter = find(slug);
		if (starter != null)
		{
			return starter.rules();
		}
		return saved == null ? Collections.<BuildRule>emptyList() : saved;
	}

	/** Whether the "Your saved build" entry is the one in force. */
	static boolean usesSaved(String slug, List<BuildRule> saved)
	{
		return find(slug) == null && saved != null && !saved.isEmpty();
	}
}
