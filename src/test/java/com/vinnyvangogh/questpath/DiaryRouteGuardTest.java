package com.vinnyvangogh.questpath;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every reason {@link DiaryRouteGuard#shouldNudge} can say no, checked one at
 * a time, plus the cooldown's exact boundary -- the same discipline {@link
 * ShopRouteGuardTest} applies to {@link ShopRouteGuard}.
 */
public class DiaryRouteGuardTest
{
	private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

	/** Every condition satisfied: the guard says yes. */
	@Test
	public void nudgesWhenEveryConditionIsSatisfied()
	{
		assertTrue(DiaryRouteGuard.shouldNudge(true, 0, false, false, null, NOW));
	}

	/** The feature's own toggle is off -- no, regardless of everything else. */
	@Test
	public void doesNotNudgeWhenTheFeatureIsDisabled()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(false, 0, false, false, null, NOW));
	}

	/** Exactly at the radius is still close enough. */
	@Test
	public void nudgesAtExactlyTheProximityRadius()
	{
		assertTrue(DiaryRouteGuard.shouldNudge(true, DiaryRouteGuard.PROXIMITY_RADIUS_TILES,
			false, false, null, NOW));
	}

	/** One tile past the radius is too far. */
	@Test
	public void doesNotNudgeOneTilePastTheProximityRadius()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(true, DiaryRouteGuard.PROXIMITY_RADIUS_TILES + 1,
			false, false, null, NOW));
	}

	/** A distance of {@code Integer.MAX_VALUE} -- unknown position or cross-plane -- never nudges. */
	@Test
	public void doesNotNudgeAnUnknownOrCrossPlaneDistance()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(true, Integer.MAX_VALUE, false, false, null, NOW));
	}

	/** A shop route sent to Shortest Path is still active -- no. */
	@Test
	public void doesNotNudgeWhileAShopRouteBlocks()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(true, 0, true, false, null, NOW));
	}

	/** A quest on the account's saved route is in progress -- no. */
	@Test
	public void doesNotNudgeWhileAQuestRouteIsInProgress()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(true, 0, false, true, null, NOW));
	}

	/** Both busy-elsewhere signals at once is still just "no," not a special case. */
	@Test
	public void doesNotNudgeWhenBothShopAndQuestRoutesAreActive()
	{
		assertFalse(DiaryRouteGuard.shouldNudge(true, 0, true, true, null, NOW));
	}

	/** Nudged a moment ago -- still inside the cooldown -- no. */
	@Test
	public void doesNotNudgeInsideTheCooldown()
	{
		Instant lastNudgedAt = NOW.minus(DiaryRouteGuard.NUDGE_COOLDOWN.dividedBy(2));
		assertFalse(DiaryRouteGuard.shouldNudge(true, 0, false, false, lastNudgedAt, NOW));
	}

	/**
	 * One instant before the cooldown elapses: still blocked. Exercised
	 * directly against the boundary, which is exactly what a pure function
	 * taking {@code Instant} parameters is for -- see the class doc.
	 */
	@Test
	public void doesNotNudgeOneInstantBeforeTheCooldownExpires()
	{
		Instant lastNudgedAt = NOW.minus(DiaryRouteGuard.NUDGE_COOLDOWN).plusMillis(1);
		assertFalse(DiaryRouteGuard.shouldNudge(true, 0, false, false, lastNudgedAt, NOW));
	}

	/** Exactly at the cooldown boundary: no longer inside it (the check is a strict {@code isBefore}). */
	@Test
	public void nudgesExactlyAtTheCooldownBoundary()
	{
		Instant lastNudgedAt = NOW.minus(DiaryRouteGuard.NUDGE_COOLDOWN);
		assertTrue(DiaryRouteGuard.shouldNudge(true, 0, false, false, lastNudgedAt, NOW));
	}

	/** One instant past the cooldown boundary: definitely clear. */
	@Test
	public void nudgesOneInstantAfterTheCooldownExpires()
	{
		Instant lastNudgedAt = NOW.minus(DiaryRouteGuard.NUDGE_COOLDOWN).minusMillis(1);
		assertTrue(DiaryRouteGuard.shouldNudge(true, 0, false, false, lastNudgedAt, NOW));
	}

	/** Never nudged before -- a null lastNudgedAt -- never blocks on cooldown. */
	@Test
	public void aNullLastNudgedAtNeverBlocksOnCooldown()
	{
		assertTrue(DiaryRouteGuard.shouldNudge(true, 0, false, false, null, NOW));
	}
}
