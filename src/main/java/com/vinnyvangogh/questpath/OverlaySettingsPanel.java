package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import static com.vinnyvangogh.questpath.Ui.*;

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
 * and it is not this class. The same goes for the words: every control's
 * tooltip, and any third-party warning, is the
 * {@code @ConfigItem}'s own description and warning, read off the config
 * descriptor RuneLite builds for its own panel, so the two surfaces cannot
 * drift.
 *
 * <p>Controls are grouped two levels deep, both levels built from the same
 * {@link CollapsibleGroup}: a header row a player can click to hide a body
 * once they know it's set the way they want. Collapsing is view state only
 * -- it never calls {@code ConfigManager}.
 *
 * <p>The outer level is named sections -- PARTY, SHARED, OVERLAYS, EXTRAS --
 * each headed by a plain uppercase label with no checkbox of its own, since
 * a section groups several toggles rather than gating one. They always start
 * expanded regardless of what their children start as.
 *
 * <p>The inner level is the feature groups, each headed by its own enable
 * checkbox rather than a plain label, and starting expanded only if that
 * checkbox is already ticked. That checkbox's own listener also expands or
 * collapses the group afterwards ("smart-close"), so ticking a feature on
 * always shows you its settings and one click puts them away again.
 */
class OverlaySettingsPanel extends JPanel
{
	private final ConfigManager configManager;
	private final ColorPickerManager colorPickerManager;

	/** Each key's own description and warning, from the config descriptor. */
	private final Map<String, String> descriptions = new HashMap<>();
	private final Map<String, String> warnings = new HashMap<>();

	/**
	 * True only while the constructor is populating controls with their
	 * stored values: every control's listener checks this before writing, or
	 * opening this tab would stamp every default as an explicit saved setting.
	 */
	private boolean populating = true;

	OverlaySettingsPanel(QuestpathConfig config, ConfigManager configManager)
	{
		this.configManager = configManager;
		// RuneliteColorPicker's own constructor is package-private to
		// RuneLite; ColorPickerManager is the way in.
		this.colorPickerManager = new ColorPickerManager(configManager);
		ConfigDescriptor descriptor = configManager.getConfigDescriptor(config);
		if (descriptor != null)
		{
			for (ConfigItemDescriptor item : descriptor.getItems())
			{
				descriptions.put(item.getItem().keyName(), item.getItem().description());
				warnings.put(item.getItem().keyName(), item.getItem().warning());
			}
		}

		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Deeper at the bottom than the sides on purpose: the last control in
		// the last group otherwise ends flush against the scroll viewport's
		// edge, which is indistinguishable from the panel being clipped.
		setBorder(BorderFactory.createEmptyBorder(8, 8, 24, 8));
		setAlignmentX(LEFT_ALIGNMENT);

		add(partySection(config));
		add(spacer());
		add(sharedGroup(config));
		add(spacer());
		add(overlaysSection(config));
		add(spacer());
		add(extrasSection(config));

		populating = false;
	}

	/** The PARTY section: what is shared with the RuneLite party, which is the only thing that leaves the client. */
	private CollapsibleGroup partySection(QuestpathConfig config)
	{
		JPanel panel = column();
		panel.add(toggle("Share with my party", config.partyShare(), "partyShare"));
		panel.add(spacer(6));
		panel.add(toggle("Group needs in shop alerts", config.groupShopAlert(), "groupShopAlert"));
		return sectionGroup("Party", panel);
	}

	/** The SHARED section: the settings every overlay follows unless it overrides its own. */
	private CollapsibleGroup sharedGroup(QuestpathConfig config)
	{
		JPanel panel = column();
		addField(panel, "Show", combo(OverlayDisplayMode.values(), config.overlayDisplayMode(), "overlayDisplayMode"));
		addField(panel, "Width", combo(OverlayWidth.values(), config.overlayWidth(), "overlayWidth"));
		addField(panel, "List length",
			combo(OverlayListLength.values(), config.overlayListLength(), "overlayListLength"));
		panel.add(toggle("Smaller text", config.overlayCompactText(), "overlayCompactText"));
		panel.add(toggle("Say when a list is cut short", config.overlayShowOverflow(), "overlayShowOverflow"));
		return sectionGroup("Shared", panel);
	}

