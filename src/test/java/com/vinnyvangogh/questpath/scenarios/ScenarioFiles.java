package com.vinnyvangogh.questpath.scenarios;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * A flat folder of named text files, so {@link ScenarioStore} does not care whether they are on disk. The dev
 * plugin uses {@link OnDisk} (RuneLite's Filepath API, as the Plugin Hub requires); tests use an in-memory one,
 * because a {@code Filepath} cannot be built outside RuneLite.
 */
public interface ScenarioFiles
{
	/** File names (with extension), sorted. */
	List<String> names() throws IOException;

	/** The file's text, or null when it does not exist. */
	String read(String name) throws IOException;

	void write(String name, String text) throws IOException;

	/** Returns whether a file was removed. */
	boolean delete(String name) throws IOException;

	/** The dev plugin's own folder, {@code .runelite/plugin-data/<dev plugin internalName>/scenarios}. */
	final class OnDisk implements ScenarioFiles
	{
		private final Callable<Filepath> pluginDirectory;

		/** {@code pluginDirectory}: {@code Plugin::getPluginDirectory}, resolved per call. */
		public OnDisk(Callable<Filepath> pluginDirectory)
		{
			this.pluginDirectory = pluginDirectory;
		}

		private Filepath dir() throws IOException
		{
			try
			{
				return pluginDirectory.call().joinSegment("scenarios");
			}
			catch (IOException | RuntimeException e)
			{
				throw e;
			}
			catch (Exception e)
			{
				throw new IOException(e);
			}
		}

		@Override
		public List<String> names() throws IOException
		{
			Filepath dir = dir();
			if (!dir.isDirectory())
			{
				return new ArrayList<>();
			}
			try (Stream<Filepath> files = dir.walk(1))
			{
				return files.filter(Filepath::isFile).map(Filepath::getFileName).sorted()
					.collect(Collectors.toList());
			}
		}

		@Override
		public String read(String name) throws IOException
		{
			Filepath file = dir().joinSegment(name);
			if (!file.isFile())
			{
				return null;
			}
			try (InputStream in = file.openInputStream())
			{
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		}

		@Override
		public void write(String name, String text) throws IOException
		{
			Filepath dir = dir();
			dir.createDirectories();
			try (OutputStream out = dir.joinSegment(name).openOutputStream())
			{
				out.write(text.getBytes(StandardCharsets.UTF_8));
			}
		}

		@Override
		public boolean delete(String name) throws IOException
		{
			Filepath file = dir().joinSegment(name);
			boolean there = file.isFile();
			file.deleteIfExists();
			return there;
		}
	}

	/** Files kept in a map, for tests. */
	final class InMemory implements ScenarioFiles
	{
		private final java.util.Map<String, String> files = new java.util.TreeMap<>();

		@Override
		public List<String> names()
		{
			return Collections.unmodifiableList(new ArrayList<>(files.keySet()));
		}

		@Override
		public String read(String name)
		{
			return files.get(name);
		}

		@Override
		public void write(String name, String text)
		{
			files.put(name, text);
		}

		@Override
		public boolean delete(String name)
		{
			return files.remove(name) != null;
		}
	}
}
