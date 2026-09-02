package com.vinnyvangogh.questpath;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The rule the sidebar's unbuilt sections exist to keep: "questpath cannot
 * answer this yet" must never be renderable as "questpath looked and found
 * nothing". A section with no explanation would be exactly that.
 */
public class PanelSectionsTest
{
	@Test
	public void everyUnbuiltSectionSaysWhatItWillShow()
	{
		for (PanelSections section : PanelSections.values())
		{
			if (section.status() != PanelSections.Status.NOT_BUILT)
			{
				continue;
			}
			assertNotNull(section + " must explain itself", section.plan());
			assertFalse(section + " must explain itself", section.plan().trim().isEmpty());
		}
	}

	@Test
	public void aLiveSectionCarriesNoPlanBecauseItIsAlreadyTheThing()
	{
		for (PanelSections section : PanelSections.values())
		{
			if (section.status() == PanelSections.Status.LIVE)
			{
				assertNull(section + " is built; a plan would be stale prose", section.plan());
			}
		}
	}

	@Test
	public void everySectionHasATitle()
	{
		for (PanelSections section : PanelSections.values())
		{
			assertNotNull(section.title());
			assertFalse(section.title().trim().isEmpty());
		}
	}

	/**
	 * Every tab must have something in it. A tab strip with a dead tab reads
	 * as a broken plugin, and the strip is built from this enum, so an empty
	 * one is only ever discovered by clicking it.
	 */
	/**
	 * Every tab needs an icon, since that is what the strip renders. A null
	 * would drop the tab to its text fallback silently — one word among four
	 * pictures, which looks like a bug rather than a choice.
	 */
	@Test
	public void everyTabHasAnIcon()
	{
		for (PanelSections.Tab tab : PanelSections.Tab.values())
		{
			assertNotNull(tab + " has no icon", tab.icon());
		}
	}

	@Test
	public void noTabIsEmpty()
	{
		for (PanelSections.Tab tab : PanelSections.Tab.values())
		{
			boolean any = false;
			for (PanelSections section : PanelSections.values())
			{
				any |= section.tab() == tab;
			}
			assertTrue(tab + " has no sections, so its tab would be blank", any);
		}
	}

	/**
	 * Tabs show icons, so the label is normally the tooltip — but it is also
	 * the fallback when an icon cannot be produced, and five text tabs share a
	 * 225px sidebar. A long one does not wrap, it truncates, and a truncated
	 * tab is a tab nobody can read.
	 */
	@Test
	public void tabLabelsAreShortEnoughForTheSidebar()
	{
		for (PanelSections.Tab tab : PanelSections.Tab.values())
		{
			assertNotNull(tab.label());
			assertFalse(tab.label().trim().isEmpty());
			assertTrue(tab + " label is too long for a five-tab strip: " + tab.label(),
				tab.label().length() <= 8);
		}
	}

	/**
	 * If every section were built this enum would be pointless, and if none
	 * were the panel would be a promise. Both ends are worth failing on.
	 */
	@Test
	public void thePanelHasBothBuiltAndUnbuiltSections()
	{
		boolean anyLive = false;
		boolean anyPlanned = false;
		for (PanelSections section : PanelSections.values())
		{
			anyLive |= section.status() == PanelSections.Status.LIVE;
			anyPlanned |= section.status() == PanelSections.Status.NOT_BUILT;
		}
		assertTrue(anyLive);
		assertTrue(anyPlanned);
	}
}