	/** A top-level section heading: a plain label, always starting expanded. */
	private CollapsibleGroup sectionGroup(String name, JPanel body)
	{
		JLabel header = bold(name.toUpperCase(), ColorScheme.BRAND_ORANGE);
		CollapsibleGroup group = new CollapsibleGroup(header, body, true);
		group.alsoToggleOn(header);
		return group;
	}

	/**
	 * A group headed by its own enable checkbox. Its listener greys out
	 * {@code extras} while off and smart-closes the group to match.
	 */
	private CollapsibleGroup toggleGroup(String name, boolean enabled, String toggleKey, Consumer<JPanel> extras)
	{
		JCheckBox header = toggle(name, enabled, toggleKey);
		header.setFont(BOLD);

		JPanel sub = indented();
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

	private static JPanel indented()
	{
		JPanel sub = column();
		sub.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
		return sub;
	}

	/** The OVERLAYS section: one {@link #toggleGroup} per panel. */
	private CollapsibleGroup overlaysSection(QuestpathConfig config)
	{
		JPanel body = column();
		body.add(toggleGroup("Next on your route", config.overlayNextQuest(), "overlayNextQuest", sub ->
		{
			sizeFields(sub, config.overlayNextQuestWidth(), "overlayNextQuestWidth",
				config.overlayNextQuestLength(), "overlayNextQuestLength");
			sub.add(toggle("List its own items", config.overlayNextQuestItems(), "overlayNextQuestItems"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Get while you're here", config.overlayPickups(), "overlayPickups", sub ->
		{
			sizeFields(sub, config.overlayPickupsWidth(), "overlayPickupsWidth",
				config.overlayPickupsLength(), "overlayPickupsLength");
			addField(sub, "How far ahead", intField(config.pickupsLookahead(), 1, 12, "pickupsLookahead"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Everything you need", config.overlayItems(), "overlayItems", sub ->
		{
			sizeFields(sub, config.overlayItemsWidth(), "overlayItemsWidth",
				config.overlayItemsLength(), "overlayItemsLength");
			sub.add(toggle("Hide items you already hold", config.overlayItemsHideHeld(), "overlayItemsHideHeld"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Shop route: where and why", config.overlayShopRoute(), "overlayShopRoute", sub ->
			sizeFields(sub, config.overlayShopRouteWidth(), "overlayShopRouteWidth",
				config.overlayShopRouteLength(), "overlayShopRouteLength")));
		body.add(spacer());
		body.add(diaryRouteGroup(config));
		body.add(spacer());
		body.add(toggleGroup("Grand Exchange: queued, next, left", config.overlayGrandExchange(),
			"overlayGrandExchange", sub ->
			{
				sizeFields(sub, config.overlayGrandExchangeWidth(), "overlayGrandExchangeWidth",
					config.overlayGrandExchangeLength(), "overlayGrandExchangeLength");
				sub.add(toggle("Recommended items", config.recommendedItems(), "recommendedItems"));
				addField(sub, "Teleports as",
					combo(TeleportForm.values(), config.recommendedTeleportForm(), "recommendedTeleportForm"));
			}));
		body.add(spacer());
		body.add(toggleGroup("Combat XP to next level", config.overlayCombatXp(), "overlayCombatXp", sub ->
			sizeFields(sub, config.overlayCombatXpWidth(), "overlayCombatXpWidth",
				config.overlayCombatXpLength(), "overlayCombatXpLength")));
		body.add(spacer());
		body.add(toggleGroup("Build rules", config.overlayBuildRules(), "overlayBuildRules", sub ->
		{
			sizeFields(sub, config.overlayBuildRulesWidth(), "overlayBuildRulesWidth",
				config.overlayBuildRulesLength(), "overlayBuildRulesLength");
			addField(sub, "Which rules",
				combo(BuildRuleSections.values(), config.overlayBuildRulesSections(), "overlayBuildRulesSections"));
		}));
		return sectionGroup("Overlays", body);
	}

	/**
	 * The diary route overlay's own group. It has no enable checkbox: {@link
	 * QuestpathConfig#questJournalFilter()} is its entire on/off switch, the
	 * same filter that drives the Quests tab's own diary sections.
	 */
	private CollapsibleGroup diaryRouteGroup(QuestpathConfig config)
	{
		JPanel sub = indented();
		addField(sub, "Quests tab shows",
			combo(QuestJournalFilter.values(), config.questJournalFilter(), "questJournalFilter"));
		sizeFields(sub, config.overlayDiaryRouteWidth(), "overlayDiaryRouteWidth",
			config.overlayDiaryRouteLength(), "overlayDiaryRouteLength");
		return sectionGroup("Diary route: nearest task", sub);
	}

	/** The EXTRAS section: the marker groups, voice, lamps, and the flat chat toggles. */
	private CollapsibleGroup extrasSection(QuestpathConfig config)
	{
		JPanel body = column();
		body.add(toggleGroup("Shops", config.shopHighlight(), "shopHighlight", sub ->
		{
			sub.add(toggle("Show how many to buy", config.shopHighlightCounts(), "shopHighlightCounts"));
			addField(sub, "Outline colour",
				colorField(config.shopHighlightColour(), "Shop outline colour", "shopHighlightColour"));
			sub.add(toggle("Say when a shop sells what you need", config.shopArrivalPrompt(), "shopArrivalPrompt"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Ground items", config.groundHighlight(), "groundHighlight", sub ->
		{
			sub.add(toggle("Show how many to pick up", config.groundHighlightCounts(), "groundHighlightCounts"));
			addField(sub, "Marker colour",
				colorField(config.groundHighlightColour(), "Ground marker colour", "groundHighlightColour"));
			sub.add(toggle("Left-click take items you need", config.groundTakeLeftClick(), "groundTakeLeftClick"));
			sub.add(toggle("Bouncing arrow over needed items", config.floorItemArrow(), "floorItemArrow"));
			addField(sub, "Floor-item range (tiles)", intField(config.floorItemRadius(), 1, 15, "floorItemRadius"));
		}));
		body.add(toggleGroup("Drop sources", config.dropHighlight(), "dropHighlight", sub ->
		{
			sub.add(toggle("Show what it drops", config.dropHighlightLabels(), "dropHighlightLabels"));
			addField(sub, "Outline colour",
				colorField(config.dropHighlightColour(), "Drop source colour", "dropHighlightColour"));
			sub.add(toggle("Whole route, not just this quest", config.dropHighlightWholeRoute(),
				"dropHighlightWholeRoute"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Voice", config.voiceEnabled(), "voiceEnabled", sub ->
		{
			addField(sub, "Volume", intField(config.voiceVolume(), 0, 100, "voiceVolume"));
			sub.add(toggle("Say what to buy near a shop", config.voiceShops(), "voiceShops"));
			sub.add(toggle("Say needed items on the floor", config.voiceFloorItems(), "voiceFloorItems"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Bank", config.bankHighlight(), "bankHighlight", sub ->
		{
			sub.add(toggle("Show how many more to find", config.bankHighlightCounts(), "bankHighlightCounts"));
			sub.add(toggle("Only mark what you are short of", config.bankHighlightShortOnly(),
				"bankHighlightShortOnly"));
			sub.add(toggle("Rearrange the bank itself", config.bankTab(), "bankTab"));
			sub.add(toggle("Show the bank title-bar button", config.bankButton(), "bankButton"));
			sub.add(toggle("Show the ordered list too", config.bankList(), "bankList"));
			addField(sub, "Marker colour",
				colorField(config.bankHighlightColour(), "Bank marker colour", "bankHighlightColour"));
		}));
		body.add(spacer());
		body.add(toggleGroup("Experience lamps", config.lampAdvisor(), "lampAdvisor", sub ->
		{
			sub.add(toggle("Grey out skills a rule closes", config.lampGreyBlocked(), "lampGreyBlocked"));
			addField(sub, "Suggest", combo(LampSuggestions.values(), config.lampSuggestions(), "lampSuggestions"));
			addField(sub, "Best target",
				colorField(config.lampBestColour(), "Best lamp target colour", "lampBestColour"));
			addField(sub, "Runner-up",
				colorField(config.lampNextColour(), "Runner-up lamp target colour", "lampNextColour"));
		}));
		body.add(spacer());
		// Flat rather than a group of its own: one checkbox behind a
		// collapsible header is a header that costs more rows than it hides.
		body.add(toggle("Chat when an item is done", config.chatOnItemComplete(), "chatOnItemComplete"));
		body.add(toggle("Float a note where it happened", config.floatingNotes(), "floatingNotes"));
		body.add(toggle("Say when a shop nearby has what you need", config.shopNearbyPrompt(), "shopNearbyPrompt"));
		addField(body, "Float notes",
			combo(NotePlacement.values(), config.floatingNotePlacement(), "floatingNotePlacement"));
		return sectionGroup("Extras", body);
	}

	/** A panel's own width and length overrides, under its toggle. */
	private void sizeFields(JPanel sub, PanelWidth width, String widthKey, PanelListLength length, String lengthKey)
	{
		addField(sub, "Width", combo(PanelWidth.values(), width, widthKey));
		addField(sub, "Length", combo(PanelListLength.values(), length, lengthKey));
	}

	/** A spinner wired straight to a bounded {@code int} config key. */
	private JSpinner intField(int initial, int min, int max, String keyName)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
		spinner.setToolTipText(descriptions.get(keyName));
		spinner.setFont(SMALL);
		spinner.setAlignmentX(LEFT_ALIGNMENT);
		spinner.setMaximumSize(CONTROL_MAX);
		spinner.addChangeListener(e -> write(keyName, spinner.getValue()));
		return spinner;
	}

	/** A stacked label-then-control block: at 225px the two do not fit side by side. */
	private static void addField(JPanel target, String label, JComponent control)
	{
		target.add(small(label, ColorScheme.LIGHT_GRAY_COLOR));
		target.add(spacer(2));
		target.add(control);
		target.add(spacer(6));
	}

	/**
	 * A checkbox wired straight to a boolean config key. Writes happen on
	 * every click, immediately -- there is no Save button, matching RuneLite's
	 * own config panel. A key carrying a {@code warning} (a third-party
	 * server) confirms it first, exactly like RuneLite's own panel, and is
	 * written only if the player agrees; switching it off never asks.
	 */
	private JCheckBox toggle(String label, boolean initial, String keyName)
	{
		String warning = warnings.get(keyName);
		JCheckBox box = check(label, initial, Color.WHITE, e ->
		{
			JCheckBox source = (JCheckBox) e.getSource();
			if (populating)
			{
				return;
			}
			if (source.isSelected() && warning != null && !warning.isEmpty())
			{
				int choice = JOptionPane.showConfirmDialog(this, warning + "\n\nTurn it on anyway?",
					"Quest Route", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (choice != JOptionPane.OK_OPTION)
				{
					// Declined: the box must not stay ticked for a setting
					// that was not actually turned on.
					source.setSelected(false);
					return;
				}
			}
			write(keyName, source.isSelected());
		});
		box.setToolTipText(descriptions.get(keyName));
		box.setMaximumSize(CONTROL_MAX);
		return box;
	}

	/**
	 * A combo wired straight to an enum config key. The enum's own
	 * {@code toString()} is what RuneLite renders in its config panel too.
	 */
	private <E extends Enum<E>> JComboBox<E> combo(E[] values, E initial, String keyName)
	{
		JComboBox<E> box = styled(new JComboBox<>(values), descriptions.get(keyName));
		box.setSelectedItem(initial);
		box.addActionListener(e -> write(keyName, box.getSelectedItem()));
		return box;
	}

	/**
	 * A colour swatch button wired to a {@code Color} config key through
	 * RuneLite's own colour picker. Only the swatch updates live; the write
	 * happens once, when the dialog closes. Alpha is hidden: every colour here
	 * is an opaque outline or marker.
	 */
	private ColorJButton colorField(Color initial, String dialogTitle, String keyName)
	{
		ColorJButton button = new ColorJButton("", initial);
		button.setToolTipText(descriptions.get(keyName));
		button.setFocusPainted(false);
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setMaximumSize(CONTROL_MAX);
		button.addActionListener(e ->
		{
			RuneliteColorPicker picker = colorPickerManager.create(button, button.getColor(), dialogTitle, true);
			picker.setOnColorChange(button::setColor);
			picker.setOnClose(chosen ->
			{
				button.setColor(chosen);
				write(keyName, chosen);
			});
			picker.setVisible(true);
		});
		return button;
	}

	private void write(String keyName, Object value)
	{
		if (!populating)
		{
			configManager.setConfiguration(QuestpathConfig.GROUP, keyName, value);
		}
	}

	/** Disables (or re-enables) a subtree: greyed, not hidden, so nothing jumps. */
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

	private static Component spacer()
	{
		return spacer(10);
	}

	private static Component spacer(int height)
	{
		return Box.createRigidArea(new Dimension(0, height));
	}
}
