package com.vinnyvangogh.questpath;

import java.util.List;
import java.util.Map;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One party member's quest state, as it crosses RuneLite's party relay.
 *
 * <p><b>The class's simple name is the wire type.</b> RuneLite's {@code
 * WSClient} registers and dispatches party messages by {@link
 * Class#getSimpleName()}, so renaming this class is a protocol break with
 * every other member's client -- including a rename that comes along with the
 * plugin's own display-name change. The name is deliberately specific enough
 * that no other hub plugin is likely to register the same one.
 *
 * <p>Public because RuneLite's event bus delivers it to a {@code @Subscribe}
 * method; the fields stay package-private and are read only through {@link
 * GroupMember#decode}, which is what applies the version check. Nothing else
 * should trust these fields directly -- they come from another client, which
 * may be running a different build.
 *
 * <p>Gson, through {@code WSClient}'s own instance, is the codec: null fields
 * are omitted from the JSON and unknown keys are ignored on the way in, which
 * is what lets a newer build add an optional field without bumping {@link
 * #version}. A change an older build would misread must bump it instead.
 */
public class QuestpathGroupState extends PartyMemberMessage
{
	/** The shape this message was written in. 0 (absent) is never valid. */
	int version;

	/**
	 * Set when a member turns sharing off: "forget what I sent you". Carries
	 * no account data at all -- every other field is null.
	 */
	boolean withdrawn;

	/** Names of finished quests, in the game's own spelling. */
	List<String> completed;

	/** Names of started, unfinished quests -- "Started [[X]]" prerequisites need these. */
	List<String> started;

	/** Real (unboosted) level per skill name. */
	Map<String, Integer> levels;

	/** Experience per skill name: a cap is judged on xp, not level. */
	Map<String, Integer> experience;

	/** The sender's build rules, so the group can see who is capped where. */
	List<BuildRule> rules;

	/**
	 * Held counts by lower-cased item name, for items some quest asks for --
	 * never the whole bank. Null when the sender has not opened any container
	 * yet, which is "unknown", not "holds nothing".
	 */
	Map<String, Long> held;

	/** Whether {@link #held} includes the bank, or only what is carried. */
	boolean bankSeen;
}
