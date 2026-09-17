package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The wire contract for the account's saved route, which the NEXT_QUEST
 * section reads off the push response.
 *
 * questpath always sends route_order, even empty, and that emptiness is the
 * signal for "no route saved" -- so parsing it must never turn a present-but-
 * empty array into the same null a genuinely absent field would produce.
 */
public class PushResponseTest
{
	private static final Gson GSON = new Gson();

	private static PushResponse parse(String json)
	{
		return GSON.fromJson(json, PushResponse.class);
	}

	@Test
	public void parsesTheSavedRouteInOrder()
	{
		PushResponse response = parse("{\"player\":\"its chyl\","
			+ "\"route_order\":[\"Cook's Assistant\",\"Sheep Shearer\"]}");

		assertEquals(Arrays.asList("Cook's Assistant", "Sheep Shearer"), response.routeOrder());
	}

	@Test
	public void anExplicitlyEmptyRouteStaysEmptyNotNull()
	{
		PushResponse response = parse("{\"player\":\"its chyl\",\"route_order\":[]}");

		List<String> routeOrder = response.routeOrder();
		assertTrue(routeOrder.isEmpty());
	}

	/**
	 * A questpath old enough to predate this field sends no key at all.
	 * Treated the same as an empty route rather than a crash -- there is
	 * nothing else honest to show.
	 */
	@Test
	public void aResponseWithoutTheFieldIsAlsoEmpty()
	{
		assertTrue(parse("{\"player\":\"its chyl\"}").routeOrder().isEmpty());
	}

	// ------------------------------------------------------------------
	// diary -- absent (an older server) must stay distinct from present but
	// empty (a newer server with nothing to report).

	@Test
	public void aResponseWithNoDiaryKeyHasNoDiaryAtAll()
	{
		PushResponse response = parse("{\"player\":\"its chyl\"}");

		assertFalse(response.hasDiary());
		assertNull(response.diary());
	}

	@Test
	public void aPresentButEmptyDiaryBlockHasDiary()
	{
		PushResponse response = parse("{\"player\":\"its chyl\",\"diary\":{}}");

		assertTrue(response.hasDiary());
		assertTrue(response.diary().targets().isEmpty());
		assertTrue(response.diary().actionable().isEmpty());
		assertTrue(response.diary().outstanding().isEmpty());
		assertTrue(response.diary().taskSync().isEmpty());
	}

	@Test
	public void parsesATargetsPinAndDeferFlags()
	{
		PushResponse response = parse("{\"diary\":{\"targets\":["
			+ "{\"tier_id\":3,\"pinned\":true,\"deferred\":false}]}}");

		PushResponse.Diary.Target target = response.diary().targets().get(0);
		assertEquals(3L, target.tierId());
		assertTrue(target.pinned());
		assertFalse(target.deferred());
	}

	@Test
	public void parsesAnActionableEntryWithWarnings()
	{
		PushResponse response = parse("{\"diary\":{\"actionable\":["
			+ "{\"tier_id\":3,\"ordinal\":2,\"warnings\":[\"boostable Agility\"]}]}}");

		PushResponse.Diary.Actionable actionable = response.diary().actionable().get(0);
		assertEquals(3L, actionable.tierId());
		assertEquals(2, actionable.ordinal());
		assertEquals(java.util.List.of("boostable Agility"), actionable.warnings());
	}

	@Test
	public void parsesAnOutstandingEntryWithStatusAndReason()
	{
		PushResponse response = parse("{\"diary\":{\"outstanding\":["
			+ "{\"tier_id\":3,\"ordinal\":5,\"status\":\"BLOCKED\",\"reason\":\"needs Desert Treasure I\","
			+ "\"deferred\":true}]}}");

		PushResponse.Diary.Outstanding outstanding = response.diary().outstanding().get(0);
		assertEquals(3L, outstanding.tierId());
		assertEquals(5, outstanding.ordinal());
		assertEquals("BLOCKED", outstanding.status());
		assertEquals("needs Desert Treasure I", outstanding.reason());
		assertTrue(outstanding.deferred());
	}

	@Test
	public void parsesTaskSyncStateAndAnRfc3339Timestamp()
	{
		PushResponse response = parse("{\"diary\":{\"task_sync\":["
			+ "{\"tier_id\":3,\"state\":\"ok\",\"synced_at\":\"2026-09-13T08:00:00Z\"}]}}");

		PushResponse.Diary.TaskSync sync = response.diary().taskSync().get(0);
		assertEquals(3L, sync.tierId());
		assertEquals("ok", sync.state());
		assertEquals(java.time.Instant.parse("2026-09-13T08:00:00Z"), sync.syncedAt());
	}

	@Test
	public void aShapeMismatchStateParsesVerbatim()
	{
		PushResponse response = parse("{\"diary\":{\"task_sync\":["
			+ "{\"tier_id\":3,\"state\":\"shape_mismatch\"}]}}");

		assertEquals("shape_mismatch", response.diary().taskSync().get(0).state());
	}

	/** An unparsable or missing synced_at is unknown, never a fabricated instant. */
	@Test
	public void aMissingSyncedAtParsesToNull()
	{
		PushResponse response = parse("{\"diary\":{\"task_sync\":[{\"tier_id\":3,\"state\":\"none\"}]}}");

		assertNull(response.diary().taskSync().get(0).syncedAt());
	}

	@Test
	public void aMalformedSyncedAtParsesToNullRatherThanThrowing()
	{
		PushResponse response = parse("{\"diary\":{\"task_sync\":["
			+ "{\"tier_id\":3,\"state\":\"ok\",\"synced_at\":\"not a date\"}]}}");

		assertNull(response.diary().taskSync().get(0).syncedAt());
	}
}
