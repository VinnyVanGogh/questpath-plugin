package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.client.events.PluginMessage;

/**
 * Quest Route's end of the conversation with Build Guard, which owns the
 * build rules (caps, no-XP skills, goals, lamp advice, warnings).
 *
 * <p>The two plugins never call each other. They post {@link PluginMessage}s
 * on RuneLite's event bus, each tagged {@code v=1}. Messages <em>to</em> Build
 * Guard carry the namespace {@value #BUILD_GUARD}; messages <em>to</em> Quest
 * Route carry {@value #QUEST_ROUTE}. Unknown versions, names and fields are
 * ignored, so either side can grow without breaking the other.
 *
 * <ul>
 *   <li>to Build Guard: {@code hello}, {@code rules-request}, {@code open-panel}
 *       ({@code section}), {@code check} ({@code skill}, {@code xp_gain}),
 *       {@code import-rules} (the one-time migration of rules saved here).</li>
 *   <li>to Quest Route: {@code present} (also the answer to {@code hello}),
 *       {@code hello}, {@code rules} ({@code account_type}, {@code rules} as a
 *       list of {@code skill}/{@code kind}/{@code level}), {@code check-result}
 *       ({@code allowed}, {@code rule}, {@code margin}).</li>
 * </ul>
 *
 * <p>This class holds only state and shapes; the plugin owns timing and the
 * client thread. It is thread-safe: messages arrive on whichever thread posts
 * them and the panel reads from the Swing thread.
 */
final class BuildGuardLink
{
	static final String BUILD_GUARD = "build-guard";
	static final String QUEST_ROUTE = "quest-route";
	static final int VERSION = 1;

	static final String HELLO = "hello";
	static final String PRESENT = "present";
	static final String RULES = "rules";
	static final String RULES_REQUEST = "rules-request";
	static final String OPEN_PANEL = "open-panel";
	static final String CHECK = "check";
	static final String CHECK_RESULT = "check-result";
	static final String IMPORT_RULES = "import-rules";

	/** Where the player starts to write rules in Build Guard's own panel. */
	static final String SECTION_RULES = "rules";

	private final Consumer<Object> poster;

	private volatile boolean present;
	private volatile boolean rulesKnown;
	private volatile String accountType = "";
	private volatile List<BuildRule> rules = Collections.emptyList();
	private volatile CheckResult lastCheck;

	/**
	 * @param poster posts to the event bus; in the plugin it is {@link
	 *     ShortestPathBridge#clientThreadEventPoster}, so messages always land on the client thread
	 */
	BuildGuardLink(Consumer<Object> poster)
	{
		this.poster = poster;
	}

	/** The answer to a {@code check}. */
	static final class CheckResult
	{
		final String skill;
		final boolean allowed;
		final String rule;
		final int margin;

		CheckResult(String skill, boolean allowed, String rule, int margin)
		{
			this.skill = skill;
			this.allowed = allowed;
			this.rule = rule;
			this.margin = margin;
		}
	}

	/** Whether Build Guard has said it is installed this session. */
	boolean present()
	{
		return present;
	}

	/** Whether Build Guard has sent its rules (an empty set counts: that is its answer). */
	boolean rulesKnown()
	{
		return rulesKnown;
	}

	/** The rules Build Guard last sent; empty until it has. */
	List<BuildRule> rules()
	{
		return rules;
	}

	/** Build Guard's account type ("main", "iron", ...), or empty when it has not said. */
	String accountType()
	{
		return accountType;
	}

	CheckResult lastCheck()
	{
		return lastCheck;
	}

	/** Forgets everything; for plugin shutdown, so a restart does not trust a stale answer. */
	void reset()
	{
		present = false;
		rulesKnown = false;
		accountType = "";
		rules = Collections.emptyList();
		lastCheck = null;
	}

	/** On start: announce ourselves and ask for the rules. */
	void sendStartup()
	{
		send(HELLO, new HashMap<>());
		send(RULES_REQUEST, new HashMap<>());
	}

	void requestRules()
	{
		send(RULES_REQUEST, new HashMap<>());
	}

	void openPanel(String section)
	{
		Map<String, Object> data = new HashMap<>();
		if (section != null && !section.isEmpty())
		{
			data.put("section", section);
		}
		send(OPEN_PANEL, data);
	}

