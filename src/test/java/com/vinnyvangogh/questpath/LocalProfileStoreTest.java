package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The saved local profile: first run, save and reload, reset, and every way a
 * saved value can be bad. Runs against a map standing in for RuneLite's
 * config, never the real one.
 */
public class LocalProfileStoreTest
{
	private Map<String, String> config;
	private LocalProfileStore store;

	@Before
	public void setUp()
	{
		config = new HashMap<>();
		store = new LocalProfileStore(new LocalProfileStore.Backend()
		{
			@Override
			public String get(String key)
			{
				return config.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				config.put(key, value);
			}

			@Override
			public void unset(String key)
			{
				config.remove(key);
			}
		}, new Gson());
	}

	private void write(String key, String content)
	{
		config.put(LocalProfileStore.configKey(key), content);
	}

	@Test
	public void firstRunWithNothingSavedIsAbsentAndWritesNothing()
	{
		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.ABSENT, result.status());
		assertNull(result.profile());
		assertTrue("reading must not write", config.isEmpty());
	}

	@Test
	public void aSavedProfileComesBackIntact()
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
		assertEquals("one value per account", 1, config.size());
	}

	@Test
	public void accountsDoNotShareAValue()
	{
		store.save("1", LocalProfile.create(LocalProfile.AccountType.MAIN, EnumSet.allOf(LocalProfile.QuestType.class)));

		assertEquals(LocalProfileStore.Status.ABSENT, store.load("2").status());
	}

	@Test
	public void resetWipesTheProfileSoSetupRunsAgain()
	{
		store.save("123", LocalProfile.create(LocalProfile.AccountType.GIM, EnumSet.allOf(LocalProfile.QuestType.class)));

		store.reset("123");

		assertFalse(config.containsKey(LocalProfileStore.configKey("123")));
		assertEquals(LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void resetWithNothingSavedIsHarmless()
	{
		store.reset("123");

		assertEquals(LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void resetAlsoClearsASetAsideCopy()
	{
		write("123", "{not json");
		store.load("123");
		assertTrue(config.containsKey(LocalProfileStore.corruptKey("123")));

		store.reset("123");

		assertTrue(config.isEmpty());
	}

	@Test
	public void garbageIsCorruptAndSetAsideNotDeleted()
	{
		write("123", "{this is not json");

		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.CORRUPT, result.status());
		assertNull(result.profile());
		assertFalse(config.containsKey(LocalProfileStore.configKey("123")));
		assertEquals("{this is not json", config.get(LocalProfileStore.corruptKey("123")));
		assertEquals("a second load starts clean",
			LocalProfileStore.Status.ABSENT, store.load("123").status());
	}

	@Test
	public void anEmptyValueIsCorrupt()
	{
		write("123", "");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aTruncatedValueIsCorrupt()
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[\"FREE_TO");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void validJsonThatIsNotAProfileIsCorrupt()
	{
		write("123", "[1,2,3]");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void anUnknownAccountTypeSendsThePlayerBackToSetup()
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"WIZARD\",\"quest_types\":[\"MEMBERS\"]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aProfileWithNoQuestTypesIsNotASetup()
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aNewerSchemaIsNotGuessedAt()
	{
		write("123", "{\"schema_version\":99,\"account_type\":\"MAIN\",\"quest_types\":[\"MEMBERS\"]}");

		assertEquals(LocalProfileStore.Status.CORRUPT, store.load("123").status());
	}

	@Test
	public void aQuestTypeFromANewerBuildIsIgnoredNotFatal()
	{
		write("123", "{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[\"MEMBERS\",\"MINIQUESTS\"]}");

		LocalProfileStore.Result result = store.load("123");

		assertEquals(LocalProfileStore.Status.LOADED, result.status());
		assertEquals(EnumSet.of(LocalProfile.QuestType.MEMBERS), result.profile().questTypes());
	}

	@Test
	public void anAccountKeyMustBeASimpleToken()
	{
		for (String bad : new String[]{"../evil", "a/b", "", "..", "a b", "a.b"})
		{
			try
			{
				store.load(bad);
				org.junit.Assert.fail("accepted " + bad);
			}
			catch (IllegalArgumentException expected)
			{
				// refused before it reaches a config key
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
