package com.vinnyvangogh.questpath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the Plugin Hub's "Forbidden Language Features": no reflection, no
 * dynamic classloading, no subprocesses, no {@code Unsafe}. Any of them is an
 * automatic rejection, and the Quest Helper bridge was 18 call sites of the
 * first before it was removed.
 *
 * <p>A source scan over {@code src/main}, not a bytecode check: it reads what
 * a reviewer reads. It cannot see reflection a library does on this plugin's
 * behalf (Gson binding a class, Guice injecting a field) -- that is
 * RuneLite's own stack and every Hub plugin shares it. What it closes is this
 * plugin's own code reaching for {@code java.lang.reflect} again.
 *
 * <p>Tests may reflect -- {@link OverlaySettingsPanelConfigParityTest} reads
 * the config interface's annotations -- because test sources never ship.
 */
public class NoReflectionTest
{
	private static final Path MAIN = Path.of("src/main/java");

	/** Each one a Forbidden Language Feature, or the usual way to reach one. */
	private static final Pattern FORBIDDEN = Pattern.compile(String.join("|",
		"java\\.lang\\.reflect",
		"java\\.lang\\.invoke",
		"com\\.google\\.gson\\.reflect",
		"Class\\.forName\\s*\\(",
		"\\.getDeclared(Method|Field|Constructor)s?\\s*\\(",
		"\\.get(Method|Field|Constructor)s?\\s*\\(",
		"\\.setAccessible\\s*\\(",
		"\\.newInstance\\s*\\(",
		"ClassLoader",
		"sun\\.misc\\.Unsafe",
		"ProcessBuilder",
		"Runtime\\.getRuntime\\s*\\(",
		"ObjectInputStream",
		"ObjectOutputStream"));

	@Test
	public void noForbiddenLanguageFeatureInMainSources() throws IOException
	{
		List<String> hits = new ArrayList<>();
		for (Path file : javaSources())
		{
			List<String> lines = Files.readAllLines(file);
			for (int i = 0; i < lines.size(); i++)
			{
				if (FORBIDDEN.matcher(lines.get(i)).find())
				{
					hits.add(MAIN.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
				}
			}
		}
		assertTrue("Plugin Hub forbidden language features in src/main:\n" + String.join("\n", hits),
			hits.isEmpty());
	}

	/** Guards the guard: the scan must actually see something, and must flag the shapes it exists for. */
	@Test
	public void theScanFindsSourcesAndFlagsEachForbiddenShape() throws IOException
	{
		assertTrue("no sources found -- the scan would pass vacuously", javaSources().size() > 50);
		for (String line : List.of(
			"Class<?> c = Class.forName(\"com.questhelper.QuestHelperPlugin\");",
			"java.lang.reflect.Method m = c.getMethod(\"startUpQuest\", String.class);",
			"field.setAccessible(true);",
			"import com.google.gson.reflect.TypeToken;",
			"new ProcessBuilder(\"sh\").start();"))
		{
			assertTrue("not flagged: " + line, FORBIDDEN.matcher(line).find());
		}
		assertEquals(false, FORBIDDEN.matcher("clientThread.invoke(this::refresh);").find());
	}

	/** The Quest Helper bridge comes back over PluginMessage, never by name lookup -- no Quest Helper class is named in src/main. */
	@Test
	public void noMainSourceNamesAQuestHelperClass() throws IOException
	{
		List<String> hits = new ArrayList<>();
		for (Path file : javaSources())
		{
			String source = Files.readString(file);
			if (source.contains("com.questhelper") || source.contains("QuestHelperPlugin")
				|| source.contains("QuestHelperBridge"))
			{
				hits.add(MAIN.relativize(file).toString());
			}
		}
		assertTrue("src/main still names Quest Helper's classes: " + hits, hits.isEmpty());
	}

	private static List<Path> javaSources() throws IOException
	{
		try (Stream<Path> walk = Files.walk(MAIN))
		{
			return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}
}
