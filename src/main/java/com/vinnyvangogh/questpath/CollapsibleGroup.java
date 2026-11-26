package com.vinnyvangogh.questpath;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A header row plus a body panel that the header shows or hides.
 *
 * <p>The settings tab outgrew a flat column of controls once every overlay,
 * Shops and Ground items were spelled out in full. This carries the
 * caret/header/body plumbing so each group in {@link OverlaySettingsPanel} is
 * one call instead of a repeated block of mouse listeners.
 *
 * <p>Collapsing is view state, not a setting: nothing in this class ever
 * touches {@code ConfigManager}.
 */
class CollapsibleGroup extends JPanel
{
	private final Chevron caret;
	private final JPanel body;
	private boolean expanded;

	/**
	 * @param header the header's content -- a heading label for a group with
	 *               no toggle of its own, or the group's own enable checkbox
	 *               for one that has one. Either way it keeps its normal
	 *               behaviour: a checkbox still toggles its setting when
	 *               clicked directly. Only the caret and the row's own
	 *               background collapse the group, unless {@link
	 *               #alsoToggleOn} adds the header itself to that set.
	 * @param body the group's controls, shown only while expanded.
	 * @param startExpanded left as a parameter, not hardcoded, so the one
	 *               place that decides it is the call site. A group headed
	 *               by its own enable checkbox starts expanded only if that
	 *               checkbox is already ticked, matching the smart-close
	 *               behaviour below. A group headed by a plain label -- the
	 *               Shared group, and the three top-level sections in
	 *               {@link OverlaySettingsPanel} -- has no checkbox to read,
	 *               so it always passes {@code true}: collapsing a whole
	 *               section is a different intent from collapsing one
	 *               overlay inside it, and must not depend on the state of
	 *               whatever happens to be nested underneath.
	 */
	CollapsibleGroup(JComponent header, JPanel body, boolean startExpanded)
	{
		this.body = body;
		this.expanded = startExpanded;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);

		caret = new Chevron(startExpanded);
		caret.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_HOVER_COLOR),
			BorderFactory.createEmptyBorder(0, 0, 4, 0)));

		header.setAlignmentX(LEFT_ALIGNMENT);
		row.add(caret);
		row.add(header);
		row.add(Box.createHorizontalGlue());

		// Swing delivers a click to the deepest component under the pointer
		// rather than bubbling it to the parent, so the row's own listener
		// only ever sees clicks that land on its own background -- caret and
		// header need the same listener attached directly to catch clicks on
		// them too.
		toggleOnClick(row);
		toggleOnClick(caret);

		body.setAlignmentX(LEFT_ALIGNMENT);
		body.setVisible(startExpanded);

		add(row);
		add(body);
	}

	/**
	 * Wires an extra component to the same expand/collapse click as the
	 * caret and the row background. Used for a plain heading label, which
	 * unlike a checkbox has no click behaviour of its own to preserve.
	 */
	void alsoToggleOn(JComponent extra)
	{
		toggleOnClick(extra);
	}

	/**
	 * Shows or hides the body.
	 *
	 * <p>Called both by a click on the header and, for a group whose header
	 * is a toggle, by that toggle's own listener -- ticking it expands the
	 * group, unticking it collapses back, so the control that turns a
	 * feature on always shows you its settings too.
	 */
	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		caret.setExpanded(expanded);
		body.setVisible(expanded);

		// setVisible invalidates this group's own parent and stops there. It
		// never asks for a layout pass, so the column of groups above and
		// below kept the heights it had worked out for the previous state --
		// which is how the last group in a section ended up drawn at half
		// height with the next section's heading over the top of it. It only
		// showed with every group collapsed because that is the arrangement
		// where the stale heights and the real ones differ most.
		revalidate();
		repaint();
	}

	/**
	 * Never taller than it needs to be.
	 *
	 * BoxLayout hands surplus vertical space to any child whose maximum
	 * height is unbounded, which a JPanel's is. In a column of these that
	 * means a collapsed group can be stretched well past its header and then
	 * paint its border in the middle of the gap. Capping the height at the
	 * preferred one -- while leaving the width free to fill the sidebar --
	 * makes the column stack tightly whatever is open.
	 */
	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void toggleOnClick(Component component)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!expanded);
			}
		});
	}

	/**
	 * The open/closed arrow, painted rather than typed.
	 *
	 * It was a text label carrying a unicode triangle, which the game's font
	 * has no glyph for -- so it rendered as an orange tofu box, on every group
	 * header, which is the first thing anyone sees on this tab. Painting two
	 * dozen pixels is both certain to work and one less non-ASCII character in
	 * a source tree that is meant not to have any.
	 */
	private static final class Chevron extends JComponent
	{
		private static final int SIZE = 9;

		private boolean expanded;

		private Chevron(boolean expanded)
		{
			this.expanded = expanded;
			Dimension box = new Dimension(SIZE + 4, SIZE + 2);
			setPreferredSize(box);
			setMinimumSize(box);
			setMaximumSize(box);
		}

		private void setExpanded(boolean expanded)
		{
			this.expanded = expanded;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(ColorScheme.BRAND_ORANGE);

				int x = 2;
				int y = (getHeight() - SIZE) / 2;
				Polygon arrow = new Polygon();
				if (expanded)
				{
					// Pointing down: the body below is showing.
					arrow.addPoint(x, y + 2);
					arrow.addPoint(x + SIZE, y + 2);
					arrow.addPoint(x + SIZE / 2, y + SIZE - 1);
				}
				else
				{
					// Pointing right: the body is folded away.
					arrow.addPoint(x + 1, y);
					arrow.addPoint(x + SIZE - 1, y + SIZE / 2);
					arrow.addPoint(x + 1, y + SIZE);
				}
				g2.fillPolygon(arrow);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
