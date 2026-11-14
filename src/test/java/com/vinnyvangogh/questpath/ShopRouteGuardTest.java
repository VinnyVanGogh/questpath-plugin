package com.vinnyvangogh.questpath;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link ShopRouteGuard#blocksQuestRouting}: a shop route the player
 * deliberately sent to Shortest Path holds route-adjacent chat back for its
 * window, and no longer. See the class doc.
 *
 * <p>Pure function, no client and no real clock -- every {@link Instant} is
 * a fixed test value, so {@link ShopRouteGuard#ACTIVE_WINDOW}'s boundary is
 * exercised directly rather than by actually waiting five minutes.
 */
public class ShopRouteGuardTest
{
	private static final Instant SENT_AT = Instant.parse("2026-09-04T21:00:00Z");

	/** Never sent this session -- nothing to block on. */
	@Test
	public void nullSentAtNeverBlocks()
	{
		assertFalse(ShopRouteGuard.blocksQuestRouting(null, SENT_AT));
	}

	/** The ordinary case: sent a moment ago, well inside the active window. */
	@Test
	public void blocksImmediatelyAfterSending()
	{
		Instant now = SENT_AT.plusSeconds(5);
		assertTrue(ShopRouteGuard.blocksQuestRouting(SENT_AT, now));
	}

	/** Still inside the window a few minutes later. */
	@Test
	public void blocksPartwayThroughTheWindow()
	{
		Instant now = SENT_AT.plus(ShopRouteGuard.ACTIVE_WINDOW.dividedBy(2));
		assertTrue(ShopRouteGuard.blocksQuestRouting(SENT_AT, now));
	}

	/** Exactly at the window's edge no longer blocks -- the window is a strict "before", not "before or at". */
	@Test
	public void noLongerBlocksExactlyAtTheWindowsEnd()
	{
		Instant now = SENT_AT.plus(ShopRouteGuard.ACTIVE_WINDOW);
		assertFalse(ShopRouteGuard.blocksQuestRouting(SENT_AT, now));
	}

	/** Well past the window -- the trip was abandoned rather than finished, and the safety net has expired. */
	@Test
	public void noLongerBlocksLongAfterTheWindow()
	{
		Instant now = SENT_AT.plus(ShopRouteGuard.ACTIVE_WINDOW).plusSeconds(60);
		assertFalse(ShopRouteGuard.blocksQuestRouting(SENT_AT, now));
	}
}
