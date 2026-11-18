package com.vinnyvangogh.questpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sentence building: which clips, in which order, and the words in chat.
 * A small hand-written list, so each case reads on its own.
 */
public class VoiceLinesTest
{
	private static final String LIST = String.join("\n",
		"# test list",
		"phrase\tlooking-for\tlooking-for\tLooking for",
		"phrase\tand\tand\tand",
		"phrase\tat\tat\tat",
		"phrase\tnearby\tnearby\tnearby",
		"phrase\ta\ta\ta",
		"phrase\tan\tan\tan",
		"phrase\thundred\thundred\thundred",
		"phrase\tthousand\tthousand\tthousand",
		"number\t1\tnum-1\t1",
		"number\t2\tnum-2\t2",
		"number\t3\tnum-3\t3",
		"number\t5\tnum-5\t5",
		"number\t30\tnum-30\t30",
		"number\t50\tnum-50\t50",
		"item\tbeer\titem-beer\tBeer",
		"item\tair rune\titem-air-rune\tAir rune",
		"item\tair talisman\titem-air-talisman\tAir talisman",
		"item\tbucket\titem-bucket\tBucket",
		"item\tegg\titem-egg\tEgg",
		"item\tcoins\titem-coins\tCoins",
		"plural\tair rune\titems-air-rune\tAir runes",
		"plural\tbeer\titems-beer\tBeers",
		"plural\tcoins\titem-coins\tCoins",
		"shop\tblue moon inn\tshop-blue-moon-inn\tthe Blue Moon Inn",
		"shop\taubury's rune shop.\tshop-aubury-s-rune-shop\tAubury's Rune Shop",
		"");

	private static final Predicate<String> ALL = path -> true;

	private VoiceLines voice;

	@Before
	public void load() throws IOException
	{
		voice = VoiceLines.parse(new BufferedReader(new StringReader(LIST)));
	}

	private static List<VoiceLines.Want> wants(Object... pairs)
	{
		List<VoiceLines.Want> out = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.add(new VoiceLines.Want((String) pairs[i], ((Number) pairs[i + 1]).longValue()));
		}
		return out;
	}

	private static List<String> paths(String... ids)
	{
		List<String> out = new ArrayList<>();
		for (String id : ids)
		{
			out.add(VoiceLines.path(id));
		}
		return out;
	}

	@Test
	public void oneItemTakesAnArticle()
	{
		Utterance line = voice.shopLine(wants("Beer", 1), "Blue Moon Inn", ALL);
		assertEquals("Looking for a beer at the Blue Moon Inn", line.text);
		assertEquals(paths("looking-for", "a", "item-beer", "at", "shop-blue-moon-inn"), line.clips);
	}

	@Test
	public void twoItemsJoinWithAnd()
	{
		Utterance line = voice.shopLine(wants("Air rune", 30, "Air talisman", 1), "Aubury's Rune Shop.", ALL);
		assertEquals("Looking for 30 air runes and an air talisman at Aubury's Rune Shop", line.text);
		assertEquals(paths("looking-for", "num-30", "items-air-rune", "and", "an", "item-air-talisman", "at",
			"shop-aubury-s-rune-shop"), line.clips);
	}

	@Test
	public void manyItemsSayAndOnlyBeforeTheLast()
	{
		Utterance line = voice.shopLine(wants("Bucket", 1, "Egg", 2, "Beer", 3), "Blue Moon Inn", ALL);
		assertEquals("Looking for a bucket, 2 egg, and 3 beers at the Blue Moon Inn", line.text);
		assertEquals(paths("looking-for", "a", "item-bucket", "num-2", "item-egg", "and", "num-3", "items-beer",
			"at", "shop-blue-moon-inn"), line.clips);
		assertEquals("one \"and\", before the last", 1, Collections.frequency(line.clips, VoiceLines.path("and")));
	}

	@Test
	public void anBeforeAVowelAndABeforeAConsonant()
	{
		assertEquals("an", VoiceLines.article("Air talisman"));
		assertEquals("an", VoiceLines.article("egg"));
		assertEquals("a", VoiceLines.article("Bucket"));
		assertEquals("a", VoiceLines.article(""));
	}

	@Test
	public void quantitiesPastOneHundredAreBuiltFromParts()
	{
		Utterance line = voice.shopLine(wants("Coins", 3250), "Blue Moon Inn", ALL);
		assertEquals("Looking for 3250 coins at the Blue Moon Inn", line.text);
		assertEquals(paths("looking-for", "num-3", "thousand", "num-2", "hundred", "num-50", "item-coins", "at",
			"shop-blue-moon-inn"), line.clips);
	}

	@Test
	public void aMissingClipFallsBackToTextNeverToSilence()
	{
		Predicate<String> noBeer = path -> !path.equals(VoiceLines.path("item-beer"));
		Utterance line = voice.shopLine(wants("Beer", 1), "Blue Moon Inn", noBeer);
		assertTrue(line.fallback());
		assertEquals("Looking for a beer at the Blue Moon Inn", line.text);
	}

	@Test
	public void anItemTheListDoesNotKnowFallsBackWithItsOwnName()
	{
		Utterance line = voice.shopLine(wants("Mystery box", 1), "Blue Moon Inn", ALL);
		assertTrue(line.fallback());
		assertEquals("Looking for a mystery box at the Blue Moon Inn", line.text);
	}

	@Test
	public void floorLineSaysTheItemThenNearby()
	{
		Utterance line = voice.floorLine("Bucket", ALL);
		assertEquals("Bucket nearby", line.text);
		assertEquals(paths("item-bucket", "nearby"), line.clips);
		assertFalse(line.fallback());
	}

	@Test
	public void floorLineForAnUnknownItemFallsBack()
	{
		assertTrue(voice.floorLine("Mystery box", ALL).fallback());
	}

	@Test
	public void keysMatchWhateverTheCase()
	{
		assertTrue(voice.hasItem("AIR RUNE"));
		assertTrue(voice.hasPlural("air rune"));
		assertTrue(voice.hasShop("Blue Moon Inn"));
		assertEquals(Arrays.asList(VoiceLines.path("item-bucket"), VoiceLines.path("nearby")),
			voice.floorLine("bUcKeT", ALL).clips);
	}
}
