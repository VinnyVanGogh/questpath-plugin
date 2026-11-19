package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and writes {@link LocalProfile} files under one root directory,
 * {@code <root>/<account hash>/profile.json}.
 *
 * <p>The plugin passes {@code RuneLite.RUNELITE_DIR/questroute}, the only
 * place Plugin Hub rules let it write. The root is a constructor argument so
 * tests run against a temp directory.
 *
 * <p>Blocking disk IO: never call from the client thread.
 *
 * <p>Never throws for bad content. A missing file, an empty one, one that is
 * not JSON, one from a newer build, and one naming an account type this
 * build does not know are all answered with a {@link Result} the caller can
 * show, because the alternative -- a plugin that fails to start over a
 * hand-edited file -- is worse than asking the player to run setup again.
 * A damaged file is moved aside rather than deleted, so it can be inspected.
 */
@Slf4j
final class LocalProfileStore
{
	private static final String FILE = "profile.json";
	private static final String CORRUPT_FILE = "profile.json.corrupt";

	/** Account hashes are numbers; anything else is refused before it reaches a path. */
	private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	enum Status
	{
		/** No file: first run (or after a reset). */
		ABSENT,
		/** A usable profile. */
		LOADED,
		/** A file existed but could not be used; it was moved aside. */
		CORRUPT
	}

	static final class Result
	{
		private final Status status;
		private final LocalProfile profile;

		Result(Status status, LocalProfile profile)
		{
			this.status = status;
			this.profile = profile;
		}

		Status status()
		{
			return status;
		}

		/** Non-null only when {@link #status()} is {@link Status#LOADED}. */
		LocalProfile profile()
		{
			return profile;
		}
	}

	private final Path root;
	private final Gson gson;

	LocalProfileStore(Path root, Gson gson)
	{
		this.root = root;
		this.gson = gson;
	}

	/** The account key for a client account hash, or null when it is not known yet (-1). */
	static String keyFor(long accountHash)
	{
		return accountHash == -1L ? null : Long.toUnsignedString(accountHash);
	}

	Result load(String accountKey)
	{
		Path file = fileFor(accountKey);
		if (!Files.exists(file))
		{
			return new Result(Status.ABSENT, null);
		}

		LocalProfile profile;
		try
		{
			String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			profile = gson.fromJson(json, LocalProfile.class);
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("questroute: profile unreadable: {}", e.toString());
			return quarantine(file);
		}
		if (profile == null || !profile.isUsable())
		{
			// An empty file parses to null; a parsed one can still be
			// unusable (unknown account type, newer schema).
			return quarantine(file);
		}
		return new Result(Status.LOADED, profile);
	}

	/**
	 * Writes the profile via a temp file and a rename, so a crash mid-write
	 * leaves the previous file intact instead of half of a new one.
	 */
	void save(String accountKey, LocalProfile profile) throws IOException
	{
		Path file = fileFor(accountKey);
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(FILE + ".tmp");
		Files.write(temp, gson.toJson(profile).getBytes(StandardCharsets.UTF_8));
		try
		{
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Wipes the local profile (and any quarantined copy) for this account. */
	void reset(String accountKey) throws IOException
	{
		Path file = fileFor(accountKey);
		Files.deleteIfExists(file);
		Files.deleteIfExists(file.resolveSibling(CORRUPT_FILE));
		Files.deleteIfExists(file.resolveSibling(FILE + ".tmp"));
	}

	private Result quarantine(Path file)
	{
		try
		{
			Files.move(file, file.resolveSibling(CORRUPT_FILE), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.debug("questroute: could not move a bad profile aside: {}", e.toString());
		}
		return new Result(Status.CORRUPT, null);
	}

	private Path fileFor(String accountKey)
	{
		if (accountKey == null || !SAFE_KEY.matcher(accountKey).matches())
		{
			throw new IllegalArgumentException("unsafe account key");
		}
		return root.resolve(accountKey).resolve(FILE);
	}
}
