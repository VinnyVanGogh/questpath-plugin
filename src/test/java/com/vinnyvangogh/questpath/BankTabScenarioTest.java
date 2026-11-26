package com.vinnyvangogh.questpath;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The bank button's click sequences, replayed as the client delivers them.
 *
 * <p>The "Hide tab does nothing" report: a press on the button while the tab
 * is shown reaches the plugin twice, once as the menu click ("Hide tab") and
 * once as the widget's own op listener, on the same tick. The first leaves
 * the tab; the second, left alone, toggled it straight back on.
 */
public class BankTabScenarioTest
{
	private static final String OURS = "<col=ff9040>Quest Route</col>";

	@Test
	public void hideTabLeavesTheTabAndTheEchoOnTheSameTickDoesNotReopenIt()
	{
		BankTabState tab = new BankTabState();

		assertTrue("first press opens", tab.buttonPressed(10));
		assertTrue(tab.shown());

		// Tick 14: the player presses the button again. The menu click lands
		// first and is ours to consume...
		assertTrue(tab.menuClicked(BankButton.ACTION_HIDE, OURS, 14));
		assertFalse("Hide tab leaves the tab", tab.shown());
		// ...then the op listener fires for the same press.
		assertFalse("the echo changes nothing", tab.buttonPressed(14));
		assertFalse("and the tab stays hidden", tab.shown());

		// A later press is a real one again.
		assertTrue(tab.buttonPressed(15));
		assertTrue(tab.shown());
	}

	@Test
	public void pickingARealBankTabLeavesOursWithoutConsumingTheClick()
	{
		BankTabState tab = new BankTabState();
		tab.buttonPressed(1);

		assertFalse("the real tab's click must reach the bank", tab.menuClicked("View tab", "Tab 3", 2));
		assertFalse(tab.shown());

		tab.buttonPressed(3);
		assertFalse(tab.menuClicked("Potion store", "", 4));
		assertFalse(tab.shown());
	}

	@Test
	public void otherClicksAndClicksWhileHiddenChangeNothing()
	{
		BankTabState tab = new BankTabState();
		assertFalse(tab.menuClicked(BankButton.ACTION_HIDE, OURS, 1));
		assertFalse("hidden stays hidden", tab.shown());

		tab.buttonPressed(2);
		assertFalse(tab.menuClicked("View tab", OURS, 3));
		assertFalse(tab.menuClicked("Withdraw-1", "Shark", 3));
		assertFalse(tab.menuClicked(null, null, 3));
		assertTrue("an unrelated click leaves the tab alone", tab.shown());

		assertFalse("already shown: no change", tab.set(true));
		assertTrue(tab.set(false));
	}
}
