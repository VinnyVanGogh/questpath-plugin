package com.vinnyvangogh.questpath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Short-lived lines of text that float in the world, for the moments a chat
 * line is too quiet to land.
 *
 * <p>The plan this serves, in the project owner's words: "make it feel like
 * a quest or a game. Every time you pick up an item or get to a shop, the
 * shopkeeper could congratulate you." The chat box already says these things
 * and is exactly where nobody is looking at the moment they happen -- the
 * eleventh log goes into your bags while you are watching a tree, and the
 * line scrolls past behind a wall of game spam.
 *
 * <p><b>Pure and timed, so the awkward parts are testable.</b> No {@code
 * Graphics2D} and no client: this holds what should currently be on screen
 * and answers what is still alive at a given instant, the same split {@link
 * ShopRouteStops} has from its overlay. What makes that worth doing is that
 * the failure modes here are all about time and volume rather than drawing
 * -- notes that never expire, or ten of them at once when a bank deposit
 * satisfies half a shopping list in one tick.
 *
 * <p><b>Capped, newest kept.</b> {@link #CAP} notes show at most. Past that
 * the oldest are dropped rather than the newest, because the reason to cap
 * is a burst -- and in a burst the interesting thing is what just happened,
 * not what happened three items ago.
 */
class FloatingNotes
{
	/** How long a note stays on screen unless the caller says otherwise. */
	static final Duration LIFETIME = Duration.ofSeconds(4);

	/**
	 * How long a shopkeeper's offer stays up.
	 *
	 * <p>Much longer than {@link #LIFETIME}, because the two notes ask for
	 * different things. A completion confirms something already done and can
	 * fade; an offer is asking you to change where you are walking, and four
	 * seconds is not enough to read a name, find the NPC it belongs to and
	 * decide. Ten was the number asked for.
	 */
	static final Duration OFFER_LIFETIME = Duration.ofSeconds(10);

	/**
	 * The most notes shown at once. Three fits above a player without
	 * becoming a wall, and a burst bigger than that is better summarised by
	 * the panel than stacked over someone's head.
	 */
	static final int CAP = 3;

	/**
	 * The phrasings a completion can take, chosen by the item's own name so
	 * the same item always reads the same way.
	 *
	 * <p>Deliberately not random. A line that changes every time it is shown
	 * for the same item reads as noise rather than voice, and it cannot be
	 * tested without reaching for a seed.
	 */
	private static final String[] PRAISE = {
		"Got them all: %s",
		"%s -- done!",
		"That's every %s",
		"%s sorted.",
	};

	/**
	 * What a shopkeeper shouts as you come up the road.
	 *
	 * <p>Written to be a wave rather than a receipt. The first attempt said
	 * "I sell Big fishing net -- you still need 1", which is accurate and
	 * reads like a till printout; the point of putting words over an NPC's
	 * head is that somebody looks up. The item name stays exact -- it is the
	 * one part that has to survive being cute.
	 *
	 * <p>Chosen by the item's own name, like {@link #PRAISE}, so the same
	 * shop greets you the same way twice.
	 */
	private static final String[] OFFERS = {
		"Psst -- got your %s right here!",
		"Oi! %s, over here!",
		"Looking for a %s? Say no more.",
		"%s! Fresh in! Come see!",
		"You there -- I stock %s!",
	};

	private final Deque<Note> notes = new ArrayDeque<>();

	/** One line of floating text, and where and when it belongs. */
	static final class Note
	{
		private final String text;
		private final WorldPoint at;
		private final Instant shownAt;
		private final Duration lifetime;
		private final String accent;

		Note(String text, WorldPoint at, Instant shownAt)
		{
			this(text, at, shownAt, LIFETIME, "");
		}

		Note(String text, WorldPoint at, Instant shownAt, Duration lifetime, String accent)
		{
			this.text = text;
			this.at = at;
			this.shownAt = shownAt;
			this.lifetime = lifetime == null ? LIFETIME : lifetime;
			this.accent = accent == null ? "" : accent;
		}

		/**
		 * The stretch of {@link #text} worth picking out in a second colour,
		 * or empty for a line drawn flat.
		 *
		 * <p>Always the item's name. A shout over a shopkeeper is read at a
		 * glance from across a room, and the only word in it that has to
		 * survive that glance is what they are selling -- the rest is
		 * phrasing. One flat colour makes the phrasing as loud as the answer.
		 */
		String accent()
		{
			return accent;
		}

		/** What it says. */
		String text()
		{
			return text;
		}

		/** Where in the world it floats. */
		WorldPoint at()
		{
			return at;
		}

		/**
		 * How far through its life this note is at {@code now}, from 0.0 at
		 * the moment it appeared to 1.0 when it should be gone. Drives the
		 * fade and the drift upward, so both are a function of time rather
		 * than of frames -- a note must not fade faster on a machine that
		 * renders faster.
		 */
		double age(Instant now)
		{
			double elapsed = Duration.between(shownAt, now).toMillis();
			double span = lifetime.toMillis();
			double fraction = elapsed / span;
			return fraction < 0.0 ? 0.0 : Math.min(fraction, 1.0);
		}
	}

	/**
	 * The line to float when an item's last unit finally lands.
	 *
	 * @param itemName the item, spelled as the shopping list spells it
	 */
	static String praiseFor(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return "";
		}
		// Math.abs of hashCode is not safe on Integer.MIN_VALUE, which stays
		// negative and would index out of the array. Masking the sign bit
		// off cannot.
		int index = (itemName.hashCode() & 0x7fffffff) % PRAISE.length;
		return String.format(PRAISE[index], itemName);
	}

	/**
	 * The line a shopkeeper says when you walk in and they stock something
	 * your plan still needs.
	 *
	 * <p>States the count rather than only the item, because "I sell rope" is
	 * something the shelf already says, and "you still need 2" is the part
	 * that is actually this plugin's to know.
	 *
	 * @param outstanding how many more are wanted; at or below zero yields
	 *     nothing, since there is no offer to make about an item in hand
	 */
	static String offerFor(String itemName, long outstanding)
	{
		if (itemName == null || itemName.isEmpty() || outstanding <= 0)
		{
			return "";
		}
		String phrasing = OFFERS[(itemName.hashCode() & 0x7fffffff) % OFFERS.length];
		String line = String.format(phrasing, itemName);
		return outstanding > 1 ? line + " (x" + outstanding + ")" : line;
	}

	/**
	 * Floats a line at a world point. An empty line or a missing point is
	 * dropped rather than shown blank or at the origin.
	 */
	void add(String text, WorldPoint at, Instant now)
	{
		add(text, at, now, LIFETIME);
	}

	/** The same, for a note that should outlive the default. */
	void add(String text, WorldPoint at, Instant now, Duration lifetime)
	{
		add(text, at, now, lifetime, "");
	}

	/** The same, naming the stretch of text to pick out in a second colour. */
	void add(String text, WorldPoint at, Instant now, Duration lifetime, String accent)
	{
		if (text == null || text.isEmpty() || at == null || now == null)
		{
			return;
		}
		notes.addLast(new Note(text, at, now, lifetime, accent));
		while (notes.size() > CAP)
		{
			notes.removeFirst();
		}
	}

	/**
	 * What should be on screen at {@code now}, oldest first so a stack keeps
	 * its order as it drains from the top.
	 *
	 * <p>Expiry happens here rather than on a timer: nothing ticks this
	 * class, and a note whose life ran out while the plugin was doing
	 * nothing should simply not be returned.
	 */
	List<Note> live(Instant now)
	{
		notes.removeIf(note -> note.age(now) >= 1.0);
		return new ArrayList<>(notes);
	}

	/** Forgets everything -- on logout, and when the feature is switched off. */
	void clear()
	{
		notes.clear();
	}
}
