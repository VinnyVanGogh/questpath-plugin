package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The game-chat lines posted on the first login after Quest Route updates.
 *
 * <p>Nothing stored is either a brand-new install or a user from before notes
 * existed. The Plugin Hub updates plugins while the client starts, so an
 * update starts the plugin on the login screen; installing from the Plugin Hub
 * panel starts it in a running, usually logged-in client. Nothing stored and
 * started logged in is therefore a fresh install and gets one welcome line
 * instead of the whole list.
 *
 * <p>Developer builds do not bump {@link #VERSION} on every change, so they
 * also compare the build's commit (see {@link BuildInfo}) with the last one
 * seen, and show the notes again when it moves.
 */
final class UpdateNotes
{
	/** Must match {@code version} in runelite-plugin.properties (UpdateNotesTest). */
	static final String VERSION = "1.0.0";

	static final String TITLE = "Quest Route " + VERSION + ". What's new:";

	/** Where the setting is, as the settings panel titles it. */
	static final String CONFIGURE = "Configure Quest Route > Chat";

	/** Plain player language, one short line each. */
	static final List<String> NOTES = Collections.unmodifiableList(Arrays.asList(
		"Quest Route now works with no server: pick your account type once in the sidebar and it builds your route.",
		"Your route follows your skill caps: no quest that would push a capped skill past its limit.",
		"Account setup is a button at the top of the sidebar: pick a named build, or set a limit per skill.",
		"A Skiller build: no combat experience at all, Hitpoints included.",
		"Quick settings at the top of the sidebar: the eight most-used switches, one click each.",
		"The monsters and objects that drop something your route still needs are outlined in the world.",
		"A bank button opens the Quest Route tab, and a second click puts it away again.",
		"Items you can get from any tier now fill from whichever tier you own.",
		"New sidebar icon, and a spoken heads-up near shops and for needed floor items.",
		"Turn these notes off in " + CONFIGURE + "; the What's new button beside Account setup replays them."
	));

	static final String WELCOME = "Quest Route " + VERSION
		+ " is on. Pick your account type in the sidebar to get a route. More: " + CONFIGURE + ".";

	private UpdateNotes()
	{
	}

	/**
	 * What to post on this login, the title first; empty when nothing.
	 *
	 * @param lastSeen         the stored last-seen version, "" when none
	 * @param enabled          the "Show update notes in chat" setting
	 * @param startedLoggedIn  whether the plugin started while already logged in
	 */
	static List<String> toShow(String lastSeen, boolean enabled, boolean startedLoggedIn)
	{
		if (!enabled || !needsRecord(lastSeen))
		{
			return Collections.emptyList();
		}
		if (isNullOrEmpty(lastSeen) && startedLoggedIn)
		{
			return Collections.singletonList(WELCOME);
		}
		return replay();
	}

	/** Whether this version still has to be stored as seen; true with the setting off too. */
	static boolean needsRecord(String lastSeen)
	{
		return !VERSION.equals(lastSeen);
	}

	/**
	 * Whether a developer build is a different build from the last one seen.
	 * Always false when there is no build commit (a Plugin Hub build).
	 */
	static boolean buildChanged(String lastSeenBuild, String currentBuild)
	{
		return !isNullOrEmpty(currentBuild) && !currentBuild.equals(lastSeenBuild);
	}

	/** The developer-build lines: the notes again, whenever the build commit moved. */
	static List<String> toShowForBuild(String lastSeenBuild, String currentBuild, boolean enabled)
	{
		if (!enabled || !buildChanged(lastSeenBuild, currentBuild))
		{
			return Collections.emptyList();
		}
		return replay();
	}

	/** The whole list, title first: what the sidebar's "What's new" button posts. */
	static List<String> replay()
	{
		final List<String> lines = new ArrayList<>(NOTES.size() + 1);
		lines.add(TITLE);
		lines.addAll(NOTES);
		return lines;
	}

	private static boolean isNullOrEmpty(String s)
	{
		return s == null || s.isEmpty();
	}
}
