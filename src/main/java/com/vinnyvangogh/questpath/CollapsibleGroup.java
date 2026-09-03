package com.vinnyvangogh.questpath;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
	private final JLabel caret;
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
	 *               place that decides it is the call site. Every call site
	 *               in this plugin passes {@code true}: groups start
	 *               expanded.
	 */
	CollapsibleGroup(JComponent header, JPanel body, boolean startExpanded)
	{
		this.body = body;
		this.expanded = startExpanded;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);

		caret = new JLabel(caretText(startExpanded));
		caret.setFont(FontManager.getRunescapeSmallFont());
		caret.setForeground(ColorScheme.BRAND_ORANGE);
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
		caret.setText(caretText(expanded));
		body.setVisible(expanded);
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

	private static String caretText(boolean expanded)
	{
		return expanded ? "▾" : "▸";
	}
}
