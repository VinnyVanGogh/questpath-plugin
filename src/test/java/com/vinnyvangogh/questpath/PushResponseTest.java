package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
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
}
