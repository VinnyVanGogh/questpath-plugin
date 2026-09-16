package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

/**
 * Persists named gear loadouts through {@link ConfigManager}, one JSON array
 * under a single config key ({@link #LOADOUTS_KEY}), scoped to the
 * RuneScape account that saved them.
 *
 * <p><b>RuneLite config comes in two flavours, and this class deliberately
 * uses the less obvious one.</b> Plain {@code getConfiguration}/{@code
 * setConfiguration} read and write the active <i>RuneLite profile's</i>
 * {@code .properties} file under {@code ~/.runelite/profiles2/*.properties}
 * -- one file per RuneLite profile, shared by whichever RuneScape account
 * happens to be logged in when that profile is active. A loadout belongs to
 * the account that wore the gear, not to whichever RuneLite profile was
 * active at the time, so this class uses {@code
 * getRSProfileConfiguration}/{@code setRSProfileConfiguration} instead. Per
 * {@code runelite-api.md} item 10, those key off {@link
 * ConfigManager#getRSProfileKey()}, which is derived from the logged-in
 * account's account hash and follows that account even if it is later used
 * under a different RuneLite profile. The plain pair would let a second
 * account, logged in later under the same RuneLite profile, silently see and
 * overwrite the first account's loadouts -- exactly the failure the
 * RSProfile pair exists to prevent. This also means a loadout saved under one
 * RS account is invisible to another **by design**, the account-scoped
 * analogue of the profile-scoped statement in the RuneLite docs.
 *
 * <p><b>Consequence: no logged-in account means no RSProfile key.</b> {@code
 * getRSProfileKey()} returns null before an account has finished logging in.
 * Every method here checks that first and reports or throws {@link
 * Status#NOT_LOGGED_IN} rather than calling into {@code ConfigManager} with a
 * null key -- see {@link #all()} and {@link #requireLoggedIn()}.
 *
 * <p>All JSON (de)serialisation and the replace/delete/rename edit logic live
 * in {@link LoadoutCodec}, a pure {@code String}-in-{@code String}-out helper
 * with no dependency on {@code ConfigManager}. {@code ConfigManager} itself
 * has only a private constructor (confirmed with {@code javap -p}: {@code
 * private net.runelite.client.config.ConfigManager(String,
 * ScheduledExecutorService, EventBus, Client, Gson, ConfigClient,
 * ProfileManager, SessionManager)}, and no other constructor) and so cannot
 * be constructed, subclassed, or otherwise faked in a JUnit test without
 * Mockito, which this project does not depend on. This class is therefore
 * kept a thin, deliberately under-tested adapter -- {@code LoadoutStoreTest}
 * only exercises the parts of it that do not require a real {@code
 * ConfigManager} -- and {@link LoadoutCodec} carries the logic {@code
 * LoadoutCodecTest} exercises directly.
 */
public final class LoadoutStore
{
	/** The single config key every loadout is stored under, as a JSON array. */
	static final String LOADOUTS_KEY = "loadouts";

	/** What {@link #all()} found, or what a mutating method could not get past. */
	public enum Status
	{
		/** Reading succeeded; {@link Result#loadouts()} is safe to call. */
		OK,
		/** No RuneScape account is logged in, so there is no account to scope a loadout list to. */
		NOT_LOGGED_IN,
		/** The stored config value exists but is not a valid loadouts list. */
		CORRUPT
	}

	/**
	 * The outcome of reading every saved loadout.
	 *
	 * <p>Deliberately not just a {@code List<Loadout>}: {@link
	 * Status#NOT_LOGGED_IN} and {@link Status#CORRUPT} are both real,
	 * different claims from "there are genuinely zero saved loadouts", and
	 * collapsing either into an empty list would make the UI render a wrong
	 * state as a true one -- see this class's own javadoc.
	 */
	public static final class Result
	{
		private final Status status;
		private final List<Loadout> loadouts;
		private final String detail;

		private Result(Status status, List<Loadout> loadouts, String detail)
		{
			this.status = status;
			this.loadouts = loadouts;
			this.detail = detail;
		}

		static Result ok(List<Loadout> loadouts)
		{
			return new Result(Status.OK, loadouts, null);
		}

		static Result notLoggedIn()
		{
			return new Result(Status.NOT_LOGGED_IN, null, "No RuneScape account is logged in.");
		}

