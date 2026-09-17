package com.vinnyvangogh.questpath;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A named snapshot of worn equipment, plus the combat choices a max-hit
 * calculation needs -- for all three styles at once -- saved so it can be
 * compared against whatever the account is actually wearing right now.
 *
 * <p><b>One loadout, three independent sets of selections.</b> The panel this
 * class serves shows a loadout's melee, ranged, and magic max hit
 * simultaneously, not one style at a time, so a single {@code attackStyle}/
 * {@code prayer}/{@code spell} triple (this class's first shape) cannot
 * describe it -- melee and ranged each need their own style and prayer, and
 * magic needs a spell and its own prayer. Hence six fields: {@link
 * #meleeStyle}/{@link #strengthPrayer}, {@link #rangedStyle}/{@link
 * #rangedPrayer}, {@link #spell}/{@link #magicPrayer}.
 *
 * <p><b>All six are opaque enum names, not enums.</b> The enums themselves
 * ({@code AttackStyle}, {@code StrengthPrayer}, {@code RangedStyle}, {@code
 * RangedPrayer}, {@code Spell}, {@code MagicPrayer}) belong to another task's
 * {@code MaxHit} code, which this class must not depend on -- a loadout is
 * pure data, saved and loaded long before or after that code runs, and this
 * class neither imports nor validates against any of them. Each field
 * defaults to {@code null}, meaning "not chosen yet" -- distinct from a real,
 * deliberately-selected value (which, depending on what the {@code MaxHit}
 * enums turn out to contain, might itself be spelled something like {@code
 * "NONE"}). {@link #hasMeleeSelections()}, {@link #hasRangedSelections()} and
 * {@link #hasMagicSelections()} are how the UI tells the two apart, per
 * style, without inspecting the strings itself.
 *
 * <p><b>No migration for the old three-field shape.</b> Nothing has shipped
 * yet -- this plugin has no released version with a persisted {@code
 * attackStyle}/{@code prayer}/{@code spell} loadout in the wild -- so there is
 * no stored data to migrate and none is attempted.
 *
 * <p>Immutable: every field is set once, at construction, and {@link
 * #withMelee}, {@link #withRanged}, {@link #withMagic} and {@link #withName}
 * each return a new instance rather than mutating this one. Gson-serialisable
 * with {@code @SerializedName}, the same shape {@link BuildRule} uses -- see
 * {@link LoadoutCodec}'s class javadoc for how this class's own validation
 * still runs on a value Gson deserialises, even though Gson itself bypasses
 * the constructor below.
 */
public final class Loadout
{
	@SerializedName("name")
	private final String name;

	@SerializedName("items")
	private final List<LoadoutItem> items;

	@SerializedName("meleeStyle")
	private final String meleeStyle;

	@SerializedName("strengthPrayer")
	private final String strengthPrayer;

	@SerializedName("rangedStyle")
	private final String rangedStyle;

	@SerializedName("rangedPrayer")
	private final String rangedPrayer;

	@SerializedName("spell")
	private final String spell;

	@SerializedName("magicPrayer")
	private final String magicPrayer;

	@SerializedName("savedAtEpochMillis")
	private final long savedAtEpochMillis;

	/**
	 * @param name trimmed and required to be non-empty after trimming;
	 *     uniqueness is enforced by {@link LoadoutStore}, not here, because a
	 *     single loadout has no way to know about any other
	 * @param items one entry per occupied equipment slot; null is treated as
	 *     empty, and the list is copied and made unmodifiable
	 * @param meleeStyle an {@code AttackStyle} enum name from the {@code
	 *     MaxHit} code (ACCURATE/AGGRESSIVE/CONTROLLED/DEFENSIVE), or null for
	 *     "not chosen yet" -- see the class javadoc
	 * @param strengthPrayer a {@code StrengthPrayer} enum name (NONE, PIETY,
	 *     ...), or null the same way
	 * @param rangedStyle a {@code RangedStyle} enum name
	 *     (ACCURATE/RAPID/LONGRANGE), or null the same way
	 * @param rangedPrayer a {@code RangedPrayer} enum name, or null the same way
	 * @param spell a {@code Spell} enum name, or null the same way
	 * @param magicPrayer a {@code MagicPrayer} enum name, or null the same way
	 * @param savedAtEpochMillis when this loadout was captured, in {@link
	 *     System#currentTimeMillis()} terms
	 * @throws IllegalArgumentException if {@code name} is null or blank after
	 *     trimming
	 */
	public Loadout(String name, List<LoadoutItem> items, String meleeStyle, String strengthPrayer,
		String rangedStyle, String rangedPrayer, String spell, String magicPrayer, long savedAtEpochMillis)
	{
		String trimmed = name == null ? null : name.trim();
		if (trimmed == null || trimmed.isEmpty())
		{
			throw new IllegalArgumentException("Loadout name must not be null or blank");
		}
		this.name = trimmed;
		this.items = items == null ? Collections.emptyList() : List.copyOf(items);
		this.meleeStyle = meleeStyle;
		this.strengthPrayer = strengthPrayer;
		this.rangedStyle = rangedStyle;
		this.rangedPrayer = rangedPrayer;
		this.spell = spell;
		this.magicPrayer = magicPrayer;
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

	/** The chosen melee attack style's enum name, or null when not chosen yet. */
	public String meleeStyle()
	{
		return meleeStyle;
	}

	/** The chosen melee ({@code StrengthPrayer}) prayer's enum name, or null when not chosen yet. */
	public String strengthPrayer()
	{
		return strengthPrayer;
	}

	/** The chosen ranged attack style's enum name, or null when not chosen yet. */
	public String rangedStyle()
	{
		return rangedStyle;
	}

	/** The chosen ranged prayer's enum name, or null when not chosen yet. */
	public String rangedPrayer()
	{
		return rangedPrayer;
	}

	/** The chosen spell's enum name, or null when not chosen yet. */
	public String spell()
	{
		return spell;
	}

	/** The chosen magic prayer's enum name, or null when not chosen yet. */
	public String magicPrayer()
	{
		return magicPrayer;
	}

	/** When this loadout was captured, in {@link System#currentTimeMillis()} terms. */
	public long savedAtEpochMillis()
	{
		return savedAtEpochMillis;
	}

	/**
	 * Whether both a melee style and a {@code StrengthPrayer} have been
	 * chosen, so a max-hit UI can compute melee's number instead of only
	 * listing the saved gear.
	 */
	public boolean hasMeleeSelections()
	{
		return meleeStyle != null && strengthPrayer != null;
	}

	/**
	 * Whether both a ranged style and a ranged prayer have been chosen, so a
	 * max-hit UI can compute ranged's number instead of only listing the
	 * saved gear.
	 */
	public boolean hasRangedSelections()
	{
		return rangedStyle != null && rangedPrayer != null;
	}

	/**
	 * Whether both a spell and a magic prayer have been chosen, so a max-hit
	 * UI can compute magic's number instead of only listing the saved gear.
	 */
	public boolean hasMagicSelections()
	{
		return spell != null && magicPrayer != null;
	}

	/** A copy of this loadout with its melee choices replaced. Everything else is unchanged. */
	public Loadout withMelee(String meleeStyle, String strengthPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis);
	}

	/** A copy of this loadout with its ranged choices replaced. Everything else is unchanged. */
	public Loadout withRanged(String rangedStyle, String rangedPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis);
	}

	/** A copy of this loadout with its magic choices replaced. Everything else is unchanged. */
	public Loadout withMagic(String spell, String magicPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis);
	}

	/**
	 * A copy of this loadout under a new name. Package-private: only {@link
	 * LoadoutCodec} needs this, to implement {@link LoadoutStore#rename}: no
	 * other caller renames a loadout without going through the store, which
	 * is what enforces uniqueness.
	 */
	Loadout withName(String newName)
	{
		return new Loadout(newName, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis);
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
			&& Objects.equals(meleeStyle, that.meleeStyle)
			&& Objects.equals(strengthPrayer, that.strengthPrayer)
			&& Objects.equals(rangedStyle, that.rangedStyle)
			&& Objects.equals(rangedPrayer, that.rangedPrayer)
			&& Objects.equals(spell, that.spell)
			&& Objects.equals(magicPrayer, that.magicPrayer);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis);
	}

	@Override
	public String toString()
	{
		return "Loadout{name='" + name + "', items=" + items
			+ ", meleeStyle=" + meleeStyle + ", strengthPrayer=" + strengthPrayer
			+ ", rangedStyle=" + rangedStyle + ", rangedPrayer=" + rangedPrayer
			+ ", spell=" + spell + ", magicPrayer=" + magicPrayer
			+ ", savedAtEpochMillis=" + savedAtEpochMillis + "}";
	}
}
