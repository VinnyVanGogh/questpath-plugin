package com.vinnyvangogh.questpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds Quest Route's spoken lines out of the bundled word list
 * ({@code voice/lines.tsv}): fixed phrases, numbers, one clip per item name
 * and one per shop, joined in order. "Looking for" + "30" + "air runes" +
 * "and" + "an" + "air talisman" + "at" + "Aubury's Rune Shop".
 *
 * <p>Ported from Combat Vitals' {@code VoiceLines}
 * (combat-vitals-plugin/src/main/java/com/vinnyvangogh/combatvitals/VoiceLines.java):
 * the same four-column list, read with {@code getResourceAsStream}, the same
 * rule that a line plays only when every one of its clips is bundled.
 *
 * <p>Pure: the list and whether a clip exists are handed in, no client.
 */
@Slf4j
final class VoiceLines
{
	/** The word list, a class resource beside this class. */
	static final String LIST = "voice/lines.tsv";

	static final String LOOKING_FOR = "looking-for";
	static final String AND = "and";
	static final String AT = "at";
	static final String NEARBY = "nearby";
	static final String A = "a";
	static final String AN = "an";
	static final String HUNDRED = "hundred";
	static final String THOUSAND = "thousand";

	/** One thing to buy: the item's name as the plan carries it, and how many. */
	static final class Want
	{
		final String name;
		final long quantity;

		Want(String name, long quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	private final Map<String, String> phrases = new HashMap<>();
	private final Map<String, String> numbers = new HashMap<>();
	private final Map<String, String> items = new HashMap<>();
	private final Map<String, String> plurals = new HashMap<>();
	private final Map<String, String> shops = new HashMap<>();
	/** Every clip id to the words it says. */
	private final Map<String, String> spoken = new HashMap<>();

	/** The bundled list, or an empty one (every line then falls back) if it cannot be read. */
	static VoiceLines load()
	{
		// getResourceAsStream, not getResource: the plugin ships inside a jar.
		try (InputStream in = VoiceLines.class.getResourceAsStream(LIST))
		{
			if (in == null)
			{
				log.warn("Voice word list {} is missing", LIST);
				return new VoiceLines();
			}
			return parse(new BufferedReader(new InputStreamReader(in, "UTF-8")));
		}
		catch (IOException e)
		{
			log.warn("Could not read voice word list", e);
			return new VoiceLines();
		}
	}

	static VoiceLines parse(BufferedReader reader) throws IOException
	{
		final VoiceLines lines = new VoiceLines();
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			final String[] cells = line.split("\t");
			if (cells.length == 4)
			{
				lines.add(cells[0].trim(), cells[1].trim(), cells[2].trim(), cells[3].trim());
			}
		}
		return lines;
	}

	private void add(String kind, String key, String clip, String words)
	{
		final Map<String, String> table = table(kind);
		if (table == null)
		{
			return;
		}
		table.put(key(key), clip);
		spoken.put(clip, words);
	}

	private Map<String, String> table(String kind)
	{
		switch (kind)
		{
			case "phrase":
				return phrases;
			case "number":
				return numbers;
			case "item":
				return items;
			case "plural":
				return plurals;
			case "shop":
				return shops;
			default:
				return null;
		}
	}

	/** Every clip id the list names, to the words it says. */
	Map<String, String> clips()
	{
		return Collections.unmodifiableMap(spoken);
	}

	boolean hasItem(String name)
	{
		return name != null && items.containsKey(key(name));
	}

	boolean hasPlural(String name)
	{
		return name != null && plurals.containsKey(key(name));
	}

	boolean hasShop(String name)
	{
		return name != null && shops.containsKey(key(name));
	}

	/** The class resource path of a clip id. */
	static String path(String clip)
	{
		return "voice/" + clip + ".wav";
	}

	/** Whether a clip's resource path is bundled in the jar. */
	static boolean bundled(String path)
	{
		try (InputStream in = VoiceLines.class.getResourceAsStream(path))
		{
			return in != null;
		}
		catch (IOException e)
		{
			return false;
		}
	}

