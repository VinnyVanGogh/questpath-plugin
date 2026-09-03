package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The 27 overlay/shop settings, rendered as real controls inside the
 * questpath sidebar instead of RuneLite's own config panel.
 *
 * <p>That panel is a 225px column behind a different icon, and at 27 items
 * every label there truncates to things like "Route:..." or "Rules:..." --
 * unreadable and impossible to scan. This panel exists to give the same
 * settings room: headings, grouping, and a control wide enough to read.
 *
 * <p>It reads and writes through {@link ConfigManager} directly rather than
 * caching values locally, so it always agrees with whatever RuneLite's own
 * config panel shows -- there is exactly one source of truth for each key,
 * and it is not this class.
 */
class OverlaySettingsPanel extends JPanel
{
	private static final Dimension CONTROL_MAX = new Dimension(Integer.MAX_VALUE, 22);
	private static final java.awt.Insets INDENT = new java.awt.Insets(0, 14, 0, 0);

	private final ConfigManager configManager;

	/**
	 * True only while the constructor is populating controls with their
	 * stored values.
	 *
	 * Every control's listener checks this before writing. Without it,
	 * simply opening this tab would call {@code setSelectedItem}/{@code
	 * setSelected} on all 27 controls, each firing a change event that
	 * writes the value straight back -- stamping every default as an
	 * explicit saved setting the first time anyone looks at the tab, which
	 * is not a write the player asked for.
	 */
	private boolean populating = true;

