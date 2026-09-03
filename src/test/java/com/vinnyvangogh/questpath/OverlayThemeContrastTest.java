package com.vinnyvangogh.questpath;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The overlay palette, measured rather than eyeballed.
 *
 * <p>An overlay is drawn over a game whose background nobody controls. The
 * panel ground is translucent, so every text colour's real contrast is against
 * the ground <em>composited over whatever the player is standing on</em> --
 * and the worst case, sunlit grass, is also the most common one. Checking the
 * tokens against a nominal dark ground would pass everything and prove
 * nothing.
 *
 * <p>This caught a live failure: the red that says a build rule has been
 * crossed measured 3.93:1 over bright terrain, below WCAG AA, on the single
 * most consequential colour in the plugin.
 */
public class OverlayThemeContrastTest
{
	/**
	 * Sunlit grass, about the brightest ground an overlay realistically sits
	 * over. Dark scenery only ever makes these ratios better.
	 */
	private static final Color BRIGHT_TERRAIN = new Color(168, 176, 120);

	/** WCAG AA for body text. */
	private static final double AA_BODY = 4.5;

	@Test
	public void everyTextColourClearsAaOverBrightTerrain()
	{
		Color ground = composite(OverlayTheme.GROUND, BRIGHT_TERRAIN);

		assertReadable("TEXT", OverlayTheme.TEXT, ground);
		assertReadable("MUTED", OverlayTheme.MUTED, ground);
		assertReadable("ACCENT", OverlayTheme.ACCENT, ground);
		assertReadable("GOOD", OverlayTheme.GOOD, ground);
		assertReadable("WARN", OverlayTheme.WARN, ground);
		assertReadable("BAD", OverlayTheme.BAD, ground);
	}

	/**
	 * The lighter top of the panel's gradient is a second ground, and text
	 * sits on it too -- the title and the first rows land there.
	 */
	@Test
	public void theGradientsLighterEndIsAlsoReadable()
	{
		Color ground = composite(OverlayTheme.GROUND_TOP, BRIGHT_TERRAIN);

		assertReadable("TEXT on the gradient top", OverlayTheme.TEXT, ground);
		assertReadable("MUTED on the gradient top", OverlayTheme.MUTED, ground);
		assertReadable("ACCENT on the gradient top", OverlayTheme.ACCENT, ground);
		assertReadable("BAD on the gradient top", OverlayTheme.BAD, ground);
	}

	/**
	 * The ground has to stay mostly opaque for any of the above to hold. A
	 * future "make it prettier" pass that drops the alpha would quietly
	 * invalidate every ratio in this file, so the assumption is pinned where
	 * it is made rather than left implied.
	 */
	@Test
	public void thePanelGroundStaysMostlyOpaque()
	{
		assertTrue("the ground must stay opaque enough for the ratios above to mean anything",
			OverlayTheme.GROUND.getAlpha() >= 235);
		assertTrue(OverlayTheme.GROUND_TOP.getAlpha() >= 235);
	}

	private static void assertReadable(String name, Color foreground, Color ground)
	{
		double ratio = contrast(foreground, ground);
		assertTrue(String.format("%s is %.2f:1 against the panel over bright terrain, below %.1f:1",
			name, ratio, AA_BODY), ratio >= AA_BODY);
	}

	/** src-over composite of a translucent colour onto an opaque one. */
	private static Color composite(Color translucent, Color under)
	{
		double alpha = translucent.getAlpha() / 255.0;
		return new Color(
			blend(translucent.getRed(), under.getRed(), alpha),
			blend(translucent.getGreen(), under.getGreen(), alpha),
			blend(translucent.getBlue(), under.getBlue(), alpha));
	}

	private static int blend(int over, int under, double alpha)
	{
		return (int) Math.round(over * alpha + under * (1 - alpha));
	}

	private static double contrast(Color a, Color b)
	{
		double la = luminance(a);
		double lb = luminance(b);
		double hi = Math.max(la, lb);
		double lo = Math.min(la, lb);
		return (hi + 0.05) / (lo + 0.05);
	}

	private static double luminance(Color c)
	{
		return 0.2126 * channel(c.getRed())
			+ 0.7152 * channel(c.getGreen())
			+ 0.0722 * channel(c.getBlue());
	}

	private static double channel(int value)
	{
		double c = value / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}
}
