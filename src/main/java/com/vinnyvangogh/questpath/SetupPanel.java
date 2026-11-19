package com.vinnyvangogh.questpath;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;

/**
 * Account setup: the same route-shaping choices the Quest Route website offers,
 * with the same defaults, answered here so no server is needed.
 *
 * <ul>
 *   <li><b>Account type</b> -- main, ironman, hardcore, ultimate, group, hardcore group.</li>
 *   <li><b>Quests to route</b> -- free-to-play, members (the website's "Free-to-play only" is
 *       members unticked).</li>
 *   <li><b>Build</b> -- one of the named builds (Pure, 1 Defence, Zerker, Void...), or custom.
 *       Choosing one fills the per-skill limits below; the limits stay editable, and are what
 *       is saved.</li>
 *   <li><b>Skill limits</b> -- per skill: no limit, no XP ever, stop at a level, warn only, or a
 *       goal. Quests whose rewards or requirements break a limit are flagged or left off the
 *       route, with the reason.</li>
 * </ul>
 *
 * <p>Shown first run, and again from the sidebar's "Account setup" button with the saved answers
 * filled in. Saving reports through the callback; this class knows nothing about files.
 */
final class SetupPanel extends JPanel
{
	/** The limit choices a skill row offers. */
	enum Limit
	{
		NONE("No limit", null),
		NO_XP("No XP ever", BuildRule.NO_XP),
		HARD_CAP("Stop at level", BuildRule.HARD_CAP),
		SOFT_CAP("Warn only", BuildRule.SOFT_CAP),
		GOAL("Goal level", BuildRule.GOAL);

		private final String label;
		private final String ruleType;

		Limit(String label, String ruleType)
		{
			this.label = label;
			this.ruleType = ruleType;
		}

		String ruleType()
		{
			return ruleType;
		}

		static Limit of(String ruleType)
		{
			for (Limit limit : values())
			{
				if (limit.ruleType != null && limit.ruleType.equals(ruleType))
				{
					return limit;
				}
			}
			return NONE;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private static final String NO_BUILD = "Custom (set limits below)";

	private final Map<LocalProfile.AccountType, JRadioButton> accounts = new EnumMap<>(LocalProfile.AccountType.class);
	private final Map<LocalProfile.QuestType, JCheckBox> quests = new EnumMap<>(LocalProfile.QuestType.class);
	private final Map<String, JComboBox<Limit>> limitKinds = new LinkedHashMap<>();
	private final Map<String, JSpinner> limitLevels = new LinkedHashMap<>();
	private final JComboBox<Object> build = new JComboBox<>();
	private boolean filling;

	SetupPanel(String note, LocalProfile current, List<BuildRule> currentRules,
		BiConsumer<LocalProfile, List<BuildRule>> onDone, Runnable onCancel)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(label("Account setup"));
		add(Box.createVerticalStrut(4));
		add(muted(note));
		add(Box.createVerticalStrut(10));

		add(label("Account type"));
		ButtonGroup group = new ButtonGroup();
		for (LocalProfile.AccountType type : LocalProfile.AccountType.values())
		{
			JRadioButton button = new JRadioButton(type.label());
			button.setOpaque(false);
			button.setAlignmentX(Component.LEFT_ALIGNMENT);
			group.add(button);
			accounts.put(type, button);
			add(button);
		}
		LocalProfile.AccountType account = current != null && current.accountType() != null
			? current.accountType() : LocalProfile.AccountType.MAIN;
		accounts.get(account).setSelected(true);

		add(Box.createVerticalStrut(10));
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

		add(Box.createVerticalStrut(10));
		add(label("Build"));
		build.setAlignmentX(Component.LEFT_ALIGNMENT);
		build.setMaximumSize(new Dimension(Integer.MAX_VALUE, build.getPreferredSize().height));
		build.addItem(NO_BUILD);
		for (BuildPresets.Preset preset : BuildPresets.ALL)
		{
			build.addItem(preset);
		}
		add(build);
		add(Box.createVerticalStrut(4));
		add(muted("Picking a build fills in the limits below. Change any of them and it becomes custom."));

		add(Box.createVerticalStrut(8));
		add(label("Skill limits"));
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			add(limitRow(skill.getName()));
		}

		fill(currentRules == null ? new ArrayList<>() : currentRules);
		BuildPresets.Preset saved = current == null ? null : BuildPresets.find(current.buildPreset());
		if (saved != null && sameAsPreset(saved))
		{
			build.setSelectedItem(saved);
		}
		build.addActionListener(event ->
		{
			Object picked = build.getSelectedItem();
			if (!filling && picked instanceof BuildPresets.Preset)
			{
				fill(((BuildPresets.Preset) picked).rules());
			}
		});

