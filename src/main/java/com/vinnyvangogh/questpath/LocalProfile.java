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
		GIM("Group ironman");

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

	/** Quest names finished as of the last route computation. */
	@SerializedName("completed_quests")
	private List<String> completedQuests = new ArrayList<>();

	/** Quest names, in the order to do them. */
	@SerializedName("route")
	private List<String> route = new ArrayList<>();

	/** For Gson. */
	LocalProfile()
	{
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

	/** A copy carrying a freshly computed route and progress. */
	LocalProfile withRoute(int graphVersion, List<String> completed, List<String> route)
	{
		LocalProfile copy = new LocalProfile();
		copy.accountType = accountType;
		copy.questTypes = new ArrayList<>(questTypes == null ? Collections.emptyList() : questTypes);
		copy.graphVersion = graphVersion;
		copy.completedQuests = new ArrayList<>(completed);
		copy.route = new ArrayList<>(route);
		return copy;
	}
}
