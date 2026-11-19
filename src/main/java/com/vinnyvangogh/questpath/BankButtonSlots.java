package com.vinnyvangogh.questpath;

import java.util.List;

/**
 * Picks the x for a bank title-bar button that does not sit on another
 * plugin's.
 *
 * <p>Several plugins hang square buttons on the bank's root layer at fixed
 * x positions on the same row (Quest Helper at 408, Bank Views at 371, Return
 * Sets from 305), and none of them can see the others in advance. Whoever draws
 * second wins the overlap, so a collision looks like a missing button. This
 * looks at what is already on the row and steps left until there is room.
 *
 * <p>Pure on purpose: the Widget scan lives in {@link BankButton}; the
 * arithmetic is testable without a client.
 */
final class BankButtonSlots
{
	/** Pixels left empty between neighbouring buttons. */
	static final int GAP = 3;

	private BankButtonSlots()
	{
	}

	/**
	 * @param occupied  {x, width} pairs already on the row
	 * @param preferred where this button would like to go
	 * @param size      this button's width
	 * @param minX      leftmost x it may use; below this it gives up
	 * @return the first free x at or left of {@code preferred}, stepping left;
	 *         {@code preferred} itself if nothing at or above {@code minX} is
	 *         free, since a button on top of another is no worse than a button
	 *         that is missing
	 */
	static int pick(List<int[]> occupied, int preferred, int size, int minX)
	{
		int x = preferred;
		while (x >= minX)
		{
			int blocker = blockerLeftEdge(occupied, x, size);
			if (blocker == Integer.MIN_VALUE)
			{
				return x;
			}
			// Jump past the thing in the way rather than crawl a pixel at a time.
			x = blocker - GAP - size;
		}
		return preferred;
	}

	/** Left edge of the leftmost-reaching overlapping neighbour, or MIN_VALUE. */
	private static int blockerLeftEdge(List<int[]> occupied, int x, int size)
	{
		int left = Integer.MIN_VALUE;
		boolean hit = false;
		for (int[] o : occupied)
		{
			boolean overlaps = x < o[0] + o[1] + GAP && o[0] < x + size + GAP;
			if (overlaps && (!hit || o[0] < left))
			{
				left = o[0];
				hit = true;
			}
		}
		return hit ? left : Integer.MIN_VALUE;
	}
}
