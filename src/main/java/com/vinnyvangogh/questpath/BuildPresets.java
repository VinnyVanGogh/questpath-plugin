package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The named builds the Quest Route web app offers on its setup page, with the
 * same slugs, the same rules and the same defaults, so choosing "1 Defence
 * pure" here and there means the same limits.
 *
 * <p>Ported from the web app's {@code internal/builds/presets.go}. A cap of 99
 * is not listed: it forbids nothing, and a rule that forbids nothing is noise
 * in the rules list. Goals are kept, because they warn.
 *
 * <p>A preset is only a bundle of {@link BuildRule}s. Applying one writes those
 * rules through {@link LocalBuildRules}, after which they are ordinary local
 * rules and can be edited one by one like any other.
 */
final class BuildPresets
{
	/** Which half of the game a preset belongs to, matching the web app's "f2p"/"members". */
	enum Category
	{
		F2P("Free-to-play"),
		MEMBERS("Members");

		private final String label;

		Category(String label)
		{
			this.label = label;
		}

		String label()
		{
			return label;
		}
	}

	/** One preset: slug (stored, frozen), label (shown), and its rules. */
	static final class Preset
	{
		private final String slug;
		private final String label;
		private final Category category;
		private final List<BuildRule> rules;

		Preset(String slug, String label, Category category, BuildRule... rules)
		{
			this.slug = slug;
			this.label = label;
			this.category = category;
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

		Category category()
		{
			return category;
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

	private static BuildRule no(String skill)
	{
		return BuildRule.local(skill, BuildRule.NO_XP, 0, "");
	}

	private static BuildRule cap(String skill, int level)
	{
		return BuildRule.local(skill, BuildRule.HARD_CAP, level, "");
	}

	private static BuildRule goal(String skill, int level)
	{
		return BuildRule.local(skill, BuildRule.GOAL, level, "");
	}

	static final List<Preset> ALL = Collections.unmodifiableList(Arrays.asList(
		new Preset("f2p_one_defence_pure", "F2P 1 Defence pure", Category.F2P,
			no("Defence"), cap("Attack", 40), cap("Prayer", 45), goal("Strength", 99), goal("Hitpoints", 99)),
		new Preset("f2p_rune_warhammer_pure", "F2P rune warhammer pure", Category.F2P,
			no("Attack"), no("Defence"), cap("Ranged", 67), cap("Magic", 67), cap("Prayer", 31)),
		new Preset("f2p_20_attack_tier", "F2P 20 Attack tier", Category.F2P,
			cap("Attack", 20), no("Defence"), cap("Ranged", 79), cap("Magic", 79), cap("Prayer", 31)),
		new Preset("f2p_30_attack_tier", "F2P 30 Attack tier", Category.F2P,
			cap("Attack", 30), no("Defence"), cap("Ranged", 86), cap("Magic", 86), cap("Prayer", 31)),
		new Preset("f2p_40_attack_melee_pure", "F2P 40 Attack melee pure", Category.F2P,
			cap("Attack", 40), no("Defence"), cap("Ranged", 40), cap("Magic", 93), cap("Prayer", 43)),
		new Preset("f2p_adamant_pure", "F2P adamant pure", Category.F2P,
			cap("Defence", 30), cap("Attack", 49), cap("Prayer", 44)),
		new Preset("f2p_rune_pure", "F2P rune pure", Category.F2P,
			cap("Defence", 40), cap("Attack", 49), cap("Prayer", 45)),
		new Preset("members_one_defence_pure_50", "1 Defence pure (50 Attack)", Category.MEMBERS,
			no("Defence"), cap("Attack", 50), goal("Prayer", 31)),
		new Preset("members_one_defence_pure_60", "1 Defence pure (60 Attack)", Category.MEMBERS,
			no("Defence"), cap("Attack", 60), goal("Prayer", 52)),
		new Preset("members_one_defence_pure_75", "1 Defence pure (75 Attack)", Category.MEMBERS,
			no("Defence"), cap("Attack", 75), goal("Prayer", 63)),
		new Preset("members_13_defence_pure", "13 Defence pure", Category.MEMBERS,
			cap("Defence", 13)),
		new Preset("members_20_defence_initiate_pure", "20 Defence initiate pure", Category.MEMBERS,
			cap("Defence", 20)),
		new Preset("members_25_defence_clan_pure", "25 Defence clan pure", Category.MEMBERS,
			cap("Defence", 25), goal("Attack", 99), goal("Prayer", 99)),
		new Preset("members_40_defence_rune_pure", "40 Defence rune pure", Category.MEMBERS,
			cap("Defence", 40)),
		new Preset("members_void_pure", "Void pure", Category.MEMBERS,
			cap("Defence", 42), cap("Hitpoints", 42), goal("Attack", 42), goal("Strength", 42),
			goal("Ranged", 42), goal("Magic", 42), goal("Prayer", 22)),
		new Preset("members_berserker_45_50", "Berserker pure (50 Attack)", Category.MEMBERS,
			cap("Defence", 45), cap("Attack", 50), cap("Ranged", 90), cap("Magic", 94), goal("Hitpoints", 80)),
		new Preset("members_berserker_45_60", "Berserker pure (60 Attack)", Category.MEMBERS,
			cap("Defence", 45), cap("Attack", 60), cap("Magic", 94), goal("Hitpoints", 90)),
		new Preset("members_berserker_45_75", "Berserker pure (75 Attack)", Category.MEMBERS,
			cap("Defence", 45), cap("Attack", 75), cap("Magic", 94), goal("Hitpoints", 99)),
		new Preset("members_obsidian_mauler_1def", "Obsidian mauler (1 Defence)", Category.MEMBERS,
			no("Attack"), no("Defence"), no("Prayer"), cap("Magic", 50)),
		new Preset("members_obsidian_zerker_45def", "Obsidian zerker (45 Defence)", Category.MEMBERS,
			no("Attack"), cap("Defence", 45), cap("Magic", 50)),
		new Preset("members_obsidian_tank_60def", "Obsidian tank (60 Defence)", Category.MEMBERS,
			no("Attack"), cap("Defence", 60), cap("Magic", 50)),
		new Preset("members_obsidian_tank_75def", "Obsidian tank (75 Defence)", Category.MEMBERS,
			no("Attack"), cap("Defence", 75), cap("Magic", 50)),
		new Preset("members_moon_pure_50def", "Moon pure (50 Defence)", Category.MEMBERS,
			cap("Defence", 50), cap("Attack", 60)),
		new Preset("members_defence_pure_tank", "Defence pure tank", Category.MEMBERS,
			no("Attack"), no("Strength"), no("Ranged"), no("Magic"), no("Prayer"), goal("Defence", 75)),
		new Preset("members_medium_level_build", "Medium level build", Category.MEMBERS,
			cap("Defence", 70), cap("Attack", 75), goal("Strength", 99), goal("Prayer", 77)),
		new Preset("skiller", "Skiller (no combat XP)", Category.MEMBERS,
			no("Attack"), no("Strength"), no("Defence"), no("Hitpoints"), no("Ranged"), no("Magic"),
			no("Prayer")),
		new Preset("owner_pure", "Owner pure", Category.MEMBERS,
			no("Attack"), no("Defence"), no("Prayer"), cap("Hitpoints", 28), cap("Strength", 84),
			cap("Ranged", 55), cap("Magic", 55))));

	private BuildPresets()
	{
	}

	/** The preset with this slug, or null (a slug from a newer build). */
	static Preset find(String slug)
	{
		if (slug == null)
		{
			return null;
		}
		for (Preset preset : ALL)
		{
			if (preset.slug().equals(slug))
			{
				return preset;
			}
		}
		return null;
	}

	/** Presets of one category, in the web app's order. */
	static List<Preset> of(Category category)
	{
		List<Preset> out = new ArrayList<>();
		for (Preset preset : ALL)
		{
			if (preset.category() == category)
			{
				out.add(preset);
			}
		}
		return out;
	}

	/**
	 * The preset whose rules are exactly these, or null: how a saved set of
	 * rules is recognised as a named build again. Order and notes are ignored.
	 */
	static Preset identify(List<BuildRule> rules)
	{
		if (rules == null || rules.isEmpty())
		{
			return null;
		}
		for (Preset preset : ALL)
		{
			if (sameRules(preset.rules(), rules))
			{
				return preset;
			}
		}
		return null;
	}

	private static boolean sameRules(List<BuildRule> a, List<BuildRule> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (BuildRule rule : a)
		{
			boolean found = false;
			for (BuildRule other : b)
			{
				if (rule.skillName().toLowerCase(Locale.ROOT).equals(other.skillName().toLowerCase(Locale.ROOT))
					&& rule.type().equals(other.type()) && rule.value() == other.value())
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				return false;
			}
		}
		return true;
	}
}
