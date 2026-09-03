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
	/**
	 * NOTE: this test is currently VACUOUS. Every section is LIVE, so the loop
	 * below reaches no assertion and passes by finding nothing to check.
	 *
	 * It is kept rather than deleted because the rule it states is the one
	 * that matters the moment anyone adds the next unbuilt section, and a rule
	 * with no test is how "questpath cannot answer this yet" quietly starts
	 * rendering as "questpath looked and found nothing". But a green tick here
	 * is not evidence of anything today, and saying so costs less than someone
	 * later mistaking it for coverage. The same applies to
	 * QuestpathPanel.renderPlannedSectionsFor, whose not-built rendering path
	 * no section currently exercises.
	 */
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
	 * If every section were unbuilt the panel would be a promise rather than a
	 * plugin. Worth failing on.
	 *
	 * This used to also require at least one NOT_BUILT section, back when
	 * NEXT_QUEST and QUEST_BROWSER were genuinely still waiting on the
	 * bundled quest graph. Now that every section is LIVE, requiring an
	 * unbuilt one would fail the moment the panel finished rather than the
	 * moment it regressed -- a fully-built panel is success, not something to
	 * guard against.
	 */
	@Test
	public void thePanelHasAtLeastOneLiveSection()
	{
		boolean anyLive = false;
		for (PanelSections section : PanelSections.values())
		{
			anyLive |= section.status() == PanelSections.Status.LIVE;
		}
		assertTrue(anyLive);
	}
}
