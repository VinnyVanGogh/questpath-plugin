package com.vinnyvangogh.questpath.groupshop;

/**
 * The trading rules an account lives under, as far as a group shopping list
 * cares.
 *
 * <p>Deliberately not {@code net.runelite.api.vars.AccountType}: this package
 * is meant to be copied into another plugin as-is, and the four values here
 * are the only distinctions the math draws. Ultimate and hardcore ironmen
 * trade exactly like ironmen; hardcore group ironmen exactly like group
 * ironmen.
 */
public enum AccountKind
{
	/** Trades with anyone and has the Grand Exchange. */
	NORMAL,
	/** Trades with nobody. */
	IRONMAN,
	/** Trades only with their own group, and has group storage. */
	GROUP_IRONMAN,
	/**
	 * Not said: an older client, or the account type has not loaded. Never
	 * pooled and never sent to the Grand Exchange -- guessing either way
	 * would be a confident wrong answer.
	 */
	UNKNOWN;

	/** Reads the wire spelling; anything unrecognised is {@link #UNKNOWN}. */
	public static AccountKind parse(String wire)
	{
		if (wire == null)
		{
			return UNKNOWN;
		}
		for (AccountKind kind : values())
		{
			if (kind.name().equals(wire))
			{
				return kind;
			}
		}
		return UNKNOWN;
	}

	/** Whether this account can buy at a Grand Exchange counter. */
	public boolean canUseGrandExchange()
	{
		return this == NORMAL;
	}

	/**
	 * Whether a spare tradeable item can pass between the two accounts.
	 * Normal accounts trade with each other, group ironmen with each other,
	 * and nobody else trades with anyone.
	 */
	public boolean canTradeWith(AccountKind other)
	{
		return (this == NORMAL || this == GROUP_IRONMAN) && this == other;
	}
}
