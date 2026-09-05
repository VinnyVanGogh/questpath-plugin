package com.vinnyvangogh.questpath;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestStartArrivalGate#allow}: the fix for the third
 * arrival-trigger bug -- an arrival must never hijack a quest already
 * selected in Quest Helper. See the class doc for why Quest Helper's own
 * selection, not an account-level {@code IN_PROGRESS} quest, is what this
 * gates on.
 *
 * <p>Pure function, no client, no Quest Helper -- {@code
 * questHelperHasSelection} is a plain boolean here, standing in for
 * whatever {@link QuestHelperBridge#hasSelectedQuest()} would answer at
 * that moment. This proves the gating decision; it says nothing about
 * whether {@code hasSelectedQuest} itself reads Quest Helper correctly,
 * which {@code QuestHelperBridgeTest} covers separately.
 */
public class QuestStartArrivalGateTest
{
	/** Nothing arrived, whatever Quest Helper's selection is -- vacuously nothing to allow. */
	@Test
	public void emptyArrivalStaysEmpty()
	{
		assertTrue(QuestStartArrivalGate.allow(List.of(), false).isEmpty());
		assertTrue(QuestStartArrivalGate.allow(List.of(), true).isEmpty());
	}

	/**
	 * The ordinary case this feature exists for: nothing else selected in
	 * Quest Helper, so a genuine arrival is let through unchanged.
	 */
	@Test
	public void arrivalsPassThroughWhenNothingIsAlreadySelected()
	{
		List<String> arrived = List.of("Cook's Assistant");
		assertEquals(arrived, QuestStartArrivalGate.allow(arrived, false));
	}

	/**
	 * The root cause of the third report, closed: Prince Ali Rescue was
	 * selected in Quest Helper, arriving at A Porcine of Interest's start
	 * must not switch it over. Nothing fires at all while something else is
	 * already selected -- not even a partial list.
	 */
	@Test
	public void nothingFiresWhileQuestHelperAlreadyHasSomethingSelected()
	{
		List<String> arrived = List.of("A Porcine of Interest");
		assertTrue(QuestStartArrivalGate.allow(arrived, true).isEmpty());
	}

	/** Several arrivals on the same tick are all suppressed together, not just the first. */
	@Test
	public void multipleArrivalsAreAllSuppressedTogetherWhenSomethingIsSelected()
	{
		List<String> arrived = List.of("Quest A", "Quest B");
		assertTrue(QuestStartArrivalGate.allow(arrived, true).isEmpty());
	}
}
