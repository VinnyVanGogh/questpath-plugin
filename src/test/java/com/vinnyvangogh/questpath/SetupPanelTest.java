package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import org.junit.Test;

public class SetupPanelTest
{
	private static final List<LocalProfile> saved = new ArrayList<>();
	private static boolean opened;

	private static SetupPanel panel(LocalProfile current, List<BuildRule> savedRules, boolean buildGuard,
		Runnable cancel)
	{
		saved.clear();
		opened = false;
		return new SetupPanel("note", current, savedRules, buildGuard, () -> opened = true, saved::add, cancel);
	}

	private static SetupPanel panel(LocalProfile current)
	{
		return panel(current, Collections.emptyList(), false, null);
	}

	private static <T extends Component> List<T> all(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				out.addAll(all((Container) c, type));
			}
		}
		return out;
	}

	private static JButton button(Container root, String text)
	{
		for (JButton b : all(root, JButton.class))
		{
			if (text.equals(b.getText()))
			{
				return b;
			}
		}
		return null;
	}

	private static JRadioButton radio(Container root, String startsWith)
	{
		for (JRadioButton r : all(root, JRadioButton.class))
		{
			if (r.getText().startsWith("<html>" + startsWith))
			{
				return r;
			}
		}
		return null;
	}

	private static JCheckBox check(Container root, String text)
	{
		for (JCheckBox c : all(root, JCheckBox.class))
		{
			if (text.equals(c.getText()))
			{
				return c;
			}
		}
		return null;
	}

	private static boolean mentions(Container root, String fragment)
	{
		for (JLabel l : all(root, JLabel.class))
		{
			if (l.getText() != null && l.getText().contains(fragment))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void withoutBuildGuardTheThreeStartersAreOffered()
	{
		SetupPanel panel = panel(null);
		assertEquals(3, all(panel, JRadioButton.class).size());
		assertNotNull(radio(panel, "60 Attack zerker"));
		assertNotNull(radio(panel, "60 Attack pure"));
		assertNotNull(radio(panel, "Maxed main"));
		assertNull("no editor here", button(panel, "Open Build Guard"));
	}

	@Test
	public void maxedMainIsTheDefaultAndAMainIsTheDefaultAccount()
	{
		SetupPanel panel = panel(null);
		assertTrue(radio(panel, "Maxed main").isSelected());
		assertFalse(check(panel, "Ironman").isSelected());
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = saved.get(0);
		assertEquals(BuildPresets.MAXED.slug(), profile.buildPreset());
		assertEquals(LocalProfile.AccountType.MAIN, profile.accountType());
		assertEquals(2, profile.questTypes().size());
	}

	@Test
	public void pickingAStarterSavesItsSlugAndTheIronmanToggleSetsTheAccount()
	{
		SetupPanel panel = panel(null);
		radio(panel, "60 Attack pure").setSelected(true);
		check(panel, "Ironman").setSelected(true);
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = saved.get(0);
		assertEquals(BuildPresets.PURE.slug(), profile.buildPreset());
		assertEquals(LocalProfile.AccountType.IRONMAN, profile.accountType());
	}

	@Test
	public void anUntouchedToggleKeepsAHardcoreGroupAccount()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.HCGIM,
			EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY), BuildPresets.ZERKER.slug());
		SetupPanel panel = panel(current, Collections.emptyList(), false, () ->
		{
		});
		assertTrue(check(panel, "Ironman").isSelected());
		assertTrue(radio(panel, "60 Attack zerker").isSelected());
		assertNotNull(button(panel, "Cancel"));
		button(panel, "Save and show my route").doClick();
		LocalProfile profile = saved.get(0);
		assertEquals(LocalProfile.AccountType.HCGIM, profile.accountType());
		assertEquals(1, profile.questTypes().size());
	}

	@Test
	public void turningIronmanOffGoesToMain()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.UIM,
			EnumSet.allOf(LocalProfile.QuestType.class), null);
		SetupPanel panel = panel(current);
		check(panel, "Ironman").setSelected(false);
		button(panel, "Save and show my route").doClick();
		assertEquals(LocalProfile.AccountType.MAIN, saved.get(0).accountType());
	}

	@Test
	public void savedRulesAppearAsAReadOnlyYourSavedBuild()
	{
		List<BuildRule> rules = Collections.singletonList(BuildRule.local("Defence", BuildRule.HARD_CAP, 13, ""));
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class), "members_13_defence_pure");
		SetupPanel panel = panel(current, rules, false, null);
		assertEquals(4, all(panel, JRadioButton.class).size());
		assertTrue("an old slug falls back to the saved build", radio(panel, "Your saved build").isSelected());
		button(panel, "Save and show my route").doClick();
		assertEquals(BuildPresets.SAVED, saved.get(0).buildPreset());
	}

	@Test
	public void noSavedRulesMeansNoSavedBuildEntry()
	{
		assertNull(radio(panel(null), "Your saved build"));
	}

	@Test
	public void theGetMoreSectionSaysWhatBuildGuardAddsAndHowToInstallIt()
	{
		SetupPanel panel = panel(null);
		assertTrue(mentions(panel, "Get more with Build Guard"));
		for (String benefit : SetupPanel.BUILD_GUARD_BENEFITS)
		{
			assertTrue(benefit, mentions(panel, benefit));
		}
		assertTrue(mentions(panel, "Plugin Hub"));
		assertTrue(mentions(panel, "Install"));
		String all = String.join(" ", SetupPanel.BUILD_GUARD_BENEFITS).toLowerCase();
		assertTrue(all.contains("custom caps"));
		assertTrue(all.contains("warnings"));
		assertTrue(all.contains("lamp"));
		assertTrue(all.contains("other plugins"));
	}

	@Test
	public void withBuildGuardTheBuildStepOpensItInstead()
	{
		SetupPanel panel = panel(null, Collections.emptyList(), true, null);
		assertEquals(0, all(panel, JRadioButton.class).size());
		assertFalse(mentions(panel, "Get more with Build Guard"));
		button(panel, "Open Build Guard").doClick();
		assertTrue(opened);
		assertTrue("opening it does not save", saved.isEmpty());
	}

	@Test
	public void savingWithBuildGuardKeepsTheStoredBuild()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class), BuildPresets.PURE.slug());
		SetupPanel panel = panel(current, Collections.emptyList(), true, null);
		button(panel, "Save and show my route").doClick();
		assertEquals(BuildPresets.PURE.slug(), saved.get(0).buildPreset());
	}

	@Test
	public void cancelSavesNothing()
	{
		boolean[] cancelled = {false};
		SetupPanel panel = panel(LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class)), Collections.emptyList(), false, () -> cancelled[0] = true);
		button(panel, "Cancel").doClick();
		assertTrue(cancelled[0]);
		assertTrue(saved.isEmpty());
	}

	@Test
	public void firstRunHasNoCancel()
	{
		assertNull(button(panel(null), "Cancel"));
	}

	@Test
	public void noQuestTypeIsRefusedNotSaved()
	{
		LocalProfile current = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.of(LocalProfile.QuestType.MEMBERS));
		SetupPanel panel = panel(current);
		check(panel, "Members quests").setSelected(false);
		button(panel, "Save and show my route").doClick();
		assertTrue(saved.isEmpty());
	}
}
