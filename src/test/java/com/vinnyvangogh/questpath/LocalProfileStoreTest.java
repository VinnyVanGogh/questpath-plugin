package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The local profile file: first run, save and reload, reset, and every way a
 * file can be bad. Runs against a temp directory, never the real
 * {@code .runelite}.
 */
public class LocalProfileStoreTest
{
	private Path root;
	private LocalProfileStore store;

	/**
	 * Under build/ rather than java.io.tmpdir: that is inside the project, so
	 * it works where the system temp directory is not writable, and Gradle's
	 * clean removes it.
	 */
	@Before
	public void setUp() throws IOException
	{
		Path base = Path.of("build", "test-profiles");
		Files.createDirectories(base);
		root = Files.createTempDirectory(base, "case").resolve("questroute");
		store = new LocalProfileStore(root, new Gson());
	}

	private Path profileFile(String key)
	{
		return root.resolve(key).resolve("profile.json");
	}

	private void write(String key, String content) throws IOException
	{
		Files.createDirectories(profileFile(key).getParent());
		Files.write(profileFile(key), content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void firstRunWithNoFilesIsAbsentAndCreatesNothing()
	{
		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.ABSENT, result.status());
		assertNull(result.profile());
		assertFalse("reading must not create the directory", Files.exists(root));
	}

	@Test
	public void aSavedProfileComesBackIntact() throws IOException
	{
		LocalProfile profile = LocalProfile.create(LocalProfile.AccountType.HCIM,
			EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY))
			.withRoute(12, Arrays.asList("Cook's Assistant"), Arrays.asList("Sheep Shearer", "Romeo & Juliet"));

		store.save("123", profile);
		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.LOADED, result.status());
		LocalProfile loaded = result.profile();
		assertEquals(LocalProfile.AccountType.HCIM, loaded.accountType());
		assertEquals(EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY), loaded.questTypes());
		assertEquals(Arrays.asList("Sheep Shearer", "Romeo & Juliet"), loaded.route());
		assertEquals(Arrays.asList("Cook's Assistant"), loaded.completedQuests());
		assertEquals(12, loaded.graphVersion());
		assertFalse("the temp file must not be left behind",
			Files.exists(profileFile("123").resolveSibling("profile.json.tmp")));
	}

	@Test
	public void accountsDoNotShareAFile() throws IOException
	{
		store.save("1", LocalProfile.create(LocalProfile.AccountType.MAIN, EnumSet.allOf(LocalProfile.QuestType.class)));

		assertEquals(LocalProfileStore.Status.ABSENT, store.load("2").status());
	}

	@Test
	public void resetWipesTheProfileSoSetupRunsAgain() throws IOException
	{
		store.save("123", LocalProfile.create(LocalProfile.AccountType.GIM, EnumSet.allOf(LocalProfile.QuestType.class)));

		store.reset("123");

		assertFalse(Files.exists(profileFile("123")));
		assertEquals(LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void resetWithNothingSavedIsHarmless() throws IOException
	{
		store.reset("123");

		assertEquals(LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void resetAlsoClearsAQuarantinedFile() throws IOException
	{
		write("123", "{not json");
		store.load("123");
		assertTrue(Files.exists(profileFile("123").resolveSibling("profile.json.corrupt")));

		store.reset("123");

		assertFalse(Files.exists(profileFile("123").resolveSibling("profile.json.corrupt")));
	}

	@Test
	public void garbageIsCorruptAndSetAsideNotDeleted() throws IOException
	{
		write("123", "{this is not json");

		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.CORRUPT, result.status());
		assertNull(result.profile());
		assertFalse(Files.exists(profileFile("123")));
		assertEquals("{this is not json", new String(
			Files.readAllBytes(profileFile("123").resolveSibling("profile.json.corrupt")), StandardCharsets.UTF_8));
		assertEquals("a second load starts clean",
			LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void anEmptyFileIsCorrupt() throws IOException
	{
		write("123", "");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aTruncatedWriteIsCorrupt() throws IOException
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[\"FREE_TO");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void validJsonThatIsNotAProfileIsCorrupt() throws IOException
	{
		write("123", "[1,2,3]");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void anUnknownAccountTypeSendsThePlayerBackToSetup() throws IOException
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"WIZARD\",\"quest_types\":[\"MEMBERS\"]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aProfileWithNoQuestTypesIsNotASetup() throws IOException
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aNewerSchemaIsNotGuessedAt() throws IOException
	{
		write("123", "{\"schema_version\":99,\"account_type\":\"MAIN\",\"quest_types\":[\"MEMBERS\"]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aQuestTypeFromANewerBuildIsIgnoredNotFatal() throws IOException
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[\"MEMBERS\",\"MINIQUESTS\"]}");

		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.LOADED, result.status());
		assertEquals(EnumSet.of(LocalProfile.QuestType.MEMBERS), result.profile().questTypes());
	}

	@Test
	public void anAccountKeyCannotEscapeTheDirectory()
	{
		for (String bad : new String[]{"../evil", "a/b", "", "..", "a b"})
		{
			try
			{
				store.load(bad);
				org.junit.Assert.fail("accepted " + bad);
			}
			catch (IllegalArgumentException expected)
			{
				// refused before it reaches a path
			}
		}
		try
		{
			store.load(null);
			org.junit.Assert.fail("accepted null");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void theKeyIsTheUnsignedAccountHashAndMinusOneMeansNotKnownYet()
	{
		assertNull(LocalProfileStore.keyFor(-1L));
		assertEquals("42", LocalProfileStore.keyFor(42L));
		assertTrue(LocalProfileStore.keyFor(Long.MIN_VALUE).matches("[0-9]+"));
	}
}
