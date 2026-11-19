package com.vinnyvangogh.questpath.scenarios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Named scenarios saved as JSON files in the dev plugin's folder. Anything the Paste JSON panel accepts can be
 * saved; it is validated before it is written, so a saved file always loads. Names become file names, so they are
 * reduced to {@code a-z 0-9 - _}: a name can never reach outside the folder.
 */
public final class ScenarioStore
{
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
	private static final String EXT = ".json";

	private final ScenarioFiles files;
	private final EventKinds kinds;

	public ScenarioStore(ScenarioFiles files, EventKinds kinds)
	{
		this.files = files;
		this.kinds = kinds;
	}

	/** Validates {@code json} (a scenario, a step or a list of steps), names it, and writes it. Returns the file. */
	public String save(String name, String json) throws IOException
	{
		String file = fileName(name);
		JsonObject scenario = ScenarioParser.wrapPasted(json, kinds);
		scenario.addProperty("name", name.trim());
		ScenarioParser.parse(scenario.toString(), file, kinds);
		files.write(file, PRETTY.toJson(scenario) + "\n");
		return file;
	}

	/** Every saved scenario that still parses; one that no longer does is reported in {@code problems}. */
	public List<Scenario> list(List<String> problems) throws IOException
	{
		List<Scenario> out = new ArrayList<>();
		for (String file : files.names())
		{
			if (!file.endsWith(EXT))
			{
				continue;
			}
			try
			{
				out.add(ScenarioParser.parse(files.read(file), file, kinds));
			}
			catch (ScenarioException e)
			{
				problems.add(e.getMessage());
			}
		}
		return out;
	}

	public Scenario load(String name) throws IOException
	{
		String file = fileName(name);
		String text = files.read(file);
		if (text == null)
		{
			throw new ScenarioException("no saved scenario '" + name + "' (" + file + ")");
		}
		return ScenarioParser.parse(text, file, kinds);
	}

	public boolean delete(String name) throws IOException
	{
		return files.delete(fileName(name));
	}

	/** "Looting Bag 2!" -> "looting-bag-2.json"; refuses a name with nothing usable in it. */
	static String fileName(String name)
	{
		String slug = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "-")
			.replaceAll("^-+|-+$", "");
		if (slug.isEmpty())
		{
			throw new ScenarioException("a saved scenario needs a name with letters or digits in it, not '" + name
				+ "'");
		}
		return slug + EXT;
	}
}
