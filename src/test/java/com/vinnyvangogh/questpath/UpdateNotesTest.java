package com.vinnyvangogh.questpath;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The "what's new" chat lines: once per version, never with the toggle off,
 * one welcome line for a fresh install, a replay on demand, and a re-show
 * when a developer build's commit changes.
 */
public class UpdateNotesTest
{
	private static final String NEVER_SEEN = "";

	@Test
	public void versionIsThePluginPropertiesVersion() throws Exception
	{
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(Paths.get("runelite-plugin.properties")))
		{
			properties.load(reader);
		}
		assertEquals(properties.getProperty("version"), UpdateNotes.VERSION);
	}

	@Test
	public void anUpdateShowsTheTitleThenTheNotes()
	{
		final List<String> lines = UpdateNotes.toShow(NEVER_SEEN, true, false);

		assertEquals(UpdateNotes.TITLE, lines.get(0));
		assertEquals(UpdateNotes.NOTES, lines.subList(1, lines.size()));
		assertEquals(UpdateNotes.TITLE, UpdateNotes.toShow("0.9.0", true, true).get(0));
	}

	@Test
	public void theSeenVersionShowsNothing()
	{
		assertTrue(UpdateNotes.toShow(UpdateNotes.VERSION, true, false).isEmpty());
		assertTrue(UpdateNotes.toShow(UpdateNotes.VERSION, true, true).isEmpty());
	}

	@Test
	public void theToggleOffShowsNothingButStillRecords()
	{
		assertTrue(UpdateNotes.toShow(NEVER_SEEN, false, false).isEmpty());
		assertTrue(UpdateNotes.toShow("0.9.0", false, true).isEmpty());
		assertTrue(UpdateNotes.needsRecord(NEVER_SEEN));
		assertFalse(UpdateNotes.needsRecord(UpdateNotes.VERSION));
		assertTrue(UpdateNotes.toShowForBuild("abc", "def", false).isEmpty());
	}

	@Test
	public void aFreshInstallGetsOneWelcomeLine()
	{
		final List<String> lines = UpdateNotes.toShow(NEVER_SEEN, true, true);

		assertEquals(1, lines.size());
		assertEquals(UpdateNotes.WELCOME, lines.get(0));
	}

	@Test
	public void showsOnceAcrossLoginsAndHops()
	{
		final List<String> posted = new ArrayList<>();
		String stored = NEVER_SEEN;
		for (int login = 0; login < 4; login++)
		{
			posted.addAll(UpdateNotes.toShow(stored, true, false));
			if (UpdateNotes.needsRecord(stored))
			{
				stored = UpdateNotes.VERSION;
			}
		}

		assertEquals(UpdateNotes.toShow(NEVER_SEEN, true, false), posted);
	}

	@Test
	public void replayIsAlwaysTheWholeListTitleFirst()
	{
		final List<String> lines = UpdateNotes.replay();

		assertEquals(UpdateNotes.TITLE, lines.get(0));
		assertEquals(UpdateNotes.NOTES, lines.subList(1, lines.size()));
	}

	@Test
	public void aDevBuildShowsTheNotesWhenItsCommitChanges()
	{
		assertEquals(UpdateNotes.replay(), UpdateNotes.toShowForBuild("", "abc1234", true));
		assertEquals(UpdateNotes.replay(), UpdateNotes.toShowForBuild("abc1234", "def5678", true));
		assertTrue(UpdateNotes.toShowForBuild("abc1234", "abc1234", true).isEmpty());
	}

	@Test
	public void aBuildWithNoCommitNeverShowsDevNotes()
	{
		assertFalse(UpdateNotes.buildChanged("", ""));
		assertFalse(UpdateNotes.buildChanged("abc", ""));
		assertTrue(UpdateNotes.toShowForBuild("abc", "", true).isEmpty());
	}

	@Test
	public void notesAreShortAndEndWithWhereToTurnThemOff()
	{
		for (String line : UpdateNotes.NOTES)
		{
			assertTrue("too long for a chat line: " + line, line.length() <= 115);
			assertFalse("a note is a feature, not a fix: " + line,
				line.toLowerCase(Locale.ROOT).contains("fix"));
		}
		assertTrue(UpdateNotes.NOTES.get(UpdateNotes.NOTES.size() - 1).contains("Quest Route > Chat"));
		assertTrue(UpdateNotes.TITLE.contains(UpdateNotes.VERSION));
		assertTrue(UpdateNotes.WELCOME.contains(UpdateNotes.VERSION));
	}
}
