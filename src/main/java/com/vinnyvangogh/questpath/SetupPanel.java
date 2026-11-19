package com.vinnyvangogh.questpath;

import java.awt.Component;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import net.runelite.client.ui.ColorScheme;

/**
 * First-run setup, shown on the Quests tab until an account has a profile.
 *
 * <p>Two questions -- what kind of account this is, and which quest types
 * the route should cover -- then one button. Answering is the only way
 * forward, and the choice is saved by the plugin, not here: this class
 * reports it through the callback and knows nothing about files.
 *
 * <p>Changing the answers later is "Reset account setup" in the plugin's
 * config panel, which wipes the profile and brings this back.
 */
final class SetupPanel extends JPanel
{
	SetupPanel(String note, BiConsumer<LocalProfile.AccountType, Set<LocalProfile.QuestType>> onDone)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(label("Set up Quest Route"));
		add(Box.createVerticalStrut(4));
		add(muted(note));
		add(Box.createVerticalStrut(10));

		add(label("Account type"));
		ButtonGroup group = new ButtonGroup();
		Map<LocalProfile.AccountType, JRadioButton> accounts = new EnumMap<>(LocalProfile.AccountType.class);
		for (LocalProfile.AccountType type : LocalProfile.AccountType.values())
		{
			JRadioButton button = new JRadioButton(type.label());
			button.setOpaque(false);
			button.setAlignmentX(Component.LEFT_ALIGNMENT);
			group.add(button);
			accounts.put(type, button);
			add(button);
		}
		accounts.get(LocalProfile.AccountType.MAIN).setSelected(true);

		add(Box.createVerticalStrut(10));
		add(label("Quests to route"));
		Map<LocalProfile.QuestType, JCheckBox> quests = new EnumMap<>(LocalProfile.QuestType.class);
		for (LocalProfile.QuestType type : LocalProfile.QuestType.values())
		{
			JCheckBox box = new JCheckBox(type.label(), true);
			box.setOpaque(false);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			quests.put(type, box);
			add(box);
		}

		add(Box.createVerticalStrut(10));
		JButton done = new JButton("Save and show my route");
		done.setAlignmentX(Component.LEFT_ALIGNMENT);
		done.setMaximumSize(new Dimension(Integer.MAX_VALUE, done.getPreferredSize().height));
		JLabel warning = muted(" ");
		done.addActionListener(event ->
		{
			Set<LocalProfile.QuestType> chosen = EnumSet.noneOf(LocalProfile.QuestType.class);
			quests.forEach((type, box) ->
			{
				if (box.isSelected())
				{
					chosen.add(type);
				}
			});
			if (chosen.isEmpty())
			{
				warning.setText("Pick at least one kind of quest.");
				return;
			}
			LocalProfile.AccountType account = LocalProfile.AccountType.MAIN;
			for (Map.Entry<LocalProfile.AccountType, JRadioButton> entry : accounts.entrySet())
			{
				if (entry.getValue().isSelected())
				{
					account = entry.getKey();
				}
			}
			done.setEnabled(false);
			onDone.accept(account, chosen);
		});
		add(done);
		add(warning);
		add(Box.createVerticalStrut(6));
		add(muted("Change this later with \"Reset account setup\" in Quest Route's settings."));
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
