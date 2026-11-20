package com.vinnyvangogh.questpath;

/**
 * Stops one click on the bank button from being applied twice.
 *
 * <p>The button is reached by two paths: the menu click, which can consume
 * the event, and the widget's own op listener. If both see the same click, a
 * "Hide tab" handled by the first would be undone by the second turning the
 * tab straight back on -- which looks exactly like "Hide tab does nothing".
 * Leaving records the tick; a toggle that would switch the tab back on in
 * that same tick is the echo of the same click and is ignored.
 */
final class BankToggleGuard
{
	private int leftTick = -1;

	/** Whether a menu click is ours to look at: tab on, or our "Hide tab". */
	static boolean handles(boolean tabShown, boolean ourButton, String option)
	{
		return tabShown || (ourButton && BankButton.ACTION_HIDE.equals(option));
	}

	/** Records that the tab was left by the button on this client tick. */
	void left(int tick)
	{
		leftTick = tick;
	}

	/** Whether a toggle now is the echo of a click that just left the tab. */
	boolean isEcho(boolean tabShown, int tick)
	{
		return !tabShown && leftTick == tick;
	}
}
