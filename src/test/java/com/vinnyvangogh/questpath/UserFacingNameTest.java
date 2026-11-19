package com.vinnyvangogh.questpath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The plugin is Quest Route. No string a player can read (config names and
 * descriptions, panel and overlay text, chat lines, tooltips) may still say
 * "questpath" or "Quest Path".
 *
 * <p>Scans every string literal in {@code src/main}. The few literals that
 * must keep the old spelling are named in {@link #KEPT}, each for a reason.
 */
public class UserFacingNameTest
{
	private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
	private static final Pattern OLD_NAME = Pattern.compile("quest\\s?path", Pattern.CASE_INSENSITIVE);

	/** Literals allowed to contain the old name. */
	private static final Set<String> KEPT = Set.of(
		// The pre-rename config group read once by ConfigGroupMigration, and
		// also the Grand Exchange search sentinel (matched on both sides,
		// not a label).
		"questpath",
		// Marker key written by that migration; saved config, never shown.
		"migratedFromQuestpath");

	@Test
	public void noStringLiteralSaysQuestpath() throws IOException
	{
		List<String> hits = new ArrayList<>();
		for (Path file : sources())
		{
			List<String> lines = Files.readAllLines(file);
			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i).trim();
				if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*"))
				{
					continue;
				}
				Matcher m = LITERAL.matcher(line);
				while (m.find())
				{
					String literal = m.group(1);
					if (OLD_NAME.matcher(literal).find() && !KEPT.contains(literal))
					{
						hits.add(file + ":" + (i + 1) + "  " + literal);
					}
				}
			}
		}
		assertTrue("These strings still say questpath / Quest Path:\n" + String.join("\n", hits), hits.isEmpty());
	}

	private static List<Path> sources() throws IOException
	{
		try (Stream<Path> walk = Files.walk(Path.of("src/main/java")))
		{
			return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}
}
