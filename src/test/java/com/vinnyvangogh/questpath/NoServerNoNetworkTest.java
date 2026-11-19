package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * With no server address configured, Quest Route must not attempt a single
 * request. The client here counts every call that reaches it.
 */
public class NoServerNoNetworkTest
{
	private final AtomicInteger requests = new AtomicInteger();

	private OkHttpClient countingClient()
	{
		return new OkHttpClient.Builder()
			.addInterceptor(chain ->
			{
				requests.incrementAndGet();
				throw new IOException("no network expected in this test");
			})
			.build();
	}

	@Test
	public void theDefaultAddressIsEmptyNotLocalhost()
	{
		QuestpathConfig defaults = new QuestpathConfig()
		{
		};
		assertEquals("", defaults.serverUrl());
		assertFalse(defaults.syncEnabled());
		assertFalse(defaults.fetchQuestGraph());
	}

	@Test
	public void theQuestGraphFetchWithNoUrlMakesNoRequestAndSaysWhy()
	{
		QuestGraphFetcher fetcher = new QuestGraphFetcher(countingClient(), new Gson());

		for (String blank : new String[]{null, "", "   "})
		{
			QuestGraphFetcher.Outcome[] outcome = new QuestGraphFetcher.Outcome[1];
			fetcher.fetch(blank, o -> outcome[0] = o);

			assertNotNull("answered synchronously, without a request", outcome[0]);
			assertFalse(outcome[0].succeeded());
		}
		assertEquals(0, requests.get());
	}

	@Test
	public void theStatePushWithNoUrlMakesNoRequest()
	{
		AccountStateSender sender = new AccountStateSender(countingClient(), new Gson());

		boolean started = sender.send("", "token", Collections.singletonMap("k", "v"), o ->
		{
		}, a ->
		{
		});

		assertFalse(started);
		assertEquals(0, requests.get());
	}

	@Test
	public void theCountingClientWouldHaveSeenARealRequest()
	{
		// Guards the test itself: with an address, the same client is hit.
		QuestGraphFetcher fetcher = new QuestGraphFetcher(countingClient(), new Gson());
		QuestGraphFetcher.Outcome[] outcome = new QuestGraphFetcher.Outcome[1];
		fetcher.fetch("http://localhost:1", o -> outcome[0] = o);

		long deadline = System.currentTimeMillis() + 5000;
		while (outcome[0] == null && System.currentTimeMillis() < deadline)
		{
			Thread.onSpinWait();
		}
		assertNotNull(outcome[0]);
		assertTrue(requests.get() >= 1);
		assertNull(outcome[0].graph());
	}
}
