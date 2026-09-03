package com.vinnyvangogh.questpath;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The display-mode rule, pinned in the one place it is implemented.
 *
 * <p>Five overlays share it and none of them checks the mode itself, which is
 * the whole reason "just images" can be trusted to mean the same thing on all
 * five. If that rule ever moves back out into the overlays this test is what
 * should stop it.
 */
public class OverlayDisplayModeTest
{
	@Test
	public void eachModeShowsWhatItsNameSays()
	{
		assertTrue(OverlayDisplayMode.TEXT_AND_ICONS.showsText());
		assertTrue(OverlayDisplayMode.TEXT_AND_ICONS.showsIcons());

		assertTrue(OverlayDisplayMode.TEXT_ONLY.showsText());
		assertFalse(OverlayDisplayMode.TEXT_ONLY.showsIcons());

		assertFalse(OverlayDisplayMode.ICONS_ONLY.showsText());
		assertTrue(OverlayDisplayMode.ICONS_ONLY.showsIcons());
	}

	/**
	 * RuneLite renders an enum config item by its toString, so a missing
	 * override would put TEXT_AND_ICONS in the dropdown.
	 */
	@Test
	public void theDropdownReadsInWords()
	{
		assertEquals("Text and images", OverlayDisplayMode.TEXT_AND_ICONS.toString());
		assertEquals("Just text", OverlayDisplayMode.TEXT_ONLY.toString());
		assertEquals("Just images", OverlayDisplayMode.ICONS_ONLY.toString());
	}

	/**
	 * An item questpath could not identify keeps its text in every mode,
	 * icon-only included.
	 *
	 * This is the rule most worth pinning. questpath sends no id for an item
	 * its catalogue could not resolve, so icon-only would otherwise render
	 * that item as a blank space -- silently hiding exactly the item worth
	 * knowing about, in the mode where there is no text to notice it by. An
	 * identified item does collapse to its sprite, which is what the mode is
	 * for.
	 */
	@Test
	public void anItemWithNoIdKeepsItsTextEvenInIconsOnlyMode()
	{
		BufferedImage canvas = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		OverlayContext iconsOnly = new OverlayContext(null, OverlayDisplayMode.ICONS_ONLY);

		// With no id there is no sprite, so the row must fall back to a text
		// line -- one text line tall, not an icon slot.
		OverlayRow unidentified = OverlayRow.item(0, "Mysterious thing", "0 / 1", false,
			OverlayTheme.TEXT, OverlayTheme.MUTED);
		g.setFont(OverlayTheme.font());
		int textHeight = g.getFontMetrics().getHeight();
		assertEquals(textHeight, unidentified.height(g, iconsOnly, 180));

		// An identified one collapses to its slot, which is taller than a
		// line of text -- the density icon-only mode exists for.
		OverlayRow identified = OverlayRow.item(1925, "Bucket", "0 / 1", false,
			OverlayTheme.TEXT, OverlayTheme.MUTED);
		assertTrue(identified.height(g, iconsOnly, 180) > textHeight);

		g.dispose();
	}

	/**
	 * Rows that carry no picture keep their text in every mode. An xp figure,
	 * a cap and a sentence have no sprite, and blanking them in icon-only
	 * mode would hide the rows carrying what no picture could say.
	 */
	@Test
	public void textOnlyRowsSurviveIconsOnlyMode()
	{
		BufferedImage canvas = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		OverlayContext iconsOnly = new OverlayContext(null, OverlayDisplayMode.ICONS_ONLY);

		assertTrue(OverlayRow.line("Defence", "1", OverlayTheme.TEXT, OverlayTheme.BAD)
			.height(g, iconsOnly, 180) > 0);
		assertTrue(OverlayRow.note("No route saved in questpath.", OverlayTheme.MUTED)
			.height(g, iconsOnly, 180) > 0);
		assertTrue(OverlayRow.hero("Cook's Assistant", OverlayTheme.ACCENT)
			.height(g, iconsOnly, 180) > 0);

		g.dispose();
	}

