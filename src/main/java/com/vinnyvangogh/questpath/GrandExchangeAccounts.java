package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.AccountKind;
import net.runelite.api.vars.AccountType;

/**
 * Whether an account's own RuneLite account type has Grand Exchange access at
 * all.
 *
 * <p>This is the gate {@link QuestpathPlugin#grandExchangeAvailable()} and
 * {@link QuestpathPanel}'s Grand Exchange list both rely on to never offer
 * the list to an account that cannot act on it -- see this project's own
 * rule against suggesting anything an account cannot do, and the brief this
 * class was built for: "an ironman must never be offered it, because the GE
 * is exactly the thing they cannot use."
 *
 * <p>Read from {@code net.runelite.api.Client#getAccountType()} directly,
 * never from questpath's own push response. The client already knows this
 * for free, with no server round trip and nothing for questpath to get
 * wrong or go stale on -- unlike build rules or constraints, which genuinely
 * only questpath can answer, an account's own type is exactly the kind of
 * fact the game itself is the one true source for.
 *
 * <p><b>{@link AccountType#isIronman()} alone is not enough.</b> The obvious
 * first draft of this class called only that method, on the assumption its
 * name meant what it said. It does not: {@code javap -c -p} against the
 * actually-installed {@code runelite-api-1.12.37.jar} shows {@code
 * isIronman()} testing only whether this type's ordinal falls between {@code
 * IRONMAN} and {@code HARDCORE_IRONMAN} inclusive -- {@code GROUP_IRONMAN}
 * and {@code HARDCORE_GROUP_IRONMAN} sit in a separate ordinal range {@code
 * isIronman()} never reaches, tested only by the type's own {@code
 * isGroupIronman()}. A first version of this class's own test caught it: {@code
 * assertFalse(canUseGrandExchange(AccountType.GROUP_IRONMAN))} failed
 * against an implementation that trusted {@code isIronman()} alone, because
 * that account type is genuinely GE-locked in the real game the same as
 * every other ironman variant, and the method named for exactly that check
 * said otherwise. Both methods are therefore checked here, not one.
 */
final class GrandExchangeAccounts
{
	private GrandExchangeAccounts()
	{
	}

	/**
	 * @param type the account's own type, or null when it is not known yet
	 *     -- before login, or before the client has read it. Null answers
	 *     false rather than true: an account this cannot yet identify must
	 *     not be offered a feature it might turn out unable to use.
	 * @return whether this account can use the Grand Exchange at all
	 */
	static boolean canUseGrandExchange(AccountType type)
	{
		return type != null && !type.isIronman() && !type.isGroupIronman();
	}

	/**
	 * The same type as the group shopping package's trading rules. Both
	 * ironman checks again, for the reason the class doc gives.
	 */
	static AccountKind kindOf(AccountType type)
	{
		if (type == null)
		{
			return AccountKind.UNKNOWN;
		}
		if (type.isGroupIronman())
		{
			return AccountKind.GROUP_IRONMAN;
		}
		return type.isIronman() ? AccountKind.IRONMAN : AccountKind.NORMAL;
	}
}
