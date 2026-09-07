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
 * propagate, and every one of those three seams is only ever invoked from
 * inside the client-thread hand-off -- never before it, never without it.
 *
 * <p>What this class does not prove: that {@code
 * client.getWidget(InterfaceID.GeOffers.UNIVERSE)}, {@code
 * client.setVarcStrValue}, and {@code client.runScript(ScriptID
 * .GE_ITEM_SEARCH)} are the real, verified members this bridge is built on.
 * That was confirmed separately with {@code javap} against the actually
 * installed {@code runelite-api-1.12.37.jar} and {@code
 * client-1.12.37.jar} -- see {@link GrandExchangeSearchBridge}'s own class
 * doc for exactly what that confirmed and why a decompile is not something
 * worth re-running inside a unit test.
 */
public class GrandExchangeSearchBridgeTest
{
	private static GrandExchangeSearchBridge.Outcome searchOf(GrandExchangeSearchBridge bridge, String itemName)
	{
		AtomicReference<GrandExchangeSearchBridge.Outcome> result = new AtomicReference<>();
		bridge.search(itemName, result::set);
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
		List<String> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> false, searched::add, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.NOT_OPEN, searchOf(bridge, "Pot"));
		assertTrue("nothing should be searched for while the interface is reported closed", searched.isEmpty());
	}

	@Test
	public void sentSetsTheSearchTextAndRunsTheScriptWhenOpen()
	{
		List<String> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, searched::add, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.SENT, searchOf(bridge, "Ball of wool"));
		assertEquals(List.of("Ball of wool"), searched);
	}

	@Test
	public void callFailedWhenTheOpenCheckThrows()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() ->
		{
			throw new RuntimeException("widget tree not ready");
		}, name ->
		{
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, "Pot"));
	}

	@Test
	public void callFailedWhenTheActionThrows()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, name ->
		{
			throw new RuntimeException("client rejected the call");
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, "Pot"));
	}

	/**
	 * The exact hazard this project has already hit twice: {@code
	 * setVarcStrValue}/{@code runScript} assert they are on the client
	 * thread, and that assertion is an {@code AssertionError} -- an {@code
	 * Error}, not an {@code Exception}. A catch narrowed to {@code
	 * RuntimeException} would let it straight through; this pins that it is
	 * caught and reported instead.
	 */
	@Test
	public void callFailedWhenTheActionThrowsAnAssertionError()
	{
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, name ->
		{
			throw new AssertionError("must be called on client thread");
		}, inline());

		assertEquals(GrandExchangeSearchBridge.Outcome.CALL_FAILED, searchOf(bridge, "Pot"));
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
		List<String> searched = new ArrayList<>();
		GrandExchangeSearchBridge bridge = new GrandExchangeSearchBridge(() -> true, searched::add,
			capturedTasks::add);

		AtomicReference<GrandExchangeSearchBridge.Outcome> result = new AtomicReference<>();
		bridge.search("Pot", result::set);

		assertEquals("exactly one task must be handed to the client-thread stand-in", 1, capturedTasks.size());
		assertTrue("nothing should be searched for until the hand-off actually runs its task", searched.isEmpty());
		assertEquals("the outcome callback must not fire until the hand-off runs its task either",
			null, result.get());

		capturedTasks.get(0).run();
		assertEquals(List.of("Pot"), searched);
		assertEquals(GrandExchangeSearchBridge.Outcome.SENT, result.get());
	}
}