	/**
	 * A grid lays out into as many columns as the width allows and wraps the
	 * rest, rather than drawing off the edge of the panel.
	 */
	@Test
	public void aGridWrapsRatherThanOverflowing()
	{
		BufferedImage canvas = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		OverlayContext ctx = new OverlayContext(null, OverlayDisplayMode.ICONS_ONLY);

		OverlayRow one = OverlayRow.grid(Collections.singletonList(
			new OverlayRow.Cell(1925, null, false)));
		OverlayRow many = OverlayRow.grid(Arrays.asList(
			new OverlayRow.Cell(1925, null, false),
			new OverlayRow.Cell(1927, null, false),
			new OverlayRow.Cell(1931, null, false),
			new OverlayRow.Cell(1933, null, false),
			new OverlayRow.Cell(1944, null, false),
			new OverlayRow.Cell(995, null, false),
			new OverlayRow.Cell(560, null, false),
			new OverlayRow.Cell(2349, null, false)));

		// Narrow enough to force more than one row of slots.
		assertTrue(many.height(g, ctx, 70) > one.height(g, ctx, 70));
		// An empty grid takes no space at all rather than a blank row.
		assertEquals(0, OverlayRow.grid(Collections.emptyList()).height(g, ctx, 180));

		g.dispose();
	}

	/**
	 * An overflow line is a distinct kind, so the panel can drop it in one
	 * place rather than five overlays each deciding.
	 */
	@Test
	public void onlyOverflowRowsAreDroppable()
	{
		assertTrue(OverlayRow.overflow("...and 58 more short.").isOverflow());
		assertFalse(OverlayRow.note("No route saved.", OverlayTheme.MUTED).isOverflow());
		assertFalse(OverlayRow.line("Defence", "1", OverlayTheme.TEXT, OverlayTheme.BAD).isOverflow());
	}

	/**
	 * An item row collapses to a grid cell only when it has a sprite to
	 * collapse to.
	 *
	 * This is what makes icon-only mode denser rather than merely quieter:
	 * consecutive collapsible rows are packed into one wrapping grid. An item
	 * questpath could not identify has no picture, so it keeps its own row and
	 * its text -- otherwise packing would silently swallow exactly the item
	 * worth knowing about.
	 */
	@Test
	public void onlyIdentifiedItemsCollapseIntoTheGrid()
	{
		assertNotNull(OverlayRow.item(1925, "Bucket", "0 / 1", false,
			OverlayTheme.TEXT, OverlayTheme.MUTED).asCell());
		assertNull(OverlayRow.item(0, "Mysterious thing", "0 / 1", false,
			OverlayTheme.TEXT, OverlayTheme.MUTED).asCell());
		assertNull(OverlayRow.line("Defence", "1", OverlayTheme.TEXT, OverlayTheme.BAD).asCell());
		assertNull(OverlayRow.note("Anything.", OverlayTheme.MUTED).asCell());
	}

	/**
	 * A list of plain rows must not change length when the display mode does.
	 *
	 * This was a real bug: the icon-grid bonus was applied to every list, so
	 * switching "Next on your route" to images silently added four quests to
	 * a panel that has no item art in it at all. Only lists that genuinely
	 * pack into a grid may claim the bonus.
	 */
	@Test
	public void displayModeDoesNotChangeThelengthOfAPlainList()
	{
		int base = 10;
		int text = context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.NORMAL).listCap(base);
		int both = context(OverlayDisplayMode.TEXT_AND_ICONS, false, OverlayListLength.NORMAL).listCap(base);
		int icons = context(OverlayDisplayMode.ICONS_ONLY, false, OverlayListLength.NORMAL).listCap(base);

