package com.vinnyvangogh.questpath;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Every action the Locked gear section can call back into the plugin for,
 * bundled into one object.
 *
 * Each field is a callback exactly like every other one already passed into
 * {@link QuestpathPanel} (see its {@code onSyncNow}) -- this class only
 * groups eight of them together, rather than adding eight more raw
 * parameters to a constructor that already takes fifteen.
 */
final class LockedGearActions
{
	/**
	 * Three inputs, no return value -- {@code java.util.function} has no
	 * such shape built in. Used only for the three selection-change
	 * callbacks below, each of which needs to name which loadout it is
	 * editing as well as the two values chosen for it.
	 */
	interface TriConsumer<A, B, C>
	{
		void accept(A a, B b, C c);
	}

	/**
	 * Locks the account's currently worn gear under this name. Client-thread
	 * work -- see {@code QuestpathPlugin#lockCurrentGear}. If the name
	 * already names a saved loadout, the panel has already confirmed the
	 * overwrite on the EDT before this is called.
	 */
	final Consumer<String> lockCurrentGear;

	/** Selects a different saved loadout to show, by name. */
	final Consumer<String> selectLoadout;

	/** Deletes a saved loadout, by name. The panel has already confirmed this on the EDT. */
	final Consumer<String> deleteLoadout;

	/** Renames a saved loadout: {@code (from, to)}. */
	final BiConsumer<String, String> renameLoadout;

	/**
	 * The melee style + strength prayer selection changed for the named
	 * loadout: {@code (loadoutName, styleName, prayerName)}. The loadout
	 * name travels with every call rather than the plugin guessing "whatever
	 * is currently selected" -- the panel always knows exactly which loadout
	 * it is showing, including before anything has ever been explicitly
	 * selected (the first-by-name fallback).
	 */
	final TriConsumer<String, String, String> meleeSelectionChanged;

	/**
	 * The melee potion selection changed for the named loadout: {@code
	 * (loadoutName, potionName)}. Its own callback rather than a fourth arm on
	 * {@link #meleeSelectionChanged}: style and prayer travel together because
	 * one {@code withMelee} call needs both at once, but the potion has its
	 * own {@code withMeleePotion} mutator and no such pairing to keep atomic.
	 */
	final BiConsumer<String, String> meleePotionChanged;

	/** The ranged style + ranged prayer selection changed: {@code (loadoutName, styleName, prayerName)}. */
	final TriConsumer<String, String, String> rangedSelectionChanged;

	/** The spell + magic prayer selection changed: {@code (loadoutName, spellName, prayerName)}. */
	final TriConsumer<String, String, String> magicSelectionChanged;

	LockedGearActions(Consumer<String> lockCurrentGear, Consumer<String> selectLoadout,
		Consumer<String> deleteLoadout, BiConsumer<String, String> renameLoadout,
		TriConsumer<String, String, String> meleeSelectionChanged,
		BiConsumer<String, String> meleePotionChanged,
		TriConsumer<String, String, String> rangedSelectionChanged,
		TriConsumer<String, String, String> magicSelectionChanged)
	{
		this.lockCurrentGear = lockCurrentGear;
		this.selectLoadout = selectLoadout;
		this.deleteLoadout = deleteLoadout;
		this.renameLoadout = renameLoadout;
		this.meleeSelectionChanged = meleeSelectionChanged;
		this.meleePotionChanged = meleePotionChanged;
		this.rangedSelectionChanged = rangedSelectionChanged;
		this.magicSelectionChanged = magicSelectionChanged;
	}
}
