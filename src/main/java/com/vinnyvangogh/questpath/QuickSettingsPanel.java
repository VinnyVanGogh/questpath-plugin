package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The handful of settings players change most, as checkboxes at the top of the
 * sidebar's settings tab, with an "All settings" link down to the full panel.
 *
 * <p>Each checkbox writes the real config key through {@link Store}, the same
 * key {@link OverlaySettingsPanel} and RuneLite's own config panel use, so the
 * three surfaces never disagree. Nothing is cached: {@link #refresh()} re-reads
 * every value, and the plugin calls it when the config group changes, so a
 * toggle flipped elsewhere shows here too.
 *
 * <p>The picks, and why:
 * <ul>
 * <li>{@code overlayNextQuest}: the headline overlay, the one most players
 * toggle for a clean screen.</li>
 * <li>{@code overlayItems}: the needed-items overlay, the second most
 * visible, often hidden once the shopping is done.</li>
 * <li>{@code overlayShopRoute}: the shopping route overlay, only wanted
 * during a shopping run.</li>
 * <li>{@code lampAdvisor}: lamp advice, the feature a capped account either
 * relies on or finds noisy.</li>
 * <li>{@code bankTab}: the Quest Route bank tab, a bank-layout change players
 * want to switch off quickly.</li>
 * <li>{@code bankHighlight}: bank item highlighting, the other half of the
 * bank features.</li>
 * <li>{@code voiceEnabled}: spoken prompts, the setting most likely to be
 * muted on short notice.</li>
 * <li>{@code syncEnabled}: server or local mode. Turning it on is gated by
 * the same third-party-server warning RuneLite's config panel shows.</li>
 * </ul>
 */
class QuickSettingsPanel extends JPanel
{
	static final String SYNC_WARNING = "This feature submits your IP address to a 3rd-party server not"
		+ " controlled or verified by RuneLite developers.\n\nTurn it on anyway?";

	/** Where a toggle writes to; {@code ConfigManager} in the plugin, a map in tests. */
	interface Store
	{
		void write(String keyName, boolean value);
	}

	/** One quick setting: the key it writes, how it reads the current value, and its words. */
	static final class Row
	{
		final String keyName;
		final String label;
		final String tooltip;
		final BooleanSupplier current;
		final String warning;

		Row(String keyName, String label, String tooltip, BooleanSupplier current, String warning)
		{
			this.keyName = keyName;
			this.label = label;
			this.tooltip = tooltip;
			this.current = current;
			this.warning = warning;
		}
	}

	/** The rows, in display order, reading their current value from {@code config}. */
	static List<Row> rows(QuestpathConfig config)
	{
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("overlayNextQuest", "Next quest overlay",
			"Show the 'Next on your route' overlay in the game.", config::overlayNextQuest, null));
		rows.add(new Row("overlayItems", "Needed items overlay",
			"Show the items-you-still-need overlay in the game.", config::overlayItems, null));
		rows.add(new Row("overlayShopRoute", "Shopping route overlay",
			"Show the shopping route overlay in the game.", config::overlayShopRoute, null));
		rows.add(new Row("lampAdvisor", "Lamp advice",
			"Show which skill to spend an XP lamp on.", config::lampAdvisor, null));
		rows.add(new Row("bankTab", "Quest Route bank tab",
			"Add the Quest Route tab to your bank.", config::bankTab, null));
		rows.add(new Row("bankHighlight", "Bank highlights",
			"Highlight the items you need in your bank.", config::bankHighlight, null));
		rows.add(new Row("voiceEnabled", "Spoken prompts",
			"Speak shop and floor item prompts aloud.", config::voiceEnabled, null));
		rows.add(new Row("syncEnabled", "Server mode (send account state)",
			"Push quest completion, levels and diaries to your Quest Route server. Off keeps"
				+ " everything local.", config::syncEnabled, SYNC_WARNING));
		return Collections.unmodifiableList(rows);
	}

	/**
	 * RuneLite's pixel font, or a plain sans-serif where its font loader cannot
	 * run (a headless or sandboxed test JVM): the font is decoration, and a
	 * missing one must not stop the panel being built.
	 */
	private static Font smallFont()
	{
		try
		{
			return FontManager.getRunescapeSmallFont();
		}
		catch (LinkageError e)
		{
			return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		}
	}

	private final List<Row> rows;
	private final List<JCheckBox> boxes = new ArrayList<>();
	private final Store store;

	/** True while {@link #refresh()} sets boxes, so that is never mistaken for a click. */
	private boolean populating = true;

	QuickSettingsPanel(List<Row> rows, Store store, Runnable onAllSettings)
	{
		this.rows = rows;
		this.store = store;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel heading = new JLabel("QUICK SETTINGS");
		heading.setFont(smallFont().deriveFont(Font.BOLD));
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		add(heading);
		add(Box.createRigidArea(new Dimension(0, 4)));

		for (Row row : rows)
		{
			JCheckBox box = new JCheckBox(row.label);
			box.setToolTipText(row.tooltip);
			box.setForeground(Color.WHITE);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setFont(smallFont());
			box.setFocusPainted(false);
			box.setAlignmentX(LEFT_ALIGNMENT);
			box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			box.addActionListener(e -> onClick(row, box));
			boxes.add(box);
			add(box);
		}

		add(Box.createRigidArea(new Dimension(0, 4)));
		JLabel link = new JLabel("All settings ↓");
		link.setFont(smallFont());
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onAllSettings.run();
			}
		});
		add(link);

		refresh();
	}

	/** Reads every value again; called at build and whenever the config group changes. */
	void refresh()
	{
		populating = true;
		try
		{
			for (int i = 0; i < rows.size(); i++)
			{
				boxes.get(i).setSelected(rows.get(i).current.getAsBoolean());
			}
		}
		finally
		{
			populating = false;
		}
	}

	private void onClick(Row row, JCheckBox box)
	{
		if (populating)
		{
			return;
		}
		if (box.isSelected() && row.warning != null)
		{
			int choice = JOptionPane.showConfirmDialog(this, row.warning, "Quest Route",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				box.setSelected(false);
				return;
			}
		}
		store.write(row.keyName, box.isSelected());
	}

	/** The checkbox for a key, so tests can click it; null if the key is not a quick setting. */
	JCheckBox boxFor(String keyName)
	{
		for (int i = 0; i < rows.size(); i++)
		{
			if (rows.get(i).keyName.equals(keyName))
			{
				return boxes.get(i);
			}
		}
		return null;
	}
}
