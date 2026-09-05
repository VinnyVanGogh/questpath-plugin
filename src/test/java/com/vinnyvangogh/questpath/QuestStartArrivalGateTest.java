package com.vinnyvangogh.questpath;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuestStartArrivalGate#allow}: an arrival must never hijack a
 * quest already selected in Quest Helper, nor offer a different start while
 * a quest on the account's own saved route is still in progress -- even
 * with Quest Helper's panel closed and nothing selected there. See the
 * class doc for why both signals exist and why the route, not the whole
 * account, is what the second one is scoped to.
 *
 * <p>Pure function, no client, no Quest Helper -- both booleans are plain
 * {@code boolean}s here, standing in for whatever {@link
 * QuestHelperBridge#hasSelectedQuest()} and {@code
 * QuestpathPlugin#routeQuestInProgress(NextQuestStatus)} would each answer
 * at that moment. This proves the gating decision; it says nothing about
 * whether either of those reads the client correctly, which {@code
 * QuestHelperBridgeTest} and this plugin's own quest-state reads cover
 * separately.
 */
public class QuestStartArrivalGateTest
{
	/** Nothing arrived, whatever either signal says -- vacuously nothing to allow. */
	@Test
	public void emptyArrivalStaysEmpty()
	{
		assertTrue(QuestStartArrivalGate.allow(List.of(), false, false).isEmpty());
		assertTrue(QuestStartArrivalGate.allow(List.of(), true, false).isEmpty());
		assertTrue(QuestStartArrivalGate.allow(List.of(), false, true).isEmpty());
		assertTrue(QuestStartArrivalGate.allow(List.of(), true, true).isEmpty());
	}

	/**
	 * The ordinary case this feature exists for: nothing selected in Quest
	 * Helper and nothing on the route in progress, so a genuine arrival is
	 * let through unchanged.
	 */
	@Test
	public void arrivalsPassThroughWhenNothingIsAlreadyUnderway()
	{
		List<String> arrived = List.of("Cook's Assistant");
		assertEquals(arrived, QuestStartArrivalGate.allow(arrived, false, false));
	}

	/**
	 * The first report this class closed: Prince Ali Rescue was selected in
	 * Quest Helper, arriving at A Porcine of Interest's start must not
	 * switch it over. Nothing fires at all while something else is already
	 * selected -- not even a partial list.
	 */
	@Test
	public void nothingFiresWhileQuestHelperAlreadyHasSomethingSelected()
	{
		List<String> arrived = List.of("A Porcine of Interest");
		assertTrue(QuestStartArrivalGate.allow(arrived, true, false).isEmpty());
	}

	/**
	 * The second report this class closed, and the one the mid-quest gate
	 * bug was actually about: the real workflow is start a quest, do a bit,
	 * close Quest Helper -- so {@code questHelperHasSelection} reads false
	 * (nothing selected, panel closed) even though Sheep Herder, a route
	 * quest, is still genuinely in progress. Arriving at Tribal Totem's
	 * start must still not fire.
	 */
	@Test
	public void nothingFiresWhenARouteQuestIsInProgressEvenWithQuestHelperClosed()
	{
		List<String> arrived = List.of("Tribal Totem");
		assertTrue(QuestStartArrivalGate.allow(arrived, false, true).isEmpty());
	}

	/** Both signals true at once is still just "blocked", not a special case. */
	@Test
	public void nothingFiresWhenBothSignalsAreTrue()
	{
		List<String> arrived = List.of("Tribal Totem");
		assertTrue(QuestStartArrivalGate.allow(arrived, true, true).isEmpty());
	}

	/** Several arrivals on the same tick are all suppressed together, not just the first. */
	@Test
	public void multipleArrivalsAreAllSuppressedTogetherWhenQuestHelperHasSomethingSelected()
	{
		List<String> arrived = List.of("Quest A", "Quest B");
		assertTrue(QuestStartArrivalGate.allow(arrived, true, false).isEmpty());
	}

	/** Same all-or-nothing suppression, driven by the route signal instead of Quest Helper's own. */
	@Test
	public void multipleArrivalsAreAllSuppressedTogetherWhenARouteQuestIsInProgress()
	{
		List<String> arrived = List.of("Quest A", "Quest B");
		assertTrue(QuestStartArrivalGate.allow(arrived, false, true).isEmpty());
	}
}
