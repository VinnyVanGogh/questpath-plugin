package com.vinnyvangogh.questpath;

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
 * confident wrong answer and "Bob runs a Quest Path this build can't read" is
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
			Map<String, Long> relevant = new LinkedHashMap<>();
			for (Map.Entry<String, Long> entry : held.entrySet())
			{
				String key = entry.getKey() == null ? null : entry.getKey().toLowerCase();
				if (key != null && relevantItems.contains(key) && entry.getValue() != null && entry.getValue() > 0)
				{
					relevant.merge(key, entry.getValue(), Long::sum);
				}
			}
			message.held = relevant;
			message.bankSeen = bankSeen;
		}
		return message;
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
		Map<String, Long> held = null;
		if (message.held != null)
		{
			held = new HashMap<>();
			for (Map.Entry<String, Long> entry : message.held.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null)
				{
					held.merge(entry.getKey().toLowerCase(), entry.getValue(), Long::sum);
				}
			}
		}
		PlayerState state = new PlayerState(levels, experience, completed, names(message.started), rules);
		return new GroupMember(memberId, name, message.version, true, completed, state,
			Collections.unmodifiableList(rules), held, message.bankSeen);
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
}
