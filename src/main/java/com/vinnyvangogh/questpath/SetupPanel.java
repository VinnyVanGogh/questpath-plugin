package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import static com.vinnyvangogh.questpath.Ui.*;

/**
 * Account setup: which quests to route, whether the account is an ironman,
 * and what to protect.
 *
 * <p>Build rules belong to Build Guard. With it installed, the build step is a
 * button that opens Build Guard's panel. Without it, three read-only starter
 * builds ({@link BuildPresets}) stand in -- plus "Your saved build" for anyone
 * who wrote rules here before Build Guard existed -- and an expandable section
 * says what Build Guard adds and how to install it.
 *
 * <p>Shown on first run, and again from the sidebar's "Account setup" button
 * with the saved answers filled in. Saving reports through the callback; this
 * class knows nothing about files.
 */
final class SetupPanel extends JPanel
{
	/** Shown in the "Get more" section, and tested so the wording cannot quietly lose a point. */
	static final String[] BUILD_GUARD_BENEFITS = {
		"Custom caps for every skill, not just three starter builds",
		"Warnings before you break a rule",
		"Lamp advice: which skill is safe to rub a lamp on",
		"Works with other plugins, Quest Route included",
	};

	static final String BUILD_GUARD_INSTALL =
		"To install: open the Plugin Hub (the plug icon in RuneLite's side bar),"
			+ " search for \"Build Guard\" and press Install.";

	private final Map<LocalProfile.QuestType, JCheckBox> quests = new EnumMap<>(LocalProfile.QuestType.class);
	private final Map<String, JRadioButton> builds = new LinkedHashMap<>();
	private final JCheckBox ironman;
	private final boolean ironmanAtStart;
	private final LocalProfile current;
	private final boolean buildGuardPresent;

	SetupPanel(String note, LocalProfile current, List<BuildRule> savedRules, boolean buildGuardPresent,
		Runnable onOpenBuildGuard, Consumer<LocalProfile> onDone, Runnable onCancel)
	{
		this.current = current;
		this.buildGuardPresent = buildGuardPresent;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(label("Account setup"));
		add(gap(4));
		add(muted(note));
		add(gap(10));

		add(label("Account type"));
		ironmanAtStart = current != null && current.accountType() != null
			&& current.accountType() != LocalProfile.AccountType.MAIN;
		ironman = new JCheckBox("Ironman", ironmanAtStart);
		ironman.setOpaque(false);
		ironman.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(ironman);

		add(gap(10));
		add(label("Quests to route"));
		Set<LocalProfile.QuestType> chosenTypes = current != null && !current.questTypes().isEmpty()
			? current.questTypes() : EnumSet.allOf(LocalProfile.QuestType.class);
		for (LocalProfile.QuestType type : LocalProfile.QuestType.values())
		{
			JCheckBox box = new JCheckBox(type.label(), chosenTypes.contains(type));
			box.setOpaque(false);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			quests.put(type, box);
			add(box);
		}

		add(gap(10));
		add(label("Build"));
		if (buildGuardPresent)
		{
			add(muted("Your build rules live in Build Guard."));
			add(gap(4));
			add(wide(button("Open Build Guard", event -> onOpenBuildGuard.run())));
		}
		else
		{
			addStarterBuilds(savedRules);
			add(gap(8));
			add(getMoreSection());
		}

		add(gap(10));
		JLabel warning = muted(" ");
		JButton done = wide(button("Save and show my route", null));
		done.addActionListener(event ->
		{
			Set<LocalProfile.QuestType> types = chosenQuestTypes();
			if (types.isEmpty())
			{
				warning.setText("Pick at least one kind of quest.");
				return;
			}
			done.setEnabled(false);
			onDone.accept(LocalProfile.create(chosenAccount(), types, chosenBuild()));
		});
		add(done);
		if (onCancel != null)
		{
			add(gap(4));
			add(wide(button("Cancel", event -> onCancel.run())));
		}
		add(warning);
	}

