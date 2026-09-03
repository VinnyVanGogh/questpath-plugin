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
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

/**
 * The overlay/shop/ground-item settings, rendered as real controls inside the
 * questpath sidebar instead of RuneLite's own config panel.
 *
 * <p>That panel is a 225px column behind a different icon, and once every
 * setting is spelled out every label there truncates to things like
 * "Route:..." or "Rules:..." -- unreadable and impossible to scan. This panel
 * exists to give the same settings room: headings, grouping, and a control
 * wide enough to read.
 *
 * <p>It reads and writes through {@link ConfigManager} directly rather than
 * caching values locally, so it always agrees with whatever RuneLite's own
 * config panel shows -- there is exactly one source of truth for each key,
 * and it is not this class.
 *
 * <p>Controls are grouped two levels deep, both levels built from the same
 * {@link CollapsibleGroup}: a header row a player can click to hide a body
 * once they know it's set the way they want. Collapsing is view state only
 * -- it never calls {@code ConfigManager}.
 *
 * <p>The outer level is three named sections -- SHARED, OVERLAYS, EXTRAS --
 * each headed by a plain uppercase label with no checkbox of its own, since
 * a section groups several toggles rather than gating one. All three always
 * start expanded regardless of what their children start as: collapsing an
 * entire section is a different intent from collapsing one overlay inside
 * it, so the two must not follow the same rule.
 *
 * <p>The inner level is the eight feature groups -- five overlays under
 * OVERLAYS, Shops and Ground items under EXTRAS, plus Shared's own controls
 * (which have no further nesting; Shared has no per-feature toggles to
 * group). Every one of those eight is headed by its own enable checkbox
 * rather than a plain label, and starts expanded only if that checkbox is
 * already ticked -- collapsed otherwise. That checkbox's own listener also
 * expands or collapses the group afterwards ("smart-close"), so ticking a
 * feature on always shows you its settings and one click puts them away
 * again once you're done. The checkbox itself lives in the header row, so it
 * stays visible and clickable whether the group is collapsed or not.
 */
class OverlaySettingsPanel extends JPanel
{
	private static final Dimension CONTROL_MAX = new Dimension(Integer.MAX_VALUE, 22);
	private static final java.awt.Insets INDENT = new java.awt.Insets(0, 14, 0, 0);

	private final ConfigManager configManager;
	private final ColorPickerManager colorPickerManager;

	/**
	 * True only while the constructor is populating controls with their
	 * stored values.
	 *
	 * Every control's listener checks this before writing. Without it,
	 * simply opening this tab would call {@code setSelectedItem}/{@code
	 * setSelected} on all of them, each firing a change event that writes
	 * the value straight back -- stamping every default as an explicit saved
	 * setting the first time anyone looks at the tab, which is not a write
	 * the player asked for.
	 */
	private boolean populating = true;

	OverlaySettingsPanel(QuestpathConfig config, ConfigManager configManager)
	{
		this.configManager = configManager;
		// RuneliteColorPicker's own constructor is package-private to
		// RuneLite; ColorPickerManager is the way in, and it asks only for
		// the ConfigManager we already have -- nothing here needs Guice
		// injection.
		this.colorPickerManager = new ColorPickerManager(configManager);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setAlignmentX(LEFT_ALIGNMENT);

		add(sharedGroup(config));
		add(spacer());
		add(overlaysSection(config));
		add(spacer());
		add(extrasSection(config));

		populating = false;
	}

	/**
	 * The SHARED top-level section: the five settings shared by every
	 * overlay, unless a panel overrides its own width/length above.
	 *
	 * <p>Unlike OVERLAYS and EXTRAS, Shared has no per-feature toggles to
	 * nest -- these five controls are its entire body, not a further layer
	 * of {@link #toggleGroup}s.
	 */
	private CollapsibleGroup sharedGroup(QuestpathConfig config)
	{
		JPanel panel = column();
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

		return sectionGroup("Shared", panel);
	}

	/**
	 * A top-level section heading: SHARED, OVERLAYS or EXTRAS.
	 *
	 * <p>None of the three has an enable checkbox of its own -- a section
	 * groups several toggles rather than gating one -- so the header is a
	 * plain label, wired to collapse the same way the caret does. It always
	 * starts expanded, independent of {@code body}'s own children: see
	 * {@link CollapsibleGroup}'s {@code startExpanded} javadoc for why that
	 * must not follow the same rule as a feature group.
	 */
	private CollapsibleGroup sectionGroup(String name, JPanel body)
	{
		JLabel header = headingLabel(name);
		CollapsibleGroup group = new CollapsibleGroup(header, body, true);
		group.alsoToggleOn(header);
		return group;
	}

	/**
	 * A group headed by its own enable checkbox: one of the five overlays,
	 * Shops, or Ground items.
	 *
	 * <p>The checkbox doubles as the group's heading -- it already carries
	 * the feature's name, so a second label saying the same thing would just
	 * be noise in a column this narrow -- and its own listener greys out
	 * {@code extras} while off (existing behaviour, kept) and smart-closes
	 * the group to match (new behaviour): unticked collapses it, ticked
	 * expands it. The checkbox lives in the header row, so it stays visible
	 * and clickable either way.
	 */
	private CollapsibleGroup toggleGroup(String name, String tooltip, boolean enabled, String toggleKey,
		java.util.function.Consumer<JPanel> extras)
	{
		JCheckBox header = toggle(name, tooltip, enabled, toggleKey);
		header.setFont(FontManager.getRunescapeBoldFont());

		JPanel sub = column();
		sub.setBorder(BorderFactory.createEmptyBorder(INDENT.top, INDENT.left, INDENT.bottom, INDENT.right));
		extras.accept(sub);
		setEnabledDeep(sub, enabled);

		CollapsibleGroup group = new CollapsibleGroup(header, sub, enabled);
		header.addActionListener(e ->
		{
			setEnabledDeep(sub, header.isSelected());
			group.setExpanded(header.isSelected());
		});
		return group;
	}

