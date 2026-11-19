package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import org.junit.Test;

public class SetupPanelTest
{
	private static List<Object[]> saved = new ArrayList<>();

	private static SetupPanel panel(LocalProfile current, List<BuildRule> rules, Runnable cancel)
	{
		saved.clear();
		return new SetupPanel("note", current, rules,
			(profile, built) -> saved.add(new Object[]{profile, built}), cancel);
	}

	private static JButton button(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && text.equals(((JButton) c).getText()))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton inner = button((Container) c, text);
				if (inner != null)
				{
					return inner;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static JComboBox<Object> buildCombo(Container root)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JComboBox && ((JComboBox<?>) c).getItemAt(0) instanceof String)
			{
				return (JComboBox<Object>) c;
			}
		}
		return null;
	}

	@Test
	public void defaultsMatchTheWebsite()
	{
		SetupPanel panel = panel(null, Collections.emptyList(), null);
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = (LocalProfile) saved.get(0)[0];
		assertEquals(LocalProfile.AccountType.MAIN, profile.accountType());
		assertEquals(2, profile.questTypes().size());
		assertTrue(((List<?>) saved.get(0)[1]).isEmpty());
		assertEquals(null, profile.buildPreset());
	}

	@Test
	public void pickingABuildFillsTheLimitsAndSavesItsSlug()
	{
		SetupPanel panel = panel(null, Collections.emptyList(), null);
		JComboBox<Object> combo = buildCombo(panel);
		combo.setSelectedItem(BuildPresets.find("members_obsidian_mauler_1def"));
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = (LocalProfile) saved.get(0)[0];
		assertEquals("members_obsidian_mauler_1def", profile.buildPreset());
		@SuppressWarnings("unchecked")
		List<BuildRule> rules = (List<BuildRule>) saved.get(0)[1];
		assertEquals(4, rules.size());
	}

	@Test
	public void editingAfterPickingABuildMakesItCustom()
	{
		SetupPanel panel = panel(null, Collections.emptyList(), null);
		JComboBox<Object> combo = buildCombo(panel);
		combo.setSelectedItem(BuildPresets.find("members_void_pure"));
		List<BuildRule> edited = BuildRuleCodec.set(panel.collectRules(), "Attack", BuildRule.HARD_CAP, 30, "");
		panel.fill(edited);
		button(panel, "Save and show my route").doClick();
		assertEquals(null, ((LocalProfile) saved.get(0)[0]).buildPreset());
	}

	@Test
	public void savedLimitsAndAccountComeBack()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.HCGIM,
			java.util.EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY), null);
		List<BuildRule> rules = Collections.singletonList(
			BuildRule.local("Defence", BuildRule.HARD_CAP, 13, ""));
		SetupPanel panel = panel(current, rules, () ->
		{
		});
		assertNotNull(button(panel, "Cancel"));
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = (LocalProfile) saved.get(0)[0];
		assertEquals(LocalProfile.AccountType.HCGIM, profile.accountType());
		assertEquals(1, profile.questTypes().size());
		@SuppressWarnings("unchecked")
		List<BuildRule> out = (List<BuildRule>) saved.get(0)[1];
		assertEquals(1, out.size());
		assertEquals(13, out.get(0).value());
	}

	@Test
	public void cancelSavesNothing()
	{
		boolean[] cancelled = {false};
		SetupPanel panel = panel(LocalProfile.create(LocalProfile.AccountType.MAIN,
			java.util.EnumSet.allOf(LocalProfile.QuestType.class)), Collections.emptyList(), () -> cancelled[0] = true);
		button(panel, "Cancel").doClick();
		assertTrue(cancelled[0]);
		assertTrue(saved.isEmpty());
	}

	@Test
	public void firstRunHasNoCancel()
	{
		assertEquals(null, button(panel(null, Collections.emptyList(), null), "Cancel"));
	}

	@Test
	public void noQuestTypeIsRefusedNotSaved()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.MAIN,
			java.util.EnumSet.of(LocalProfile.QuestType.MEMBERS));
		SetupPanel panel = panel(current, Collections.emptyList(), null);
		for (Component c : panel.getComponents())
		{
			if (c instanceof javax.swing.JCheckBox)
			{
				((javax.swing.JCheckBox) c).setSelected(false);
			}
		}
		button(panel, "Save and show my route").doClick();
		assertTrue(saved.isEmpty());
	}
}
