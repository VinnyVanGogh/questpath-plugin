package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which lamp the open interface belongs to.
 *
 * <p>The thing under test is an attribution, and getting it wrong is worse
 * than not making it. Pricing one lamp as another produces a confident ranking
 * built on the wrong number, against a choice that cannot be taken back; the
 * failure to attribute at all produces "amount unknown", which is true.
 */
public class LampWatcherTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void theInterfaceBelongsToTheItemJustClicked() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		LampWatcher watcher = new LampWatcher();

		watcher.itemClicked(2528, "Lamp", 100);
		watcher.interfaceOpened(101);

		assertTrue(watcher.open());
		assertEquals(2528, watcher.lampItemId());
		Lamp lamp = watcher.lamp(catalogue);
		assertNotNull(lamp);
		assertTrue(lamp.modelled());
		assertEquals(10, lamp.xpPerLevel());
	}

	/**
	 * An item clicked long before the interface opened is not the lamp.
	 *
	 * <p>The window errs short on purpose. Forgetting a lamp costs an
	 * "amount unknown"; remembering the wrong one costs a confident ranking
	 * built on another item's value.
	 */
	@Test
	public void aStaleClickIsNotAttributedToTheInterface()
	{
		LampWatcher watcher = new LampWatcher();

		watcher.itemClicked(2528, "Lamp", 100);
		watcher.interfaceOpened(100 + LampWatcher.MEMORY_TICKS + 1);

		assertTrue(watcher.open());
		assertEquals("a stale click must not be attributed", 0, watcher.lampItemId());
	}

	@Test
	public void aClickExactlyAtTheEdgeOfTheWindowStillCounts()
	{
		LampWatcher watcher = new LampWatcher();

		watcher.itemClicked(2528, "Lamp", 100);
		watcher.interfaceOpened(100 + LampWatcher.MEMORY_TICKS);

		assertEquals(2528, watcher.lampItemId());
	}

	/**
	 * An interface opened with nothing remembered still yields a lamp object,
	 * not null.
	 *
	 * <p>Null here would become "skip the advisor" at the call site within one
	 * refactor, and skipping it is exactly wrong: a NO_XP skill has to be
	 * greyed whether or not the amount is known.
	 */
	@Test
	public void anUnattributedInterfaceStillYieldsAnUnknownLamp() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		LampWatcher watcher = new LampWatcher();

		watcher.interfaceOpened(50);

		Lamp lamp = watcher.lamp(catalogue);
		assertNotNull(lamp);
		assertFalse("an unattributed interface must not claim to know the amount", lamp.modelled());
	}

	@Test
	public void noLampWhileTheInterfaceIsShut() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		LampWatcher watcher = new LampWatcher();

		watcher.itemClicked(2528, "Lamp", 100);
		assertNull("nothing to advise on with no interface open", watcher.lamp(catalogue));

		watcher.interfaceOpened(101);
		watcher.interfaceClosed();

		assertFalse(watcher.open());
		assertNull(watcher.lamp(catalogue));
		assertEquals(0, watcher.lampItemId());
	}

	/**
	 * Which lamp it is, is fixed when the interface opens.
	 *
	 * <p>Read live instead, a click elsewhere while the interface is up would
	 * change what the overlay believes it is advising on, mid-decision, under
	 * the player's cursor.
	 */
	@Test
	public void theLampIsFixedAtOpenAndNotRereadWhileTheInterfaceIsUp() throws Exception
	{
		LampCatalogue catalogue = LampCatalogue.load(GSON);
		LampWatcher watcher = new LampWatcher();

		watcher.itemClicked(2528, "Lamp", 100);
		watcher.interfaceOpened(101);
		watcher.itemClicked(11640, "Book of Knowledge", 102);

		assertEquals("the open interface keeps the lamp it opened with",
			2528, watcher.lampItemId());
		assertEquals(10, watcher.lamp(catalogue).xpPerLevel());
	}

	/**
	 * The interface is identified by a gameval constant, never a number this
	 * file remembers.
	 */
	@Test
	public void theInterfaceGroupComesFromAGamevalConstant()
	{
		assertEquals(net.runelite.api.gameval.InterfaceID.XPREWARD, LampSkillWidgets.group());
	}

	/**
	 * Every skill the interface offers has a component, and they are all
	 * distinct.
	 *
	 * <p>A duplicated id would draw two verdicts over one square and none
	 * over another, which reads as the advisor simply having no opinion about
	 * the skill it lost.
	 */
	@Test
	public void everySkillMapsToItsOwnDistinctComponent()
	{
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (String skill : LampSkillWidgets.skills())
		{
			int component = LampSkillWidgets.componentFor(skill);
			assertTrue(skill + " has no component", component > 0);
			assertTrue(skill + " shares a component with another skill", seen.add(component));
		}
		assertEquals("all 24 skills, Sailing included", 24, seen.size());
	}

	@Test
	public void anUnknownSkillNameHasNoComponentRatherThanADefaultOne()
	{
		assertEquals(-1, LampSkillWidgets.componentFor("not a skill"));
	}
}
