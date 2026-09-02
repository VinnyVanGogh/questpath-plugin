package com.vinnyvangogh.questpath;

import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * The parts of the push that are decidable without a client or a network.
 *
 * The request itself is not tested here: exercising it would need an OkHttp
 * mock server, which is a dependency this plugin does not carry and the Plugin
 * Hub would want hash-verified. What is tested is everything that decides
 * whether a request is worth making at all.
 */
public class AccountStateSenderTest
{
	@Test
	public void endpointAppendsThePathToTheConfiguredAddress()
	{
		assertEquals(HttpUrl.parse("http://localhost:8080/api/plugin/state"),
			AccountStateSender.endpoint("http://localhost:8080"));
	}

	@Test
	public void endpointToleratesATrailingSlashAndSurroundingSpace()
	{
		HttpUrl want = HttpUrl.parse("http://localhost:8080/api/plugin/state");
		assertEquals(want, AccountStateSender.endpoint("http://localhost:8080/"));
		assertEquals(want, AccountStateSender.endpoint("  http://localhost:8080  "));
	}

	@Test
	public void endpointKeepsAHostedInstanceSubPath()
	{
		assertEquals(HttpUrl.parse("https://example.test/questpath/api/plugin/state"),
			AccountStateSender.endpoint("https://example.test/questpath"));
	}

	/**
	 * An address that does not parse is a configuration someone has not
	 * finished, and must not become a request to somewhere unintended.
	 */
	@Test
	public void endpointRefusesWhatItCannotParse()
	{
		assertNull(AccountStateSender.endpoint(null));
		assertNull(AccountStateSender.endpoint(""));
		assertNull(AccountStateSender.endpoint("   "));
		assertNull(AccountStateSender.endpoint("localhost:8080"));
		assertNull(AccountStateSender.endpoint("not a url"));
	}

	private static Map<String, Object> payload(Map<String, Integer> quests, Map<String, Integer> levels)
	{
		return payload(quests, levels, one("Strength", 2_673_114));
	}

	private static Map<String, Object> payload(Map<String, Integer> quests, Map<String, Integer> levels,
		Map<String, Integer> experience)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("username", "its chyl");
		m.put("quests", quests);
		m.put("levels", levels);
		m.put("experience", experience);
		return m;
	}

	private static Map<String, Integer> one(String key, int value)
	{
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put(key, value);
		return m;
	}

	/**
	 * The structural fingerprint is what earns an immediate push. It must
	 * ignore what the server does not act on, notice what it does, and -- the
	 * part that matters -- ignore experience, which moves every few seconds
	 * while training and has its own timer.
	 */
	@Test
	public void fingerprintIgnoresTheUsernameButNoticesQuestsAndLevels()
	{
		Map<String, Integer> quests = one("Cook's Assistant", 2);
		Map<String, Integer> levels = one("Strength", 83);

		Map<String, Object> renamed = payload(quests, levels);
		renamed.put("username", "someone else");
		assertEquals("a display-name difference is not a state change",
			AccountState.fingerprint(payload(quests, levels)),
			AccountState.fingerprint(renamed));

		assertNotEquals("a finished quest must push",
			AccountState.fingerprint(payload(quests, levels)),
			AccountState.fingerprint(payload(one("Cook's Assistant", 1), levels)));

		assertNotEquals("a level up must push",
			AccountState.fingerprint(payload(quests, levels)),
			AccountState.fingerprint(payload(quests, one("Strength", 84))));

		assertEquals("experience alone must not earn an immediate push",
			AccountState.fingerprint(payload(quests, levels, one("Strength", 1))),
			AccountState.fingerprint(payload(quests, levels, one("Strength", 999_999))));
	}

	/**
	 * Experience gets its own fingerprint so it can be sent on a timer: often
	 * enough that questpath's stored xp is never far behind, rarely enough
	 * that training is not a request per tick.
	 */
	@Test
	public void experienceFingerprintNoticesExperienceAndNothingElse()
	{
		Map<String, Integer> quests = one("Cook's Assistant", 2);
		Map<String, Integer> levels = one("Strength", 83);

		assertNotEquals("an xp drop must be visible to the timer",
			AccountState.experienceFingerprint(payload(quests, levels, one("Strength", 1))),
			AccountState.experienceFingerprint(payload(quests, levels, one("Strength", 2))));

		assertEquals("a quest finishing is not an experience change",
			AccountState.experienceFingerprint(payload(quests, levels)),
			AccountState.experienceFingerprint(payload(one("Cook's Assistant", 1), levels)));
	}

	@Test
	public void fingerprintIsStableForAnUnchangedAccount()
	{
		Map<String, Integer> quests = one("Dragon Slayer I", 2);
		Map<String, Integer> levels = one("Magic", 39);
		assertEquals(AccountState.fingerprint(payload(quests, levels)),
			AccountState.fingerprint(payload(quests, levels)));
	}
}