		static Result corrupt(String detail)
		{
			return new Result(Status.CORRUPT, null, detail);
		}

		/** Which of the three outcomes this is. */
		public Status status()
		{
			return status;
		}

		/**
		 * The saved loadouts, sorted by name.
		 *
		 * @throws IllegalStateException when {@link #status()} is not {@link Status#OK}
		 */
		public List<Loadout> loadouts()
		{
			if (status != Status.OK)
			{
				throw new IllegalStateException("loadouts() called on a " + status + " result: " + detail);
			}
			return loadouts;
		}

		/** A human-readable reason, present only when {@link #status()} is not {@link Status#OK}. */
		public String detail()
		{
			return detail;
		}
	}

	/**
	 * Thrown by every method but {@link #all()} when the current loadouts
	 * cannot be safely read before the requested change is made.
	 */
	public static final class LoadoutStoreException extends RuntimeException
	{
		private final Status status;

		LoadoutStoreException(Status status, String message)
		{
			super(message);
			this.status = status;
		}

		/** {@link Status#NOT_LOGGED_IN} or {@link Status#CORRUPT} -- never {@link Status#OK}. */
		public Status status()
		{
			return status;
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;

	/** Production wiring: the real {@code ConfigManager}, RuneLite's own injected {@code Gson}. */
	@Inject
	LoadoutStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Every saved loadout, sorted by name -- see {@link Result} for why this
	 * cannot simply be a {@code List}.
	 */
	public Result all()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return Result.notLoggedIn();
		}
		String json = configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY);
		try
		{
			return Result.ok(LoadoutCodec.decode(gson, json));
		}
		catch (LoadoutCodec.CorruptLoadoutsException e)
		{
			return Result.corrupt(e.getMessage());
		}
	}

	/** The loadout named {@code name}, or empty when none matches. */
	public Optional<Loadout> byName(String name)
	{
		List<Loadout> loadouts = requireOk(all());
		String trimmed = name == null ? "" : name.trim();
		return loadouts.stream().filter(loadout -> loadout.name().equals(trimmed)).findFirst();
	}

	/** Saves {@code loadout}, replacing any existing loadout of the same name. */
	public void save(Loadout loadout)
	{
		requireLoggedIn();
		String current = configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY);
		String updated = wrapCorrupt(() -> LoadoutCodec.withSaved(gson, current, loadout));
		configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY, updated);
	}

	/** Deletes the loadout named {@code name}, if one exists. A missing name is not an error. */
	public void delete(String name)
	{
		requireLoggedIn();
		String current = configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY);
		String updated = wrapCorrupt(() -> LoadoutCodec.withDeleted(gson, current, name));
		configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY, updated);
	}

	/**
	 * Renames the loadout named {@code from} to {@code to}.
	 *
	 * @throws IllegalArgumentException when {@code from} does not name an
	 *     existing loadout, {@code to} is null or blank after trimming, or
	 *     {@code to} already names a different loadout
	 */
	public void rename(String from, String to)
	{
		requireLoggedIn();
		String current = configManager.getRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY);
		String updated = wrapCorrupt(() -> LoadoutCodec.withRenamed(gson, current, from, to));
		configManager.setRSProfileConfiguration(QuestpathConfig.GROUP, LOADOUTS_KEY, updated);
	}

	/** @throws LoadoutStoreException with {@link Status#NOT_LOGGED_IN} when no account is logged in */
	private void requireLoggedIn()
	{
		if (configManager.getRSProfileKey() == null)
		{
			throw new LoadoutStoreException(Status.NOT_LOGGED_IN,
				"No RuneScape account is logged in; cannot change a loadout.");
		}
	}

	private static List<Loadout> requireOk(Result result)
	{
		if (result.status() != Status.OK)
		{
			throw new LoadoutStoreException(result.status(), result.detail());
		}
		return result.loadouts();
	}

	/** Converts a {@link LoadoutCodec.CorruptLoadoutsException} into this class's own {@link LoadoutStoreException}. */
	private static String wrapCorrupt(Supplier<String> action)
	{
		try
		{
			return action.get();
		}
		catch (LoadoutCodec.CorruptLoadoutsException e)
		{
			throw new LoadoutStoreException(Status.CORRUPT, e.getMessage());
		}
	}
}
