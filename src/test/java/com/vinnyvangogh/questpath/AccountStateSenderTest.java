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
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("username", "its chyl");
		m.put("quests", quests);
		m.put("levels", levels);
		return m;
	}

	private static Map<String, Integer> one(String key, int value)
	{
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put(key, value);
		return m;
	}

	/**
	 * The fingerprint is what stops a push per varbit while training. It must
	 * ignore what the server does not act on, and notice what it does.
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
