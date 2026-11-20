package com.vinnyvangogh.questpath;

/**
 * Whether Quest Route's own bank tab is the one on screen, and what each click
 * does to that -- kept free of the client so the click sequences can be
 * replayed in a test.
 *
 * <p>Off by default, and that is the important half: this is a tab, not a
 * filter. Switched on it replaces what the grid shows, and a bank tab you did
 * not choose is a broken bank. Session state rather than config for the same
 * reason a real tab is: you pick one for a trip.
 *
 * <p>The button's own op listener and the menu click both see one press of
 * the bank button, and whichever runs second must not undo the first: a "Hide
 * tab" click consumed by the menu handler and then re-delivered to the
 * listener would turn the tab straight back on, which reads as "Hide tab does
 * nothing". {@link BankToggleGuard} is what tells the echo from a real press.
 */
final class BankTabState
{
	private final BankToggleGuard guard = new BankToggleGuard();
	private boolean shown;

	boolean shown()
	{
		return shown;
	}

	/** The button's op listener: toggles, unless this is the echo of a Hide-tab click. True when the tab changed. */
	boolean buttonPressed(int tick)
	{
		return !guard.isEcho(shown, tick) && set(!shown);
	}

	/**
	 * A bank menu click while the tab is shown. "Hide tab" on our own button
	 * leaves the tab and is ours to consume; picking any real tab leaves it
	 * too, because the tab strip has to keep working while the feature is on.
	 *
	 * @return whether the click was our own Hide tab, which the caller consumes
	 */
	boolean menuClicked(String option, String target, int tick)
	{
		if (!shown || option == null)
		{
			return false;
		}
		if (target != null && target.contains("Quest Route"))
		{
			if (BankButton.ACTION_HIDE.equals(option))
			{
				guard.left(tick);
				set(false);
				return true;
			}
			return false;
		}
		if (option.startsWith("View tab") || option.startsWith("View tag tab")
			|| option.equals("View all items") || option.startsWith("Potion store"))
		{
			set(false);
		}
		return false;
	}

	/** Selects or leaves the tab outright. True when that changed anything. */
	boolean set(boolean on)
	{
		if (shown == on)
		{
			return false;
		}
		shown = on;
		return true;
	}
}
