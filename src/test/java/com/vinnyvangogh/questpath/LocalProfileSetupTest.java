package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.Test;

public class LocalProfileSetupTest
{
	@Test
	public void changingSetupKeepsProgressAndRoute()
	{
		LocalProfile old = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class))
			.withRoute(7, Arrays.asList("Cook's Assistant"), Arrays.asList("Sheep Shearer"));
		LocalProfile answers = LocalProfile.create(LocalProfile.AccountType.HCIM,
			EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY), "f2p_adamant_pure");

		LocalProfile merged = old.withSetup(answers);

		assertEquals(LocalProfile.AccountType.HCIM, merged.accountType());
		assertEquals(EnumSet.of(LocalProfile.QuestType.FREE_TO_PLAY), merged.questTypes());
		assertEquals("f2p_adamant_pure", merged.buildPreset());
		assertEquals(Arrays.asList("Sheep Shearer"), merged.route());
		assertEquals(Arrays.asList("Cook's Assistant"), merged.completedQuests());
		assertEquals(7, merged.graphVersion());
		assertTrue(merged.isUsable());
	}

	@Test
	public void routeRecomputationKeepsTheBuild()
	{
		LocalProfile profile = LocalProfile.create(LocalProfile.AccountType.MAIN,
			EnumSet.allOf(LocalProfile.QuestType.class), "owner_pure");
		assertEquals("owner_pure", profile.withRoute(1, Arrays.asList(), Arrays.asList()).buildPreset());
	}

	@Test
	public void hardcoreGroupIronmanIsAnAccountType()
	{
		assertEquals(LocalProfile.AccountType.HCGIM, LocalProfile.AccountType.valueOf("HCGIM"));
	}

	@Test
	public void aProfileSavedBeforeBuildsExistedHasNoBuild()
	{
		com.google.gson.Gson gson = new com.google.gson.Gson();
		LocalProfile old = gson.fromJson(
			"{\"schema_version\":1,\"account_type\":\"MAIN\",\"quest_types\":[\"MEMBERS\"]}", LocalProfile.class);
		assertTrue(old.isUsable());
		assertEquals(null, old.buildPreset());
	}
}
