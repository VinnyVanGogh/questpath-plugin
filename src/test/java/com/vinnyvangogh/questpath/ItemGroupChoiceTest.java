package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link ItemGroupChoice}: which member of "any pickaxe" the player is
 * told about, and whether they already have one.
 *
 * <p>Built from JSON rather than by calling constructors, because {@link
 * QuestGraph.Alternative} is a Gson-deserialised type with no public
 * constructor -- and going through Gson tests the field names in the bundle
 * at the same time, which is where a rename would otherwise pass every test
 * and ship a silently empty set.
 */
public class ItemGroupChoiceTest
{
	private static final Gson GSON = new Gson();

	private static List<QuestGraph.Alternative> pickaxes()
	{
		return alternatives("["
			+ "{\"name\":\"Bronze pickaxe\",\"item_id\":1265,\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":1},{\"skill\":\"Attack\",\"level\":1}]},"
			+ "{\"name\":\"Mithril pickaxe\",\"item_id\":1273,\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":21},{\"skill\":\"Attack\",\"level\":20}]},"
			+ "{\"name\":\"Rune pickaxe\",\"item_id\":1275,\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":41},{\"skill\":\"Attack\",\"level\":40}]},"
			+ "{\"name\":\"Dragon pickaxe\",\"item_id\":11920,\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":61},{\"skill\":\"Attack\",\"level\":60}]}"
			+ "]");
	}

	private static List<QuestGraph.Alternative> alternatives(String json)
	{
		return List.of(GSON.fromJson(json, QuestGraph.Alternative[].class));
	}

	private static Map<String, Integer> levels(int mining, int attack)
	{
		return Map.of("Mining", mining, "Attack", attack);
	}

