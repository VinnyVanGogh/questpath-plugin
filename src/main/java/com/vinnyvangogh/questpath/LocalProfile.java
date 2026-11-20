package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the player told Quest Route about this account, as it is saved.
 *
 * <p>One of these per RuneScape account, as JSON in RuneLite's config
 * ({@code questrouteprofile / profile.<account hash>}), written by
 * {@link LocalProfileStore}. It is the source of truth when no questpath
 * server is configured, which is the normal case: the account type, the quest
 * types chosen in first-run setup, and the progress and route derived from
 * them.
 *
 * <p>Plain data with Gson field names pinned by {@link SerializedName}: these
 * names are a file format that outlives a release, so a refactor that renames
 * a field must not silently rename a key. {@link #schemaVersion} is the
 * escape hatch for when the shape itself has to change.
 */
final class LocalProfile
{
	/** The file format this build writes and the newest it understands. */
	static final int SCHEMA_VERSION = 1;

	/** The kinds of account the setup offers. */
	enum AccountType
	{
		MAIN("Main"),
		IRONMAN("Ironman"),
		HCIM("Hardcore ironman"),
		UIM("Ultimate ironman"),
		GIM("Group ironman"),
		HCGIM("Hardcore group ironman");

		private final String label;

		AccountType(String label)
		{
			this.label = label;
		}

		String label()
		{
			return label;
		}
	}

	/** Which quests the route is built from. */
	enum QuestType
	{
		FREE_TO_PLAY("Free-to-play quests"),
		MEMBERS("Members quests");

		private final String label;

		QuestType(String label)
		{
			this.label = label;
		}

		String label()
		{
			return label;
		}
	}

	@SerializedName("schema_version")
	private int schemaVersion = SCHEMA_VERSION;

	/** The quest graph version the route was last computed against. */
	@SerializedName("graph_version")
	private int graphVersion;

	@SerializedName("account_type")
	private String accountType;

	@SerializedName("quest_types")
	private List<String> questTypes = new ArrayList<>();

	/**
	 * Slug of the named build chosen in setup ({@link BuildPresets}), or null
	 * for none/custom. Added after schema 1 shipped: an older file simply lacks
	 * it, which reads as "no preset", so no schema bump is needed.
	 */
	@SerializedName("build_preset")
	private String buildPreset;

	/** Quest names finished as of the last route computation. */
	@SerializedName("completed_quests")
	private List<String> completedQuests = new ArrayList<>();

	/** Quest names, in the order to do them. */
	@SerializedName("route")
	private List<String> route = new ArrayList<>();

	/**
	 * The player's own say over the route, the same marks the web app's quest
	 * pages offer: never do this, do this first, do this last, and whether the
	 * Quests tab hides what the build has closed off. Absent in an older file,
	 * which reads as none.
	 */
	@SerializedName("refused_quests")
	private List<String> refused = new ArrayList<>();
	@SerializedName("pinned_quests")
	private List<String> pinned = new ArrayList<>();
	@SerializedName("deferred_quests")
	private List<String> deferred = new ArrayList<>();
	@SerializedName("hide_blocked")
	private boolean hideBlocked;

	/** One of the player's marks on a quest, or the tab-wide blocked filter. */
	enum Mark
	{
		REFUSED,
		PINNED,
		DEFERRED,
		HIDE_BLOCKED
	}

	/** For Gson. */
	LocalProfile()
	{
	}

	List<String> refused()
	{
		return names(refused);
	}

	List<String> pinned()
	{
		return names(pinned);
	}

	List<String> deferred()
	{
		return names(deferred);
	}

	boolean hideBlocked()
	{
		return hideBlocked;
	}

	boolean has(Mark mark, String quest)
	{
		return mark != Mark.HIDE_BLOCKED && names(list(mark)).contains(quest);
	}

	/**
	 * A copy with one mark flipped. Refusing a quest clears its pin or defer,
	 * and pin and defer exclude each other, as the web app refuses the pair.
	 */
	LocalProfile toggled(Mark mark, String quest)
	{
		LocalProfile copy = withRoute(graphVersion, completedQuests(), route());
		if (mark == Mark.HIDE_BLOCKED)
		{
			copy.hideBlocked = !hideBlocked;
			return copy;
		}
		List<String> list = copy.list(mark);
		if (!list.remove(quest))
		{
			for (Mark other : Mark.values())
			{
				if (other != mark && other != Mark.HIDE_BLOCKED)
				{
					copy.list(other).remove(quest);
				}
			}
			list.add(quest);
		}
		return copy;
	}

	private List<String> list(Mark mark)
	{
		return mark == Mark.REFUSED ? refused : mark == Mark.PINNED ? pinned : deferred;
	}

	private static List<String> names(List<String> list)
	{
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	static LocalProfile create(AccountType accountType, Set<QuestType> questTypes)
	{
		LocalProfile profile = new LocalProfile();
		profile.accountType = accountType.name();
		for (QuestType type : questTypes)
		{
			profile.questTypes.add(type.name());
		}
		return profile;
	}

	int schemaVersion()
	{
		return schemaVersion;
	}

	/** The account type, or null when the file names one this build does not know. */
	AccountType accountType()
	{
		if (accountType == null)
		{
			return null;
		}
		try
		{
			return AccountType.valueOf(accountType);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/** Recognised quest types only; an unknown name is dropped, not fatal. */
	Set<QuestType> questTypes()
	{
		Set<QuestType> types = EnumSet.noneOf(QuestType.class);
		if (questTypes == null)
		{
			return types;
		}
		for (String name : questTypes)
		{
			try
			{
				types.add(QuestType.valueOf(name));
			}
			catch (IllegalArgumentException | NullPointerException e)
			{
				// A type from a newer build: ignored rather than refused.
			}
		}
		return types;
	}

	List<String> route()
	{
		return route == null ? Collections.emptyList() : Collections.unmodifiableList(route);
	}

	List<String> completedQuests()
	{
		return completedQuests == null
			? Collections.emptyList() : Collections.unmodifiableList(completedQuests);
	}

	int graphVersion()
	{
		return graphVersion;
	}

	/**
	 * Whether setup has been finished: a known account type and at least one
	 * quest type. Anything less is treated as no setup at all, so a
	 * half-written or hand-edited file puts the player back in front of the
	 * setup screen rather than in front of an empty route.
	 */
	boolean isUsable()
	{
		return schemaVersion >= 1 && schemaVersion <= SCHEMA_VERSION
			&& accountType() != null && !questTypes().isEmpty();
	}

	/** The chosen named build's slug, or null. */
	String buildPreset()
	{
		return buildPreset;
	}

	/** A new setup answer: account type, quest types and build, with no route yet. */
	static LocalProfile create(AccountType accountType, Set<QuestType> questTypes, String buildPreset)
	{
		LocalProfile profile = create(accountType, questTypes);
		profile.buildPreset = buildPreset;
		return profile;
	}

	/**
	 * These answers over this profile: account type, quest types and build come
	 * from {@code answers}; progress and the route stay, and are recomputed
	 * from the new answers on the next refresh.
	 */
	LocalProfile withSetup(LocalProfile answers)
	{
		LocalProfile copy = withRoute(graphVersion, completedQuests(), route());
		copy.accountType = answers.accountType;
		copy.questTypes = new ArrayList<>(answers.questTypes == null ? Collections.emptyList() : answers.questTypes);
		copy.buildPreset = answers.buildPreset;
		return copy;
	}

	/** A copy carrying a freshly computed route and progress. */
	LocalProfile withRoute(int graphVersion, List<String> completed, List<String> route)
	{
		LocalProfile copy = new LocalProfile();
		copy.buildPreset = buildPreset;
		copy.accountType = accountType;
		copy.questTypes = new ArrayList<>(questTypes == null ? Collections.emptyList() : questTypes);
		copy.graphVersion = graphVersion;
		copy.completedQuests = new ArrayList<>(completed);
		copy.route = new ArrayList<>(route);
		copy.refused = new ArrayList<>(refused());
		copy.pinned = new ArrayList<>(pinned());
		copy.deferred = new ArrayList<>(deferred());
		copy.hideBlocked = hideBlocked;
		return copy;
	}
}
