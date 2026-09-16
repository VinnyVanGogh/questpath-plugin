package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure JSON encode/decode and edit logic for the persisted loadouts list,
 * kept free of {@code ConfigManager} so it can be unit tested directly. See
 * {@link LoadoutStore}'s class javadoc for why that split exists: {@code
 * ConfigManager} has only a private constructor and cannot be constructed or
 * faked in a JUnit test without Mockito, which this project does not depend
 * on.
 *
 * <p>Every method here takes and/or returns a plain {@code String} -- exactly
 * the shape a config value has -- and never touches persistence itself.
 *
 * <p><b>Why {@link #decode} re-validates every entry.</b> Gson deserialises
 * {@link Loadout} and {@link LoadoutItem} by allocating each instance and
 * setting its fields directly through reflection, the same way it does for
 * every other {@code @SerializedName}-annotated class in this plugin (e.g.
 * {@link BuildRule}) -- it never calls either class's constructor, so the
 * validation those constructors do (a non-blank {@link Loadout} name, an
 * in-range {@link LoadoutItem} slot, ...) does not run during
 * deserialisation. A hand-edited or otherwise corrupted config value could
 * therefore parse as valid JSON and still describe an invalid loadout. {@link
 * #decode} closes that gap by rebuilding each parsed {@link Loadout} through
 * its own constructor, converting any validation failure into a {@link
 * CorruptLoadoutsException} the same way a JSON syntax error is.
 */
final class LoadoutCodec
{
	private LoadoutCodec()
	{
	}

	/**
	 * Thrown when a stored config value is present but is not a valid
	 * loadouts list: malformed JSON, JSON that is not an array, a null entry,
	 * or an entry that fails {@link Loadout}'s own validation.
	 */
	static final class CorruptLoadoutsException extends RuntimeException
	{
		CorruptLoadoutsException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	/**
	 * Parses a stored config value into loadouts, sorted by name.
	 *
	 * @param json the raw config value; null or blank (the key has never been
	 *     set) decodes to an empty list, not a failure
	 * @throws CorruptLoadoutsException per this class's own javadoc
	 */
	static List<Loadout> decode(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		Loadout[] raw;
		try
		{
			raw = gson.fromJson(json, Loadout[].class);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			// IllegalStateException: Gson throws this (not JsonParseException)
			// when the top-level value parses but is not an array, e.g. a
			// bare JSON object or string where an array was expected.
			throw new CorruptLoadoutsException("loadouts config value is not a valid JSON array: " + e.getMessage(), e);
		}
		if (raw == null)
		{
			return Collections.emptyList();
		}
		List<Loadout> result = new ArrayList<>(raw.length);
		for (Loadout candidate : raw)
		{
			result.add(revalidate(candidate));
		}
		sortByName(result);
		return result;
	}

	/**
	 * Rebuilds a Gson-deserialised {@link Loadout} -- and every {@link
	 * LoadoutItem} it carries -- through their own constructors, so the
	 * validation Gson's field-reflection deserialisation bypassed on both
	 * actually runs. See this class's own javadoc.
	 */
	private static Loadout revalidate(Loadout candidate)
	{
		if (candidate == null)
		{
			throw new CorruptLoadoutsException("loadouts array contains a null entry", null);
		}
		List<LoadoutItem> items = new ArrayList<>();
		if (candidate.items() != null)
		{
			for (LoadoutItem rawItem : candidate.items())
			{
				items.add(revalidateItem(rawItem));
			}
		}
		try
		{
			return new Loadout(candidate.name(), items, candidate.attackStyle(),
				candidate.prayer(), candidate.spell(), candidate.savedAtEpochMillis());
		}
		catch (RuntimeException e)
		{
			throw new CorruptLoadoutsException("a stored loadout failed validation: " + e.getMessage(), e);
		}
	}

	/** The {@link LoadoutItem} half of {@link #revalidate}. */
	private static LoadoutItem revalidateItem(LoadoutItem rawItem)
	{
		if (rawItem == null)
		{
			throw new CorruptLoadoutsException("a loadout's items array contains a null entry", null);
		}
		try
		{
			return new LoadoutItem(rawItem.slot(), rawItem.itemId(), rawItem.quantity(), rawItem.name());
		}
		catch (RuntimeException e)
		{
			throw new CorruptLoadoutsException("a stored loadout item failed validation: " + e.getMessage(), e);
		}
	}

	/** Renders loadouts back to the JSON shape {@link #decode} reads, sorted by name. */
	static String encode(Gson gson, List<Loadout> loadouts)
	{
		List<Loadout> sorted = new ArrayList<>(loadouts);
		sortByName(sorted);
		return gson.toJson(sorted);
	}

	/**
	 * The current list with {@code loadout} added, or replacing any existing
	 * entry of the same name.
	 *
	 * @throws CorruptLoadoutsException if {@code currentJson} itself cannot
	 *     be decoded -- a save must not silently discard whatever else was
	 *     already stored
	 */
	static String withSaved(Gson gson, String currentJson, Loadout loadout)
	{
		List<Loadout> loadouts = new ArrayList<>(decode(gson, currentJson));
		loadouts.removeIf(existing -> existing.name().equals(loadout.name()));
		loadouts.add(loadout);
		return encode(gson, loadouts);
	}

	/**
	 * The current list with the loadout named {@code name} removed, if one
	 * existed. Deleting a name that does not exist is not an error.
	 *
	 * <p>{@code name} is trimmed before matching, the same as every stored
	 * {@link Loadout#name()} is trimmed at construction -- a caller passing a
	 * padded name (e.g. from a text field) must still match the entry it
	 * plainly refers to.
	 *
	 * @throws CorruptLoadoutsException per {@link #withSaved}
	 */
	static String withDeleted(Gson gson, String currentJson, String name)
	{
		List<Loadout> loadouts = new ArrayList<>(decode(gson, currentJson));
		String trimmedName = name == null ? "" : name.trim();
		loadouts.removeIf(existing -> existing.name().equals(trimmedName));
		return encode(gson, loadouts);
	}

	/**
	 * The current list with the loadout named {@code from} renamed to
	 * {@code to}.
	 *
	 * <p>Both {@code from} and {@code to} are trimmed before matching or
	 * storing, the same as every stored {@link Loadout#name()} is trimmed at
	 * construction -- see {@link #withDeleted}'s javadoc for why.
	 *
	 * @throws CorruptLoadoutsException per {@link #withSaved}
	 * @throws IllegalArgumentException when {@code from} does not name an
	 *     existing loadout, {@code to} is null or blank after trimming, or
	 *     {@code to} already names a different loadout -- a rename never
	 *     silently overwrites another loadout
	 */
	static String withRenamed(Gson gson, String currentJson, String from, String to)
	{
		List<Loadout> loadouts = new ArrayList<>(decode(gson, currentJson));
		String trimmedFrom = from == null ? "" : from.trim();
		String trimmedTo = to == null ? null : to.trim();
		if (trimmedTo == null || trimmedTo.isEmpty())
		{
			throw new IllegalArgumentException("new name must not be blank");
		}
		int index = indexOfName(loadouts, trimmedFrom);
		if (index < 0)
		{
			throw new IllegalArgumentException("no loadout named \"" + trimmedFrom + "\"");
		}
		if (!trimmedTo.equals(trimmedFrom) && indexOfName(loadouts, trimmedTo) >= 0)
		{
			throw new IllegalArgumentException("a loadout named \"" + trimmedTo + "\" already exists");
		}
		loadouts.set(index, loadouts.get(index).withName(trimmedTo));
		return encode(gson, loadouts);
	}

	private static int indexOfName(List<Loadout> loadouts, String name)
	{
		for (int i = 0; i < loadouts.size(); i++)
		{
			if (loadouts.get(i).name().equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	private static void sortByName(List<Loadout> loadouts)
	{
		loadouts.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
	}
}