	/** Asks whether {@code xpGain} experience in {@code skill} breaks a rule; the reply is a check-result. */
	void check(String skill, int xpGain)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("skill", skill);
		data.put("xp_gain", xpGain);
		send(CHECK, data);
	}

	/** Hands saved rules to Build Guard, once; it decides whether to keep them. */
	void importRules(List<BuildRule> saved)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("rules", encode(saved));
		send(IMPORT_RULES, data);
	}

	/**
	 * The one-time hand-over of rules saved in Quest Route.
	 *
	 * <p>Does nothing unless Build Guard has said it is installed, so a player
	 * without it keeps the rules as "Your saved build" and the hand-over
	 * happens the day it appears. An account with nothing saved is marked done
	 * without sending anything.
	 *
	 * @return whether the hand-over was done now
	 */
	boolean migrateOnce(List<BuildRule> saved, boolean alreadyMigrated, Runnable markMigrated)
	{
		if (!present || alreadyMigrated)
		{
			return false;
		}
		if (!saved.isEmpty())
		{
			importRules(saved);
		}
		markMigrated.run();
		return true;
	}

	/**
	 * Reads one message off the bus.
	 *
	 * @return true when something this link reports changed (presence or
	 *     rules), so the caller knows to redraw
	 */
	boolean handle(String namespace, String name, Map<String, Object> data)
	{
		if (!QUEST_ROUTE.equals(namespace) || name == null || data == null || versionOf(data) != VERSION)
		{
			return false;
		}
		switch (name)
		{
			case HELLO:
			case PRESENT:
			{
				boolean changed = !present;
				present = true;
				if (HELLO.equals(name))
				{
					send(PRESENT, new HashMap<>());
				}
				return changed;
			}
			case RULES:
			{
				present = true;
				rules = decode(data.get("rules"));
				Object type = data.get("account_type");
				accountType = type instanceof String ? (String) type : "";
				rulesKnown = true;
				return true;
			}
			case CHECK_RESULT:
			{
				Object skill = data.get("skill");
				Object allowed = data.get("allowed");
				Object rule = data.get("rule");
				Object margin = data.get("margin");
				lastCheck = new CheckResult(skill instanceof String ? (String) skill : "",
					!Boolean.FALSE.equals(allowed), rule instanceof String ? (String) rule : "",
					margin instanceof Number ? ((Number) margin).intValue() : 0);
				return false;
			}
			default:
				return false;
		}
	}

	private static int versionOf(Map<String, Object> data)
	{
		Object v = data.get("v");
		return v instanceof Number ? ((Number) v).intValue() : -1;
	}

	private void send(String name, Map<String, Object> data)
	{
		data.put("v", VERSION);
		data.put("source", QUEST_ROUTE);
		poster.accept(new PluginMessage(BUILD_GUARD, name, data));
	}

	/** Our rules as the wire shape: skill, kind, level. */
	static List<Map<String, Object>> encode(List<BuildRule> rules)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (BuildRule rule : rules)
		{
			Map<String, Object> row = new HashMap<>();
			row.put("skill", rule.skillName().toLowerCase(Locale.ROOT));
			row.put("kind", kindOf(rule.type()));
			if (rule.hasValue())
			{
				row.put("level", rule.value());
			}
			out.add(row);
		}
		return out;
	}

	/** Build Guard's rule list, tolerant of spelling and of rows it cannot read, which are skipped. */
	static List<BuildRule> decode(Object raw)
	{
		if (!(raw instanceof List))
		{
			return Collections.emptyList();
		}
		List<BuildRule> out = new ArrayList<>();
		for (Object item : (List<?>) raw)
		{
			if (!(item instanceof Map))
			{
				continue;
			}
			Map<?, ?> row = (Map<?, ?>) item;
			Object skill = row.get("skill");
			Object kind = row.get("kind") != null ? row.get("kind") : row.get("type");
			String type = typeOf(kind);
			if (!(skill instanceof String) || type == null)
			{
				continue;
			}
			Object level = row.get("level") != null ? row.get("level")
				: row.get("cap") != null ? row.get("cap") : row.get("value");
			int value = level instanceof Number ? ((Number) level).intValue() : 0;
			out.add(BuildRule.local(capitalise((String) skill), type, value, ""));
		}
		return out;
	}

	private static String kindOf(String type)
	{
		switch (type)
		{
			case BuildRule.NO_XP:
				return "no-xp";
			case BuildRule.HARD_CAP:
				return "hard-cap";
			case BuildRule.SOFT_CAP:
				return "soft-cap";
			default:
				return "goal";
		}
	}

	/** The rule type for a wire kind, or null for one this build does not know. */
	private static String typeOf(Object kind)
	{
		if (!(kind instanceof String))
		{
			return null;
		}
		String k = ((String) kind).toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
		switch (k)
		{
			case "no-xp":
			case "noxp":
				return BuildRule.NO_XP;
			case "hard-cap":
			case "hard":
				return BuildRule.HARD_CAP;
			case "soft-cap":
			case "soft":
				return BuildRule.SOFT_CAP;
			case "goal":
				return BuildRule.GOAL;
			default:
				return null;
		}
	}

	private static String capitalise(String skill)
	{
		String s = skill.trim();
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
	}
}