	/**
	 * "Looking for a beer at the Blue Moon Inn": every want, in order, then
	 * the shop.
	 *
	 * @param wants  what to buy there, already filtered to still-needed items
	 * @param exists whether a clip's resource path is bundled
	 */
	Utterance shopLine(List<Want> wants, String shop, Predicate<String> exists)
	{
		final List<String> ids = new ArrayList<>();
		final StringBuilder text = new StringBuilder("Looking for ");
		ids.add(phrases.get(LOOKING_FOR));
		for (int i = 0; i < wants.size(); i++)
		{
			if (i > 0)
			{
				final boolean last = i == wants.size() - 1;
				if (last)
				{
					ids.add(phrases.get(AND));
				}
				text.append(last ? (wants.size() > 2 ? ", and " : " and ") : ", ");
			}
			want(wants.get(i), ids, text);
		}
		ids.add(phrases.get(AT));
		ids.add(shops.get(key(shop)));
		text.append(" at ").append(shopWords(shop));
		return finish(ids, text.toString(), exists);
	}

	/** "Bucket nearby". */
	Utterance floorLine(String item, Predicate<String> exists)
	{
		final List<String> ids = new ArrayList<>();
		ids.add(items.get(key(item)));
		ids.add(phrases.get(NEARBY));
		return finish(ids, item + " nearby", exists);
	}

	private void want(Want want, List<String> ids, StringBuilder text)
	{
		final String singular = items.get(key(want.name));
		if (want.quantity <= 1)
		{
			final String words = singular == null ? want.name : spoken.get(singular);
			final String article = article(words);
			ids.add(phrases.get(article));
			ids.add(singular);
			text.append(article).append(' ').append(midSentence(words));
			return;
		}
		count(want.quantity, ids);
		final String plural = plurals.get(key(want.name));
		// No plural row: the singular, "2 bucket", is better than silence.
		final String clip = plural != null ? plural : singular;
		ids.add(clip);
		text.append(want.quantity).append(' ').append(midSentence(clip == null ? want.name : spoken.get(clip)));
	}

	/** "Air talisman" as said inside a sentence: "air talisman". */
	private static String midSentence(String words)
	{
		if (words.length() > 1 && Character.isUpperCase(words.charAt(0)) && Character.isLowerCase(words.charAt(1)))
		{
			return Character.toLowerCase(words.charAt(0)) + words.substring(1);
		}
		return words;
	}

	/**
	 * The clips for a count: 1 to 100 are one clip each; past that,
	 * "3 thousand 2 hundred 50" out of the same clips.
	 */
	private void count(long n, List<String> ids)
	{
		if (n <= 100)
		{
			ids.add(numbers.get(Long.toString(n)));
			return;
		}
		if (n >= 1_000_000)
		{
			// Never a quest count; the line still says something.
			ids.add(null);
			return;
		}
		final long thousands = n / 1000;
		final long hundreds = n % 1000 / 100;
		final long rest = n % 100;
		if (thousands > 0)
		{
			count(thousands, ids);
			ids.add(phrases.get(THOUSAND));
		}
		if (hundreds > 0)
		{
			ids.add(numbers.get(Long.toString(hundreds)));
			ids.add(phrases.get(HUNDRED));
		}
		if (rest > 0)
		{
			ids.add(numbers.get(Long.toString(rest)));
		}
	}

	/** "an" before a vowel letter, "a" otherwise. */
	static String article(String words)
	{
		if (words == null || words.isEmpty())
		{
			return A;
		}
		return "aeiou".indexOf(Character.toLowerCase(words.charAt(0))) >= 0 ? AN : A;
	}

	private String shopWords(String shop)
	{
		final String clip = shops.get(key(shop));
		return clip == null ? shop : spoken.get(clip);
	}

	private static Utterance finish(List<String> ids, String text, Predicate<String> exists)
	{
		for (String id : ids)
		{
			if (id == null || !exists.test(path(id)))
			{
				return Utterance.textOnly(text);
			}
		}
		final List<String> paths = new ArrayList<>(ids.size());
		for (String id : ids)
		{
			paths.add(path(id));
		}
		return new Utterance(text, paths);
	}

	private static String key(String raw)
	{
		return raw.toLowerCase(Locale.ROOT);
	}
}
