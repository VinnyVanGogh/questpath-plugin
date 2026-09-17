package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The frozen combat-relevant numbers a saved loadout contributes, separate
 * from the caller's live levels.
 *
 * A direct-field value type, matching {@link CombatStats}'s style: no
 * Lombok, manual {@code equals}/{@code hashCode}/{@code toString}.
 */
public final class GearBonuses
{
	private final int strengthBonus;
	private final int rangedStrength;
	private final int magicDamagePercent;
	private final Set<GearEffect> effects;
	private final Integer poweredStaffItemId;

	/**
	 * @param strengthBonus equipment strength bonus (melee), the Infobox
	 * Bonuses {@code str} field summed across worn items.
	 * @param rangedStrength equipment ranged strength bonus, the Infobox
	 * Bonuses {@code rstr} field summed across worn items (weapon or ammo,
	 * whichever carries it &mdash; RESEARCH.md &sect;20.2).
	 * @param magicDamagePercent equipment magic damage bonus as a whole
	 * percentage integer, matching {@code ItemEquipmentStats.getMdmg()}'s
	 * {@code int} type (runelite-api.md item 8) rather than a fractional
	 * double.
	 * @param effects every {@link GearEffect} detected for this loadout,
	 * e.g. from {@link GearEffects#detect}. Copied defensively into an
	 * unmodifiable {@link EnumSet}.
	 * @param poweredStaffItemId the worn weapon's item id when it is a
	 * powered staff whose max hit is level-based (RESEARCH.md &sect;20.4),
	 * or {@code null} when the weapon is not a powered staff.
	 */
	public GearBonuses(int strengthBonus, int rangedStrength, int magicDamagePercent,
		Set<GearEffect> effects, Integer poweredStaffItemId)
	{
		this.strengthBonus = strengthBonus;
		this.rangedStrength = rangedStrength;
		this.magicDamagePercent = magicDamagePercent;
		this.effects = effects == null || effects.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(EnumSet.copyOf(effects));
		this.poweredStaffItemId = poweredStaffItemId;
	}

	/** Equipment strength bonus (melee). */
	public int strengthBonus()
	{
		return strengthBonus;
	}

	/** Equipment ranged strength bonus. */
	public int rangedStrength()
	{
		return rangedStrength;
	}

	/** Equipment magic damage bonus, as a whole percentage integer. */
	public int magicDamagePercent()
	{
		return magicDamagePercent;
	}

	/** Every {@link GearEffect} detected for this loadout. Never null. */
	public Set<GearEffect> effects()
	{
		return effects;
	}

	/**
	 * The worn weapon's item id when it is a powered staff, or {@code null}
	 * when it is not.
	 */
	public Integer poweredStaffItemId()
	{
		return poweredStaffItemId;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof GearBonuses))
		{
			return false;
		}
		GearBonuses other = (GearBonuses) o;
		return strengthBonus == other.strengthBonus
			&& rangedStrength == other.rangedStrength
			&& magicDamagePercent == other.magicDamagePercent
			&& effects.equals(other.effects)
			&& (poweredStaffItemId == null ? other.poweredStaffItemId == null : poweredStaffItemId.equals(other.poweredStaffItemId));
	}

	@Override
	public int hashCode()
	{
		int result = strengthBonus;
		result = 31 * result + rangedStrength;
		result = 31 * result + magicDamagePercent;
		result = 31 * result + effects.hashCode();
		result = 31 * result + (poweredStaffItemId == null ? 0 : poweredStaffItemId.hashCode());
		return result;
	}

	@Override
	public String toString()
	{
		return "GearBonuses{str=" + strengthBonus + " rstr=" + rangedStrength + " mdmg=" + magicDamagePercent
			+ "% effects=" + effects + " poweredStaffItemId=" + poweredStaffItemId + "}";
	}
}
