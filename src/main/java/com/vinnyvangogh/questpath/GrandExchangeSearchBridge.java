package com.vinnyvangogh.questpath;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Shows one item in the Grand Exchange's own search results, for accounts
 * that can use one at all -- the ironman gate itself lives in {@link
 * GrandExchangeAccounts} and {@link QuestpathPlugin#grandExchangeAvailable()},
 * not here.
 *
 * <p><b>By id, through the sentinel path, never by typing the item's
 * name.</b> An earlier version wrote the item's real name into {@code
 * VarClientStr.INPUT_TEXT} and replayed the search box's key listener, so the
 * game ran a native search for that term. That is the Plugin Hub's "no
 * autotyping -- plugins must not programmatically insert text into the
 * chatbox input" rule: varcstr 359 is the chatbox message-layer input, and
 * every base-client write to it is a delete or a preserve, never new content.
 * The compliant shape is the one the buy-list button already uses, with Quest
 * Helper's {@code QuestBankTab} as precedent: write the plugin's own sentinel
 * ({@link GrandExchangeButton#SEARCH_SENTINEL}, never a resolvable search
 * term), consume {@code GrandExchangeSearched} on that sentinel, and hand the
 * client the result ids directly. {@link #action} is that path with exactly
 * one id -- production wiring passes {@link GrandExchangeButton#showOne}.
 *
 * <p><b>This class talks to no other plugin.</b> The Grand Exchange search
 * box is native client behaviour, so there is no "installed"/"enabled" state
 * to check and nothing to reflect on.
 *
 * <p><b>Both calls must run on the client thread.</b> The widget reads and
 * varc writes behind {@link #open} and {@link #action} assert they are on the
 * client thread, and that assertion is an {@code AssertionError} -- an {@code
 * Error}, not an {@code Exception}. {@link #search} never calls either itself;
 * both are only ever invoked from inside the {@link Runnable} handed to
 * {@link #onClientThread}, e.g. {@code clientThread::invoke} in production --
 * the same seam {@link ShortestPathBridge#clientThreadEventPoster} takes.
 */
@Slf4j
class GrandExchangeSearchBridge
{
	/** What became of a {@link #search} call. */
	enum Outcome
	{
		/**
		 * The Grand Exchange item search was not open at the moment the
		 * check landed on the client thread. Never silent: {@link #search}'s
		 * caller must say so to the player -- a click doing nothing reads as
		 * "nothing needed".
		 */
		NOT_OPEN,
		/**
		 * {@link #open} or {@link #action} threw while running on the
		 * client thread. Logged at warn already.
		 */
		CALL_FAILED,
		/**
		 * The sentinel search was started without throwing. Not proof the
		 * result list visibly updated -- the client's own script returns
		 * nothing on success -- but the strongest claim honestly available.
		 */
		SENT,
	}

	/**
	 * True when the Grand Exchange's own interface is on screen <b>and</b>
	 * its item-search chatbox is the thing currently showing inside it --
	 * only ever called from inside {@link #onClientThread}'s runnable. The
	 * offer screen can be open while showing its quantity/price step instead
	 * of the search chatbox, and {@link #action} has nothing to act on in
	 * that state; checking only the offer screen would report {@link
	 * Outcome#SENT} for a click that changed nothing.
	 */
	private final BooleanSupplier open;

	/**
	 * Starts the sentinel search answered with this one item id. Only ever
	 * called from inside {@link #onClientThread}'s runnable.
	 */
	private final IntConsumer action;

	/**
	 * Runs a task on the client thread, deferring it if the caller is not
	 * already on it -- production wiring passes {@code clientThread::invoke}.
	 */
	private final Consumer<Runnable> onClientThread;

	GrandExchangeSearchBridge(BooleanSupplier open, IntConsumer action, Consumer<Runnable> onClientThread)
	{
		this.open = open;
		this.action = action;
		this.onClientThread = onClientThread;
	}

	/**
	 * Shows itemId in the Grand Exchange search results, or reports why not.
	 *
	 * @param onOutcome called exactly once, from whatever thread {@link
	 *     #onClientThread} actually runs the check-and-search on -- almost
	 *     always the client thread, and almost never the caller's own
	 *     thread when that caller is Swing. A Swing caller must marshal
	 *     back to the EDT itself.
	 */
	void search(int itemId, Consumer<Outcome> onOutcome)
	{
		onClientThread.accept(() -> onOutcome.accept(searchNow(itemId)));
	}

	/** Runs only on the client thread -- see this class's own doc. */
	private Outcome searchNow(int itemId)
	{
		try
		{
			if (!open.getAsBoolean())
			{
				log.debug("Not showing item {} in the Grand Exchange -- its search is not open", itemId);
				return Outcome.NOT_OPEN;
			}
			action.accept(itemId);
			log.debug("Showed item {} in the Grand Exchange search", itemId);
			return Outcome.SENT;
		}
		// RuntimeException | AssertionError, not a bare RuntimeException:
		// the client-thread assertion is an Error -- see this class's doc.
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Showing item {} in the Grand Exchange failed", itemId, e);
			return Outcome.CALL_FAILED;
		}
	}
}
