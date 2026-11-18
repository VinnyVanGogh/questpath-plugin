package com.vinnyvangogh.questpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The shop line from the plan's own totals, and the approach edge that says
 * it: once per approach, every still-needed item, owned ones left out.
 */
public class ShopVoiceTest
{
	private static QuestItems.Total total(String name, int wanted, long owned)
	{
		QuestItems.Total total = new QuestItems.Total(name, owned);
		total.add(new QuestItems.Line(name, wanted, owned, false), "Rune Mysteries");
		return total;
	}

	@Test
	public void quantitiesComeFromThePlanLessWhatIsOwned()
	{
		List<VoiceLines.Want> wants = ShopVoice.wants(Arrays.asList(
			total("Air rune", 30, 0), total("Air talisman", 1, 0), total("Mind rune", 20, 5)));
		assertEquals(3, wants.size());
		assertEquals(30, wants.get(0).quantity);
		assertEquals(1, wants.get(1).quantity);
		assertEquals(15, wants.get(2).quantity);
	}

	@Test
	public void itemsAlreadyOwnedAreSkipped()
	{
		List<VoiceLines.Want> wants = ShopVoice.wants(Arrays.asList(
			total("Air rune", 30, 30), total("Air talisman", 1, 0)));
		assertEquals(1, wants.size());
		assertEquals("Air talisman", wants.get(0).name);
	}

	@Test
	public void everythingOwnedSaysNothing()
	{
		assertTrue(ShopVoice.wants(Collections.singletonList(total("Beer", 1, 1))).isEmpty());
	}

	@Test
	public void theApproachEdgeSaysTheLineOnce() throws IOException
	{
		VoiceLines voice = VoiceLines.parse(new BufferedReader(new StringReader(String.join("\n",
			"phrase\tlooking-for\tlooking-for\tLooking for",
			"phrase\tand\tand\tand",
			"phrase\tat\tat\tat",
			"phrase\ta\ta\ta",
			"phrase\tan\tan\tan",
			"number\t30\tnum-30\t30",
			"item\tair talisman\titem-air-talisman\tAir talisman",
			"plural\tair rune\titems-air-rune\tAir runes",
			"shop\taubury's rune shop.\tshop-aubury\tAubury's Rune Shop"))));
		List<QuestItems.Total> stock = Arrays.asList(total("Air rune", 30, 0), total("Air talisman", 1, 0));
		WorldPoint shop = new WorldPoint(3253, 3401, 0);
		Map<String, WorldPoint> shops = Collections.singletonMap("Aubury's Rune Shop.", shop);

		ShopApproachWatcher watcher = new ShopApproachWatcher();
		List<String> said = new ArrayList<>();
		WorldPoint[] walk = {
			new WorldPoint(3253, 3440, 0), // far: baseline
			new WorldPoint(3253, 3420, 0), // still far
			new WorldPoint(3253, 3410, 0), // in range: says it
			new WorldPoint(3253, 3405, 0), // closer: quiet
			new WorldPoint(3253, 3402, 0), // standing in the shop: quiet
		};
		for (WorldPoint at : walk)
		{
			for (String name : watcher.observe(shops, at))
			{
				said.add(voice.shopLine(ShopVoice.wants(stock), name, path -> true).text);
			}
		}
		assertEquals(Collections.singletonList(
			"Looking for 30 air runes and an air talisman at Aubury's Rune Shop"), said);
	}
}
