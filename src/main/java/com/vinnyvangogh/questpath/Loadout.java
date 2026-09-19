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
 * <p><b>{@link #meleePotion()} is the one selection not gated by {@link
 * #hasMeleeSelections()}.</b> Null there still means "not chosen yet" for
 * consistency with the other five fields, but a potion has a real, common off
 * state -- most melee play is un-potted -- so {@link LockedGear} resolves a
 * null (or unparseable) {@code meleePotion} as {@code StrengthPotion.NONE}
 * rather than withholding the whole melee line for it. Practically: a loadout
 * saved before this field existed keeps computing exactly the number it
 * always did, with no re-selection forced on upgrade.
 *
 * <p>Immutable: every field is set once, at construction, and {@link
 * #withMelee}, {@link #withMeleePotion}, {@link #withRanged}, {@link
 * #withMagic} and {@link #withName} each return a new instance rather than
 * mutating this one. Gson-serialisable with {@code @SerializedName}, the same
 * shape {@link BuildRule} uses -- see {@link LoadoutCodec}'s class javadoc for
 * how this class's own validation still runs on a value Gson deserialises,
 * even though Gson itself bypasses the constructor below.
 *
 * <p><b>{@link #weaponStyles()}: which style blocks this loadout's weapon can
 * actually produce.</b> Captured once, at "Lock current gear" time, from the
 * weapon-slot item's own offensive bonuses ({@code astab}/{@code aslash}/
 * {@code acrush} &rarr; {@link #STYLE_MELEE}, {@code arange} &rarr; {@link
 * #STYLE_RANGED}, {@code amagic} &rarr; {@link #STYLE_MAGIC}) -- see {@link
 * LoadoutCapture}. {@code null} means "not captured": either this loadout was
 * saved before this field existed, or the weapon's stats were unknown at
 * capture time. Both are the same honest fallback -- {@link #showsMeleeBlock()}
 * and its two siblings all answer {@code true} when this is {@code null},
 * exactly like before this field existed. A non-null list is never empty --
 * a weapon-slot item with no positive bonus in any of the three (or no weapon
 * at all, i.e. unarmed) still resolves to a single-entry {@code
 * [STYLE_MELEE]}, never to nothing.
 */
public final class Loadout
{
	/** A {@link #weaponStyles()} token: the weapon can produce a melee style. */
	public static final String STYLE_MELEE = "MELEE";

	/** A {@link #weaponStyles()} token: the weapon can produce a ranged style. */
	public static final String STYLE_RANGED = "RANGED";

	/** A {@link #weaponStyles()} token: the weapon can produce a magic style. */
	public static final String STYLE_MAGIC = "MAGIC";

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

	@SerializedName("weaponStyles")
	private final List<String> weaponStyles;

	@SerializedName("meleePotion")
	private final String meleePotion;

	/**
	 * Same as {@link #Loadout(String, List, String, String, String, String,
	 * String, String, long, List, String)} with {@code weaponStyles} and
	 * {@code meleePotion} both defaulted to {@code null} ("not captured" /
	 * "not chosen yet"). Kept for every caller that predates {@link
	 * #weaponStyles()} -- see the class javadoc.
	 */
	public Loadout(String name, List<LoadoutItem> items, String meleeStyle, String strengthPrayer,
		String rangedStyle, String rangedPrayer, String spell, String magicPrayer, long savedAtEpochMillis)
	{
		this(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell, magicPrayer,
			savedAtEpochMillis, null, null);
	}

	/**
	 * Same as {@link #Loadout(String, List, String, String, String, String,
	 * String, String, long, List, String)} with {@code meleePotion} defaulted
	 * to {@code null} ("not chosen yet"). Kept for every caller that predates
	 * {@link #meleePotion()} -- see the class javadoc.
	 */
	public Loadout(String name, List<LoadoutItem> items, String meleeStyle, String strengthPrayer,
		String rangedStyle, String rangedPrayer, String spell, String magicPrayer, long savedAtEpochMillis,
		List<String> weaponStyles)
	{
		this(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell, magicPrayer,
			savedAtEpochMillis, weaponStyles, null);
	}

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
	 * @param weaponStyles which of {@link #STYLE_MELEE}, {@link #STYLE_RANGED},
	 *     {@link #STYLE_MAGIC} this loadout's weapon can produce, or null for
	 *     "not captured" -- see the class javadoc. Copied and made unmodifiable
	 *     when non-null; never validated against the three known tokens here,
	 *     the same way {@code meleeStyle} et al. are opaque strings this class
	 *     does not interpret
	 * @param meleePotion a {@code StrengthPotion} enum name (NONE,
	 *     SUPER_STRENGTH_POTION, ...), or null -- see the class javadoc for why
	 *     this one field is read as {@code StrengthPotion.NONE} rather than
	 *     "not chosen yet" downstream
	 * @throws IllegalArgumentException if {@code name} is null or blank after
	 *     trimming
	 */
	public Loadout(String name, List<LoadoutItem> items, String meleeStyle, String strengthPrayer,
		String rangedStyle, String rangedPrayer, String spell, String magicPrayer, long savedAtEpochMillis,
		List<String> weaponStyles, String meleePotion)
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
		this.weaponStyles = weaponStyles == null ? null : List.copyOf(weaponStyles);
		this.meleePotion = meleePotion;
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

	/**
	 * The chosen melee ({@code StrengthPotion}) potion's enum name, or null.
	 * Unlike every other selection here, null does not gate the melee line --
	 * see the class javadoc.
	 */
	public String meleePotion()
	{
		return meleePotion;
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
	 * Which of {@link #STYLE_MELEE}, {@link #STYLE_RANGED}, {@link
	 * #STYLE_MAGIC} this loadout's weapon can produce, or null when that was
	 * never captured -- see the class javadoc. Never empty when non-null.
	 * Unmodifiable.
	 */
	public List<String> weaponStyles()
	{
		return weaponStyles;
	}

	/**
	 * Whether the "Melee" style block should be shown for this loadout: true
	 * when {@link #weaponStyles()} is null (not captured -- the honest
	 * fallback) or names {@link #STYLE_MELEE}.
	 */
	public boolean showsMeleeBlock()
	{
		return weaponStyles == null || weaponStyles.contains(STYLE_MELEE);
	}

	/** The ranged half of {@link #showsMeleeBlock()}. */
	public boolean showsRangedBlock()
	{
		return weaponStyles == null || weaponStyles.contains(STYLE_RANGED);
	}

	/** The magic half of {@link #showsMeleeBlock()}. */
	public boolean showsMagicBlock()
	{
		return weaponStyles == null || weaponStyles.contains(STYLE_MAGIC);
	}

	/**
	 * Whether both a melee style and a {@code StrengthPrayer} have been
	 * chosen, so a max-hit UI can compute melee's number instead of only
	 * listing the saved gear.
	 *
	 * <p>Deliberately does not also require {@link #meleePotion()}: a potion
	 * has an honest off state and defaults to it rather than blocking the
	 * whole line -- see the class javadoc.
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

	/** A copy of this loadout with its melee style and prayer replaced. Everything else, including {@link #meleePotion()}, is unchanged. */
	public Loadout withMelee(String meleeStyle, String strengthPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
	}

	/** A copy of this loadout with its melee potion replaced. Everything else is unchanged. */
	public Loadout withMeleePotion(String meleePotion)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
	}

	/** A copy of this loadout with its ranged choices replaced. Everything else is unchanged. */
	public Loadout withRanged(String rangedStyle, String rangedPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
	}

	/** A copy of this loadout with its magic choices replaced. Everything else is unchanged. */
	public Loadout withMagic(String spell, String magicPrayer)
	{
		return new Loadout(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
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
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
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
			&& Objects.equals(magicPrayer, that.magicPrayer)
			&& Objects.equals(weaponStyles, that.weaponStyles)
			&& Objects.equals(meleePotion, that.meleePotion);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, items, meleeStyle, strengthPrayer, rangedStyle, rangedPrayer, spell,
			magicPrayer, savedAtEpochMillis, weaponStyles, meleePotion);
	}

	@Override
	public String toString()
	{
		return "Loadout{name='" + name + "', items=" + items
			+ ", meleeStyle=" + meleeStyle + ", strengthPrayer=" + strengthPrayer
			+ ", meleePotion=" + meleePotion
			+ ", rangedStyle=" + rangedStyle + ", rangedPrayer=" + rangedPrayer
			+ ", spell=" + spell + ", magicPrayer=" + magicPrayer
			+ ", savedAtEpochMillis=" + savedAtEpochMillis + ", weaponStyles=" + weaponStyles + "}";
	}
}
