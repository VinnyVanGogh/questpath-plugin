package com.vinnyvangogh.questpath;

import com.vinnyvangogh.questpath.groupshop.AccountKind;
import com.vinnyvangogh.questpath.groupshop.GroupStorage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One party member as this client understands them: decoded from their
 * {@link QuestpathGroupState}, or built from the local account.
 *
 * <p>The only way in from the wire is {@link #decode}, and it never guesses.
 * A message in a shape this build cannot read becomes an <em>incompatible</em>
 * member -- listed, named, and excluded from planning -- rather than a member
 * who has apparently done nothing, because "Bob has finished no quests" is a
 * confident wrong answer and "Bob runs a Quest Route this build can't read" is
 * the truth.
 */
final class GroupMember
{
	/** The shape {@link #encode} writes. */
	static final int WIRE_VERSION = 1;

	/** The newest shape {@link #decode} reads. Optional additions keep the version; breaks bump it. */
	static final int MAX_READABLE = 1;

	private final long memberId;
	private final String name;
	private final int version;
	private final boolean compatible;
	private final Set<String> completed;
	private final PlayerState state;
	private final List<BuildRule> rules;
	private final Map<String, Long> held;
	private final boolean bankSeen;
	// Set once by decode, after construction; see withShopping.
	private List<String> route = Collections.emptyList();
	private AccountKind kind = AccountKind.UNKNOWN;
	private GroupStorage groupStorage;

	private GroupMember(long memberId, String name, int version, boolean compatible, Set<String> completed,
		PlayerState state, List<BuildRule> rules, Map<String, Long> held, boolean bankSeen)
	{
		this.memberId = memberId;
		this.name = name == null ? "" : name;
		this.version = version;
		this.compatible = compatible;
		this.completed = completed;
		this.state = state;
		this.rules = rules;
		this.held = held;
		this.bankSeen = bankSeen;
	}

	/**
	 * The message this client sends.
	 *
	 * @param held every held count by lower-cased name, or null when no
	 *     container has been seen yet
	 * @param relevantItems lower-cased names of items any quest asks for;
	 *     nothing outside this set is sent
	 */
	static QuestpathGroupState encode(Set<String> completed, Set<String> started, Map<String, Integer> levels,
		Map<String, Integer> experience, List<BuildRule> rules, Map<String, Long> held, boolean bankSeen,
		Set<String> relevantItems)
	{
		QuestpathGroupState message = new QuestpathGroupState();
		message.version = WIRE_VERSION;
		message.completed = sorted(completed);
		message.started = sorted(started);
		message.levels = new LinkedHashMap<>(levels == null ? Collections.emptyMap() : levels);
		message.experience = new LinkedHashMap<>(experience == null ? Collections.emptyMap() : experience);
		message.rules = new ArrayList<>(rules == null ? Collections.emptyList() : rules);
		if (held != null)
		{
			message.held = relevantCounts(held, relevantItems);
			message.bankSeen = bankSeen;
		}
		return message;
	}

	/**
	 * Adds what the group shopping list needs to a message from {@link
	 * #encode}: the route, the account's trading rules, and group storage.
	 * All optional on the wire, so older builds read the message unchanged.
	 *
	 * @param route quest names on the active route, or empty for none
	 * @param storage group storage counts, or null when not seen this session
	 */
	static QuestpathGroupState withShopping(QuestpathGroupState message, List<String> route, AccountKind kind,
		Map<String, Long> storage, long storageAt, Set<String> relevantItems)
	{
		if (message == null)
		{
			return null;
		}
		message.route = route == null || route.isEmpty() ? null : new ArrayList<>(route);
		message.accountType = kind == null || kind == AccountKind.UNKNOWN ? null : kind.name();
		if (storage != null && kind == AccountKind.GROUP_IRONMAN)
		{
			message.groupStorage = relevantCounts(storage, relevantItems);
			message.groupStorageAt = storageAt;
		}
		return message;
	}

	/** Only items some quest asks for, never the whole bank; lower-cased, positive counts only. */
	private static Map<String, Long> relevantCounts(Map<String, Long> counts, Set<String> relevantItems)
	{
		Map<String, Long> relevant = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : counts.entrySet())
		{
			String key = entry.getKey() == null ? null : entry.getKey().toLowerCase();
			if (key != null && relevantItems.contains(key) && entry.getValue() != null && entry.getValue() > 0)
			{
				relevant.merge(key, entry.getValue(), Long::sum);
			}
		}
		return relevant;
	}

	/** "Forget me": sent once when a member turns sharing off. No account data. */
	static QuestpathGroupState withdrawal()
	{
		QuestpathGroupState message = new QuestpathGroupState();
		message.version = WIRE_VERSION;
		message.withdrawn = true;
		return message;
	}

	/**
	 * What another member sent, or null when there is nothing to keep --
	 * no message, or a withdrawal.
	 */
	static GroupMember decode(long memberId, String name, QuestpathGroupState message)
	{
		if (message == null || message.withdrawn)
		{
			return null;
		}
		if (message.version < 1 || message.version > MAX_READABLE)
		{
			return new GroupMember(memberId, name, message.version, false, Collections.emptySet(),
				new PlayerState(null, null, null, null), Collections.emptyList(), null, false);
		}

		Set<String> completed = names(message.completed);
		Map<String, Long> experience = new HashMap<>();
		if (message.experience != null)
		{
			for (Map.Entry<String, Integer> entry : message.experience.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null)
				{
					experience.put(entry.getKey(), entry.getValue().longValue());
				}
			}
		}
		Map<String, Integer> levels = new HashMap<>();
		if (message.levels != null)
		{
			for (Map.Entry<String, Integer> entry : message.levels.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null)
				{
					levels.put(entry.getKey(), entry.getValue());
				}
			}
		}
		List<BuildRule> rules = new ArrayList<>();
		if (message.rules != null)
		{
			for (BuildRule rule : message.rules)
			{
				if (rule != null && !rule.skillName().isEmpty())
				{
					rules.add(rule);
				}
			}
		}
		PlayerState state = new PlayerState(levels, experience, completed, names(message.started), rules);
		GroupMember member = new GroupMember(memberId, name, message.version, true, completed, state,
			Collections.unmodifiableList(rules), counts(message.held), message.bankSeen);
		member.route = message.route == null ? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(message.route));
		member.kind = AccountKind.parse(message.accountType);
		Map<String, Long> storage = counts(message.groupStorage);
		member.groupStorage = storage == null || member.kind != AccountKind.GROUP_IRONMAN ? null
			: new GroupStorage(storage, message.groupStorageAt, name);
		return member;
	}

	/** Lower-cased counts, or null for null -- "not shared", never "holds nothing". */
	private static Map<String, Long> counts(Map<String, Long> raw)
	{
		if (raw == null)
		{
			return null;
		}
		Map<String, Long> out = new HashMap<>();
		for (Map.Entry<String, Long> entry : raw.entrySet())
		{
			if (entry.getKey() != null && entry.getValue() != null)
			{
				out.merge(entry.getKey().toLowerCase(), entry.getValue(), Long::sum);
			}
		}
		return out;
	}

	private static Set<String> names(Collection<String> raw)
	{
		Set<String> out = new TreeSet<>();
		if (raw != null)
		{
			for (String name : raw)
			{
				if (name != null)
				{
					out.add(name);
				}
			}
		}
		return out;
	}

	private static List<String> sorted(Collection<String> raw)
	{
		return new ArrayList<>(names(raw));
	}

	long memberId()
	{
		return memberId;
	}

	String name()
	{
		return name;
	}

	/** The wire version this member's client wrote. */
	int version()
	{
		return version;
	}

	/** False when this build cannot read the member's message; such a member is never planned for. */
	boolean compatible()
	{
		return compatible;
	}

	/** Finished quest names, as sent. */
	Set<String> completed()
	{
		return Collections.unmodifiableSet(completed);
	}

	/** The member as the quest classifier sees them, build rules included. */
	PlayerState state()
	{
		return state;
	}

	List<BuildRule> rules()
	{
		return rules;
	}

	/** Whether this member shared item counts at all. */
	boolean itemsKnown()
	{
		return held != null;
	}

	/** Whether the shared counts include the bank, rather than only what was carried. */
	boolean bankSeen()
	{
		return bankSeen;
	}

	/** The member's count of one item, by name; 0 when not held or not shared. */
	long held(String itemName)
	{
		if (held == null || itemName == null)
		{
			return 0L;
		}
		Long count = held.get(itemName.toLowerCase());
		return count == null ? 0L : count;
	}

	/** Every shared count, lower-cased, or null when none were shared. */
	Map<String, Long> heldCounts()
	{
		return held == null ? null : Collections.unmodifiableMap(held);
	}

	/** Quest names on the member's active route; empty when they have none or did not say. */
	List<String> route()
	{
		return route;
	}

	/** The member's trading rules; {@link AccountKind#UNKNOWN} from a build that does not say. */
	AccountKind kind()
	{
		return kind;
	}

	/** The group storage this member last saw, or null. Only ever set for a group ironman. */
	GroupStorage groupStorage()
	{
		return groupStorage;
	}
}
