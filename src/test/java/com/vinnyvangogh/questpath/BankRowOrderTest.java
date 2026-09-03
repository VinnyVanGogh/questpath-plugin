package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The order the bank's "Still needed" section is laid out in.
 *
 * <p>Worth its own test because the rule is not the obvious one. Sorting by
 * how many are missing looks reasonable and is wrong: one of five pigeon
 * cages and one of two guam leaves are both "one" by that measure, and only
 * one of them is nearly done.
 */
public class BankRowOrderTest
{
	private static List<String> ordered(String... spec)
	{
		List<BankTabLayout.Row> rows = new ArrayList<>();
		for (String entry : spec)
		{
			// "name:owned/wanted"
			String[] parts = entry.split("[:/]");
			rows.add(new BankTabLayout.Row(1, parts[0],
				Long.parseLong(parts[1]), Integer.parseInt(parts[2])));
		}
		BankTabLayout.byClosestToDone(rows);
		List<String> names = new ArrayList<>();
		for (BankTabLayout.Row row : rows)
		{
			names.add(row.name());
		}
		return names;
	}

	/** The case that prompted the rule: half done beats a fifth done. */
	@Test
	public void nearestToFinishedComesFirst()
	{
		assertEquals(Arrays.asList("Guam leaf", "Pigeon cage"),
			ordered("Pigeon cage:1/5", "Guam leaf:1/2"));
	}

	/** Something you have not started belongs at the bottom, whatever it is called. */
	@Test
	public void nothingOwnedSinksBelowAnythingStarted()
	{
		assertEquals(Arrays.asList("Zamorak brew", "Air rune"),
			ordered("Air rune:0/6", "Zamorak brew:1/9"));
	}

	/**
	 * Equal proportions break on the smaller absolute gap. Two rows both half
	 * done are not equally close when one needs one more and the other fifty.
	 */
	@Test
	public void equalProgressPrefersTheSmallerGap()
	{
		assertEquals(Arrays.asList("Bucket", "Bones"),
			ordered("Bones:50/100", "Bucket:1/2"));
	}

	/** Only once both are genuinely tied does the name decide anything. */
	@Test
	public void nameIsTheLastResort()
	{
		assertEquals(Arrays.asList("Ashes", "Bones"),
			ordered("Bones:1/2", "Ashes:1/2"));
	}
}
