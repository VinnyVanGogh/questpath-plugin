package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;

/**
 * What the sidebar can say about the account-state push.
 *
 * The push is silent by design — no chat message, no overlay — which leaves
 * no way to tell "working" from "misconfigured" from "questpath is not
 * running". Every one of those looks identical from inside the game, and the
 * first two are things only the player can fix.
 *
 * So the states are kept distinct rather than collapsed into "on" and "off".
 * A 401 is a wrong token, and saying so is the difference between a two-second
 * fix and half an hour wondering.
 */
final class SyncStatus
{
	enum State
	{
		/** The toggle is off. Not a problem, and not something to warn about. */
		DISABLED,
		/** On, but no pairing token has been entered, so nothing can be sent. */
		NO_TOKEN,
		/** On and configured, but nothing has been sent yet this session. */
		WAITING,
		/** questpath accepted the last push. */
		ACCEPTED,
		/** questpath answered, and refused. The code says why. */
		REJECTED,
		/** The request never got an answer — questpath is probably not running. */
		UNREACHABLE
	}

	private final State state;
	private final Instant at;
	private final int code;

	private SyncStatus(State state, Instant at, int code)
	{
		this.state = state;
		this.at = at;
		this.code = code;
	}

	static SyncStatus of(State state)
	{
		return new SyncStatus(state, null, 0);
	}

	static SyncStatus accepted(Instant at)
	{
		return new SyncStatus(State.ACCEPTED, at, 0);
	}

	static SyncStatus rejected(Instant at, int code)
	{
		return new SyncStatus(State.REJECTED, at, code);
	}

	static SyncStatus unreachable(Instant at)
	{
		return new SyncStatus(State.UNREACHABLE, at, 0);
	}

	State state()
	{
		return state;
	}

	/** A short line naming what happened, and when it is worth saying. */
	String describe(Instant now)
	{
		switch (state)
		{
			case DISABLED:
				return "Off";
			case NO_TOKEN:
				return "No pairing token set";
			case WAITING:
				return "Nothing sent yet";
			case ACCEPTED:
				return "Sent " + ago(now);
			case REJECTED:
				return reason() + " · " + ago(now);
			case UNREACHABLE:
				return "Quest Route server not reachable · " + ago(now);
			default:
				return "";
		}
	}

	/**
	 * What a rejection actually means, for the two codes a misconfiguration
	 * produces. Anything else is reported as its number rather than guessed
	 * at.
	 */
	private String reason()
	{
		switch (code)
		{
			case 401:
				return "Pairing token rejected";
			case 400:
				return "Quest Route server refused the payload";
			default:
				return "Quest Route server answered " + code;
		}
	}

	private String ago(Instant now)
	{
		if (at == null)
		{
			return "";
		}
		long seconds = Duration.between(at, now).getSeconds();
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return (seconds / 60) + "m ago";
		}
		return (seconds / 3600) + "h ago";
	}

	/** Whether this state is something the player has to act on. */
	boolean needsAttention()
	{
		return state == State.NO_TOKEN || state == State.REJECTED;
	}
}
