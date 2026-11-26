package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;

/**
 * The seam between the client and the panel for the "locked gear" section:
 * a saved {@link Loadout} plus the account's <b>live</b> levels, resolved
 * into three max-hit readouts.
 *
 * <p><b>Gear is frozen; levels move.</b> This class never reads currently
 * worn equipment -- {@link #resolve} takes no such argument at all, only a
 * previously-{@link LoadoutStore stored} loadout, the caller's live {@link
 * CombatStats}, and a way to look up an item's stats by id. Scoring the
 * gear currently worn would be the wrong feature however correct its
 * arithmetic; see the Task 4b brief.
 *
 * <p>Pure and client-free: {@link #resolve} touches no {@code Client} or
 * {@code ItemManager} directly, only the tiny {@link ItemStatsView}
 * interface the plugin adapts {@code ItemManager.getItemStats(id, false)}
 * into. That keeps this class -- and therefore {@code LockedGearTest} --
 * off the client thread entirely.
 */
public final class LockedGear
{
	/**
	 * The combat-relevant stats of one item, as the plugin's {@code
	 * ItemManager} adapter reports them. Never constructed for an item this
	 * class does not need to look up.
	 */
	public interface ItemStatsView
	{
		/**
		 * Whether this item's stats are known. {@code false} for a null
		 * {@code ItemStats}, a non-equipable item, or a null {@code
		 * getEquipment()} -- see this class's own javadoc for exactly which
		 * of those the plugin's adapter must check.
		 */
		boolean known();

		/** Equipment strength bonus. Meaningless when {@link #known()} is false. */
		int str();

		/** Equipment ranged strength bonus. Meaningless when {@link #known()} is false. */
		int rstr();

		/** Equipment magic damage bonus, whole percent. Meaningless when {@link #known()} is false. */
		int mdmg();
	}

	/** A plain {@link ItemStatsView}: what the plugin's adapter hands over, and the one shared unknown. */
	@lombok.Value
	@lombok.experimental.Accessors(fluent = true)
	public static class ItemStats implements ItemStatsView
	{
		public static final ItemStats UNKNOWN = new ItemStats(false, 0, 0, 0);

		boolean known;
		int str;
		int rstr;
		int mdmg;
	}

	/** Which of four distinct shapes this snapshot is in. */
	public enum Shape
	{
		/** No RuneScape account is logged in. */
		NOT_LOGGED_IN,
		/** The stored loadouts could not be read. See {@link #corruptDetail()}. */
		CORRUPT,
		/** The account is logged in and its stored loadouts read cleanly, but there are none saved yet. */
		NO_LOADOUTS,
		/** A loadout is selected and resolved. */
		READY
	}

	/**
	 * One style's row: exactly one of "no selections yet", "gear stats
	 * unknown" (the whole loadout's, not just this style's -- see {@link
	 * #resolve}'s class doc), or a real {@link MaxHitResult} (itself either
	 * a known value or an honest not-modelled reason).
	 */
	public static final class StyleLine
	{
		/** Which of the three shapes this row is in. */
		public enum Kind
		{
			/** This style's style/prayer (or spell/prayer) selection has not been made, or names a since-renamed enum constant. */
			NEEDS_SELECTION,
			/** At least one item in the loadout has unknown stats, so nothing is shown rather than a confidently low number. */
			UNKNOWN_STATS,
			/** A real {@link MaxHitResult}, known or not-modelled. */
			RESULT
		}

		private final Kind kind;
		private final List<String> unknownItemNames;
		private final MaxHitResult result;
		private final MaxHitStep nextStep;
		private final Integer withoutPrayerValue;
		private final Integer withoutPotionValue;

		private StyleLine(Kind kind, List<String> unknownItemNames, MaxHitResult result, MaxHitStep nextStep,
			Integer withoutPrayerValue, Integer withoutPotionValue)
		{
			this.kind = kind;
			this.unknownItemNames = unknownItemNames;
			this.result = result;
			this.nextStep = nextStep;
			this.withoutPrayerValue = withoutPrayerValue;
			this.withoutPotionValue = withoutPotionValue;
		}

		static StyleLine needsSelection()
		{
			return new StyleLine(Kind.NEEDS_SELECTION, null, null, null, null, null);
		}

		static StyleLine unknownStats(List<String> itemNames)
		{
			return new StyleLine(Kind.UNKNOWN_STATS, Collections.unmodifiableList(new ArrayList<>(itemNames)),
				null, null, null, null);
		}

