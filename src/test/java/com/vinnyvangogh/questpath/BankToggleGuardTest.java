package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BankToggleGuardTest
{
	@Test
	public void toggleAfterLeavingOnTheSameTickIsAnEcho()
	{
		BankToggleGuard guard = new BankToggleGuard();
		guard.left(10);
		assertTrue(guard.isEcho(false, 10));
	}

	@Test
	public void aLaterTickIsARealClick()
	{
		BankToggleGuard guard = new BankToggleGuard();
		guard.left(10);
		assertFalse(guard.isEcho(false, 11));
	}

	@Test
	public void neverLeftIsNeverAnEcho()
	{
		assertFalse(new BankToggleGuard().isEcho(false, 0));
	}

	@Test
	public void openingWhileShownIsNeverSuppressed()
	{
		BankToggleGuard guard = new BankToggleGuard();
		guard.left(10);
		assertFalse(guard.isEcho(true, 10));
	}
}
