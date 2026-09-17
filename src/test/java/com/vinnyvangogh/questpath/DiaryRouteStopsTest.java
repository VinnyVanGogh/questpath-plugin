package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which outstanding diary task is worth walking to next, and where each one
 * actually sends you.
 *
 * <p>Fixtures are built with a plain {@code Gson}, never {@link
 * QuestGraph#load}: the bundled {@code quests.json} is v11 until the
 * orchestrator regenerates it, and this class only needs the two small,
 * package-private model types anyway -- see {@link QuestGraphTest} for the
 * same convention.
 */
public class DiaryRouteStopsTest
{
	private static final Gson GSON = new Gson();

	private static QuestGraph.DiaryTask task(long tierId, int ordinal, String diary, String tier,
		String locationsJson)
	{
		String json = "{\"tier_id\":" + tierId + ",\"ordinal\":" + ordinal
			+ ",\"diary\":\"" + diary + "\",\"tier\":\"" + tier + "\",\"text\":\"Do the thing.\""
			+ (locationsJson == null ? "" : ",\"locations\":" + locationsJson) + "}";
		return GSON.fromJson(json, QuestGraph.DiaryTask.class);
	}

	private static String locationsAt(int x, int y, int plane)
	{
		return "[{\"page_name\":\"Somewhere\",\"x\":" + x + ",\"y\":" + y + ",\"plane\":" + plane + "}]";
	}

	private static PushResponse.Diary diary(String json)
	{
		return GSON.fromJson(json, PushResponse.Diary.class);
	}

	// ------------------------------------------------------------------
	// stops()

	/** No diary block at all -- an older server -- yields no stops, not an error. */
	@Test
	public void noDiaryYieldsNoStops()
	{
		List<QuestGraph.DiaryTask> tasks = List.of(task(1, 1, "Ardougne", "Easy", locationsAt(1, 1, 0)));
		assertTrue(DiaryRouteStops.stops(tasks, null, null).isEmpty());
	}

	/** An actionable task with a location becomes a routable stop. */
	@Test
	public void anActionableLocatedTaskIsAStop()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", locationsAt(3200, 3200, 0));
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1,\"warnings\":[]}]}");

		List<DiaryRouteStops.Stop> stops = DiaryRouteStops.stops(List.of(task), diary, null);

		assertEquals(1, stops.size());
		assertEquals("Ardougne", stops.get(0).task().diary());
	}

	/** A task with no locations at all is never a stop -- see locationUnknown(). */
	@Test
	public void anActionableTaskWithNoLocationIsNotAStop()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", null);
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		assertTrue(DiaryRouteStops.stops(List.of(task), diary, null).isEmpty());
	}

	/** An actionable entry the graph does not recognise is never a stop -- see unmatched(). */
	@Test
	public void anUnmatchedActionableEntryIsNotAStop()
	{
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":99,\"ordinal\":1}]}");

		assertTrue(DiaryRouteStops.stops(List.of(), diary, null).isEmpty());
	}

	/** Warnings on the actionable entry ride the Stop, verbatim. */
	@Test
	public void warningsRideTheStop()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", locationsAt(3200, 3200, 0));
		PushResponse.Diary diary = diary(
			"{\"actionable\":[{\"tier_id\":1,\"ordinal\":1,\"warnings\":[\"boost needed\"]}]}");

		DiaryRouteStops.Stop stop = DiaryRouteStops.stops(List.of(task), diary, null).get(0);

		assertEquals(List.of("boost needed"), stop.warnings());
	}

	/** A pinned tier's task sorts ahead of an unpinned one that is much nearer. */
	@Test
	public void pinnedTierWinsOverDistance()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		QuestGraph.DiaryTask nearUnpinned = task(1, 1, "Ardougne", "Easy", locationsAt(3201, 3200, 0));
		QuestGraph.DiaryTask farPinned = task(2, 1, "Falador", "Easy", locationsAt(3400, 3400, 0));
		PushResponse.Diary diary = diary("{\"targets\":[{\"tier_id\":2,\"pinned\":true,\"deferred\":false}],"
			+ "\"actionable\":[{\"tier_id\":1,\"ordinal\":1},{\"tier_id\":2,\"ordinal\":1}]}");

		List<DiaryRouteStops.Stop> stops =
			DiaryRouteStops.stops(List.of(nearUnpinned, farPinned), diary, player);

		assertEquals(2, stops.size());
		assertEquals("Falador", stops.get(0).task().diary());
		assertTrue(stops.get(0).pinned());
		assertEquals("Ardougne", stops.get(1).task().diary());
	}

	/**
	 * Among two unpinned tasks, nearer sorts first -- Chebyshev tile
	 * distance, matching {@code WorldPoint#distanceTo}.
	 */
	@Test
	public void nearestUnpinnedTaskSortsFirst()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		QuestGraph.DiaryTask near = task(1, 1, "Ardougne", "Easy", locationsAt(3205, 3200, 0));
		QuestGraph.DiaryTask far = task(2, 1, "Falador", "Easy", locationsAt(3250, 3200, 0));
		PushResponse.Diary diary = diary(
			"{\"actionable\":[{\"tier_id\":2,\"ordinal\":1},{\"tier_id\":1,\"ordinal\":1}]}");

		List<DiaryRouteStops.Stop> stops = DiaryRouteStops.stops(List.of(near, far), diary, player);

		assertEquals("Ardougne", stops.get(0).task().diary());
		assertEquals(5, stops.get(0).distanceTiles());
		assertEquals("Falador", stops.get(1).task().diary());
	}

	/**
	 * A task whose only location is on another plane sorts after every
	 * same-plane candidate -- {@code WorldPoint#distanceTo} returns {@code
	 * Integer.MAX_VALUE} across planes, confirmed by {@code javap -c}
	 * against the installed {@code runelite-api-1.12.37.jar}, and this class
	 * relies on that directly rather than special-casing planes itself.
	 */
	@Test
	public void aCrossPlaneCandidateSortsAfterEverySamePlaneOne()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		// Very close in x/y, but one floor up -- must still sort behind the
		// far-away, same-plane task.
		QuestGraph.DiaryTask upstairs = task(1, 1, "Ardougne", "Easy", locationsAt(3201, 3200, 1));
		QuestGraph.DiaryTask farSamePlane = task(2, 1, "Falador", "Easy", locationsAt(3260, 3200, 0));
		PushResponse.Diary diary = diary(
			"{\"actionable\":[{\"tier_id\":1,\"ordinal\":1},{\"tier_id\":2,\"ordinal\":1}]}");

		List<DiaryRouteStops.Stop> stops = DiaryRouteStops.stops(List.of(upstairs, farSamePlane), diary, player);

		assertEquals("Falador", stops.get(0).task().diary());
		assertEquals("Ardougne", stops.get(1).task().diary());
		assertEquals(Integer.MAX_VALUE, stops.get(1).distanceTiles());
	}

	/** With no player position, every distance is zero and order falls back to (tier_id, ordinal). */
	@Test
	public void withNoPlayerPositionOrderIsDeterministicByKey()
	{
		QuestGraph.DiaryTask a = task(5, 1, "Ardougne", "Easy", locationsAt(1, 1, 0));
		QuestGraph.DiaryTask b = task(2, 1, "Falador", "Easy", locationsAt(999, 999, 0));
		PushResponse.Diary diary = diary(
			"{\"actionable\":[{\"tier_id\":5,\"ordinal\":1},{\"tier_id\":2,\"ordinal\":1}]}");

		List<DiaryRouteStops.Stop> stops = DiaryRouteStops.stops(List.of(a, b), diary, null);

		assertEquals(2L, stops.get(0).task().tierId());
		assertEquals(5L, stops.get(1).task().tierId());
	}

	// ------------------------------------------------------------------
	// unmatched()

	@Test
	public void unmatchedListsAnActionableEntryTheGraphDoesNotRecognise()
	{
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":42,\"ordinal\":9,\"warnings\":[\"x\"]}]}");

		List<DiaryRouteStops.Unmatched> unmatched = DiaryRouteStops.unmatched(List.of(), diary);

		assertEquals(1, unmatched.size());
		assertEquals(42L, unmatched.get(0).tierId());
		assertEquals(9, unmatched.get(0).ordinal());
		assertEquals(List.of("x"), unmatched.get(0).warnings());
	}

	@Test
	public void unmatchedIsEmptyWhenEveryActionableEntryResolves()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", locationsAt(1, 1, 0));
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		assertTrue(DiaryRouteStops.unmatched(List.of(task), diary).isEmpty());
	}

	// ------------------------------------------------------------------
	// locationUnknown()

	@Test
	public void locationUnknownListsAMatchedTaskWithNoLocations()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", null);
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		List<QuestGraph.DiaryTask> unknown = DiaryRouteStops.locationUnknown(List.of(task), diary);

		assertEquals(1, unknown.size());
		assertEquals("Ardougne", unknown.get(0).diary());
	}

	@Test
	public void locationUnknownNeverIncludesAnUnmatchedEntry()
	{
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":99,\"ordinal\":1}]}");

		assertTrue(DiaryRouteStops.locationUnknown(List.of(), diary).isEmpty());
	}

	// ------------------------------------------------------------------
	// destinationOf() -- and the promise that the overlay's label and the
	// route call read the same point.

	/** Nearest, same-plane-first. */
	@Test
	public void destinationOfPicksTheNearestSamePlaneCandidate()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy",
			"[{\"page_name\":\"Far\",\"x\":3400,\"y\":3400,\"plane\":0},"
				+ "{\"page_name\":\"Near\",\"x\":3205,\"y\":3200,\"plane\":0}]");

		QuestGraph.Location chosen = DiaryRouteStops.destinationOf(task, player);

		assertEquals("Near", chosen.pageName());
	}

	/**
	 * A same-plane candidate wins even when a cross-plane one is
	 * numerically closer in x/y -- the whole point of "same-plane-first".
	 */
	@Test
	public void destinationOfPrefersSamePlaneOverCloserCrossPlane()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy",
			"[{\"page_name\":\"Upstairs\",\"x\":3201,\"y\":3200,\"plane\":1},"
				+ "{\"page_name\":\"GroundFloor\",\"x\":3230,\"y\":3200,\"plane\":0}]");

		QuestGraph.Location chosen = DiaryRouteStops.destinationOf(task, player);

		assertEquals("GroundFloor", chosen.pageName());
	}

	/** A task with no locations resolves to null, never a guessed destination. */
	@Test
	public void destinationOfALocationlessTaskIsNull()
	{
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy", null);

		assertNull(DiaryRouteStops.destinationOf(task, new WorldPoint(3200, 3200, 0)));
	}

	/**
	 * The single load-bearing promise this class exists to keep: the overlay
	 * label's destination and the route's destination are the exact same
	 * call, on the exact same data, so they can never name two different
	 * places for the same task -- see the class doc's report about a shop
	 * label that once disagreed with the route it sat beside.
	 */
	@Test
	public void theOverlayLabelPathAndTheRoutePathPickTheSamePointForAMultiLocationTask()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		QuestGraph.DiaryTask task = task(1, 1, "Ardougne", "Easy",
			"[{\"page_name\":\"Far\",\"x\":3400,\"y\":3400,\"plane\":0},"
				+ "{\"page_name\":\"Near\",\"x\":3205,\"y\":3200,\"plane\":0},"
				+ "{\"page_name\":\"Upstairs\",\"x\":3202,\"y\":3200,\"plane\":1}]");
		PushResponse.Diary diary = diary("{\"actionable\":[{\"tier_id\":1,\"ordinal\":1}]}");

		// The route path: what stops() computed and stored on the Stop.
		DiaryRouteStops.Stop stop = DiaryRouteStops.stops(List.of(task), diary, player).get(0);

		// The overlay label path: destinationOf(), called independently the
		// way DiaryRouteOverlay itself calls it.
		QuestGraph.Location labelled = DiaryRouteStops.destinationOf(task, player);

		assertEquals(labelled.pageName(), stop.destination().pageName());
		assertEquals(labelled.x(), stop.destination().x());
		assertEquals(labelled.y(), stop.destination().y());
		assertEquals(labelled.plane(), stop.destination().plane());
	}
}