		static StyleLine result(MaxHitResult result)
		{
			return new StyleLine(Kind.RESULT, null, result, null, null, null);
		}

		/**
		 * A result plus the next level that raises it, where one exists.
		 * {@code nextStep} is null when no higher level below 99 changes
		 * the number -- see {@link MaxHit#nextStep}.
		 */
		static StyleLine result(MaxHitResult result, MaxHitStep nextStep)
		{
			return new StyleLine(Kind.RESULT, null, result, nextStep, null, null);
		}

		/**
		 * A result plus the next-level breakpoint and the two "what if I
		 * dropped this buff" comparisons melee alone currently computes --
		 * see {@link #withoutPrayerValue()} and {@link #withoutPotionValue()}.
		 */
		static StyleLine result(MaxHitResult result, MaxHitStep nextStep, Integer withoutPrayerValue,
			Integer withoutPotionValue)
		{
			return new StyleLine(Kind.RESULT, null, result, nextStep, withoutPrayerValue, withoutPotionValue);
		}

		/** Which shape this row is in. */
		public Kind kind()
		{
			return kind;
		}

		/**
		 * The names of every loadout item with unknown stats.
		 *
		 * @throws IllegalStateException if {@link #kind()} is not {@link Kind#UNKNOWN_STATS}
		 */
		public List<String> unknownItemNames()
		{
			if (kind != Kind.UNKNOWN_STATS)
			{
				throw new IllegalStateException("unknownItemNames() called on a " + kind + " StyleLine");
			}
			return unknownItemNames;
		}

		/**
		 * The computed result, known or not-modelled.
		 *
		 * @throws IllegalStateException if {@link #kind()} is not {@link Kind#RESULT}
		 */
		public MaxHitResult result()
		{
			if (kind != Kind.RESULT)
			{
				throw new IllegalStateException("result() called on a " + kind + " StyleLine");
			}
			return result;
		}

		/**
		 * Whether a higher level is known to raise this style's max hit.
		 *
		 * False for a row that is not a {@link Kind#RESULT}, for a
		 * not-modelled result, for a magic line whose spell does not scale
		 * with Magic, and at 99.
		 */
		public boolean hasNextStep()
		{
			return nextStep != null;
		}

		/**
		 * The next level at which this style's max hit increases.
		 *
		 * @throws IllegalStateException if {@link #hasNextStep()} is false
		 */
		public MaxHitStep nextStep()
		{
			if (nextStep == null)
			{
				throw new IllegalStateException("nextStep() called on a StyleLine with no next step");
			}
			return nextStep;
		}

		/**
		 * What this style's max hit would be with the same gear, style and
		 * potion but no prayer active, or null when there is nothing to
		 * compare: no prayer is currently selected (removing "none" changes
		 * nothing), or this style does not compute the comparison at all
		 * (only melee does, as of the potion work this accompanied).
		 *
		 * <p>Never negative and never throws: mathematically, if {@link
		 * #result()} itself is known, removing a bonus that only ever adds
		 * to effective strength cannot push the arithmetic negative where the
		 * full bonus did not -- see {@code LockedGear#meleeLine}'s own
		 * comment for the proof, which is why this is a bare {@code Integer}
		 * rather than another {@link MaxHitResult}.
		 */
		public Integer withoutPrayerValue()
		{
			return withoutPrayerValue;
		}