	OverlaySettingsPanel(QuestpathConfig config, ConfigManager configManager)
	{
		this.configManager = configManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setAlignmentX(LEFT_ALIGNMENT);

		add(sharedGroup(config));
		add(spacer());
		add(overlayGroup(
			"Next on your route",
			"The next quests on your saved questpath route, and how far through it you are.",
			config.overlayNextQuest(), "overlayNextQuest",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayNextQuestWidth(), "overlayNextQuestWidth"));
				addField(sub, "Length", lengthCombo(config.overlayNextQuestLength(), "overlayNextQuestLength"));
				sub.add(toggle(
					"List its own items",
					"List the next quest's own items under it, ticked against what you hold.",
					config.overlayNextQuestItems(), "overlayNextQuestItems"));
			}));
		add(spacer());
		add(overlayGroup(
			"Get while you're here",
			"Items you can pick up during a quest you are already on, that other quests also want.",
			config.overlayPickups(), "overlayPickups",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayPickupsWidth(), "overlayPickupsWidth"));
				addField(sub, "Length", lengthCombo(config.overlayPickupsLength(), "overlayPickupsLength"));
			}));
		add(spacer());
		add(overlayGroup(
			"Everything you need",
			"Every item your doable quests need, ticked against what the client can see you hold.",
			config.overlayItems(), "overlayItems",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayItemsWidth(), "overlayItemsWidth"));
				addField(sub, "Length", lengthCombo(config.overlayItemsLength(), "overlayItemsLength"));
				sub.add(toggle(
					"Hide items you already hold",
					"Drop the \"already held\" section, leaving only what you are short of."
						+ " The running total still counts them.",
					config.overlayItemsHideHeld(), "overlayItemsHideHeld"));
			}));
		add(spacer());
		add(overlayGroup(
			"Combat XP to next level",
			"What each stat would cost to buy the next combat level, cheapest first.",
			config.overlayCombatXp(), "overlayCombatXp",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayCombatXpWidth(), "overlayCombatXpWidth"));
				addField(sub, "Length", lengthCombo(config.overlayCombatXpLength(), "overlayCombatXpLength"));
			}));
		add(spacer());
		add(overlayGroup(
			"Build rules",
			"Your declared caps and no-xp skills, and how much headroom is left in each.",
			config.overlayBuildRules(), "overlayBuildRules",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayBuildRulesWidth(), "overlayBuildRulesWidth"));
				addField(sub, "Length", lengthCombo(config.overlayBuildRulesLength(), "overlayBuildRulesLength"));
				addField(sub, "Which rules",
					combo(BuildRuleSections.values(), config.overlayBuildRulesSections(),
						"Blocking rules are walls; informational ones are goals and soft caps.",
						"overlayBuildRulesSections"));
			}));
		add(spacer());
		add(shopsGroup(config));

		populating = false;
	}

	/**
	 * The five settings shared by every overlay, unless a panel overrides
	 * its own width/length below.
	 */
	private JPanel sharedGroup(QuestpathConfig config)
	{
		JPanel panel = column();
		panel.add(heading("Shared"));
		addField(panel, "Show",
			combo(OverlayDisplayMode.values(), config.overlayDisplayMode(),
				"Whether overlay rows carry the item pictures, the words, or both.",
				"overlayDisplayMode"));
		addField(panel, "Width",
			combo(OverlayWidth.values(), config.overlayWidth(),
				"How wide the overlay panels may be.",
				"overlayWidth"));
		addField(panel, "List length",
			combo(OverlayListLength.values(), config.overlayListLength(),
				"How much of a capped list each overlay draws.",
				"overlayListLength"));
		panel.add(toggle(
			"Smaller text",
			"Set overlay rows in the game's small font, fitting about a third more of them"
				+ " in the same panel.",
			config.overlayCompactText(), "overlayCompactText"));
		panel.add(toggle(
			"Say when a list is cut short",
			"Show the \"...and 58 more\" line at the end of a capped list.",
			config.overlayShowOverflow(), "overlayShowOverflow"));
		return panel;
	}

	/**
	 * One overlay's toggle plus its own width/length/extras, indented
	 * underneath so the relationship reads at a glance.
	 *
	 * <p>The toggle doubles as the group's heading -- it already carries the
	 * overlay's name, so a second label saying the same thing would just be
	 * noise in a column this narrow.
	 */
	private JPanel overlayGroup(String name, String tooltip, boolean enabled, String toggleKey,
		java.util.function.Consumer<JPanel> extras)
	{
		JPanel panel = column();
		JCheckBox toggle = toggle(name, tooltip, enabled, toggleKey);
		toggle.setFont(FontManager.getRunescapeBoldFont());

		JPanel sub = column();
		sub.setBorder(BorderFactory.createEmptyBorder(INDENT.top, INDENT.left, INDENT.bottom, INDENT.right));
		extras.accept(sub);

		setEnabledDeep(sub, enabled);
		toggle.addActionListener(e -> setEnabledDeep(sub, toggle.isSelected()));

		panel.add(toggle);
		panel.add(sub);
		return panel;
	}

	/**
	 * Shop marking is two independent config items but one dependent pair in
	 * practice: showing counts means nothing if the highlight itself is off.
	 */
	private JPanel shopsGroup(QuestpathConfig config)
	{
		JPanel panel = column();
		panel.add(heading("Shops"));

		JCheckBox highlight = toggle(
			"Mark what you need in shops",
			"While a shop is open, outline the items your quests still need and show how many"
				+ " to buy. Works whether or not \"Everything you need\" is on.",
			config.shopHighlight(), "shopHighlight");

		JPanel sub = column();
		sub.setBorder(BorderFactory.createEmptyBorder(INDENT.top, INDENT.left, INDENT.bottom, INDENT.right));
		sub.add(toggle(
			"Show how many to buy",
			"Print the outstanding count on the marked item, rather than only outlining it.",
			config.shopHighlightCounts(), "shopHighlightCounts"));

		setEnabledDeep(sub, config.shopHighlight());
		highlight.addActionListener(e -> setEnabledDeep(sub, highlight.isSelected()));

		panel.add(highlight);
		panel.add(sub);
		return panel;
	}

	/**
	 * A per-panel width control.
	 *
	 * PanelWidth rather than OverlayWidth: only a panel can offer to match the
	 * shared setting, because the shared setting is the thing being matched.
	 */
	private JComboBox<PanelWidth> widthCombo(PanelWidth initial, String keyName)
	{
		return combo(PanelWidth.values(), initial,
			"Leave this matching the shared setting unless you want this one panel narrower"
				+ " or wider than the rest.",
			keyName);
	}

	/** A per-panel length control. See {@link #widthCombo} on the type. */
	private JComboBox<PanelListLength> lengthCombo(PanelListLength initial, String keyName)
	{
		return combo(PanelListLength.values(), initial,
			"Leave this matching the shared setting unless this panel wants a different"
				+ " amount shown.",
			keyName);
	}

	/**
	 * A stacked label-then-control block.
	 *
	 * <p>At 225px a label and its combo do not fit side by side once the
	 * label says anything longer than "On" -- this was checked against the
	 * actual sidebar width rather than assumed, and stacking is what fits.
	 */
	private void addField(JPanel target, String label, JComponent control)
	{
		JLabel fieldLabel = new JLabel(label);
		fieldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		fieldLabel.setFont(FontManager.getRunescapeSmallFont());
		fieldLabel.setAlignmentX(LEFT_ALIGNMENT);

		target.add(fieldLabel);
		target.add(Box.createRigidArea(new Dimension(0, 2)));
		target.add(control);
		target.add(Box.createRigidArea(new Dimension(0, 6)));
	}

	/**
	 * A checkbox wired straight to a boolean config key.
	 *
	 * Writes happen on every user click, immediately -- there is no Save
	 * button anywhere in this panel, matching how RuneLite's own config
	 * panel behaves.
	 */
	private JCheckBox toggle(String label, String tooltip, boolean initial, String keyName)
	{
		JCheckBox box = new JCheckBox(label);
		box.setToolTipText(tooltip);
		box.setSelected(initial);
		box.setForeground(Color.WHITE);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setFocusPainted(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setMaximumSize(CONTROL_MAX);
		box.addActionListener(e ->
		{
			if (populating)
			{
				return;
			}
			configManager.setConfiguration(QuestpathConfig.GROUP, keyName, box.isSelected());
		});
		return box;
	}

	/**
	 * A combo wired straight to an enum config key.
	 *
	 * The enum's own {@code toString()} is what RuneLite renders in its
	 * config panel too, so using it here keeps the two surfaces reading the
	 * same words for the same setting.
	 */
	private <E extends Enum<E>> JComboBox<E> combo(E[] values, E initial, String tooltip, String keyName)
	{
		JComboBox<E> box = new JComboBox<>(values);
		box.setSelectedItem(initial);
		box.setToolTipText(tooltip);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setForeground(Color.WHITE);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setMaximumSize(CONTROL_MAX);
		box.addActionListener(e ->
		{
			if (populating)
			{
				return;
			}
			@SuppressWarnings("unchecked")
			E selected = (E) box.getSelectedItem();
			configManager.setConfiguration(QuestpathConfig.GROUP, keyName, selected);
		});
		return box;
	}

	/**
	 * Disables (or re-enables) an entire subtree.
	 *
	 * A panel's own options are meaningless while its toggle is off, and
	 * greying them out rather than hiding them is what keeps the layout
	 * stable as the player clicks toggles on and off -- nothing jumps.
	 */
	private static void setEnabledDeep(Component component, boolean enabled)
	{
		component.setEnabled(enabled);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				setEnabledDeep(child, enabled);
			}
		}
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_HOVER_COLOR),
			BorderFactory.createEmptyBorder(0, 0, 4, 0)));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 6));
		return label;
	}

	private static Component spacer()
	{
		return Box.createRigidArea(new Dimension(0, 10));
	}
}