	/**
	 * The player's first rule: what they already own wins, and the best one
	 * they own is what gets named.
	 *
	 * <p>Sending somebody to buy a pickaxe they have in the bank is exactly
	 * the errand this feature exists to stop.
	 */
	@Test
	public void anItemAlreadyHeldBeatsAnythingToBuy()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			levels(99, 99), Set.of(), Map.of("mithril pickaxe", 1L));

		assertTrue(choice.held());
		assertEquals("Mithril pickaxe", choice.best().name());
		assertNull("nothing needs buying, so there is no purchase to hedge",
			choice.fallback());
	}

	/** Held, and more than one held: the better of them is named. */
	@Test
	public void theBestHeldIsNamedNotTheFirst()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			levels(99, 99), Set.of(),
			Map.of("bronze pickaxe", 1L, "rune pickaxe", 1L));

		assertEquals("Rune pickaxe", choice.best().name());
	}

	/**
	 * A held item the account cannot use does not count.
	 *
	 * <p>A dragon pickaxe in the bank of an account with 1 Mining satisfies
	 * nothing, and naming it would be the panel telling somebody they are
	 * sorted when they are not.
	 */
	@Test
	public void aHeldItemTheAccountCannotUseIsNotAnAnswer()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			levels(1, 1), Set.of(), Map.of("dragon pickaxe", 1L));

		assertFalse(choice.held());
		assertEquals("Bronze pickaxe", choice.best().name());
	}

	/**
	 * Rules two and three together: buy the best you can, and see a cheaper
	 * one beside it.
	 */
	@Test
	public void nothingHeldNamesTheBestUsableAndOneCheaper()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			levels(41, 40), Set.of(), Map.of());

		assertFalse(choice.held());
		assertEquals("Rune pickaxe", choice.best().name());
		assertEquals("Mithril pickaxe", choice.fallback().name());
	}

	/**
	 * One usable member yields no fallback rather than itself twice, which
	 * would imply a choice that is not there.
	 */
	@Test
	public void aSingleUsableMemberHasNoFallback()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			levels(1, 1), Set.of(), Map.of());

		assertEquals("Bronze pickaxe", choice.best().name());
		assertNull(choice.fallback());
		assertEquals(1, choice.usableCount());
	}

	/**
	 * An absent skill counts as level 1, not as unlimited.
	 *
	 * <p>The standing rule decides the direction: reading silence as "no
	 * limit" would offer a dragon pickaxe to an account with 1 Mining.
	 */
	@Test
	public void anAbsentSkillIsTreatedAsLevelOne()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(pickaxes(),
			Map.of(), Set.of(), Map.of());

		assertEquals("Bronze pickaxe", choice.best().name());
	}

	/**
	 * The build-rule case, and the reason each requirement keeps its own
	 * skill.
	 *
	 * <p>A pickaxe states Mining and Attack. Attack only gates wielding it,
	 * and a pickaxe mines perfectly well from the inventory -- so an account
	 * hard-capping Attack at 1 can still use a rune pickaxe at 41 Mining.
	 * Collapsing the two columns into one number would rule that out, for
	 * exactly the account questpath exists to serve.
	 */
	@Test
	public void aCappedSkillCanBeIgnoredWhenItDoesNotGateUse()
	{
		Map<String, Integer> capped = levels(41, 1);

		ItemGroupChoice.Choice counting = ItemGroupChoice.choose(pickaxes(),
			capped, Set.of(), Map.of());
		assertEquals("counting Attack rules out everything above bronze",
			"Bronze pickaxe", counting.best().name());

		ItemGroupChoice.Choice ignoring = ItemGroupChoice.choose(pickaxes(),
			capped, Set.of("Attack"), Map.of());
		assertEquals("Rune pickaxe", ignoring.best().name());
	}

	/** Nothing usable at all is a real answer, and not a crash. */
	@Test
	public void nothingUsableYieldsNoChoice()
	{
		List<QuestGraph.Alternative> lateOnly = alternatives("["
			+ "{\"name\":\"Dragon pickaxe\",\"item_id\":11920,\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":61}]}]");

		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(lateOnly,
			levels(1, 1), Set.of(), Map.of());

		assertFalse(choice.any());
		assertNull(choice.best());
		assertEquals(0, choice.usableCount());
	}

	/**
	 * A count can be spread across members: six of "any nails" is satisfied
	 * by three bronze and four iron.
	 *
	 * <p>Taking the largest single stack instead would report 4 and send the
	 * player to buy nails they already have.
	 */
	@Test
	public void heldAcrossSumsTheWholeSet()
	{
		List<QuestGraph.Alternative> nails = alternatives("["
			+ "{\"name\":\"Bronze nails\",\"item_id\":4819},"
			+ "{\"name\":\"Iron nails\",\"item_id\":4820},"
			+ "{\"name\":\"Steel nails\",\"item_id\":1539}]");

		assertEquals(7L, ItemGroupChoice.heldAcross(nails,
			Map.of("bronze nails", 3L, "iron nails", 4L)));
		assertEquals(0L, ItemGroupChoice.heldAcross(nails, Map.of()));
	}

	/** Null and empty inputs are answers, not exceptions. */
	@Test
	public void nullInputsAreTolerated()
	{
		assertEquals(0L, ItemGroupChoice.heldAcross(null, Map.of()));
		assertEquals(0L, ItemGroupChoice.heldAcross(List.of(), null));
		assertFalse(ItemGroupChoice.choose(List.of(), Map.of(), Set.of(), Map.of()).any());
		assertFalse(ItemGroupChoice.choose(null, null, null, null).any());
	}

	/**
	 * The wiki's tier order is used as given and never re-sorted.
	 *
	 * <p>That order is the ranking. If this ever sorted by name, "Bronze"
	 * would outrank "Rune" alphabetically and the panel would confidently
	 * recommend the worst pickaxe in the set.
	 */
	@Test
	public void tierOrderIsTheWikisNotAlphabetical()
	{
		List<QuestGraph.Alternative> usable = ItemGroupChoice.usable(pickaxes(),
			levels(99, 99), Set.of());

		assertEquals(List.of("Bronze pickaxe", "Mithril pickaxe", "Rune pickaxe",
			"Dragon pickaxe"), usable.stream().map(QuestGraph.Alternative::name)
				.collect(java.util.stream.Collectors.toList()));
	}

	/**
	 * The real Pickaxe table, including the tail that broke the first
	 * version of this.
	 *
	 * <p>bronze .. crystal ascends; gilded, 3rd Age and infernal are appended
	 * after it and restate lower requirements. Taken as one ladder, "the last
	 * usable row" recommended a gilded pickaxe to anyone who could use a rune
	 * one.
	 */
	private static List<QuestGraph.Alternative> realPickaxes()
	{
		return alternatives("["
			+ "{\"name\":\"Bronze pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":1},{\"skill\":\"Attack\",\"level\":1}]},"
			+ "{\"name\":\"Iron pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":1},{\"skill\":\"Attack\",\"level\":1}]},"
			+ "{\"name\":\"Rune pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":41},{\"skill\":\"Attack\",\"level\":40}]},"
			+ "{\"name\":\"Crystal pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":71},{\"skill\":\"Attack\",\"level\":70}]},"
			+ "{\"name\":\"Gilded pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":41},{\"skill\":\"Attack\",\"level\":40}]},"
			+ "{\"name\":\"Infernal pickaxe\",\"requirements\":"
			+ "[{\"skill\":\"Mining\",\"level\":61},{\"skill\":\"Attack\",\"level\":60}]}"
			+ "]");
	}

	/**
	 * A gilded pickaxe is never the recommendation.
	 *
	 * <p>This is the bug, and it was caught by a test rather than in play. It
	 * states the rune pickaxe's own 41 Mining and sits later in the table, so
	 * "the last usable row" chose it: not a better pickaxe, just a far rarer
	 * one, and exactly the absurd purchase the second-choice rule exists to
	 * soften.
	 */
	@Test
	public void anOrnamentalVariantIsNeverTheRecommendation()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(realPickaxes(),
			levels(41, 40), Set.of(), Map.of());

		assertEquals("Rune pickaxe", choice.best().name());
	}

	/**
	 * Within the ladder, the later row really is the better item.
	 *
	 * <p>Bronze and iron both need level 1; iron is listed after because it
	 * is the better tool. The fix for the gilded case must not invert this --
	 * an earlier attempt broke exactly here, recommending a bronze pickaxe to
	 * everybody.
	 */
	@Test
	public void withinTheLadderLaterMeansBetter()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(realPickaxes(),
			levels(1, 1), Set.of(), Map.of());

		assertEquals("Iron pickaxe", choice.best().name());
	}

	/**
	 * A variant already owned still satisfies the requirement.
	 *
	 * <p>Excluded from what to buy, not from what counts: an infernal pickaxe
	 * in the bank is a pickaxe, and telling its owner to go and buy a rune one
	 * would be the list ignoring what it was asked to check first.
	 */
	@Test
	public void anOwnedVariantStillCounts()
	{
		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(realPickaxes(),
			levels(99, 99), Set.of(), Map.of("infernal pickaxe", 1L));

		assertTrue(choice.held());
		assertEquals("Infernal pickaxe", choice.best().name());
	}

	/** The ladder stops at the first row whose requirement goes backwards. */
	@Test
	public void theLadderIsTheMonotonicPrefix()
	{
		List<QuestGraph.Alternative> ladder = ItemGroupChoice.ladderOf(realPickaxes());

		assertEquals(List.of("Bronze pickaxe", "Iron pickaxe", "Rune pickaxe",
			"Crystal pickaxe"), ladder.stream().map(QuestGraph.Alternative::name)
			.collect(java.util.stream.Collectors.toList()));
	}

	/**
	 * A tool's combat requirement is recognised as wield-only.
	 *
	 * <p>You do not wield a pickaxe to mine with it: one in the inventory
	 * mines exactly as well as one in the weapon slot, so the Attack column
	 * gates equipping and nothing else. Counting it capped an account with 41
	 * Mining and 1 Attack at an iron pickaxe when a rune one works fine.
	 */
	@Test
	public void aToolsCombatRequirementIsWieldOnly()
	{
		assertEquals(Set.of("Attack"), ItemGroupChoice.wieldOnlySkills(pickaxes()));
	}

	/**
	 * A set with no combat requirement has nothing to ignore.
	 *
	 * <p>Firemaking genuinely gates lighting a lantern, so a light source's
	 * requirement binds in full.
	 */
	@Test
	public void aNonCombatRequirementIsNeverIgnored()
	{
		List<QuestGraph.Alternative> lightSources = alternatives("["
			+ "{\"name\":\"Lit candle\",\"requirements\":"
			+ "[{\"skill\":\"Firemaking\",\"level\":1}]},"
			+ "{\"name\":\"Bullseye lantern\",\"requirements\":"
			+ "[{\"skill\":\"Firemaking\",\"level\":49}]}]");

		assertTrue(ItemGroupChoice.wieldOnlySkills(lightSources).isEmpty());

		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(lightSources,
			Map.of("Firemaking", 1), ItemGroupChoice.wieldOnlySkills(lightSources), Map.of());
		assertEquals("Lit candle", choice.best().name());
	}

	/**
	 * A set that states <em>only</em> combat skills is a weapon, and a weapon
	 * really does have to be wielded.
	 *
	 * <p>This is why the rule is derived from the set rather than hard-coded
	 * to Attack: a blanket "ignore Attack" would hand somebody a rune scimitar
	 * they cannot hold the moment a weapon becomes a group.
	 */
	@Test
	public void aWeaponsCombatRequirementIsNotIgnored()
	{
		List<QuestGraph.Alternative> weapons = alternatives("["
			+ "{\"name\":\"Bronze sword\",\"requirements\":"
			+ "[{\"skill\":\"Attack\",\"level\":1}]},"
			+ "{\"name\":\"Rune sword\",\"requirements\":"
			+ "[{\"skill\":\"Attack\",\"level\":40}]}]");

		assertTrue(ItemGroupChoice.wieldOnlySkills(weapons).isEmpty());

		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(weapons,
			Map.of("Attack", 1), ItemGroupChoice.wieldOnlySkills(weapons), Map.of());
		assertEquals("Bronze sword", choice.best().name());
	}

	/**
	 * End to end: the capped-Attack account gets the pickaxe it can actually
	 * use.
	 */
	@Test
	public void aCappedAttackDoesNotCapAPickaxe()
	{
		List<QuestGraph.Alternative> set = pickaxes();

		ItemGroupChoice.Choice choice = ItemGroupChoice.choose(set,
			Map.of("Mining", 41, "Attack", 1),
			ItemGroupChoice.wieldOnlySkills(set), Map.of());

		assertEquals("Rune pickaxe", choice.best().name());
	}
}
