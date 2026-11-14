package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The party message's wire shape: what one member's client encodes, and what
 * another member's client -- possibly on a different Quest Route build -- makes
 * of it.
 *
 * <p>Round-tripped through a plain {@link Gson}, because that is what
 * RuneLite's own {@code WSClient} serialises party messages with: the JSON in
 * these tests is the JSON on the wire.
 */
public class GroupMemberTest
{
	private final Gson gson = new Gson();

	private static Map<String, Integer> levels(Object... pairs)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return out;
	}

	private QuestpathGroupState sample()
	{
		Map<String, Long> held = new LinkedHashMap<>();
		held.put("bucket of milk", 1L);
		held.put("coins", 5000L);
		held.put("abyssal whip", 1L);
		Set<String> relevant = new HashSet<>(Arrays.asList("bucket of milk", "egg"));
		return GroupMember.encode(
			new HashSet<>(Collections.singletonList("Cook's Assistant")),
			new HashSet<>(Collections.singletonList("Dragon Slayer I")),
			levels("Defence", 1, "Attack", 60),
			levels("Defence", 0, "Attack", 273742),
			Collections.singletonList(BuildRule.local("Defence", BuildRule.HARD_CAP, 1, "")),
			held, true, relevant);
	}

	@Test
	public void encodeThenDecodeThroughJsonKeepsEveryField()
	{
		String json = gson.toJson(sample());
		GroupMember member = GroupMember.decode(7L, "Alice", gson.fromJson(json, QuestpathGroupState.class));

		assertNotNull(member);
		assertTrue(member.compatible());
		assertEquals(7L, member.memberId());
		assertEquals("Alice", member.name());
		assertTrue(member.state().isComplete("Cook's Assistant"));
		assertTrue(member.state().hasStarted("Dragon Slayer I"));
		assertFalse(member.state().isComplete("Dragon Slayer I"));
		assertEquals(60, member.state().levelIn("Attack"));
		assertEquals(273742L, member.state().experienceIn("Attack"));
		assertEquals(1, member.rules().size());
		assertEquals(BuildRule.HARD_CAP, member.rules().get(0).type());
		assertEquals(1, member.rules().get(0).value());
		assertTrue(member.itemsKnown());
		assertTrue(member.bankSeen());
		assertEquals(1L, member.held("Bucket of milk"));
	}

	/**
	 * Only items some quest asks for leave the client. A member's whole bank is
	 * not the group's business, and it is not what the split needs either.
	 */
	@Test
	public void onlyQuestRelevantItemsAreSent()
	{
		QuestpathGroupState message = sample();
		assertEquals(Collections.singleton("bucket of milk"), message.held.keySet());
		assertFalse(gson.toJson(message).contains("whip"));
		assertFalse(gson.toJson(message).contains("coins"));
	}

	/**
	 * A newer Quest Route that adds a field must still be readable here, as
	 * long as it kept the version number: unknown keys are ignored.
	 */
	@Test
	public void unknownFieldsFromANewerSenderAreIgnored()
	{
		String json = "{\"version\":1,\"completed\":[\"Cook's Assistant\"],"
			+ "\"levels\":{\"Defence\":1},\"futureField\":{\"anything\":[1,2,3]}}";
		GroupMember member = GroupMember.decode(1L, "Bob", gson.fromJson(json, QuestpathGroupState.class));

		assertNotNull(member);
		assertTrue(member.compatible());
		assertTrue(member.state().isComplete("Cook's Assistant"));
	}

	/**
	 * Missing fields decode as empty, never as null, and missing items decode
	 * as "not shared" -- distinct from "holds nothing", which would tell the
	 * split this member needs to buy everything.
	 */
	@Test
	public void missingFieldsDecodeAsEmptyAndItemsAsUnknown()
	{
		GroupMember member = GroupMember.decode(1L, "Bob",
			gson.fromJson("{\"version\":1}", QuestpathGroupState.class));

		assertNotNull(member);
		assertTrue(member.compatible());
		assertTrue(member.rules().isEmpty());
		assertFalse(member.itemsKnown());
		assertEquals(0L, member.held("Egg"));
		assertFalse(member.state().isComplete("Cook's Assistant"));
	}

	/**
	 * A shape this build cannot read is kept as a member who says so, never
	 * parsed into a confident "has done nothing".
	 */
	@Test
	public void aVersionThisBuildCannotReadIsIncompatibleNotEmpty()
	{
		GroupMember newer = GroupMember.decode(1L, "Bob",
			gson.fromJson("{\"version\":99,\"completed\":[\"Cook's Assistant\"]}", QuestpathGroupState.class));
		GroupMember unversioned = GroupMember.decode(2L, "Cy",
			gson.fromJson("{\"completed\":[\"Cook's Assistant\"]}", QuestpathGroupState.class));

		assertNotNull(newer);
		assertFalse(newer.compatible());
		assertEquals(99, newer.version());
		assertFalse("an unreadable member must not be judged", newer.state().isComplete("Cook's Assistant"));
		assertNotNull(unversioned);
		assertFalse(unversioned.compatible());
	}

	@Test
	public void aWithdrawalDecodesToNothingAndCarriesNoData()
	{
		QuestpathGroupState withdrawal = GroupMember.withdrawal();
		String json = gson.toJson(withdrawal);

		assertNull(GroupMember.decode(1L, "Bob", gson.fromJson(json, QuestpathGroupState.class)));
		assertFalse(json.contains("completed"));
		assertFalse(json.contains("levels"));
		assertFalse(json.contains("held"));
	}

	@Test
	public void decodingNullIsNothing()
	{
		assertNull(GroupMember.decode(1L, "Bob", null));
	}

	@Test
	public void rulesWithAnUnknownSkillAreKeptVerbatim()
	{
		List<BuildRule> rules = GroupMember.decode(1L, "Bob", gson.fromJson(
			"{\"version\":1,\"rules\":[{\"skill\":\"Sailing\",\"type\":\"NO_XP\"}]}",
			QuestpathGroupState.class)).rules();
		assertEquals(1, rules.size());
		assertEquals("Sailing", rules.get(0).skillName());
	}
}