	private void addStarterBuilds(List<BuildRule> savedRules)
	{
		boolean hasSaved = savedRules != null && !savedRules.isEmpty();
		String slug = current == null ? null : current.buildPreset();
		String selected = BuildPresets.find(slug) != null ? slug
			: hasSaved ? BuildPresets.SAVED : BuildPresets.MAXED.slug();

		ButtonGroup group = new ButtonGroup();
		for (BuildPresets.Preset preset : BuildPresets.ALL)
		{
			addBuildOption(group, preset.slug(), preset.label() + describe(preset.rules()), selected);
		}
		if (hasSaved)
		{
			addBuildOption(group, BuildPresets.SAVED, "Your saved build" + describe(savedRules), selected);
		}
		add(gap(4));
		add(muted("Read-only: these are starting points. Build Guard lets you change the limits."));
	}

	private void addBuildOption(ButtonGroup group, String slug, String text, String selected)
	{
		JRadioButton button = new JRadioButton("<html>" + text + "</html>", slug.equals(selected));
		button.setOpaque(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		group.add(button);
		builds.put(slug, button);
		add(button);
	}

	/** " (Attack 60, Defence 45)" -- what the build holds back; empty for a build with no limits. */
	private static String describe(List<BuildRule> rules)
	{
		if (rules.isEmpty())
		{
			return " (no limits)";
		}
		List<String> parts = new ArrayList<>();
		for (BuildRule rule : BuildRule.ordered(rules))
		{
			parts.add(rule.skillName() + ": " + rule.describe());
		}
		return " (" + String.join(", ", parts) + ")";
	}

	/** The expandable "Get more with Build Guard", folded to start with like the other guides. */
	private static JPanel getMoreSection()
	{
		JLabel header = tinted("Get more with Build Guard", Color.WHITE);
		header.setFont(BOLD);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 0));
		body.add(muted("Build Guard is a separate plugin that looks after your build:"));
		for (String benefit : BUILD_GUARD_BENEFITS)
		{
			body.add(muted("• " + benefit));
		}
		body.add(gap(4));
		body.add(muted(BUILD_GUARD_INSTALL));

		CollapsibleGroup group = new CollapsibleGroup(header, body, false);
		group.alsoToggleOn(header);
		group.setAlignmentX(Component.LEFT_ALIGNMENT);
		return group;
	}

	private static JButton wide(JButton button)
	{
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
		return button;
	}

	/**
	 * The build to store. With Build Guard installed the profile keeps whatever
	 * it had: the rules are Build Guard's, and clearing the slug here would
	 * bring the old build back the day Build Guard is uninstalled.
	 */
	private String chosenBuild()
	{
		if (buildGuardPresent)
		{
			return current == null ? null : current.buildPreset();
		}
		for (Map.Entry<String, JRadioButton> entry : builds.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				return entry.getKey();
			}
		}
		return BuildPresets.MAXED.slug();
	}

	/** Unchanged toggle keeps the saved type, so a hardcore or group account is not flattened. */
	private LocalProfile.AccountType chosenAccount()
	{
		LocalProfile.AccountType saved = current == null || current.accountType() == null
			? LocalProfile.AccountType.MAIN : current.accountType();
		if (ironman.isSelected() == ironmanAtStart)
		{
			return saved;
		}
		return ironman.isSelected() ? LocalProfile.AccountType.IRONMAN : LocalProfile.AccountType.MAIN;
	}

	private Set<LocalProfile.QuestType> chosenQuestTypes()
	{
		Set<LocalProfile.QuestType> chosen = EnumSet.noneOf(LocalProfile.QuestType.class);
		quests.forEach((type, box) ->
		{
			if (box.isSelected())
			{
				chosen.add(type);
			}
		});
		return chosen;
	}

	private static JLabel label(String text)
	{
		return tinted(text, null);
	}
}
