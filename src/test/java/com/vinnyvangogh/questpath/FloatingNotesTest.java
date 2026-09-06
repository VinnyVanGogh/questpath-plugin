package com.vinnyvangogh.questpath;

import java.time.Instant;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Floating text that expires, stays capped, and does not drift with frame
 * rate.
 *
 * <p>Every failure mode worth guarding here is about time or volume rather
 * than drawing, which is exactly why the model is separate from the overlay:
 * a note that never expires or ten of them stacked over a player's head are
 * both bugs a rendering test would not catch.
 */
public class FloatingNotesTest
{
	private static final WorldPoint SOMEWHERE = new WorldPoint(3222, 3218, 0);
	private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");

	/** A note is on screen the moment it is added. */
	@Test
	public void aNoteShowsImmediately()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("Rope -- done!", SOMEWHERE, START);

		List<FloatingNotes.Note> live = notes.live(START);
		assertEquals(1, live.size());
		assertEquals("Rope -- done!", live.get(0).text());
	}

	/** And is gone once its lifetime is up. */
	@Test
	public void aNoteExpiresOnItsOwn()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("Rope -- done!", SOMEWHERE, START);

		assertTrue("still showing just before the end",
			!notes.live(START.plusMillis(FloatingNotes.LIFETIME.toMillis() - 1)).isEmpty());
		assertTrue("gone at the end",
			notes.live(START.plus(FloatingNotes.LIFETIME)).isEmpty());
	}

	/**
	 * A burst keeps the newest, not the oldest. A bank deposit can satisfy
	 * half a shopping list in one tick, and what just happened is the
	 * interesting part.
	 */
	@Test
	public void aBurstIsCappedKeepingTheNewest()
	{
		FloatingNotes notes = new FloatingNotes();
		for (int i = 1; i <= FloatingNotes.CAP + 2; i++)
		{
			notes.add("item " + i, SOMEWHERE, START);
		}

		List<FloatingNotes.Note> live = notes.live(START);
		assertEquals(FloatingNotes.CAP, live.size());
		assertEquals("oldest first, and the earliest two dropped",
			"item 3", live.get(0).text());
		assertEquals("item " + (FloatingNotes.CAP + 2), live.get(live.size() - 1).text());
	}

	/**
	 * Age is a fraction of elapsed time, so fade and drift are the same on
	 * any machine however fast it renders.
	 */
	@Test
	public void ageIsMeasuredInTimeNotFrames()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("Rope", SOMEWHERE, START);
		FloatingNotes.Note note = notes.live(START).get(0);

		assertEquals(0.0, note.age(START), 0.001);
		assertEquals(0.5, note.age(START.plusMillis(FloatingNotes.LIFETIME.toMillis() / 2)), 0.01);
		assertEquals("clamped, never past its end",
			1.0, note.age(START.plusSeconds(600)), 0.001);
	}

	/** Nothing blank, and nothing floating at the map origin. */
	@Test
	public void emptyTextOrMissingPlaceIsDropped()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("", SOMEWHERE, START);
		notes.add(null, SOMEWHERE, START);
		notes.add("Rope", null, START);

		assertTrue(notes.live(START).isEmpty());
	}

	/** Logout forgets everything. */
	@Test
	public void clearForgetsEverything()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("Rope", SOMEWHERE, START);
		notes.clear();

		assertTrue(notes.live(START).isEmpty());
	}

	/**
	 * The same item always reads the same way. A line that changes every
	 * time it is shown for the same item is noise, not voice.
	 */
	@Test
	public void praiseIsStablePerItem()
	{
		assertEquals(FloatingNotes.praiseFor("Rope"), FloatingNotes.praiseFor("Rope"));
		assertTrue("and names the item", FloatingNotes.praiseFor("Rope").contains("Rope"));
	}

	/**
	 * Every item name lands inside the phrase list, including the one whose
	 * hash is Integer.MIN_VALUE -- where Math.abs stays negative and would
	 * index out of the array.
	 */
	@Test
	public void praiseSurvivesEveryItemName()
	{
		// "polygenelubricants" is the standard example of a String whose
		// hashCode is exactly Integer.MIN_VALUE.
		assertEquals(Integer.MIN_VALUE, "polygenelubricants".hashCode());
		assertTrue(!FloatingNotes.praiseFor("polygenelubricants").isEmpty());
		assertEquals("", FloatingNotes.praiseFor(""));
		assertEquals("", FloatingNotes.praiseFor(null));
	}

	/**
	 * A note can name the stretch of itself worth a second colour, and it is
	 * always the item.
	 *
	 * <p>The overlay draws the line in up to three runs so the item's name
	 * stands out from the phrasing around it. What this pins is the model
	 * half: that the accent survives onto the note and is a real substring of
	 * the text, because the drawing falls back to one flat colour the moment
	 * it cannot find it -- which fails silently and looks exactly like the
	 * feature not existing.
	 */
	@Test
	public void aNoteCarriesTheItemNameAsItsAccent()
	{
		FloatingNotes notes = new FloatingNotes();
		String offer = FloatingNotes.offerFor("Big fishing net", 1);
		notes.add(offer, SOMEWHERE, START, FloatingNotes.OFFER_LIFETIME, "Big fishing net");

		FloatingNotes.Note note = notes.live(START).get(0);
		assertEquals("Big fishing net", note.accent());
		assertTrue("the accent has to be findable inside the text it colours",
			note.text().contains(note.accent()));
	}

	/** A note with no accent named draws flat, and says so rather than guessing. */
	@Test
	public void aNoteWithoutAnAccentIsDrawnFlat()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("Rope -- done!", SOMEWHERE, START);

		assertEquals("", notes.live(START).get(0).accent());
	}

	/**
	 * An offer outlives a completion, because they ask for different things.
	 *
	 * <p>A completion confirms something already done and can fade. An offer
	 * is asking you to change where you are walking, and four seconds is not
	 * enough to read a name, find the NPC and decide.
	 */
	@Test
	public void anOfferStaysUpLongerThanAConfirmation()
	{
		FloatingNotes notes = new FloatingNotes();
		notes.add("done", SOMEWHERE, START);
		notes.add("offer", SOMEWHERE, START, FloatingNotes.OFFER_LIFETIME, "");

		Instant afterTheShortOne = START.plus(FloatingNotes.LIFETIME);
		assertEquals("the confirmation has gone and the offer has not",
			1, notes.live(afterTheShortOne).size());
		assertEquals("offer", notes.live(afterTheShortOne).get(0).text());
		assertTrue(notes.live(START.plus(FloatingNotes.OFFER_LIFETIME)).isEmpty());
	}
}
