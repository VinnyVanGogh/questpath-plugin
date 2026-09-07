package com.vinnyvangogh.questpath;

import net.runelite.api.vars.AccountType;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the one gate that keeps the Grand Exchange list off an ironman: every
 * {@link AccountType} this build knows about must land on the right side of
 * {@link GrandExchangeAccounts#canUseGrandExchange}, including the group
 * variants and the "not known yet" case.
 */
public class GrandExchangeAccountsTest
{
	@Test
	public void aNormalAccountCanUseTheGrandExchange()
	{
		assertTrue(GrandExchangeAccounts.canUseGrandExchange(AccountType.NORMAL));
	}

	@Test
	public void everyIronmanVariantCannot()
	{
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(AccountType.IRONMAN));
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(AccountType.ULTIMATE_IRONMAN));
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(AccountType.HARDCORE_IRONMAN));
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(AccountType.GROUP_IRONMAN));
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(AccountType.HARDCORE_GROUP_IRONMAN));
	}

	/**
	 * An account type this build has not read yet -- before login, or before
	 * the client has populated it -- must not be offered a feature it might
	 * turn out unable to use. Fail closed, not fail open.
	 */
	@Test
	public void unknownAccountTypeCannot()
	{
		assertFalse(GrandExchangeAccounts.canUseGrandExchange(null));
	}
}