		add(Box.createVerticalStrut(10));
		JButton done = new JButton("Save and show my route");
		done.setAlignmentX(Component.LEFT_ALIGNMENT);
		done.setMaximumSize(new Dimension(Integer.MAX_VALUE, done.getPreferredSize().height));
		JLabel warning = muted(" ");
		done.addActionListener(event ->
		{
			Set<LocalProfile.QuestType> types = chosenQuestTypes();
			if (types.isEmpty())
			{
				warning.setText("Pick at least one kind of quest.");
				return;
			}
			done.setEnabled(false);
			List<BuildRule> rules = collectRules();
			BuildPresets.Preset preset = BuildPresets.identify(rules);
			onDone.accept(LocalProfile.create(chosenAccount(), types, preset == null ? null : preset.slug()), rules);
		});
		add(done);
		if (onCancel != null)
		{
			JButton cancel = new JButton("Cancel");
			cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
			cancel.setMaximumSize(new Dimension(Integer.MAX_VALUE, cancel.getPreferredSize().height));
			cancel.addActionListener(event -> onCancel.run());
			add(Box.createVerticalStrut(4));
			add(cancel);
		}
		add(warning);
	}

	private JPanel limitRow(String skillName)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel name = new JLabel(skillName);
		name.setPreferredSize(new Dimension(62, name.getPreferredSize().height));
		JComboBox<Limit> kind = new JComboBox<>(Limit.values());
		JSpinner level = new JSpinner(new SpinnerNumberModel(99, 1, 99, 1));
		kind.addActionListener(event -> level.setEnabled(BuildRuleCodec.takesValue(
			((Limit) kind.getSelectedItem()).ruleType())));
		kind.addActionListener(event -> markCustom());
		level.addChangeListener(event -> markCustom());
		level.setEnabled(false);
		row.add(name);
		row.add(kind);
		row.add(level);
		limitKinds.put(skillName, kind);
		limitLevels.put(skillName, level);
		return row;
	}

	/** An edit after a build was picked makes the choice custom again, unless it still matches. */
	private void markCustom()
	{
		if (filling)
		{
			return;
		}
		Object picked = build.getSelectedItem();
		if (picked instanceof BuildPresets.Preset && !sameAsPreset((BuildPresets.Preset) picked))
		{
			filling = true;
			build.setSelectedItem(NO_BUILD);
			filling = false;
		}
	}

	private boolean sameAsPreset(BuildPresets.Preset preset)
	{
		BuildPresets.Preset now = BuildPresets.identify(collectRules());
		return now != null && now.slug().equals(preset.slug());
	}

	/** Sets every row from these rules; a skill with no rule goes back to no limit. */
	void fill(List<BuildRule> rules)
	{
		boolean was = filling;
		filling = true;
		for (Map.Entry<String, JComboBox<Limit>> entry : limitKinds.entrySet())
		{
			entry.getValue().setSelectedItem(Limit.NONE);
			limitLevels.get(entry.getKey()).setValue(99);
		}
		for (BuildRule rule : rules)
		{
			String key = matchingSkill(rule.skillName());
			if (key == null)
			{
				continue;
			}
			limitKinds.get(key).setSelectedItem(Limit.of(rule.type()));
			if (rule.hasValue())
			{
				limitLevels.get(key).setValue(Math.min(99, rule.value()));
			}
		}
		filling = was;
	}

	private String matchingSkill(String name)
	{
		for (String key : limitKinds.keySet())
		{
			if (key.equalsIgnoreCase(name))
			{
				return key;
			}
		}
		return null;
	}

	/** The rules the rows now describe; a row on "No limit" writes nothing. */
	List<BuildRule> collectRules()
	{
		List<BuildRule> rules = new ArrayList<>();
		for (Map.Entry<String, JComboBox<Limit>> entry : limitKinds.entrySet())
		{
			Limit limit = (Limit) entry.getValue().getSelectedItem();
			if (limit == null || limit.ruleType() == null)
			{
				continue;
			}
			int level = (Integer) limitLevels.get(entry.getKey()).getValue();
			rules.add(BuildRule.local(entry.getKey(), limit.ruleType(),
				BuildRuleCodec.takesValue(limit.ruleType()) ? level : 0, ""));
		}
		return rules;
	}

	private LocalProfile.AccountType chosenAccount()
	{
		for (Map.Entry<LocalProfile.AccountType, JRadioButton> entry : accounts.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				return entry.getKey();
			}
		}
		return LocalProfile.AccountType.MAIN;
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
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel muted(String text)
	{
		JLabel label = new JLabel("<html><body style='width: 190px'>" + text + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		return label;
	}
}