		assertEquals(base, text);
		assertEquals(base, both);
		assertEquals("a list of quest names has nothing to pack", base, icons);
	}

	/**
	 * An item list, which really does pack into a grid, may show more in
	 * icon-only mode -- several fit on the line one text row would take.
	 */
	@Test
	public void anItemListGrowsInIconOnlyModeBecauseItPacks()
	{
		int base = 10;
		assertEquals(base, context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.NORMAL)
			.itemListCap(base));
		assertTrue(context(OverlayDisplayMode.ICONS_ONLY, false, OverlayListLength.NORMAL)
			.itemListCap(base) > base);
	}

	/**
	 * The length setting scales the overlays' own baselines rather than
	 * replacing them, so a route preview stays shorter than a shopping list at
	 * every setting instead of becoming the same length as it.
	 */
	@Test
	public void theLengthSettingScalesRatherThanFlattens()
	{
		int shortRoute = context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.SHORT).listCap(4);
		int longRoute = context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.LONG).listCap(4);
		int longList = context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.LONG).listCap(10);

		assertTrue(longRoute > shortRoute);
		assertTrue("the shorter baseline stays shorter at the same setting", longList > longRoute);
	}

	/**
	 * "Everything" means no cap, and must not overflow into one.
	 *
	 * The density bonuses multiply the cap, and multiplying MAX_VALUE wraps to
	 * a negative -- which would show nothing at all, from the setting that
	 * asks for the most.
	 */
	@Test
	public void everythingIsUncappedInEveryMode()
	{
		for (OverlayDisplayMode mode : OverlayDisplayMode.values())
		{
			for (boolean compact : new boolean[]{false, true})
			{
				OverlayContext ctx = context(mode, compact, OverlayListLength.EVERYTHING);
				assertEquals(Integer.MAX_VALUE, ctx.listCap(10));
				assertEquals(Integer.MAX_VALUE, ctx.itemListCap(10));
			}
		}
	}

	/** Smaller text fits more rows, in proportion to the height it saves. */
	@Test
	public void smallerTextFitsMoreRows()
	{
		assertTrue(context(OverlayDisplayMode.TEXT_ONLY, true, OverlayListLength.NORMAL).listCap(10)
			> context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.NORMAL).listCap(10));
	}

	/**
	 * A panel set to match the shared width takes it; one set explicitly does
	 * not. A shared setting that is itself MATCH_GLOBAL would be a cycle, and
	 * resolves to a real width rather than to zero -- a panel zero pixels wide
	 * draws nothing.
	 */
	@Test
	public void perPanelWidthFallsBackToTheSharedOne()
	{
		assertEquals(OverlayWidth.WIDE.pixels(), PanelWidth.MATCH_SHARED.resolve(OverlayWidth.WIDE));
		assertEquals(OverlayWidth.COMPACT.pixels(), PanelWidth.COMPACT.resolve(OverlayWidth.WIDEST));
		assertTrue(PanelWidth.MATCH_SHARED.resolve(null) > 0);
	}

	/**
	 * The shared settings cannot offer to match themselves.
	 *
	 * They were one enum with a MATCH member, which put "Match the shared
	 * setting" in the shared dropdown -- an option that cannot be true, since
	 * the shared setting is the thing the others match. Two types now: the
	 * shared one has only real values, and only the per-panel one can defer.
	 */
	@Test
	public void onlyPerPanelSettingsCanDefer()
	{
		for (OverlayWidth width : OverlayWidth.values())
		{
			assertTrue(width + " is a shared width and must be a real size",
				width.pixels() > 0);
		}
		for (OverlayListLength length : OverlayListLength.values())
		{
			assertTrue(length + " is a shared length and must be a real length",
				length.baseline() > 0);
		}

		// The per-panel types keep the deferring member, and it is their
		// default -- a panel that has never been configured follows the
		// shared setting.
		assertEquals(PanelWidth.MATCH_SHARED, PanelWidth.values()[0]);
		assertEquals(PanelListLength.MATCH_SHARED, PanelListLength.values()[0]);
	}

	/** Each build-rule setting shows exactly the halves its name claims. */
	@Test
	public void buildRuleSectionsShowWhatTheyName()
	{
		assertTrue(BuildRuleSections.BOTH.showsBlocking());
		assertTrue(BuildRuleSections.BOTH.showsInformational());

		assertTrue(BuildRuleSections.BLOCKING.showsBlocking());
		assertFalse(BuildRuleSections.BLOCKING.showsInformational());

		assertFalse(BuildRuleSections.INFORMATIONAL.showsBlocking());
		assertTrue(BuildRuleSections.INFORMATIONAL.showsInformational());
	}

	private static OverlayContext context(OverlayDisplayMode mode, boolean compact,
		OverlayListLength length)
	{
		return new OverlayContext(null, mode, compact, true, length);
	}

	/**
	 * The overflow setting hides the explanation, never the cap. A panel that
	 * quietly showed more when you asked it to stop explaining itself would be
	 * answering a different question.
	 */
	@Test
	public void hidingOverflowNotesDoesNotChangeTheCap()
	{
		OverlayContext saying = new OverlayContext(null, OverlayDisplayMode.TEXT_AND_ICONS,
			false, true, OverlayListLength.NORMAL);
		OverlayContext quiet = new OverlayContext(null, OverlayDisplayMode.TEXT_AND_ICONS,
			false, false, OverlayListLength.NORMAL);

		assertTrue(saying.showOverflow());
		assertFalse(quiet.showOverflow());
		assertEquals(saying.listCap(10), quiet.listCap(10));
		assertEquals(saying.itemListCap(10), quiet.itemListCap(10));
	}

	/**
	 * A long run of one-line rows is laid in columns, and the columns really
	 * do make it shorter.
	 *
	 * Sixty-eight items in one column is a stripe down the screen whatever
	 * font it is set in. Short runs are deliberately left alone -- two columns
	 * of three is harder to read than six stacked.
	 */
	@Test
	public void columnsMakeALongListShorter()
	{
		BufferedImage canvas = new BufferedImage(400, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		OverlayContext ctx = context(OverlayDisplayMode.TEXT_ONLY, false, OverlayListLength.NORMAL);

		java.util.List<OverlayRow> many = new java.util.ArrayList<>();
		for (int i = 0; i < 30; i++)
		{
			many.add(OverlayRow.line("Item " + i, "0 / 1", OverlayTheme.TEXT, OverlayTheme.WARN));
		}

		int stacked = 0;
		for (OverlayRow row : many)
		{
			stacked += row.height(g, ctx, 400);
		}

		// 400px is wide enough for the three-column maximum, so thirty rows
		// become ten deep.
		int wide = OverlayRow.columns(many).height(g, ctx, 400);
		assertTrue("three columns should be roughly a third the height, was "
			+ wide + " against " + stacked, wide < stacked / 2);

		// 100px fits one column, so it is back to the full stack -- taller
		// than the wide case, and nothing dropped to get there.
		int narrow = OverlayRow.columns(many).height(g, ctx, 100);
		assertTrue(narrow > wide);
		assertTrue("one column is no shorter than stacking them", narrow >= stacked);

		g.dispose();
	}

	/**
	 * Only single-line rows may sit in a column. A heading, a bar or a wrapped
	 * note spans the panel by design, and columnising one would break the
	 * grouping it exists to provide.
	 */
	@Test
	public void onlySingleLineRowsAreColumnisable()
	{
		assertTrue(OverlayRow.line("Ashes", "0 / 1", OverlayTheme.TEXT, OverlayTheme.WARN)
			.isColumnisable());
		assertTrue(OverlayRow.item(1925, "Bucket", "0 / 1", false,
			OverlayTheme.TEXT, OverlayTheme.WARN).isColumnisable());

		assertFalse(OverlayRow.header("Still short").isColumnisable());
		assertFalse(OverlayRow.note("Bank not opened.", OverlayTheme.WARN).isColumnisable());
		assertFalse(OverlayRow.bar("Items held", "38 / 106", 0.35, OverlayTheme.WARN)
			.isColumnisable());
		assertFalse(OverlayRow.hero("A Kingdom Divided", OverlayTheme.ACCENT).isColumnisable());
	}

	/**
	 * Per-panel length falls back to the shared setting, and an explicit one
	 * wins.
	 *
	 * One length for all five was the wrong shape: "Everything" is what makes
	 * the shopping list usable and what makes the route preview ninety quests
	 * long, and "Short" is right for the route and leaves the shopping list
	 * showing five of sixty-five. No single value is correct for both.
	 */
	@Test
	public void perPanelLengthFallsBackToTheSharedOne()
	{
		assertEquals(OverlayListLength.EVERYTHING,
			PanelListLength.MATCH_SHARED.resolve(OverlayListLength.EVERYTHING));
		assertEquals(OverlayListLength.SHORT,
			PanelListLength.SHORT.resolve(OverlayListLength.EVERYTHING));
		assertEquals(OverlayListLength.NORMAL, PanelListLength.MATCH_SHARED.resolve(null));
	}
}
