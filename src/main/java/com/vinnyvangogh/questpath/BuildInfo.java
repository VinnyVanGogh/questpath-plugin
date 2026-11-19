package com.vinnyvangogh.questpath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The commit this build was made from, for developer builds only.
 *
 * <p>build.gradle writes {@code build-info.properties} into the jar. The Plugin
 * Hub replaces build.gradle with its own, so a Hub build has no such resource
 * and {@link #commit()} is empty: Hub users are told about updates by version
 * ({@link UpdateNotes#VERSION}), developers by commit.
 */
final class BuildInfo
{
	private static final String RESOURCE = "/build-info.properties";

	private BuildInfo()
	{
	}

	/** The build's commit, or "" when this build carries none. */
	static String commit()
	{
		try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return "";
			}
			final Properties properties = new Properties();
			properties.load(in);
			return properties.getProperty("commit", "").trim();
		}
		catch (IOException e)
		{
			return "";
		}
	}
}
