package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A named snapshot of worn equipment, plus the combat choices a max-hit
 * calculation needs, saved so it can be compared against whatever the
 * account is actually wearing right now.
 *
 * <p><b>{@link #attackStyle}, {@link #prayer} and {@link #spell} are opaque
 * enum names, not enums.</b> The enums themselves belong to another task's
 * {@code MaxHit} code, which this class must not depend on -- a loadout is
 * pure data, saved and loaded long before or after that code runs. Each of
 * the three defaults to {@code null}, meaning "not chosen yet" -- distinct
 * from a real, deliberately-selected value (which, depending on what the
 * {@code MaxHit} enums turn out to contain, might itself be spelled something
 * like {@code "NONE"}). {@link #hasSelections()} is how the UI tells the two
 * apart without inspecting the strings itself.
 *
 * <p>Immutable: every field is set once, at construction, and {@link
 * #withSelections} and {@link #withName} return a new instance rather than
 * mutating this one. Gson-serialisable with {@code @SerializedName}, the same
 * shape {@link BuildRule} uses -- see {@link LoadoutCodec}'s class javadoc
 * for how this class's own validation still runs on a value Gson
 * deserialises, even though Gson itself bypasses the constructor below.
 */
public final class Loadout
{
	@SerializedName("name")
	private final String name;

	@SerializedName("items")
	private final List<LoadoutItem> items;

	@SerializedName("attackStyle")
	private final String attackStyle;

	@SerializedName("prayer")
	private final String prayer;

	@SerializedName("spell")
	private final String spell;

	@SerializedName("savedAtEpochMillis")
	private final long savedAtEpochMillis;

	/**
	 * @param name trimmed and required to be non-empty after trimming;
	 *     uniqueness is enforced by {@link LoadoutStore}, not here, because a
	 *     single loadout has no way to know about any other
	 * @param items one entry per occupied equipment slot; null is treated as
	 *     empty, and the list is copied and made unmodifiable
	 * @param attackStyle an opaque enum name from the {@code MaxHit} code, or
	 *     null for "not chosen yet" -- see the class javadoc
	 * @param prayer the same, for the active prayer
	 * @param spell the same, for the active spell
	 * @param savedAtEpochMillis when this loadout was captured, in {@link
	 *     System#currentTimeMillis()} terms
	 * @throws IllegalArgumentException if {@code name} is null or blank after
	 *     trimming
	 */
	public Loadout(String name, List<LoadoutItem> items, String attackStyle, String prayer, String spell,
		long savedAtEpochMillis)
	{
		String trimmed = name == null ? null : name.trim();
		if (trimmed == null || trimmed.isEmpty())
		{
			throw new IllegalArgumentException("Loadout name must not be null or blank");
		}
		this.name = trimmed;
		this.items = items == null ? Collections.emptyList() : List.copyOf(items);
		this.attackStyle = attackStyle;
		this.prayer = prayer;
		this.spell = spell;
		this.savedAtEpochMillis = savedAtEpochMillis;
	}

	/** The loadout's name -- trimmed, never null or blank. */
	public String name()
	{
		return name;
	}

	/** One entry per occupied equipment slot. Never null; unmodifiable. */
	public List<LoadoutItem> items()
	{
		return items;
	}

	/** The chosen attack style's enum name, or null when not chosen yet. */
	public String attackStyle()
	{
		return attackStyle;
	}

	/** The chosen prayer's enum name, or null when not chosen yet. */
	public String prayer()
	{
		return prayer;
	}

	/** The chosen spell's enum name, or null when not chosen yet. */
	public String spell()
	{
		return spell;
	}

	/** When this loadout was captured, in {@link System#currentTimeMillis()} terms. */
	public long savedAtEpochMillis()
	{
		return savedAtEpochMillis;
	}

	/**
	 * Whether both an attack style and a prayer have been chosen, so a
	 * max-hit UI can compute a number instead of only listing the saved gear.
	 *
	 * <p>{@link #spell} is deliberately excluded: it only applies to some
	 * attack styles, so requiring it here would permanently block the number
	 * for a melee or ranged loadout that will never need one.
	 */
	public boolean hasSelections()
	{
		return attackStyle != null && prayer != null;
	}

	/** A copy of this loadout with its combat choices replaced. Everything else is unchanged. */
	public Loadout withSelections(String attackStyle, String prayer, String spell)
	{
		return new Loadout(name, items, attackStyle, prayer, spell, savedAtEpochMillis);
	}

	/**
	 * A copy of this loadout under a new name. Package-private: only {@link
	 * LoadoutCodec} needs this, to implement {@link LoadoutStore#rename}: no
	 * other caller renames a loadout without going through the store, which
	 * is what enforces uniqueness.
	 */
	Loadout withName(String newName)
	{
		return new Loadout(newName, items, attackStyle, prayer, spell, savedAtEpochMillis);
	}

	/**
	 * This loadout's item ids, in slot order (ascending {@link
	 * LoadoutItem#slot()}), regardless of the order {@link #items} happens to
	 * hold them in.
	 */
	public List<Integer> itemIds()
	{
		List<LoadoutItem> sorted = new ArrayList<>(items);
		sorted.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
		List<Integer> ids = new ArrayList<>(sorted.size());
		for (LoadoutItem item : sorted)
		{
			ids.add(item.itemId());
		}
		return Collections.unmodifiableList(ids);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof Loadout))
		{
			return false;
		}
		Loadout that = (Loadout) other;
		return savedAtEpochMillis == that.savedAtEpochMillis
			&& name.equals(that.name)
			&& items.equals(that.items)
			&& Objects.equals(attackStyle, that.attackStyle)
			&& Objects.equals(prayer, that.prayer)
			&& Objects.equals(spell, that.spell);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, items, attackStyle, prayer, spell, savedAtEpochMillis);
	}

	@Override
	public String toString()
	{
		return "Loadout{name='" + name + "', items=" + items + ", attackStyle=" + attackStyle
			+ ", prayer=" + prayer + ", spell=" + spell + ", savedAtEpochMillis=" + savedAtEpochMillis + "}";
	}
}
