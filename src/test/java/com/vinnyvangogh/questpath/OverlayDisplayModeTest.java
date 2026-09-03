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
	 * List caps scale with how much the chosen settings actually fit.
	 *
	 * The caps are there so a panel stays a readout rather than a wall, and a
	 * wall is measured in screen height, not entries. A grid fits several
	 * items on the line a text row spends on one, so holding both to the same
	 * count would waste the mode the player switched to in order to see more.
	 */
	@Test
	public void listCapsGrowWithDensity()
	{
		OverlayIcons noIcons = null;
		int base = 10;

		int roomy = new OverlayContext(noIcons, OverlayDisplayMode.TEXT_AND_ICONS, false, true).listCap(base);
		int small = new OverlayContext(noIcons, OverlayDisplayMode.TEXT_AND_ICONS, true, true).listCap(base);
		int grid = new OverlayContext(noIcons, OverlayDisplayMode.ICONS_ONLY, false, true).listCap(base);

		assertEquals(base, roomy);
		assertTrue("smaller text fits more rows", small > roomy);
		assertTrue("a grid fits far more than smaller text does", grid > small);
	}

	/**
	 * The overflow setting hides the explanation, never the cap. A panel that
	 * quietly showed more when you asked it to stop explaining itself would be
	 * answering a different question.
	 */
	@Test
	public void hidingOverflowNotesDoesNotChangeTheCap()
	{
		OverlayContext saying = new OverlayContext(null, OverlayDisplayMode.TEXT_AND_ICONS, false, true);
		OverlayContext quiet = new OverlayContext(null, OverlayDisplayMode.TEXT_AND_ICONS, false, false);

		assertTrue(saying.showOverflow());
		assertFalse(quiet.showOverflow());
		assertEquals(saying.listCap(10), quiet.listCap(10));
	}
}
