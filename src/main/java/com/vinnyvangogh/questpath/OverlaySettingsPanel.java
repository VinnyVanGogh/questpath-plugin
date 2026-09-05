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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
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
 * <p>The inner level is the ten feature groups -- six overlays under
 * OVERLAYS, Shops, Ground items, Bank and "Open Quest Helper on arrival"
 * under EXTRAS, plus Shared's own controls (which have no further nesting;
 * Shared has no per-feature toggles to group). Every one of those ten is
 * headed by its own enable checkbox
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

		add(syncSection(config));
		add(spacer());
		add(sharedGroup(config));
		add(spacer());
		add(overlaysSection(config));
		add(spacer());
		add(extrasSection(config));

		populating = false;
	}

	/**
	 * The SYNC top-level section: the one setting that leaves the client at
	 * all -- see this file's own class doc's second paragraph, and {@link
	 * QuestpathConfig}'s.
	 *
	 * <p>Only {@code syncEnabled} lives here. {@code serverUrl} and {@code
	 * pairingToken} are the two settings this file deliberately never
	 * duplicates -- a URL and a secret token are exactly the free-text and
	 * masked-field controls RuneLite's own config panel already renders
	 * correctly, and {@link OverlaySettingsPanelConfigParityTest} enforces
	 * that exemption by name rather than by a filter nobody has to notice
	 * growing. {@code syncEnabled} itself does not get the same pass: it is
	 * a plain on/off switch, exactly the shape every other toggle in this
	 * panel already is, and it was the one a player would look here for
	 * first -- "am I sending my data anywhere" -- and not find, because it
	 * used to live only behind RuneLite's own gear icon.
	 */
	private CollapsibleGroup syncSection(QuestpathConfig config)
	{
		JPanel panel = column();
		panel.add(warningToggle(
			"Send my account state",
			"Push quest completion, levels and diaries to your questpath instance when they"
				+ " change. questpath then stops polling WikiSync for this account.",
			"This feature submits your IP address to a 3rd-party server not controlled or"
				+ " verified by RuneLite developers.\n\nTurn it on anyway?",
			config.syncEnabled(), "syncEnabled"));
		panel.add(Box.createRigidArea(new Dimension(0, 6)));
		JLabel note = new JLabel(wrap("The address to push to, and the pairing token from"
			+ " questpath -plugin-token, are set in RuneLite's own Configuration panel for this"
			+ " plugin -- not duplicated here."));
		note.setFont(FontManager.getRunescapeSmallFont());
		note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		note.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(note);
		return sectionGroup("Sync", panel);
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
	 * A group headed by its own enable checkbox: one of the six overlays,
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
				addField(sub, "How far ahead",
					intField(config.pickupsLookahead(), 1, 12,
						"How many quests down the route count as \"here\": the one in hand plus the"
							+ " next few. Higher catches more detours worth making; lower keeps the"
							+ " list about where you actually are.",
						"pickupsLookahead"));
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
			"Shop route: where and why",
			"While a shop route sent to Shortest Path is active, the next few stops:"
				+ " the item, how many short you are, the shop it sends you to, and which"
				+ " quest wants it. Replaces \"Everything you need\" for as long as the"
				+ " route is active.",
			config.overlayShopRoute(), "overlayShopRoute",
			sub ->
			{
				addField(sub, "Width", widthCombo(config.overlayShopRouteWidth(), "overlayShopRouteWidth"));
				addField(sub, "Length", lengthCombo(config.overlayShopRouteLength(), "overlayShopRouteLength"));
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
	 * The EXTRAS top-level section: the marker groups -- Shops, Ground items,
	 * Bank -- plus the flat Quest Helper automation toggles and the item-done
	 * chat line, nested under one shared heading.
	 */
	private CollapsibleGroup extrasSection(QuestpathConfig config)
	{
		JPanel body = column();
		body.add(shopsGroup(config));
		body.add(spacer());
		body.add(groundItemsGroup(config));
		body.add(spacer());
		body.add(bankGroup(config));
		body.add(spacer());
		// Flat: nothing gates autoAdvanceQuestHelper, it is just one
		// independent yes/no automation around Quest Helper's own
		// start-quest flow, previously only reachable from RuneLite's own
		// config panel, which is exactly the gap this file exists to close
		// (see this file's own class doc).
		body.add(toggle(
			"Auto-start the next quest",
			"When the quest you just finished was the current step on your saved route,"
				+ " automatically start the next one in Quest Helper -- only if Quest Helper"
				+ " reports it available. Always says what it did (or why it could not) in the"
				+ " chat, and never retries on its own.",
			config.autoAdvanceQuestHelper(), "autoAdvanceQuestHelper"));
		body.add(spacer());
		// A toggleGroup, not a flat toggle, because questStartArrivalDepth
		// only means anything while this is on -- the same dependent-setting
		// shape as Shops/Ground items/Bank above, just with a spinner instead
		// of a colour.
		body.add(toggleGroup(
			"Open Quest Helper on arrival",
			"When you walk within a few tiles of the start of a quest you could begin, open it"
				+ " in Quest Helper automatically -- only if Quest Helper reports it available."
				+ " Says what it did in the chat, once per arrival, never on login or a resync.",
			config.questHelperArrivalPrompt(), "questHelperArrivalPrompt",
			sub -> addField(sub, "How far down the route",
				intField(config.questStartArrivalDepth(), 0, 218,
					"Only the next N outstanding quests on your saved route can ever prompt --"
						+ " 1 is just the next one, 3 is the next few. 0 means no limit, every"
						+ " outstanding route quest. Quests that are startable but not on your"
						+ " saved route are never affected by this number, at any depth.",
					"questStartArrivalDepth"))));
		body.add(spacer());
		// Flat rather than a group of its own: one checkbox behind a
		// collapsible header is a header that costs more rows than it hides.
		body.add(toggle(
			"Chat when an item is done",
			"Print a line in the game chat the moment you have all of something your plan"
				+ " needs, naming the quest it is for. Said once, at the moment it happens,"
				+ " because that is when you are looking at the ground rather than at a panel.",
			config.chatOnItemComplete(), "chatOnItemComplete"));
		body.add(toggle(
			"Float a note where it happened",
			"Show a short line in the world, above where you are standing, the moment you"
				+ " finish gathering something. Fades after a few seconds, never more than"
				+ " three at once.",
			config.floatingNotes(), "floatingNotes"));
		return sectionGroup("Extras", body);
	}

	/**
	 * Bank marking, the same dependent-trio shape as Shops and Ground items:
	 * the count, the colour and the list all mean nothing with the highlight
	 * itself off.
	 *
	 * <p>The in-bank button is the day-to-day control and this group is not a
	 * substitute for it -- switching the feature off here removes the button
	 * too, which is the honest behaviour but not the one to reach for when
	 * you only want the marks gone for one trip.
	 */
	private CollapsibleGroup bankGroup(QuestpathConfig config)
	{
		return toggleGroup(
			"Bank",
			"Mark what you need in the bank: while the bank is open, outline the items your"
				+ " plan still needs and show how many more to find. Toggled per trip from"
				+ " the Quest Path button in the bank's title bar.",
			config.bankHighlight(), "bankHighlight",
			sub ->
			{
				sub.add(toggle(
					"Show how many more to find",
					"Print the outstanding count above a marked slot.",
					config.bankHighlightCounts(), "bankHighlightCounts"));
				sub.add(toggle(
					"Only mark what you are short of",
					"Leave items you already have enough of unmarked. Off, they are outlined"
						+ " in green so you can see the plan is covered without counting.",
					config.bankHighlightShortOnly(), "bankHighlightShortOnly"));
				sub.add(toggle(
					"Rearrange the bank itself",
					"Group the bank's own grid by what your plan needs. Items you own stay"
						+ " withdrawable; ones you own none of are shown greyed, which is the"
						+ " one thing outlining a slot can never tell you.",
					config.bankTab(), "bankTab"));
				sub.add(toggle(
					"Show the ordered list too",
					"Alongside the marks, list what the plan needs beside the bank: what you"
						+ " ran out of first, then what you are short of, then what you have.",
					config.bankList(), "bankList"));
				addField(sub, "Marker colour",
					colorField(config.bankHighlightColour(),
						"The colour an item you still need is outlined in.",
						"Bank marker colour", "bankHighlightColour"));
			});
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
				sub.add(toggle(
					"Say when a shop sells what you need",
					"One chat line the moment you open a shop selling something your plan still"
						+ " needs. Not distance-based -- this only notices a shop once its interface"
						+ " is open -- and it says so once per visit, not every tick.",
					config.shopArrivalPrompt(), "shopArrivalPrompt"));
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
	 * A spinner wired straight to a bounded {@code int} config key.
	 *
	 * <p>Shared by the two numeric settings in this panel -- {@code
	 * pickupsLookahead} and {@code questStartArrivalDepth} -- so this exists
	 * as its own method rather than folded into {@link #toggle}/{@link
	 * #combo} the way those two are shared by a dozen keys each.
	 */
	private JSpinner intField(int initial, int min, int max, String tooltip, String keyName)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
		spinner.setToolTipText(tooltip);
		spinner.setFont(FontManager.getRunescapeSmallFont());
		spinner.setAlignmentX(LEFT_ALIGNMENT);
		spinner.setMaximumSize(CONTROL_MAX);
		spinner.addChangeListener(e ->
		{
			if (populating)
			{
				return;
			}
			configManager.setConfiguration(QuestpathConfig.GROUP, keyName, (Integer) spinner.getValue());
		});
		return spinner;
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
	 * A checkbox wired to a boolean config key that submits data to a
	 * third-party server, gated the same way RuneLite's own config panel
	 * gates any {@code @ConfigItem} carrying a {@code warning}: switching it
	 * on shows that exact warning as a confirm dialog first, and the config
	 * is written only if the player confirms. Switching it back off never
	 * asks -- there is nothing to warn about in stopping a push.
	 *
	 * <p>{@code syncEnabled} is the only key this panel uses this for today.
	 * If a future key needs it too, keep the warning text passed in here in
	 * sync with that key's own {@code @ConfigItem(warning = ...)} by hand --
	 * the annotation value is not read reflectively, so RuneLite's own
	 * config panel and this dialog can drift if one changes without the
	 * other.
	 */
	private JCheckBox warningToggle(String label, String tooltip, String warning, boolean initial, String keyName)
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
			if (box.isSelected())
			{
				int choice = JOptionPane.showConfirmDialog(this, warning, "Quest Path",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (choice != JOptionPane.OK_OPTION)
				{
					// Declined: the checkbox must not visibly stay ticked for
					// a setting that was not actually turned on.
					box.setSelected(false);
					return;
				}
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

	/**
	 * Forces a plain label to wrap at the sidebar's own width, matching
	 * {@code QuestpathPanel}'s own {@code wrap}: Swing only wraps a label's
	 * HTML at a width the markup states explicitly, never at whatever the
	 * container happens to be.
	 */
	private static String wrap(String text)
	{
		return "<html><body style='width:150px'>" + escape(text) + "</body></html>";
	}

	/** A stray angle bracket in one of this file's own strings would otherwise be read as markup. */
	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
