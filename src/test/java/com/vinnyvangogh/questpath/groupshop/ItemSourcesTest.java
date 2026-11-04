package com.vinnyvangogh.questpath.groupshop;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Where the viewer can get an item, and the near-shop alert's candidates and words. */
public class ItemSourcesTest
{
	/** Buckets sold at two shops, one behind a requirement the viewer lacks; dropped by a goblin. */
	private static final ItemSources.Lookup LOOKUP = new ItemSources.Lookup()
	{
		@Override
		public List<String> shops(String item)
		{
			return "bucket".equalsIgnoreCase(item)
				? Arrays.asList("Guild Shop", "General Store")
				: Collections.emptyList();
		}

		@Override
		public boolean shopUsable(String shop)
		{
			return !"Guild Shop".equals(shop);
		}

		@Override
		public List<String> drops(String item)
		{
			return "bucket".equalsIgnoreCase(item) ? Collections.singletonList("Goblin") : Collections.emptyList();
		}

		@Override
		public boolean onGrandExchange(int itemId, String item)
		{
			return true;
		}
	};

	private static List<String> described(ItemSources.Options options)
	{
		List<String> out = new java.util.ArrayList<>();
		for (ItemSources.Source source : options.sources())
		{
			out.add(source.describe());
		}
		return out;
	}

	@Test
	public void anIronmanGetsReachableShopsAndDropsButNeverTheGrandExchange()
	{
		ItemSources.Options options = ItemSources.forViewer(AccountKind.IRONMAN, "Bucket", 1925, LOOKUP);

		assertEquals(Arrays.asList("Shop: General Store", "Drop: Goblin"), described(options));
	}

	@Test
	public void aGroupIronmanIsTreatedLikeAnIronman()
	{
		ItemSources.Options options = ItemSources.forViewer(AccountKind.GROUP_IRONMAN, "Bucket", 1925, LOOKUP);

		assertEquals(Arrays.asList("Shop: General Store", "Drop: Goblin"), described(options));
	}

	@Test
	public void aNormalAccountGetsTheGrandExchangeFirst()
	{
		ItemSources.Options options = ItemSources.forViewer(AccountKind.NORMAL, "Bucket", 1925, LOOKUP);

		assertEquals(Arrays.asList("GE", "Shop: General Store"), described(options));
	}

	/** An unreachable shop is never listed, but it is counted, so "none" and "none you can use" differ. */
	@Test
	public void anUnreachableShopIsFilteredAndCounted()
	{
		ItemSources.Options options = ItemSources.forViewer(AccountKind.IRONMAN, "Bucket", 1925, LOOKUP);

		assertEquals(Collections.singletonList("General Store"), options.shops());
		assertEquals(1, options.unreachableShops());
	}

	@Test
	public void anUnknownAccountKindIsNeverSentToTheGrandExchange()
	{
		ItemSources.Options options = ItemSources.forViewer(AccountKind.UNKNOWN, "Bucket", 1925, LOOKUP);

		assertTrue(options.sources().stream().noneMatch(s -> s.kind() == ItemSources.Kind.GRAND_EXCHANGE));
	}

	private static GroupShoppingList list(long bucketsHeld)
	{
		Map<String, Long> held = new LinkedHashMap<>();
		held.put("bucket", bucketsHeld);
		MemberNeeds you = new MemberNeeds("You", AccountKind.IRONMAN, held, true, false,
			Collections.singletonList(new ItemNeed("Quest A", "Bucket", 1925, 4, true, false)));
		return GroupShoppingList.of(Collections.singletonList(you), null, (id, name) -> true);
	}

	/** Only shops the viewer can use, and only for lines still short, become alert candidates. */
	@Test
	public void alertCandidatesSkipUnreachableShopsAndCoveredLines()
	{
		Map<String, List<GroupShoppingList.Line>> shops = GroupShopAlert.byShop(list(1).lines(), LOOKUP);
		assertEquals(Collections.singletonList("General Store"), new java.util.ArrayList<>(shops.keySet()));

		assertTrue(GroupShopAlert.byShop(list(4).lines(), LOOKUP).isEmpty());
	}

	@Test
	public void theAlertSaysHowManyMoreTheGroupNeeds()
	{
		assertEquals("group needs 3 more Bucket", GroupShopAlert.describe(list(1).lines()));
		assertEquals("", GroupShopAlert.describe(Collections.emptyList()));
	}
}
