package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws {@link FloatingNotes} in the world: short lines that appear where
 * something happened, drift up, and fade.
 *
 * <p>Everything about *when* a note exists lives in {@link FloatingNotes} and
 * is tested there. This class only turns those into pixels, which is the part
 * no test can usefully hold: a canvas, a projection, and a colour.
 */
class FloatingNoteOverlay extends Overlay
{
	/** How high above the tile the first note sits. */
	private static final int BASE_HEIGHT = 180;

	/** The gap between stacked notes, so a burst reads as a list. */
	private static final int STACK_GAP = 16;

	/** How far a note drifts upward across its whole life. */
	private static final int DRIFT = 30;

	/**
	 * The share of a note's life spent at full opacity before it starts
	 * fading. Fading from the first frame makes a note look broken at the
	 * moment it most needs to be read.
	 */
	private static final double SOLID_UNTIL = 0.6;

	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;
	private final FloatingNotes notes;

	FloatingNoteOverlay(QuestpathPlugin plugin, QuestpathConfig config, Client client,
		FloatingNotes notes)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.notes = notes;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * Nothing here may throw. This runs inside RuneLite's render loop once a
	 * frame, and an uncaught exception does not fail one frame quietly -- it
	 * can take the overlay down for the rest of the session. Same guard, and
	 * the same reasoning, as {@link GroundItemOverlay#render}.
	 */
	@Override
	public Dimension render(Graphics2D g)
	{
		try
		{
			paint(g);
		}
		catch (RuntimeException e)
		{
			// Deliberately swallowed after being logged by nothing: a broken
			// frame of decoration must never cost the player the overlays
			// that actually matter.
			return null;
		}
		return null;
	}

	private void paint(Graphics2D g)
	{
		if (config == null || !config.floatingNotes() || plugin == null || client == null)
		{
			return;
		}

		List<FloatingNotes.Note> live = notes.live(Instant.now());
		if (live.isEmpty())
		{
			return;
		}

		g.setFont(OverlayTheme.smallFont());
		int index = 0;
		for (FloatingNotes.Note note : live)
		{
			double age = note.age(Instant.now());
			LocalPoint local = LocalPoint.fromWorld(client, note.at());
			if (local == null)
			{
				// The note's tile is not in the loaded scene -- the player
				// walked away, or it was somewhere they cannot see. Skipped
				// rather than clamped to the screen edge, which would park a
				// stray line in the corner with nothing to explain it.
				index++;
				continue;
			}

			int height = BASE_HEIGHT + (index * STACK_GAP) + (int) (DRIFT * age);
			Point at = Perspective.getCanvasTextLocation(client, g, local, note.text(), height);
			if (at != null)
			{
				OverlayTheme.shadowed(g, note.text(), at.getX(), at.getY(), fade(age));
			}
			index++;
		}
	}

	/**
	 * Full colour for most of a note's life, then a fade to nothing.
	 *
	 * <p>Alpha is clamped into 0-255 rather than trusted: {@link
	 * FloatingNotes.Note#age} already clamps its fraction, but an alpha
	 * outside that range is an {@code IllegalArgumentException} inside the
	 * render loop, which is the one place this class must never throw.
	 */
	private static Color fade(double age)
	{
		double remaining = age <= SOLID_UNTIL ? 1.0 : 1.0 - ((age - SOLID_UNTIL) / (1.0 - SOLID_UNTIL));
		int alpha = (int) Math.round(255 * remaining);
		alpha = Math.max(0, Math.min(255, alpha));
		Color base = OverlayTheme.GOOD;
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
	}
}
