package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Honouring what the account already said about where it will go.
 *
 * <p>The declarations were on the questpath website the whole time and the
 * plugin could not hear them, so a hardcore ironman was routed towards
 * Wilderness spawns with nothing said. The thing that must survive this
 * plumbing is the severity: WARN and FATAL are different answers, and
 * collapsing them into one would substitute this account's preference for
 * everybody's.
 */
public class DropRegionPolicyTest
{
	private static final Gson GSON = new Gson();

	private static PlayerConstraint constraint(String json)
	{
		return GSON.fromJson(json, PlayerConstraint.class);
	}

	private static PlayerConstraint wilderness(String severity, boolean enabled)
	{
		return constraint("{\"kind\":\"wilderness\",\"detail\":\"\",\"severity\":\"" + severity
			+ "\",\"enabled\":" + enabled + "}");
	}

	/** Nothing declared, nothing changes. */
	@Test
	public void anUndeclaredRegionIsAllowed()
	{
		assertEquals(DropRegionPolicy.Verdict.ALLOWED,
			DropRegionPolicy.of("wilderness", Collections.emptyList()));
		assertEquals(DropRegionPolicy.Verdict.ALLOWED,
			DropRegionPolicy.of("general", List.of(wilderness("FATAL", true))));
	}

	/** WARN routes there and names the region. */
	@Test
	public void aWarnedRegionIsOfferedAndSaidOutLoud()
	{
		List<PlayerConstraint> declared = List.of(wilderness("WARN", true));

		assertEquals(DropRegionPolicy.Verdict.WARNED,
			DropRegionPolicy.of("wilderness", declared));
	}

	/** FATAL is not offered at all. */
	@Test
	public void aBlockedRegionIsNotADestination()
	{
		List<PlayerConstraint> declared = List.of(wilderness("FATAL", true));

		assertEquals(DropRegionPolicy.Verdict.BLOCKED,
			DropRegionPolicy.of("wilderness", declared));
	}

	/** A switched-off declaration is not a declaration. */
	@Test
	public void aDisabledDeclarationDoesNothing()
	{
		assertEquals(DropRegionPolicy.Verdict.ALLOWED,
			DropRegionPolicy.of("wilderness", List.of(wilderness("FATAL", false))));
	}

	/**
	 * A declaration naming one specific thing is about that thing, not about
	 * everywhere it lives.
	 *
	 * <p>"slayer_unlock: dragons" must not be read as banning a region. This
	 * is the same error that has already been made twice on the server -- a
	 * requirement on one route into a town read as a requirement on the town,
	 * and a requirement on one item on a shelf read as a requirement on the
	 * shop.
	 */
	@Test
	public void aDeclarationAboutOneThingIsNotADeclarationAboutARegion()
	{
		List<PlayerConstraint> declared = List.of(constraint(
			"{\"kind\":\"wilderness\",\"detail\":\"Chaos Elemental\",\"severity\":\"FATAL\","
				+ "\"enabled\":true}"));

		assertEquals(DropRegionPolicy.Verdict.ALLOWED,
			DropRegionPolicy.of("wilderness", declared));
	}

	/** Two declarations on one region: the blocking one wins. */
	@Test
	public void blockOutranksWarnOnTheSameRegion()
	{
		assertEquals(DropRegionPolicy.Verdict.BLOCKED, DropRegionPolicy.of("wilderness",
			Arrays.asList(wilderness("WARN", true), wilderness("FATAL", true))));
		assertEquals("and the order it arrives in does not matter",
			DropRegionPolicy.Verdict.BLOCKED, DropRegionPolicy.of("wilderness",
				Arrays.asList(wilderness("FATAL", true), wilderness("WARN", true))));
	}

	/**
	 * A severity this build has never seen warns rather than blocks.
	 *
	 * <p>Refusing to route somewhere on the strength of a word this build
	 * cannot interpret is a worse failure than mentioning it: the player
	 * loses a destination and is told nothing about why.
	 */
	@Test
	public void anUnrecognisedSeverityWarnsRatherThanBlocks()
	{
		assertEquals(DropRegionPolicy.Verdict.WARNED,
			DropRegionPolicy.of("wilderness", List.of(wilderness("SOMETHING_NEW", true))));
	}

	/** An older questpath sends no constraints, which reads as none declared. */
	@Test
	public void anOlderServerSendingNothingIsHandledLikeNothingDeclared()
	{
		PushResponse response = GSON.fromJson("{\"player\":\"its chyl\"}", PushResponse.class);

		assertTrue(response.constraints().isEmpty());
		assertEquals(DropRegionPolicy.Verdict.ALLOWED,
			DropRegionPolicy.of("wilderness", response.constraints()));
	}

	/** The real payload questpath sends for this account parses as expected. */
	@Test
	public void theServersOwnPayloadParses()
	{
		PushResponse response = GSON.fromJson("{\"player\":\"its chyl\",\"constraints\":["
			+ "{\"kind\":\"wilderness\",\"detail\":\"\",\"severity\":\"WARN\",\"enabled\":true},"
			+ "{\"kind\":\"unsafe_combat\",\"detail\":\"\",\"severity\":\"WARN\",\"enabled\":true},"
			+ "{\"kind\":\"slayer_unlock\",\"detail\":\"dragons\",\"severity\":\"FATAL\","
			+ "\"enabled\":true}]}", PushResponse.class);

		assertEquals(3, response.constraints().size());
		assertEquals(DropRegionPolicy.Verdict.WARNED,
			DropRegionPolicy.of("wilderness", response.constraints()));
	}
}
