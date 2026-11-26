package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar's one look, in one place: the fonts, the label and control
 * shapes every panel in this plugin builds from. Static and stateless, so a
 * panel does {@code import static ...Ui.*} and reads as the layout it is.
 */
final class Ui
{
	/** RuneLite's pixel fonts, or a plain one where the font loader cannot run (a headless test JVM). */
	static final Font SMALL = font(true);
	static final Font BOLD = font(false);
	static final Dimension CONTROL_MAX = new Dimension(Integer.MAX_VALUE, 22);

	private Ui()
	{
	}

	private static Font font(boolean small)
	{
		try
		{
			return small ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeBoldFont();
		}
		catch (LinkageError e)
		{
			return new Font(Font.SANS_SERIF, small ? Font.PLAIN : Font.BOLD, 12);
		}
	}

	/**
	 * Swing labels do not wrap, and the sidebar is narrow. HTML is the
	 * supported way to get a multi-line label, with the width pinned so it
	 * does not grow the panel wider than the sidebar allows.
	 */
	static String wrap(String text)
	{
		return "<html><body style='width:150px'>" + escape(text) + "</body></html>";
	}

	/** A stray angle bracket in one of our own strings would otherwise be swallowed as markup. */
	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static Component gap(int height)
	{
		return Box.createVerticalStrut(height);
	}

	/** A plain, left-aligned label in the given colour; the text is its own tooltip. */
	static JLabel tinted(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setForeground(colour);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** A one-line row label, plain text rather than HTML: cheap enough for a list of hundreds. */
	static JLabel row(String text)
	{
		JLabel label = tinted(text, null);
		label.setToolTipText(text);
		return label;
	}

	static JLabel row(String text, Color colour)
	{
		JLabel label = row(text);
		label.setForeground(colour);
		return label;
	}

	/** {@link #row} in the small font and a colour. */
	static JLabel small(String text, Color colour)
	{
		JLabel label = row(text, colour);
		label.setFont(SMALL);
		return label;
	}

	/** A wrapped sentence in the small font. */
	static JLabel note(String text, Color colour)
	{
		JLabel label = tinted(wrap(text), colour);
		label.setFont(SMALL);
		return label;
	}

	static JLabel muted(String text)
	{
		return tinted(wrap(text), ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** A small bold sub-heading, e.g. "EVERYTHING YOU NEED". */
	static JLabel bold(String text, Color colour)
	{
		JLabel label = tinted(text, colour);
		label.setFont(SMALL.deriveFont(Font.BOLD));
		return label;
	}

	/** A section heading: uppercase, orange, underlined. */
	static JLabel heading(String text)
	{
		JLabel label = bold(text.toUpperCase(), ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 3));
		return label;
	}

	static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static JPanel grid()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** A small, left-aligned button. */
	static JButton button(String text, ActionListener onClick)
	{
		JButton button = new JButton(text);
		button.setFont(SMALL);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (onClick != null)
		{
			button.addActionListener(onClick);
		}
		return button;
	}

	/** A small, transparent, left-aligned checkbox. */
	static JCheckBox check(String text, boolean selected, Color colour, ActionListener onClick)
	{
		JCheckBox box = new JCheckBox(text, selected);
		box.setFont(SMALL);
		box.setForeground(colour);
		box.setFocusPainted(false);
		box.setOpaque(false);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (onClick != null)
		{
			box.addActionListener(onClick);
		}
		return box;
	}

	/** The sidebar's combo look: dark, small font, full width. */
	static <T> JComboBox<T> styled(JComboBox<T> combo, String tooltip)
	{
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
		combo.setFont(SMALL);
		combo.setMaximumSize(CONTROL_MAX);
		combo.setAlignmentX(Component.LEFT_ALIGNMENT);
		combo.setToolTipText(tooltip);
		return combo;
	}
}