		/**
		 * The potion half of {@link #withoutPrayerValue()}: this style's max
		 * hit with no potion active, or null when there is nothing to
		 * compare (no potion selected, or this style has no potion at all --
		 * ranged and magic never populate this).
		 */
		public Integer withoutPotionValue()
		{
			return withoutPotionValue;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof StyleLine))
			{
				return false;
			}
			StyleLine that = (StyleLine) o;
			if (kind != that.kind)
			{
				return false;
			}
			switch (kind)
			{
				case NEEDS_SELECTION:
					return true;
				case UNKNOWN_STATS:
					return unknownItemNames.equals(that.unknownItemNames);
				case RESULT:
					// The breakpoint and the two comparisons are all part of
					// what the row renders, so two rows that differ only in
					// one of them are not the same row -- otherwise the panel
					// keeps a stale "next at" or "without prayer" line after
					// a level-up or a selection change moved it.
					return resultEquals(result, that.result) && Objects.equals(nextStep, that.nextStep)
						&& Objects.equals(withoutPrayerValue, that.withoutPrayerValue)
						&& Objects.equals(withoutPotionValue, that.withoutPotionValue);
				default:
					return false;
			}
		}

		// MaxHitResult (another task's class) has no equals()/hashCode() of
		// its own -- it is compared here by its exposed fields instead of by
		// reference, so two structurally identical results (e.g. across two
		// renders of the same loadout) compare equal and do not force a tab
		// rebuild every tick.
		private static boolean resultEquals(MaxHitResult a, MaxHitResult b)
		{
			if (a.isKnown() != b.isKnown())
			{
				return false;
			}
			return a.isKnown()
				? a.value() == b.value() && a.notApplied().equals(b.notApplied())
				: a.reason().equals(b.reason());
		}

		private static int resultHashCode(MaxHitResult result)
		{
			return result.isKnown()
				? Objects.hash(Boolean.TRUE, result.value(), result.notApplied())
				: Objects.hash(Boolean.FALSE, result.reason());
		}

		@Override
		public int hashCode()
		{
			switch (kind)
			{
				case NEEDS_SELECTION:
					return kind.hashCode();
				case UNKNOWN_STATS:
					return Objects.hash(kind, unknownItemNames);
				case RESULT:
					return Objects.hash(kind, resultHashCode(result), nextStep, withoutPrayerValue, withoutPotionValue);
				default:
					return 0;
			}
		}

		@Override
		public String toString()
		{
			switch (kind)
			{
				case NEEDS_SELECTION:
					return "StyleLine{NEEDS_SELECTION}";
				case UNKNOWN_STATS:
					return "StyleLine{UNKNOWN_STATS=" + unknownItemNames + "}";
				default:
					return "StyleLine{RESULT=" + result
						+ (nextStep == null ? "" : " next=" + nextStep)
						+ (withoutPrayerValue == null ? "" : " withoutPrayer=" + withoutPrayerValue)
						+ (withoutPotionValue == null ? "" : " withoutPotion=" + withoutPotionValue) + "}";
			}
		}
	}

	private final Shape shape;
	private final String corruptDetail;
	private final List<String> loadoutNames;
	private final Loadout selectedLoadout;
	private final List<String> unknownStatsItemNames;
	private final StyleLine melee;
	private final StyleLine ranged;
	private final StyleLine magic;

	private LockedGear(Shape shape, String corruptDetail, List<String> loadoutNames, Loadout selectedLoadout,
		List<String> unknownStatsItemNames, StyleLine melee, StyleLine ranged, StyleLine magic)
	{
		this.shape = shape;
		this.corruptDetail = corruptDetail;
		this.loadoutNames = loadoutNames;
		this.selectedLoadout = selectedLoadout;
		this.unknownStatsItemNames = unknownStatsItemNames;
		this.melee = melee;
		this.ranged = ranged;
		this.magic = magic;
	}

	private static LockedGear notLoggedIn()
	{
		return new LockedGear(Shape.NOT_LOGGED_IN, null, null, null, null, null, null, null);
	}

	private static LockedGear corrupt(String detail)
	{
		return new LockedGear(Shape.CORRUPT, detail, null, null, null, null, null, null);
	}

	private static LockedGear noLoadouts()
	{
		return new LockedGear(Shape.NO_LOADOUTS, null, null, null, null, null, null, null);
	}

	private static LockedGear ready(List<String> loadoutNames, Loadout selected, List<String> unknownStatsItemNames,
		StyleLine melee, StyleLine ranged, StyleLine magic)
	{
		return new LockedGear(Shape.READY, null, loadoutNames, selected, unknownStatsItemNames, melee, ranged, magic);
	}

	/** Which of the four shapes this snapshot is in. */
	public Shape shape()
	{
		return shape;
	}

	/**
	 * Why the stored loadouts could not be read.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#CORRUPT}
	 */
	public String corruptDetail()
	{
		requireShape(Shape.CORRUPT);
		return corruptDetail;
	}

	/**
	 * Every saved loadout's name, for the picker.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public List<String> loadoutNames()
	{
		requireShape(Shape.READY);
		return loadoutNames;
	}

	/**
	 * The loadout this snapshot was resolved for.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public Loadout selectedLoadout()
	{
		requireShape(Shape.READY);
		return selectedLoadout;
	}

	/**
	 * The names of every item in the selected loadout whose stats are
	 * unknown. Empty, never null, when every item's stats are known.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public List<String> unknownStatsItemNames()
	{
		requireShape(Shape.READY);
		return unknownStatsItemNames;
	}

	/**
	 * The melee row.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public StyleLine melee()
	{
		requireShape(Shape.READY);
		return melee;
	}

	/**
	 * The ranged row.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public StyleLine ranged()
	{
		requireShape(Shape.READY);
		return ranged;
	}

	/**
	 * The magic row.
	 *
	 * @throws IllegalStateException if {@link #shape()} is not {@link Shape#READY}
	 */
	public StyleLine magic()
	{
		requireShape(Shape.READY);
		return magic;
	}

	private void requireShape(Shape expected)
	{
		if (shape != expected)
		{
			throw new IllegalStateException("called on a " + shape + " LockedGear, not " + expected);
		}
	}

	/**
	 * Resolves a stored set of loadouts, a chosen name, and the account's
	 * live levels into a snapshot the panel can render without touching the
	 * client.
	 *
	 * <p>No worn-gear input exists on this signature at all -- gear comes
	 * only from {@code stored}, which is itself read from {@link
	 * LoadoutStore#all()}, a config read that never looks at what is
	 * currently equipped. Levels are the only "live" input.
	 *
	 * <p>Bonuses are summed across every item in the selected loadout
	 * (ammo included, matching the game's own equipment screen). If any
	 * item's stats are unknown, every style that has its selections made is
	 * reported as {@link StyleLine.Kind#UNKNOWN_STATS} rather than computed
	 * from a sum that silently treated a missing bonus as zero -- the sums
	 * are shared across all three styles, so one unknown item makes all
	 * three numbers untrustworthy, not just one of them.
	 *
	 * @param stored the account's stored loadouts, from {@link LoadoutStore#all()}
	 * @param selectedName the loadout to resolve, or null/unmatched to fall
	 * back to the first by name
	 * @param levels the caller's live combat levels (never boosted)
	 * @param statsFor looks up an item's stats by id, adapting {@code
	 * ItemManager.getItemStats(id, false)} without this class ever seeing
	 * an {@code ItemManager}
	 */
	public static LockedGear resolve(LoadoutStore.Result stored, String selectedName, CombatStats levels,
		IntFunction<ItemStatsView> statsFor)
	{
		switch (stored.status())
		{
			case NOT_LOGGED_IN:
				return notLoggedIn();
			case CORRUPT:
				return corrupt(stored.detail());
			default:
				break;
		}

		List<Loadout> loadouts = stored.loadouts();
		if (loadouts.isEmpty())
		{
			return noLoadouts();
		}

		Loadout selected = null;
		if (selectedName != null)
		{
			for (Loadout candidate : loadouts)
			{
				if (candidate.name().equals(selectedName))
				{
					selected = candidate;
					break;
				}
			}
		}
		if (selected == null)
		{
			// Not among the stored loadouts (or none chosen yet): fall back
			// to the first by name. LoadoutStore#all() already sorts by
			// name, so "first" here is deterministic without re-sorting.
			selected = loadouts.get(0);
		}

		List<String> loadoutNames = new ArrayList<>();
		for (Loadout candidate : loadouts)
		{
			loadoutNames.add(candidate.name());
		}

		int strengthBonus = 0;
		int rangedStrength = 0;
		int magicDamagePercent = 0;
		Integer poweredStaffItemId = null;
		int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		List<String> unknownNames = new ArrayList<>();

		for (LoadoutItem item : selected.items())
		{
			if (item.slot() == weaponSlot)
			{
				// Always recorded, known stats or not: GearBonuses'
				// poweredStaffItemId is only ever read when the caller
				// selects Spell.POWERED_STAFF, and MaxHit.magic itself
				// decides whether this id resolves to a verified formula --
				// see MaxHit#poweredStaffBase. A non-staff weapon id here is
				// harmless; it is simply never a hit for that lookup.
				poweredStaffItemId = item.itemId();
			}

			ItemStatsView view = statsFor.apply(item.itemId());
			if (view == null || !view.known())
			{
				unknownNames.add(displayName(item));
				continue;
			}
			strengthBonus += view.str();
			rangedStrength += view.rstr();
			magicDamagePercent += view.mdmg();
		}

		Set<GearEffect> effects = GearEffects.detect(selected.itemIds());
		GearBonuses gear = new GearBonuses(strengthBonus, rangedStrength, magicDamagePercent, effects, poweredStaffItemId);
		List<String> unknownNamesView = Collections.unmodifiableList(unknownNames);

		StyleLine meleeLine = meleeLine(selected, levels, gear, unknownNamesView);
		StyleLine rangedLine = rangedLine(selected, levels, gear, unknownNamesView);
		StyleLine magicLine = magicLine(selected, levels, gear, unknownNamesView);

		return ready(Collections.unmodifiableList(loadoutNames), selected, unknownNamesView, meleeLine, rangedLine, magicLine);
	}

	private static String displayName(LoadoutItem item)
	{
		return item.name() == null || item.name().isEmpty() ? "item " + item.itemId() : item.name();
	}

	/**
	 * The breakpoint for a style line, or null when the current result is
	 * not a number to improve on. Only a known result gets one: a
	 * not-modelled line has nothing to compare a higher level against.
	 */
	private static MaxHitStep stepFor(Skill skill, int level, MaxHitResult result, IntFunction<MaxHitResult> at)
	{
		if (!result.isKnown())
		{
			return null;
		}
		return MaxHit.nextStep(skill, level, result.value(), at);
	}

	private static StyleLine meleeLine(Loadout selected, CombatStats levels, GearBonuses gear, List<String> unknownNames)
	{
		if (!selected.hasMeleeSelections())
		{
			return StyleLine.needsSelection();
		}
		AttackStyle style;
		StrengthPrayer prayer;
		try
		{
			style = AttackStyle.valueOf(selected.meleeStyle());
			prayer = StrengthPrayer.valueOf(selected.strengthPrayer());
		}
		catch (IllegalArgumentException unrecognisedName)
		{
			return StyleLine.needsSelection();
		}
		// Potion is not gated the way style and prayer are: a null or
		// unrecognised value reads as StrengthPotion.NONE rather than as
		// "needs selection" -- see Loadout's class javadoc for why a
		// potion's honest off state is common enough to default to rather
		// than withhold the whole line for.
		StrengthPotion potion = StrengthPotion.NONE;
		if (selected.meleePotion() != null)
		{
			try
			{
				potion = StrengthPotion.valueOf(selected.meleePotion());
			}
			catch (IllegalArgumentException unrecognisedName)
			{
				potion = StrengthPotion.NONE;
			}
		}
		if (!unknownNames.isEmpty())
		{
			return StyleLine.unknownStats(unknownNames);
		}
		final AttackStyle chosenStyle = style;
		final StrengthPrayer chosenPrayer = prayer;
		final StrengthPotion chosenPotion = potion;
		try
		{
			int level = levels.get(Skill.STRENGTH);
			MaxHitResult result = MaxHit.melee(level, chosenStyle, chosenPrayer, chosenPotion, gear);

			// "What if I dropped this buff" -- only when it is actually
			// active; comparing against "no prayer" while already at no
			// prayer would just repeat the number above it. Never null'd out
			// by a failure: mathematically, if the full result above didn't
			// throw, neither of these can. Both prayer and potion only ever
			// add to effective strength (StrengthPrayer.NONE and
			// StrengthPotion.NONE both contribute zero), so removing one
			// can only lower effStr -- and effStr with neither active is
			// bounded below by style.styleBonus() + 8 + the real strength
			// level (at least 1), which is always positive. A negative
			// maxHit is only reachable through a strongly negative gear
			// strength bonus multiplying effStr, and a smaller effStr
			// multiplied by that same negative-leaning term is never more
			// negative than a larger one was -- see MaxHit#melee's own
			// stage-by-stage comment for the arithmetic this leans on.
			Integer withoutPrayer = chosenPrayer == StrengthPrayer.NONE ? null
				: MaxHit.melee(level, chosenStyle, StrengthPrayer.NONE, chosenPotion, gear).value();
			Integer withoutPotion = chosenPotion == StrengthPotion.NONE ? null
				: MaxHit.melee(level, chosenStyle, chosenPrayer, StrengthPotion.NONE, gear).value();

			return StyleLine.result(result, stepFor(Skill.STRENGTH, level, result,
				l -> MaxHit.melee(l, chosenStyle, chosenPrayer, chosenPotion, gear)),
				withoutPrayer, withoutPotion);
		}
		catch (IllegalArgumentException negativeValue)
		{
			// MaxHitResult.known() rejects a negative value -- reachable
			// here only through a large negative summed strength bonus.
			// One style's arithmetic failing must never suppress the other
			// two, so this is turned into an honest not-modelled line
			// rather than letting the exception escape resolve() (which
			// runs inside the plugin's once-per-tick refresh).
			return StyleLine.result(MaxHitResult.notModelled("Gear strength bonus too low to model"));
		}
	}

	private static StyleLine rangedLine(Loadout selected, CombatStats levels, GearBonuses gear, List<String> unknownNames)
	{
		if (!selected.hasRangedSelections())
		{
			return StyleLine.needsSelection();
		}
		RangedStyle style;
		RangedPrayer prayer;
		try
		{
			style = RangedStyle.valueOf(selected.rangedStyle());
			prayer = RangedPrayer.valueOf(selected.rangedPrayer());
		}
		catch (IllegalArgumentException unrecognisedName)
		{
			return StyleLine.needsSelection();
		}
		if (!unknownNames.isEmpty())
		{
			return StyleLine.unknownStats(unknownNames);
		}
		final RangedStyle chosenStyle = style;
		final RangedPrayer chosenPrayer = prayer;
		try
		{
			int level = levels.get(Skill.RANGED);
			MaxHitResult result = MaxHit.ranged(level, chosenStyle, chosenPrayer, gear);
			return StyleLine.result(result, stepFor(Skill.RANGED, level, result,
				l -> MaxHit.ranged(l, chosenStyle, chosenPrayer, gear)));
		}
		catch (IllegalArgumentException negativeValue)
		{
			return StyleLine.result(MaxHitResult.notModelled("Gear ranged strength bonus too low to model"));
		}
	}

	private static StyleLine magicLine(Loadout selected, CombatStats levels, GearBonuses gear, List<String> unknownNames)
	{
		if (!selected.hasMagicSelections())
		{
			return StyleLine.needsSelection();
		}
		Spell spell;
		MagicPrayer prayer;
		try
		{
			spell = Spell.valueOf(selected.spell());
			prayer = MagicPrayer.valueOf(selected.magicPrayer());
		}
		catch (IllegalArgumentException unrecognisedName)
		{
			return StyleLine.needsSelection();
		}
		if (!unknownNames.isEmpty())
		{
			return StyleLine.unknownStats(unknownNames);
		}
		final Spell chosenSpell = spell;
		final MagicPrayer chosenPrayer = prayer;
		try
		{
			int level = levels.get(Skill.MAGIC);
			MaxHitResult result = MaxHit.magic(level, chosenSpell, chosenPrayer, gear);
			return StyleLine.result(result, stepFor(Skill.MAGIC, level, result,
				l -> MaxHit.magic(l, chosenSpell, chosenPrayer, gear)));
		}
		catch (IllegalArgumentException negativeValue)
		{
			// Magic's own formula never produces a negative known() value
			// in this build (see MaxHit#magic's powered-staff guard), but
			// this is caught defensively for the same reason melee/ranged
			// are: no single style's arithmetic may ever take the other two
			// down with it.
			return StyleLine.result(MaxHitResult.notModelled("Gear magic bonus too low to model"));
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LockedGear))
		{
			return false;
		}
		LockedGear that = (LockedGear) o;
		return shape == that.shape
			&& Objects.equals(corruptDetail, that.corruptDetail)
			&& Objects.equals(loadoutNames, that.loadoutNames)
			&& Objects.equals(selectedLoadout, that.selectedLoadout)
			&& Objects.equals(unknownStatsItemNames, that.unknownStatsItemNames)
			&& Objects.equals(melee, that.melee)
			&& Objects.equals(ranged, that.ranged)
			&& Objects.equals(magic, that.magic);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(shape, corruptDetail, loadoutNames, selectedLoadout, unknownStatsItemNames,
			melee, ranged, magic);
	}

	@Override
	public String toString()
	{
		return "LockedGear{shape=" + shape + " corruptDetail=" + corruptDetail + " loadoutNames=" + loadoutNames
			+ " selectedLoadout=" + selectedLoadout + " unknownStatsItemNames=" + unknownStatsItemNames
			+ " melee=" + melee + " ranged=" + ranged + " magic=" + magic + "}";
	}
}
