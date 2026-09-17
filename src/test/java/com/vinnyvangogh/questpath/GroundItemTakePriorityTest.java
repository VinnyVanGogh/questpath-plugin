package com.vinnyvangogh.questpath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * The pure ordering behind left-clickable "Take" for wanted ground items.
 *
 * <p>Every case here is expressed as index permutations, exactly what
 * {@code QuestpathPlugin#onPostMenuSort} needs to rebuild the live
 * {@code MenuEntry[]} -- see {@link GroundItemTakePriority} for why nothing
 * here touches a RuneLite type.
 */
public class GroundItemTakePriorityTest
{
	/** No wanted item on the menu: nothing to rebuild, so no permutation at all. */
	@Test
	public void noCandidatesMeansNoChange()
	{
		assertNull(GroundItemTakePriority.reorder(3, Collections.emptyList()));
	}

	/**
	 * A single wanted item's Take, buried under "Walk here" and an
	 * unrelated object's entry, ends up last -- the left-click default --
	 * with the untouched entries kept in their original relative order.
	 */
	@Test
	public void singleCandidateMovesToTheEnd()
	{
		// index 0 = Walk here, index 1 = Take (wanted, short by 3), index 2 = some object's Examine
		List<GroundItemTakePriority.Candidate> candidates = Collections.singletonList(
			new GroundItemTakePriority.Candidate(1, 1511, 3));

		int[] order = GroundItemTakePriority.reorder(3, candidates);

		assertArrayEquals(new int[] {0, 2, 1}, order);
	}

	/**
	 * Two wanted items on the same tile: the one with the larger outstanding
	 * count becomes the left-click default, the other sits directly below
	 * it, and everything not on the list keeps its place beneath both.
	 */
	@Test
	public void mostShortFirstAmongSeveralCandidatesOnOneTile()
	{
		// index 0 = Walk here
		// index 1 = Take logs (wanted, short by 1)
		// index 2 = Take bones (wanted, short by 5)
		// index 3 = Examine logs
		List<GroundItemTakePriority.Candidate> candidates = Arrays.asList(
			new GroundItemTakePriority.Candidate(1, 1511, 1),
			new GroundItemTakePriority.Candidate(2, 526, 5));

		int[] order = GroundItemTakePriority.reorder(4, candidates);

		// Untouched entries (0, 3) keep their relative order first, then the
		// less-short candidate (logs, index 1), then bones (index 2) last.
		assertArrayEquals(new int[] {0, 3, 1, 2}, order);
	}

	/** A tie on outstanding count breaks on the lower item id, deterministically. */
	@Test
	public void tiedShortfallBreaksOnLowerItemId()
	{
		List<GroundItemTakePriority.Candidate> candidates = Arrays.asList(
			new GroundItemTakePriority.Candidate(0, 2000, 2),
			new GroundItemTakePriority.Candidate(1, 1000, 2));

		int[] order = GroundItemTakePriority.reorder(2, candidates);

		// Lower id (1000, index 1) is the tie-break winner and lands last.
		assertArrayEquals(new int[] {0, 1}, order);
	}

	/** Every entry is a candidate: the whole menu is just the priority order. */
	@Test
	public void allCandidatesStillOrdersDeterministically()
	{
		List<GroundItemTakePriority.Candidate> candidates = Arrays.asList(
			new GroundItemTakePriority.Candidate(0, 100, 1),
			new GroundItemTakePriority.Candidate(1, 200, 9));

		int[] order = GroundItemTakePriority.reorder(2, candidates);

		assertArrayEquals(new int[] {0, 1}, order);
	}
}
