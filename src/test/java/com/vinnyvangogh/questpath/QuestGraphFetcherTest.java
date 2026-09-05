package com.vinnyvangogh.questpath;

import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The parts of the fetch that are decidable without a network -- see
 * {@link AccountStateSenderTest}'s own doc for why the request itself is not
 * exercised here.
 */
public class QuestGraphFetcherTest
{
	@Test
	public void endpointAppendsThePathToTheConfiguredAddress()
	{
		assertEquals(HttpUrl.parse("http://localhost:8080/api/quest-graph"),
			QuestGraphFetcher.endpoint("http://localhost:8080"));
	}

	@Test
	public void endpointToleratesATrailingSlashAndSurroundingSpace()
	{
		HttpUrl want = HttpUrl.parse("http://localhost:8080/api/quest-graph");
		assertEquals(want, QuestGraphFetcher.endpoint("http://localhost:8080/"));
		assertEquals(want, QuestGraphFetcher.endpoint("  http://localhost:8080  "));
	}

	@Test
	public void endpointKeepsAHostedInstanceSubPath()
	{
		assertEquals(HttpUrl.parse("https://example.test/questpath/api/quest-graph"),
			QuestGraphFetcher.endpoint("https://example.test/questpath"));
	}

	/**
	 * An address that does not parse is a configuration someone has not
	 * finished, and must not become a request to somewhere unintended.
	 */
	@Test
	public void endpointRefusesWhatItCannotParse()
	{
		assertNull(QuestGraphFetcher.endpoint(null));
		assertNull(QuestGraphFetcher.endpoint(""));
		assertNull(QuestGraphFetcher.endpoint("   "));
		assertNull(QuestGraphFetcher.endpoint("localhost:8080"));
		assertNull(QuestGraphFetcher.endpoint("not a url"));
	}
}
