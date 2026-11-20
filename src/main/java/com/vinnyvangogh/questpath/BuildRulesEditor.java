package com.vinnyvangogh.questpath;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import static com.vinnyvangogh.questpath.Ui.*;

/**
 * Writing build rules in the plugin, with no questpath instance involved.
 *
 * <p><b>Why the plugin needs its own editor.</b> Until now a rule could only
 * arrive from a push: held in a volatile field, cleared on logout, gone
 * entirely for anyone who has never run the web app. That made the guard
 * absent rather than reduced, and absent silently — the lamp interface came up
 * with nothing greyed, which reads as "there is nothing here to worry about"
 * rather than as "questpath has not been told what to worry about". For an
 * account whose whole premise is a skill it must never train, that is the one
 * failure mode worth building a Swing form to avoid.
 *
 * <p>Rules written here are stored per RuneScape account by
 * {@link LocalBuildRules}, and merged over anything questpath pushes, with the
 * local one winning a conflict — see {@link BuildRuleCodec#merge} for why that
 * direction and not the other.
 *
 * <p>Deliberately plain: one skill, one rule type, one level. No presets, no
 * import, no "copy from questpath". A build is about six rules, entered once,
 * and every piece of machinery added here is machinery that can disagree with
 * the web app about what a rule means.
 */
class BuildRulesEditor extends JPanel
{
	/**
	 * The rule types, in the order the web app's own editor offers them:
	 * what ends a build first, then what merely informs.
	 */
	private static final String[] TYPES =
		{BuildRule.NO_XP, BuildRule.HARD_CAP, BuildRule.SOFT_CAP, BuildRule.GOAL};

	private final JComboBox<String> skillBox = new JComboBox<>();
	private final JComboBox<String> typeBox = new JComboBox<>(TYPES);
	private final JSpinner levelField = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
	private final JLabel status = tinted("", ColorScheme.LIGHT_GRAY_COLOR);

	/**
	 * @param onChange run after any edit, so the caller can rebuild whatever
	 *     it renders from the new rules. Passed the rules as they now stand,
	 *     so nothing has to read them back and risk reading a stale set.
	 */
	BuildRulesEditor(LocalBuildRules store, Consumer<List<BuildRule>> onChange)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		for (Skill skill : Skill.values())
		{
			skillBox.addItem(skill.getName());
		}

		typeBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focused)
			{
				return super.getListCellRendererComponent(list, label(String.valueOf(value)), index, selected, focused);
			}
		});

		// The level field only means anything for the two types that carry
		// one. Shown disabled rather than hidden, so the form does not change
		// height as the type changes -- a control that appears and disappears
		// under the cursor is how a mis-click happens.
		typeBox.addActionListener(e -> syncLevelEnabled());
		syncLevelEnabled();

		add(field("Skill", skillBox));
		add(field("Rule", typeBox));
		add(field("Level", levelField));
		add(gap(4));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton save = button("Save rule", e ->
		{
			String skill = String.valueOf(skillBox.getSelectedItem());
			String type = String.valueOf(typeBox.getSelectedItem());
			List<BuildRule> now = store.set(skill, type, (Integer) levelField.getValue(), "");
			report(now, "Saved " + skill + ": " + label(type));
			onChange.accept(now);
		});
		save.setToolTipText("Store this rule for this account. Replaces any rule already on"
			+ " the same skill -- two rules on one skill is an ambiguity, not a richer"
			+ " statement of intent.");
		JButton clear = button("Remove", e ->
		{
			String skill = String.valueOf(skillBox.getSelectedItem());
			List<BuildRule> now = store.clear(skill);
			report(now, "Removed " + skill);
			onChange.accept(now);
		});
		clear.setToolTipText("Remove this account's own rule on the selected skill. A rule"
			+ " Quest Route pushed is untouched -- there is one editor per origin.");
		buttons.add(save);
		buttons.add(clear);
		add(buttons);
		add(gap(4));
		status.setFont(SMALL);
		add(status);

		report(store.all(), "");
	}

	private void syncLevelEnabled()
	{
		levelField.setEnabled(BuildRuleCodec.takesValue(String.valueOf(typeBox.getSelectedItem())));
	}

	/**
	 * Says what just happened and how many rules this account now has.
	 *
	 * <p>The count matters more than it looks: the whole failure this editor
	 * exists to fix is a guard that is silently off, so the panel has to be
	 * able to say "you have four rules" rather than leaving the player to
	 * infer it from an interface that happens to have nothing greyed.
	 */
	private void report(List<BuildRule> rules, String action)
	{
		int count = rules == null ? 0 : rules.size();
		String summary = count == 0
			? "No rules of your own yet"
			: count + (count == 1 ? " rule of your own" : " rules of your own");
		status.setText(action.isEmpty() ? summary : action + " -- " + summary);
	}

	private static JPanel field(String label, Component control)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		JLabel name = small(label, ColorScheme.LIGHT_GRAY_COLOR);
		name.setPreferredSize(new Dimension(48, 0));
		row.add(name, BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return row;
	}

	/**
	 * The human name for a rule type.
	 *
	 * <p>Matches questpath's own editor word for word ({@code
	 * domain.RuleType.Label}). Two names for one concept across two editors
	 * is how a player comes to believe "Warn only" and "Soft cap" are
	 * different settings.
	 */
	static String label(String type)
	{
		switch (type)
		{
			case BuildRule.NO_XP:
				return "No XP ever";
			case BuildRule.HARD_CAP:
				return "Stop at level";
			case BuildRule.SOFT_CAP:
				return "Warn only";
			case BuildRule.GOAL:
				return "Goal";
			default:
				return type;
		}
	}
}
