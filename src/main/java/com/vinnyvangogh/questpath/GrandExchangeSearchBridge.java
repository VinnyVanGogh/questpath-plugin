package com.vinnyvangogh.questpath;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts one item's name into the Grand Exchange's own search box and runs the
 * native search, for accounts that can use one at all -- the ironman gate
 * itself lives in {@link GrandExchangeAccounts} and {@link
 * QuestpathPlugin#grandExchangeAvailable()}, not here.
 *
 * <p><b>Unlike {@link ShortestPathBridge} and {@link QuestHelperBridge}, this
 * class talks to no other plugin.</b> The Grand Exchange search box is native
 * client behaviour -- a clientscript (cs2) baked into the game itself -- not
 * something RuneLite's own bundled {@code GrandExchangePlugin} drives. So
 * there is no "installed"/"enabled" state to check and no reflection: the
 * three members this depends on are all directly on {@code
 * net.runelite.api.Client} and its own constant classes, and all three were
 * confirmed public, byte for byte, with {@code javap -p -constants} against
 * the actually-installed {@code runelite-api-1.12.37.jar}:
 *
 * <pre>
 * public static final int net.runelite.api.ScriptID.GE_ITEM_SEARCH = 752;
 * public static final int net.runelite.api.VarClientStr.INPUT_TEXT = 359;
 * public abstract void net.runelite.api.Client.setVarcStrValue(int, String);
 * public abstract void net.runelite.api.Client.runScript(Object...);
 * </pre>
 *
 * <p><b>RuneLite's own bundled Grand Exchange plugin does not call {@code
 * runScript(GE_ITEM_SEARCH)} anywhere -- checked, not assumed.</b> {@code
 * javap -c -p} against every class in {@code
 * net.runelite.client.plugins.grandexchange} inside the installed {@code
 * client-1.12.37.jar} finds no {@code runScript} or {@code setVarcStrValue}
 * call at all in that package. That plugin only *reacts* to a search the
 * game's own native code already ran: {@code
 * GrandExchangePlugin.onScriptPostFired} matches {@code
 * event.getScriptId() == 752} exactly, then calls {@code
 * client.getVarcStrValue(359)} to read the search text back out and
 * re-highlight fuzzy matches in the results widget (packed id {@code
 * 10616884} -- which is {@code InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS},
 * group 162, child 52. An earlier version of this paragraph called that
 * constant {@code InterfaceID.Chatbox.GE_SEARCH_RESULTS}, which does not
 * exist under that name: the id was right and the name was invented. Reading
 * the whole {@code javap -constants} listing of {@code InterfaceID.Chatbox}
 * settled it, and the {@code MES_LAYER_} prefix is what showed that the
 * client's own generated rows are children of the scroll area rather than of
 * the box itself). That is the
 * reverse of what this class does, and it is strong independent
 * confirmation of both constants -- the native client only ever fires
 * script 752 after, and reads its own search box out of, varcstr 359,
 * exactly the pair this class writes to before running that same script
 * itself. There is, in short, no forward-direction call site in this
 * codebase's own Grand Exchange integration to copy; {@link #action} was
 * built directly from the four verified members above and cross-checked
 * against that reactive half rather than a matching send call.
 *
 * <p><b>That absence of a forward call site is exactly what made the first
 * version of {@link #action} broken.</b> It called {@code
 * client.runScript(ScriptID.GE_ITEM_SEARCH)} with no further arguments, and
 * every real click threw: {@code "Script 752 was called with the incorrect
 * number of arguments; takes 3+0,0, got 0+0+0"}. The four members above are
 * still exactly right -- the constant, the varcstr, and both methods all
 * exist and are public -- but a script id alone was never the whole call,
 * and nothing on this project's classpath calls {@code GE_ITEM_SEARCH}
 * forward to show what the other three arguments should be. Production
 * wiring no longer guesses them: see {@link
 * GrandExchangeButton#updateSearchInterface}'s own doc for the fix actually
 * shipped, and {@code QuestpathPlugin#startUp}'s {@code geSearchBridge}
 * construction for where it replaced the broken call. This class itself did
 * not change -- the bug was entirely in the {@link #action} lambda supplied
 * from outside it, not in the open-check/action/client-thread seam this
 * class provides.
 *
 * <p><b>Both calls must run on the client thread.</b> See {@link
 * ShortestPathBridge}'s class doc for the exact shape of the failure this
 * project has already hit twice: an assertion that the caller is on the
 * client thread, thrown as {@code AssertionError} -- an {@code Error}, not
 * an {@code Exception} -- tearing straight through whatever was rendering
 * rather than being caught by an ordinary handler. {@link #search} never
 * calls {@link #open} or {@link #action} itself; both are only ever invoked
 * from inside the {@link Runnable} handed to {@link #onClientThread}, e.g.
 * {@code clientThread::invoke} in production -- the same seam {@link
 * ShortestPathBridge#clientThreadEventPoster} takes, for the identical
 * reason.
 */
@Slf4j
class GrandExchangeSearchBridge
{
	/** What became of a {@link #search} call. */
	enum Outcome
	{
		/**
		 * The Grand Exchange interface was not open at the moment the check
		 * landed on the client thread. Never silent: {@link #search}'s
		 * caller must say so to the player -- this project's rule is that a
		 * click doing nothing reads as "nothing needed", and it has bitten
		 * repeatedly.
		 */
		NOT_OPEN,
		/**
		 * {@link #open} or {@link #action} threw while running on the
		 * client thread. Logged at warn already.
		 */
		CALL_FAILED,
		/**
		 * {@code setVarcStrValue} and {@code runScript} were both called
		 * without throwing. Not proof the search box visibly updated --
		 * {@code runScript} returns nothing on success, the same blind spot
		 * {@link ShortestPathBridge.RouteOutcome#SENT} documents for {@code
		 * EventBus#post} -- but it is the strongest claim honestly
		 * available from outside the game's own script.
		 */
		SENT,
	}

	/**
	 * True when the Grand Exchange's own interface is on screen <b>and</b>
	 * its item-search chatbox is the thing currently showing inside it --
	 * only ever called from inside {@link #onClientThread}'s runnable.
	 * Production wiring checks both {@code
	 * client.getWidget(InterfaceID.GeOffers.UNIVERSE) != null &&
	 * !widget.isHidden()} (the offer screen itself, packed id 30474240;
	 * group 465, child 0, the same id as the deprecated {@code
	 * WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER}) and {@code
	 * client.getWidget(InterfaceID.Chatbox.MES_LAYER) != null &&
	 * !widget.isHidden()} (the search chatbox {@link #action} actually feeds
	 * text into -- see {@link GrandExchangeButton}'s class doc for how
	 * that packed id was confirmed). The offer screen can be open while
	 * showing its quantity/price step instead of the search chatbox, and
	 * {@link #action} has nothing to act on in that state; checking only the
	 * offer screen would report {@link Outcome#SENT} for a click that
	 * changed nothing.
	 */
	private final BooleanSupplier open;

	/**
	 * Sets the search text and asks the search box to react to it. Only ever
	 * called from inside {@link #onClientThread}'s runnable. Production
	 * wiring sets {@code VarClientStr.INPUT_TEXT} and then replays the
	 * search input widget's own {@code getOnKeyListener()} through {@code
	 * client.runScript} -- see {@link GrandExchangeButton#updateSearchInterface}'s
	 * own doc for exactly why, and for the {@code AssertionError} this used
	 * to throw when it called {@code
	 * client.runScript(ScriptID.GE_ITEM_SEARCH)} directly instead. Given as
	 * one seam rather than two separate ones because the two calls are never
	 * meaningful apart -- there is no scenario where this class wants to
	 * set the text without also asking the box to react to it, or vice versa.
	 */
	private final Consumer<String> action;

	/**
	 * Runs a task on the client thread, deferring it if the caller is not
	 * already on it -- production wiring passes {@code clientThread::invoke}.
	 */
	private final Consumer<Runnable> onClientThread;

	/**
	 * An item name as it must be written into {@code
	 * VarClientStr.INPUT_TEXT}: trimmed, and lower case.
	 *
	 * <p><b>The capital letter was a real bug, not a cosmetic one.</b>
	 * Writing "Air talisman" produced a results list with red underlines
	 * under the matched prefixes, which reads -- correctly -- as the game
	 * saying the spelling is wrong. It was RuneLite's own bundled Grand
	 * Exchange plugin reporting that it had fallen back to a fuzzy search,
	 * and the reason it fell back was this method's absence.
	 *
	 * <p>Traced with {@code javap -c -p} against the installed {@code
	 * client-1.12.38.jar} rather than guessed. Under the default {@code
	 * FUZZY_FALLBACK} search mode, {@code
	 * GrandExchangePlugin.onGrandExchangeSearched} consumes the game's own
	 * search and runs its own filter first -- {@code
	 * lambda$onGrandExchangeSearched$4}, which is:
	 *
	 * <pre>
	 * item.isGeTradeable() &amp;&amp; item.getNote() == -1
	 *     &amp;&amp; item.getName().toLowerCase().contains(search)
	 * </pre>
	 *
	 * <p>The item's name is lower-cased. {@code search}, read straight out
	 * of {@code client.getVarcStrValue(359)}, is not. So a capital letter in
	 * the search text can never match, that filter returns zero results, and
	 * the {@code if (count != 0)} guard falls through to the fuzzy scorer,
	 * which sets {@code wasFuzzySearch = true}. {@code
	 * onScriptPostFired(752)} then runs {@code highlightSearchMatches()},
	 * which wraps each result's matching prefix in {@code
	 * "<col=FUZZY_HIGHLIGHT_COLOR><u>"} -- those underlines, exactly.
	 *
	 * <p>A person typing into that box types lower case, which is why this
	 * is not hit by hand. Only a plugin writing a display name straight in
	 * hits it, and questpath's names are the wiki's: title case, always.
	 *
	 * <p><b>Default locale, deliberately, rather than {@code Locale.ROOT}.</b>
	 * What this produces is compared against {@code getName().toLowerCase()}
	 * in the code quoted above, which uses the default locale. Matching that
	 * exactly is the whole job, and lower-casing by a different rule than
	 * the other side would reintroduce the same class of miss on precisely
	 * the locales -- Turkish, where {@code I} lower-cases to {@code ı} --
	 * that {@code Locale.ROOT} is normally reached for to avoid.
	 */
	static String searchText(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase();
	}

	GrandExchangeSearchBridge(BooleanSupplier open, Consumer<String> action, Consumer<Runnable> onClientThread)
	{
		this.open = open;
		this.action = action;
		this.onClientThread = onClientThread;
	}

	/**
	 * Puts itemName into the Grand Exchange search box and runs it, or
	 * reports why not.
	 *
	 * @param onOutcome called exactly once, from whatever thread {@link
	 *     #onClientThread} actually runs the check-and-search on -- almost
	 *     always the client thread, and almost never the caller's own
	 *     thread when that caller is Swing. A Swing caller must marshal
	 *     back to the EDT itself, the same requirement {@link
	 *     QuestHelperBridge#start} documents for its own deferred read-back.
	 */
	void search(String itemName, Consumer<Outcome> onOutcome)
	{
		onClientThread.accept(() -> onOutcome.accept(searchNow(itemName)));
	}

	/** Runs only on the client thread -- see this class's own doc. */
	private Outcome searchNow(String itemName)
	{
		try
		{
			if (!open.getAsBoolean())
			{
				log.info("Not searching the Grand Exchange for \"{}\" -- its interface is not open", itemName);
				return Outcome.NOT_OPEN;
			}
			action.accept(itemName);
			log.info("Searched the Grand Exchange for \"{}\"", itemName);
			return Outcome.SENT;
		}
		// RuntimeException | AssertionError, not a bare RuntimeException:
		// setVarcStrValue and runScript both assert they are on the client
		// thread, and that assertion is an Error, not an Exception -- see
		// this class's own doc and ShortestPathBridge's for exactly why a
		// narrower catch would let it straight through.
		catch (RuntimeException | AssertionError e)
		{
			log.warn("Searching the Grand Exchange for \"{}\" failed", itemName, e);
			return Outcome.CALL_FAILED;
		}
	}
}
