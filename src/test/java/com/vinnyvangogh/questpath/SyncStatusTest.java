package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The push is silent in game, so this text is the only way to tell "working"
 * from "wrong token" from "questpath is closed". Two of those three are things
 * only the player can fix, and neither is discoverable without being told.
 */
public class SyncStatusTest
{
	private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

	@Test
	public void aWrongTokenSaysSoRatherThanFailingQuietly()
	{
		SyncStatus rejected = SyncStatus.rejected(NOW.minus(Duration.ofSeconds(30)), 401);
		assertEquals("Pairing token rejected · just now", rejected.describe(NOW));
		assertTrue("a wrong token is the player's to fix", rejected.needsAttention());
	}

	@Test
	public void anUnknownRejectionReportsItsCodeRatherThanGuessing()
	{
		SyncStatus rejected = SyncStatus.rejected(NOW, 503);
		assertEquals("Quest Route server answered 503 · just now", rejected.describe(NOW));
	}

	@Test
	public void aClosedQuestpathIsNotTheSameAsARejection()
	{
		SyncStatus unreachable = SyncStatus.unreachable(NOW.minus(Duration.ofMinutes(5)));
		assertEquals("Quest Route server not reachable · 5m ago", unreachable.describe(NOW));
		assertFalse("questpath being closed is not a misconfiguration to warn about",
			unreachable.needsAttention());
	}

	@Test
	public void aMissingTokenIsCalledOutBeforeAnyRequestIsMade()
	{
		SyncStatus noToken = SyncStatus.of(SyncStatus.State.NO_TOKEN);
		assertEquals("No pairing token set", noToken.describe(NOW));
		assertTrue(noToken.needsAttention());
	}

	@Test
	public void beingOffIsNotAProblem()
	{
		SyncStatus disabled = SyncStatus.of(SyncStatus.State.DISABLED);
		assertEquals("Off", disabled.describe(NOW));
		assertFalse("the toggle being off is a choice, not a fault", disabled.needsAttention());
	}

	@Test
	public void nothingSentYetIsDistinctFromNothingWorking()
	{
		SyncStatus waiting = SyncStatus.of(SyncStatus.State.WAITING);
		assertEquals("Nothing sent yet", waiting.describe(NOW));
		assertFalse(waiting.needsAttention());
	}

	@Test
	public void agesReadTheWayAPersonWouldSayThem()
	{
		assertEquals("Sent just now", SyncStatus.accepted(NOW).describe(NOW));
		assertEquals("Sent just now", SyncStatus.accepted(NOW.minusSeconds(59)).describe(NOW));
		assertEquals("Sent 1m ago", SyncStatus.accepted(NOW.minusSeconds(60)).describe(NOW));
		assertEquals("Sent 59m ago", SyncStatus.accepted(NOW.minusSeconds(3599)).describe(NOW));
		assertEquals("Sent 1h ago", SyncStatus.accepted(NOW.minusSeconds(3600)).describe(NOW));
	}
}