	/**
	 * The OVERLAYS top-level section: the five panel groups, each a
	 * self-contained {@link #toggleGroup}, nested under one shared heading.
	 */
	private CollapsibleGroup overlaysSection(QuestpathConfig config)
	{
		JPanel body = column();
		body.add(toggleGroup(
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
		body.add(spacer());
		body.add(toggleGroup(
			"Get while you're here",
			"Items you can pick up during a quest you are already on, that other quests also want.",
			config.overlayPickups(), "overlayPickups",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayPickupsWidth(), "overlayPickupsWidth"));
				addField(sub, "Length", lengthCombo(config.overlayPickupsLength(), "overlayPickupsLength"));
			}));
		body.add(spacer());
		body.add(toggleGroup(
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
		body.add(spacer());
		body.add(toggleGroup(
			"Combat XP to next level",
			"What each stat would cost to buy the next combat level, cheapest first.",
			config.overlayCombatXp(), "overlayCombatXp",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayCombatXpWidth(), "overlayCombatXpWidth"));
				addField(sub, "Length", lengthCombo(config.overlayCombatXpLength(), "overlayCombatXpLength"));
			}));
		body.add(spacer());
		body.add(toggleGroup(
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
		return sectionGroup("Overlays", body);
	}

	/**
	 * The EXTRAS top-level section: the two marker groups, Shops and Ground
	 * items, nested under one shared heading.
	 */
	private CollapsibleGroup extrasSection(QuestpathConfig config)
	{
		JPanel body = column();
		body.add(shopsGroup(config));
		body.add(spacer());
		body.add(groundItemsGroup(config));
		return sectionGroup("Extras", body);
	}

	/**
	 * Shop marking is two independent toggle-shaped config items plus a
	 * colour, but one dependent trio in practice: the count and the outline
	 * colour mean nothing if the highlight itself is off.
	 *
	 * <p>Displayed as "Shops" rather than the fuller "Mark what you need in
	 * shops" -- that longer name was already being cut off in the sidebar
	 * before this group had a section to sit under. The long form survives
	 * as the checkbox's tooltip, where truncation is not a problem.
	 */
	private CollapsibleGroup shopsGroup(QuestpathConfig config)
	{
		return toggleGroup(
			"Shops",
			"Mark what you need in shops: while a shop is open, outline the items your quests"
				+ " still need and show how many to buy. Works whether or not \"Everything you"
				+ " need\" is on.",
			config.shopHighlight(), "shopHighlight",
			sub ->
			{
				sub.add(toggle(
					"Show how many to buy",
					"Print the outstanding count on the marked item, rather than only outlining it.",
					config.shopHighlightCounts(), "shopHighlightCounts"));
				addField(sub, "Outline colour",
					colorField(config.shopHighlightColour(),
						"The colour a needed item is outlined in. The buy count is drawn in green"
							+ " above the slot regardless.",
						"Shop outline colour", "shopHighlightColour"));
			});
	}

	/**
	 * Ground-item marking: the same on/count/colour shape as Shops, for
	 * items already lying on the floor rather than sitting in a shop's
	 * stock.
	 *
	 * <p>Displayed as "Ground items" rather than "Mark what you need on the
	 * ground", for the same fit reason as {@link #shopsGroup}.
	 */
	private CollapsibleGroup groundItemsGroup(QuestpathConfig config)
	{
		return toggleGroup(
			"Ground items",
			"Mark what you need on the ground: outline items lying on the floor that your plan"
				+ " still needs, so you pick them up in passing instead of buying them later.",
			config.groundHighlight(), "groundHighlight",
			sub ->
			{
				sub.add(toggle(
					"Show how many to pick up",
					"Print the outstanding count beside the item name.",
					config.groundHighlightCounts(), "groundHighlightCounts"));
				addField(sub, "Marker colour",
					colorField(config.groundHighlightColour(),
						"The colour a needed item on the floor is marked in.",
						"Ground marker colour", "groundHighlightColour"));
			});
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
	 * A colour swatch button wired to a {@code Color} config key through
	 * RuneLite's own colour picker dialog.
	 *
	 * <p>The dialog reports every slider drag through {@code onColorChange}
	 * -- writing config on each of those would be dozens of writes for one
	 * decision. Only the swatch updates live; the write to {@code
	 * ConfigManager} happens once, when the dialog closes, matching the
	 * one-write-per-decision the rest of this panel keeps.
	 *
	 * <p>Alpha is hidden: every colour this panel offers is an opaque
	 * outline or marker, never a translucent fill, so a fourth slider would
	 * only be a way to accidentally pick an invisible colour.
	 */
	private ColorJButton colorField(Color initial, String tooltip, String dialogTitle, String keyName)
	{
		ColorJButton button = new ColorJButton("", initial);
		button.setToolTipText(tooltip);
		button.setFocusPainted(false);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMaximumSize(CONTROL_MAX);
		button.addActionListener(e ->
		{
			RuneliteColorPicker picker = colorPickerManager.create(
				button, button.getColor(), dialogTitle, true);
			picker.setOnColorChange(button::setColor);
			picker.setOnClose(chosen ->
			{
				button.setColor(chosen);
				if (!populating)
				{
					configManager.setConfiguration(QuestpathConfig.GROUP, keyName, chosen);
				}
			});
			picker.setVisible(true);
		});
		return button;
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

	/** A group heading's label, styled to match the panel's one look. */
	private static JLabel headingLabel(String text)
	{
		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static Component spacer()
	{
		return Box.createRigidArea(new Dimension(0, 10));
	}
}
