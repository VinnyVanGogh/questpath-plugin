package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches the current quest graph from a self-hosted questpath instance.
 *
 * <p>The plugin ships a copy of {@link QuestGraph} baked in at release
 * (bundled as a jar resource, built by questpath's {@code
 * -export-quest-graph}), and it goes stale the moment questpath's data
 * changes underneath it -- a corrected sub-area gate, a shop's reachability,
 * a shop-source cap all reached the web app immediately and this plugin only
 * at its next build. This class is the other half: an attempt, on plugin
 * start, to ask the configured questpath instance for whatever it currently
 * computes, so a fix lands the next time the game is opened rather than the
 * next time this plugin is released.
 *
 * <p>Never blocks and never throws outward. Every way this can fail --
 * unreachable server, a non-2xx response, a body that will not parse, a
 * version this build does not understand -- funnels into one {@link Outcome}
 * so {@link QuestpathPlugin} has exactly one place to decide what "stay on
 * the bundle" means to the player, and never a place where a failure is
 * simply swallowed. See that class's own {@code startUp} for why silence
 * here is the one thing this feature exists to rule out: the player twice
 * reported a bug that was already fixed server-side, purely because their
 * bundle was older than they knew.
 */
@Slf4j
@Singleton
class QuestGraphFetcher
{
	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	QuestGraphFetcher(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * The result of one fetch attempt: a graph this build can actually use,
	 * or the reason it could not get one. Never both, and {@link #reason()}
	 * is always human-readable -- it is what the player sees when this
	 * fails, not just what a log records.
	 */
	static final class Outcome
	{
		private final QuestGraph graph;
		private final String reason;

		private Outcome(QuestGraph graph, String reason)
		{
			this.graph = graph;
			this.reason = reason;
		}

		static Outcome fetched(QuestGraph graph)
		{
			return new Outcome(graph, null);
		}

		static Outcome failed(String reason)
		{
			return new Outcome(null, reason);
		}

		boolean succeeded()
		{
			return graph != null;
		}

		/** The usable graph. Meaningless unless {@link #succeeded()}. */
		QuestGraph graph()
		{
			return graph;
		}

		/**
		 * Why a graph could not be used. Meaningless when {@link
		 * #succeeded()} -- always set otherwise, covering every failure this
		 * class can name: the address is not configured, the server did not
		 * answer, it answered with something other than 2xx, or what it sent
		 * back did not parse or carried a version this build refuses.
		 */
		String reason()
		{
			return reason;
		}
	}

	/**
	 * Starts an async {@code GET <serverUrl>/api/quest-graph} and calls
	 * {@code onOutcome} exactly once, on an OkHttp callback thread rather
	 * than the caller's.
	 *
	 * @param serverUrl the configured questpath address, e.g. {@code
	 *     http://localhost:8080}; {@code null} or unparsed answers a failed
	 *     Outcome rather than throwing
	 */
	void fetch(String serverUrl, Consumer<Outcome> onOutcome)
	{
		HttpUrl url = endpoint(serverUrl);
		if (url == null)
		{
			onOutcome.accept(Outcome.failed("Quest Route address \"" + serverUrl + "\" is not a usable URL"));
			return;
		}

		Request request = new Request.Builder().url(url).get().build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("quest graph: fetch failed", e);
				onOutcome.accept(Outcome.failed("Quest Route server unreachable at " + serverUrl));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closed = response)
				{
					if (!closed.isSuccessful())
					{
						log.debug("quest graph: fetch rejected with {}", closed.code());
						onOutcome.accept(Outcome.failed(
							"Quest Route server answered " + closed.code() + " for the quest graph"));
						return;
					}
					ResponseBody body = closed.body();
					if (body == null)
					{
						onOutcome.accept(Outcome.failed("Quest Route server sent no body for the quest graph"));
						return;
					}
					QuestGraph graph = QuestGraph.parse(
						gson, new InputStreamReader(body.byteStream(), Charset.forName("UTF-8")));
					onOutcome.accept(Outcome.fetched(graph));
				}
				catch (IOException e)
				{
					// Covers both "the body was not valid JSON" and
					// QuestGraph.parse's own refusals (wrong version, no
					// quests) -- e.getMessage() is written to be read by a
					// player, not just a log, for exactly this path.
					log.debug("quest graph: fetched body not usable", e);
					onOutcome.accept(Outcome.failed(e.getMessage()));
				}
			}
		});
	}

	/**
	 * Builds the fetch URL from the configured address, or null when it does
	 * not parse -- mirrors {@link AccountStateSender#endpoint(String)}
	 * exactly, appending this endpoint's own path instead.
	 */
	static HttpUrl endpoint(String serverUrl)
	{
		if (serverUrl == null || serverUrl.trim().isEmpty())
		{
			return null;
		}
		HttpUrl base = HttpUrl.parse(serverUrl.trim());
		if (base == null)
		{
			return null;
		}
		return base.newBuilder()
			.addPathSegments("api/quest-graph")
			.build();
	}
}
