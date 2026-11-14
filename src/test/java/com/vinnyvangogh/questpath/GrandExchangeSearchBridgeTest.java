package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link GrandExchangeSearchBridge}'s own logic: it never performs the
 * search without checking the Grand Exchange is open first, it reports
 * {@link GrandExchangeSearchBridge.Outcome#NOT_OPEN} rather than acting when
 * it is not, a thrown check or action is reported as {@link
 * GrandExchangeSearchBridge.Outcome#CALL_FAILED} rather than left to
 * propagate, every one of those seams is only ever invoked from inside the
 * client-thread hand-off, and what reaches the action is an item id -- never
 * a name, which is the Hub's autotyping rule. See the class doc.
 */
public class GrandExchangeSearchBridgeTest
{
	private static final int POT = 1931;

	private static GrandExchangeSearchBridge.Outcome searchOf(GrandExchangeSearchBridge bridge, int itemId)
	{
		AtomicReference<GrandExchangeSearchBridge.Outcome> result = new AtomicReference<>();
		bridge.search(itemId, result::set);
		return result.get();
	}

	/** The client-thread hand-off that just runs its task immediately, for tests that do not care about ordering. */
	private static Consumer<Runnable> inline()
	{
		return Runnable::run;
	}

	@Test
	public void notOpenWhenTheGrandExchangeInterfaceIsClosed()
	{
		List<Integer> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> false, searched::add, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.NOT_OPEN, searchOf(bridge, POT));
		assertTrue("nothing should be searched for while the interface is reported closed", searched.isEmpty());
	}

	@Test
	public void sentHandsTheItemIdToTheActionWhenOpen()
	{
		List<Integer> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, searched::add, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.SENT, searchOf(bridge, POT));
		assertEquals(List.of(POT), searched);
	}

	@Test
	public void callFailedWhenTheOpenCheckThrows()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() ->
		{
			throw new RuntimeException("widget tree not ready");
		}, id ->
		{
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, POT));
	}

	@Test
	public void callFailedWhenTheActionThrows()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, id ->
		{
			throw new RuntimeException("client rejected the call");
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, POT));
	}

	/**
	 * The client-thread assertion is an {@code AssertionError} -- an {@code
	 * Error}, not an {@code Exception}. A catch narrowed to {@code
	 * RuntimeException} would let it straight through; this pins that it is
	 * caught and reported instead.
	 */
	@Test
	public void callFailedWhenTheActionThrowsAnAssertionError()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, id ->
		{
			throw new AssertionError("must be called on client thread");
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, POT));
	}

	/**
	 * Neither {@code open} nor {@code action} may run before the client
	 * thread hand-off has actually run its task -- the same regression shape
	 * {@link ShortestPathBridgeTest#clientThreadEventPosterNeverPostsWithoutTheHandoffRunningItsTask}
	 * pins for {@link ShortestPathBridge}.
	 */
	@Test
	public void nothingRunsUntilTheHandoffActuallyRunsItsTask()
	{
		List<Runnable> capturedTasks = new ArrayList<>();
		List<Integer> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, searched::add,
			capturedTasks::add);

		AtomicReference<GrandExchangeSearchBridge.Outcome> result = new AtomicReference<>();
		bridge.search(POT, result::set);

		assertEquals("exactly one task must be handed to the client-thread stand-in", 1, capturedTasks.size());
		assertTrue("nothing should be searched for until the hand-off actually runs its task", searched.isEmpty());
		assertEquals("the outcome callback must not fire until the hand-off runs its task either",
			null, result.get());

		capturedTasks.get(0).run();
		assertEquals(List.of(POT), searched);
		assertEquals(GrandExchangeSearchBridge.Outcome.SENT, result.get());
	}
}
