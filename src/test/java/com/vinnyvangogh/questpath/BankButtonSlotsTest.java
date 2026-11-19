package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BankButtonSlotsTest
{
	private static int[] at(int x, int w)
	{
		return new int[]{x, w};
	}

	@Test
	public void emptyRowKeepsPreferred()
	{
		assertEquals(380, BankButtonSlots.pick(Collections.<int[]>emptyList(), 380, 25, 250));
	}

	@Test
	public void questHelperToTheRightDoesNotMoveUs()
	{
		assertEquals(380, BankButtonSlots.pick(Arrays.asList(at(408, 25)), 380, 25, 250));
	}

	@Test
	public void bankViewsAtItsOldXIsStepped()
	{
		// Bank Views at 371..396 sat under ours at 380.
		int x = BankButtonSlots.pick(Arrays.asList(at(371, 25), at(408, 25)), 380, 25, 250);
		assertTrue(x + 25 + BankButtonSlots.GAP <= 371);
	}

	@Test
	public void walksPastAChainOfNeighbours()
	{
		List<int[]> row = Arrays.asList(at(371, 25), at(346, 25), at(321, 25), at(408, 25));
		int x = BankButtonSlots.pick(row, 380, 25, 250);
		assertTrue(x + 25 + BankButtonSlots.GAP <= 321);
		for (int[] o : row)
		{
			assertTrue("overlaps " + o[0], x + 25 <= o[0] || o[0] + o[1] <= x);
		}
	}

	@Test
	public void noRoomFallsBackToPreferred()
	{
		List<int[]> wall = new ArrayList<>();
		for (int x = 240; x < 420; x += 20)
		{
			wall.add(at(x, 25));
		}
		assertEquals(380, BankButtonSlots.pick(wall, 380, 25, 250));
	}
}
