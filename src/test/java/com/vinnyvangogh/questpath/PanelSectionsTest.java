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
