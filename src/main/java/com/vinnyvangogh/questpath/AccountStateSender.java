package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Posts account state to a self-hosted questpath instance.
 *
 * <p>Never blocks and never touches the client thread: the payload is built by
 * the caller (which must be on the client thread, because reading quest
 * varbits requires it), and the request goes out on OkHttp's own pool via
 * {@code enqueue}.
 *
 * <p>One request in flight at a time. A burst of level-ups would otherwise
 * start a request each, all carrying nearly the same document, and the server
 * would apply them in whatever order they landed.
 */
@Slf4j
@Singleton
class AccountStateSender
{
	private static final MediaType JSON = MediaType.get("application/json");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final AtomicBoolean inFlight = new AtomicBoolean();

	@Inject
	AccountStateSender(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Sends one payload, or does nothing if a send is already running or the
	 * configuration is incomplete.
	 *
	 * @return whether a request was actually started
	 */
	boolean send(String serverUrl, String token, Map<String, Object> payload,
		Consumer<SyncStatus> onOutcome, Consumer<PushResponse> onAccepted)
	{
		HttpUrl url = endpoint(serverUrl);
		if (url == null || token == null || token.isEmpty())
		{
			// An address that does not parse or a missing token is a
			// configuration the user has not finished, not an error to shout
			// about on every event.
			return false;
		}
		if (!inFlight.compareAndSet(false, true))
		{
			return false;
		}

		Request request = new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight.set(false);
				onOutcome.accept(SyncStatus.unreachable(Instant.now()));
				// Debug, not error: questpath not running is the normal state
				// for anyone who has the toggle on and the app closed, and
				// this fires on every push.
				log.debug("questpath sync: not sent", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closed = response)
				{
					if (closed.isSuccessful())
					{
						onOutcome.accept(SyncStatus.accepted(Instant.now()));
						readBody(closed, onAccepted);
						log.debug("questpath sync: accepted");
					}
					else
					{
						// A 401 here means the token is wrong, which the user
						// can only fix if they are told -- and a debug log is
						// not where they will look, which is why the outcome
						// goes to the panel as well.
						onOutcome.accept(SyncStatus.rejected(Instant.now(), closed.code()));
						log.debug("questpath sync: rejected with {}", closed.code());
					}
				}
				finally
				{
					inFlight.set(false);
				}
			}
		});
		return true;
	}

	/**
	 * Parses what questpath answered, so the panel can show the account's
	 * build rules.
	 *
	 * A body that will not parse is not a failed push: the state was accepted,
	 * which is what the request was for. The panel simply keeps whatever rules
	 * it already had rather than blanking them on a response questpath may
	 * have changed the shape of.
	 */
	private void readBody(Response response, Consumer<PushResponse> onAccepted)
	{
		try
		{
			ResponseBody body = response.body();
			if (body == null)
			{
				return;
			}
			PushResponse parsed = gson.fromJson(body.charStream(), PushResponse.class);
			if (parsed != null)
			{
				onAccepted.accept(parsed);
			}
		}
		catch (Exception e)
		{
			log.debug("questpath sync: response not understood", e);
		}
	}

	/**
	 * Builds the push URL from the configured address, or null when it does
	 * not parse.
	 *
	 * The path is appended here rather than asked of the user: the endpoint is
	 * questpath's, not theirs, and a config field holding a full URL is a
	 * field someone will paste the wrong half of.
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
			.addPathSegments("api/plugin/state")
			.build();
	}
}
