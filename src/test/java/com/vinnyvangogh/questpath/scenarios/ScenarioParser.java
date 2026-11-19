package com.vinnyvangogh.questpath.scenarios;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads scenario JSON. Everything a run would trip over is refused here instead: an unknown kind, a missing or
 * mistyped field, an unknown constant. Each step's event is built once while parsing, so a scenario that parses
 * is one whose every step can fire.
 *
 * <p>{@link #parsePasted} is the lenient front door for the Paste JSON panel: it also takes a single step or a bare
 * list of steps, and wraps either into a scenario.
 */
public final class ScenarioParser
{
	/** Where scenarios live on the test classpath: {@code src/test/resources/scenarios}. */
	public static final String DEFAULT_DIR = "scenarios";

	private static final Gson GSON = new Gson();

	private ScenarioParser()
	{
	}

	public static Scenario parse(String json, String source, EventKinds kinds)
	{
		JsonObject root = object(json, source);
		try
		{
			return read(root, source, kinds);
		}
		catch (ScenarioException e)
		{
			throw new ScenarioException(source + ": " + e.getMessage(), e);
		}
	}

	/**
	 * What the Paste JSON panel accepts, in the order tried: a scenario ({@code {"name":..,"steps":[..]}}), one
	 * step ({@code {"kind":..,..}}), or a list of steps ({@code [{"kind":..},..]}). A pasted step has no wait.
	 */
	public static Scenario parsePasted(String text, EventKinds kinds)
	{
		return parse(wrapPasted(text, kinds).toString(), "pasted", kinds);
	}

	/** The pasted text as a scenario object (not yet checked against the kinds); see {@link #parsePasted}. */
	static JsonObject wrapPasted(String text, EventKinds kinds)
	{
		JsonElement root = tree(text, "pasted JSON");
		JsonObject scenario = new JsonObject();
		if (root.isJsonArray())
		{
			scenario.addProperty("name", "Pasted steps");
			scenario.add("steps", root);
		}
		else if (root.isJsonObject() && root.getAsJsonObject().has("steps"))
		{
			scenario = root.getAsJsonObject();
			if (!scenario.has("name"))
			{
				scenario.addProperty("name", "Pasted scenario");
			}
		}
		else if (root.isJsonObject() && root.getAsJsonObject().has(Step.KIND))
		{
			scenario.addProperty("name", "Pasted " + root.getAsJsonObject().get(Step.KIND).getAsString());
			JsonArray steps = new JsonArray();
			steps.add(root);
			scenario.add("steps", steps);
		}
		else
		{
			throw new ScenarioException("pasted JSON: expected a step {\"kind\": ...}, a list of steps [...], or a "
				+ "scenario {\"name\": ..., \"steps\": [...]}; the kinds are " + kinds.names());
		}
		return scenario;
	}

	private static JsonElement tree(String json, String source)
	{
		JsonElement root;
		try
		{
			root = GSON.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException e)
		{
			throw new ScenarioException(source + ": not valid JSON: " + e.getMessage(), e);
		}
		if (root == null || root.isJsonNull())
		{
			throw new ScenarioException(source + ": empty");
		}
		return root;
	}

	private static JsonObject object(String json, String source)
	{
		JsonElement root = tree(json, source);
		if (!root.isJsonObject())
		{
			throw new ScenarioException(source + ": not a JSON object");
		}
		return root.getAsJsonObject();
	}

	private static Scenario read(JsonObject root, String source, EventKinds kinds)
	{
		String name = text(root, "name");
		if (name == null || name.trim().isEmpty())
		{
			throw new ScenarioException("a scenario needs a 'name'");
		}
		List<Step> steps = new ArrayList<>();
		for (JsonElement e : array(root, "steps"))
		{
			steps.add(step(e, steps.size() + 1, kinds));
		}
		if (steps.isEmpty())
		{
			throw new ScenarioException("'" + name + "' has no steps");
		}
		List<Scenario.Expect> expect = new ArrayList<>();
		for (JsonElement e : array(root, "expect"))
		{
			expect.add(expectation(e, expect.size() + 1));
		}
		return new Scenario(source, name, text(root, "description"), steps, expect);
	}

	/** The array at {@code key}; empty when absent or not an array. */
	private static JsonArray array(JsonObject o, String key)
	{
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	private static Step step(JsonElement e, int number, EventKinds kinds)
	{
		if (!e.isJsonObject())
		{
			throw new ScenarioException("step " + number + " is not an object");
		}
		JsonObject o = e.getAsJsonObject();
		String kind = text(o, Step.KIND);
		if (kind == null)
		{
			throw new ScenarioException("step " + number + " has no 'kind'");
		}
		JsonObject fields = o.deepCopy();
		fields.remove(Step.KIND);
		fields.remove(Step.WAIT);
		fields.remove(Step.NOTE);
		Step step = new Step(number, kind, wait(o, number), text(o, Step.NOTE), fields);
		EventFactory factory;
		try
		{
			factory = kinds.factory(kind);
		}
		catch (ScenarioException ex)
		{
			throw new ScenarioException("step " + number + ": " + ex.getMessage(), ex);
		}
		build(step, factory, kinds);
		return step;
	}

	private static long wait(JsonObject o, int number)
	{
		if (!o.has(Step.WAIT))
		{
			return 0;
		}
		long wait;
		try
		{
			wait = o.get(Step.WAIT).getAsLong();
		}
		catch (RuntimeException ex)
		{
			throw new ScenarioException("step " + number + ": 'wait' must be milliseconds, not " + o.get(Step.WAIT));
		}
		if (wait < 0)
		{
			throw new ScenarioException("step " + number + ": 'wait' cannot be negative");
		}
		return wait;
	}

	/** Builds the step's event, turning any failure into one that names the step. */
	static Object build(Step step, EventFactory factory, EventKinds kinds)
	{
		try
		{
			return factory.create(new Fields(step, kinds));
		}
		catch (ScenarioException e)
		{
			throw e;
		}
		catch (RuntimeException e)
		{
			throw new ScenarioException(step.label() + ": " + e, e);
		}
	}

	private static Scenario.Expect expectation(JsonElement e, int number)
	{
		if (!e.isJsonObject())
		{
			throw new ScenarioException("expect " + number + " is not an object");
		}
		JsonObject o = e.getAsJsonObject();
		String probe = text(o, "probe");
		String contains = text(o, "contains");
		String absent = text(o, "absent");
		Integer count = o.has("count") ? o.get("count").getAsInt() : null;
		int checks = (contains == null ? 0 : 1) + (absent == null ? 0 : 1) + (count == null ? 0 : 1);
		if (probe == null || checks != 1)
		{
			throw new ScenarioException("expect " + number + " needs a 'probe' and exactly one of contains, absent "
				+ "or count");
		}
		return new Scenario.Expect(number, probe, contains, absent, count);
	}

	private static String text(JsonObject o, String key)
	{
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	/** Every {@code *.json} in {@code dir} on the test classpath, in file name order. */
	public static List<Scenario> loadAll(String dir, EventKinds kinds)
	{
		ClassLoader loader = ScenarioParser.class.getClassLoader();
		URL url = loader.getResource(dir);
		if (url == null)
		{
			throw new ScenarioException("no scenario directory '" + dir + "' on the test classpath (src/test/resources/"
				+ dir + ")");
		}
		List<Scenario> out = new ArrayList<>();
		for (String file : list(url, dir))
		{
			out.add(parse(read(loader, dir + "/" + file), file, kinds));
		}
		return out;
	}

	private static List<String> list(URL url, String dir)
	{
		try
		{
			List<String> names = "jar".equals(url.getProtocol()) ? inJar(url, dir) : inDirectory(Paths.get(url.toURI()));
			Collections.sort(names);
			return names;
		}
		catch (IOException | URISyntaxException e)
		{
			throw new ScenarioException("cannot list scenarios in " + url + ": " + e, e);
		}
	}

	private static List<String> inDirectory(Path dir) throws IOException
	{
		try (Stream<Path> files = Files.list(dir))
		{
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.collect(Collectors.toList());
		}
	}

	/** The shadow jar ({@code ./gradlew shadowJar}) keeps scenarios inside it. */
	private static List<String> inJar(URL url, String dir) throws IOException
	{
		List<String> names = new ArrayList<>();
		String prefix = dir + "/";
		try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile())
		{
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements())
			{
				String name = entries.nextElement().getName();
				if (name.startsWith(prefix) && name.endsWith(".json") && name.indexOf('/', prefix.length()) < 0)
				{
					names.add(name.substring(prefix.length()));
				}
			}
		}
		return names;
	}

	private static String read(ClassLoader loader, String resource)
	{
		try (InputStream in = loader.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new ScenarioException("cannot read " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new ScenarioException("cannot read " + resource + ": " + e, e);
		}
	}
}
