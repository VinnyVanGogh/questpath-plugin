package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Quest item lists ticked against a bank, over the real bundled graph.
 */
public class QuestItemsTest
{
	private static final Gson GSON = new Gson();

	private static PlayerState freshAccount()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(), Collections.emptyList());
	}

	private static QuestGraph graph() throws Exception
	{
		return QuestGraph.load(GSON);
	}

	/** The full form, with a bank and a separate "what is on you right now". */
	private static QuestItems withCarried(Map<String, Long> held, Map<String, Long> carried)
		throws Exception
	{
		return QuestItems.of(graph(), freshAccount(), held, true,
			Collections.<String>emptySet(), Collections.<String>emptySet(),
			Collections.<String, Long>emptyMap(), carried);
	}

	private static QuestItems.Total total(QuestItems items, String name)
	{
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equalsIgnoreCase(name))
			{
				return total;
			}
		}
		return null;
	}

	private static int positionOf(QuestItems items, String name)
	{
		for (int i = 0; i < items.totals().size(); i++)
		{
			if (items.totals().get(i).name().equalsIgnoreCase(name))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * One clay in your bags out of the six Doric wants is a mining trip in
	 * progress, and the panel's job at that moment is to say "five more" --
	 * not to file it under C between Bucket of water and Coal.
	 */
	@Test
	public void somethingYouAreCarryingPartOfSortsAboveEverythingElseShort() throws Exception
	{
		Map<String, Long> one = new HashMap<>();
		one.put("clay", 1L);

		QuestItems items = withCarried(one, one);
		QuestItems.Total clay = total(items, "Clay");
		assertNotNull(clay);
		assertTrue("one of six is still short", !clay.satisfied());
		assertTrue("one of them is in your bags", clay.inProgress());
		assertEquals(5L, clay.outstanding());
		assertEquals("Doric's Quest", clay.questSummary());

		assertEquals("a part-carried item leads the whole list", 0, positionOf(items, "Clay"));
	}

	/**
	 * The distinction the whole feature rests on: owning some and carrying
	 * some are different facts, and only the second one means you are out
	 * doing it right now.
	 */
	@Test
	public void aPartFullBankIsNotATripInProgress() throws Exception
	{
		Map<String, Long> banked = new HashMap<>();
		banked.put("clay", 5L);

		QuestItems items = withCarried(banked, Collections.<String, Long>emptyMap());
		QuestItems.Total clay = total(items, "Clay");
		assertNotNull(clay);
		assertEquals(5L, clay.owned());
		assertEquals(0L, clay.carried());
		assertFalse("five in a bank is not a mining trip", clay.inProgress());
	}

	/** Finishing one thing beats starting a second, so the nearly-done one leads. */
	@Test
	public void startedItemsAreOrderedClosestToDoneFirst() throws Exception
	{
		Map<String, Long> carried = new HashMap<>();
		carried.put("clay", 5L);
		carried.put("bones", 1L);

		QuestItems items = withCarried(carried, carried);
		assertEquals("one clay outstanding", 1L, total(items, "Clay").outstanding());
		assertEquals("twenty-four bones outstanding", 24L, total(items, "Bones").outstanding());
		assertTrue("the nearly finished pile comes first",
			positionOf(items, "Clay") < positionOf(items, "Bones"));
		assertTrue("both still lead the list",
			positionOf(items, "Bones") < positionOf(items, "Coins"));
	}

	/**
	 * A satisfied item stays at the bottom whatever is in your bags. Carrying
	 * the last one of something is not a reason to keep it at the top.
	 */
	@Test
	public void carryingSomethingYouAlreadyHaveEnoughOfIsNotInProgress() throws Exception
	{
		Map<String, Long> plenty = new HashMap<>();
		plenty.put("clay", 50L);

		QuestItems items = withCarried(plenty, plenty);
		QuestItems.Total clay = total(items, "Clay");
		assertTrue(clay.satisfied());
		assertFalse("enough is enough, wherever it is", clay.inProgress());
		assertEquals(0L, clay.outstanding());
	}

	/** Every total can say what it is for -- that is the shop tooltip's whole content. */
	@Test
	public void everyTotalNamesTheQuestsThatWantIt() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);
		for (QuestItems.Total total : items.totals())
		{
			assertFalse(total.name() + " could not say what it was for",
				total.questSummary().isEmpty());
			assertEquals("a total's quest count and its named quests must agree",
				total.quests(), total.questNames().size());
		}
	}

	private static QuestItems withPlaceholders(Map<String, Long> held,
		java.util.Set<String> placeholders) throws Exception
	{
		return QuestItems.of(graph(), freshAccount(), held, true,
			Collections.<String>emptySet(), Collections.<String>emptySet(),
			Collections.<String, Long>emptyMap(), Collections.<String, Long>emptyMap(),
			placeholders);
	}

	/**
	 * A placeholder is the game's own record that you had something and spent
	 * all of it. It is never ownership -- the count stays at zero -- but at a
	 * bank it is a sharper prompt than an item you have never touched.
	 */
	@Test
	public void aBankPlaceholderMeansYouRanOutRatherThanNeverHadIt() throws Exception
	{
		QuestItems items = withPlaceholders(Collections.<String, Long>emptyMap(),
			Collections.singleton("clay"));

		QuestItems.Total clay = total(items, "Clay");
		assertNotNull(clay);
		assertEquals("a placeholder is not an item you own", 0L, clay.owned());
		assertTrue(clay.ranOut());

		QuestItems.Total bones = total(items, "Bones");
		assertFalse("never having had it is a different state", bones.ranOut());
	}

	/** Holding some of it means you did not run out, placeholder or not. */
	@Test
	public void holdingSomeMeansYouHaveNotRunOut() throws Exception
	{
		Map<String, Long> some = new HashMap<>();
		some.put("clay", 2L);

		QuestItems.Total clay = total(withPlaceholders(some, Collections.singleton("clay")),
			"Clay");
		assertFalse("two in the bank is not running out", clay.ranOut());
	}

	/** Ran-out rows lead the plain shortfalls, behind anything already started. */
	@Test
	public void ranOutSortsAheadOfSomethingNeverOwned() throws Exception
	{
		QuestItems items = withPlaceholders(Collections.<String, Long>emptyMap(),
			Collections.singleton("clay"));
		assertTrue("an emptied slot outranks an item never bought",
			positionOf(items, "Clay") < positionOf(items, "Bones"));
	}

	/** Three named, the rest counted -- a tooltip is one line, not a manifest. */
	@Test
	public void questListsArePhrasedForOneLine()
	{
		assertEquals("", QuestItems.phrase(Collections.<String>emptyList(), 3));
		assertEquals("A", QuestItems.phrase(java.util.Arrays.asList("A"), 3));
		assertEquals("A and B", QuestItems.phrase(java.util.Arrays.asList("A", "B"), 3));
		assertEquals("A, B and C", QuestItems.phrase(java.util.Arrays.asList("A", "B", "C"), 3));
		assertEquals("A, B, C and 2 more",
			QuestItems.phrase(java.util.Arrays.asList("A", "B", "C", "D", "E"), 3));
	}

	@Test
	public void listsOnlyQuestsTheAccountCanActuallyStart() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		assertFalse("a fresh account can start something with an item list", items.isEmpty());
		assertTrue("every doable quest with an item list is shown, not the first twelve",
			items.quests().size() > 12);
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			assertFalse(needs.questName().isEmpty());
			assertFalse(needs.questName() + " was listed with no items", needs.lines().isEmpty());
		}
	}

	/**
	 * The point of the whole plugin: an empty bank and a full one give
	 * different answers for the same quest.
	 */
	@Test
	public void holdingTheItemsChangesTheAnswer() throws Exception
	{
		QuestItems empty = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);
		QuestItems.QuestNeeds first = empty.quests().get(0);
		assertEquals("an empty bank satisfies nothing", 0, first.have());
		assertFalse(first.ready());

		Map<String, Long> bank = new HashMap<>();
		for (QuestItems.Line line : first.lines())
		{
			bank.put(line.name().toLowerCase(), 100L);
		}

		QuestItems stocked = QuestItems.of(graph(), freshAccount(), bank, true);
		QuestItems.QuestNeeds sameQuest = stocked.quests().get(0);
		assertEquals(first.questName(), sameQuest.questName());
		assertEquals("a stocked bank satisfies every required line",
			sameQuest.needed(), sameQuest.have());
		assertTrue(sameQuest.ready());
	}

	/**
	 * A line with no stated count is satisfied by holding any at all. The wiki
	 * did not say how many, and inventing a threshold would fail a line for
	 * missing a number nobody set.
	 */
	/**
	 * A line the wiki gave no count for needs one, and says so. "none" told
	 * you nothing about whether you were short.
	 */
	@Test
	public void aLineWithNoStatedCountNeedsOnlyOne()
	{
		QuestItems.Line unstated = new QuestItems.Line("Bucket", 0, 1, false);
		assertTrue(unstated.held());
		assertEquals("1 / 1", unstated.describe());

		QuestItems.Line none = new QuestItems.Line("Bucket", 0, 0, false);
		assertFalse(none.held());
		assertEquals("0 / 1", none.describe());
	}

	@Test
	public void aStatedCountMustActuallyBeMet()
	{
		assertFalse(new QuestItems.Line("Egg", 3, 2, false).held());
		assertTrue(new QuestItems.Line("Egg", 3, 3, false).held());
		assertTrue("more than enough is enough", new QuestItems.Line("Egg", 3, 9, false).held());
		assertEquals("2 / 3", new QuestItems.Line("Egg", 3, 2, false).describe());
	}

	/**
	 * Optional lines are shown but do not count towards readiness. A quest is
	 * not held up by something the wiki called optional.
	 */
	/**
	 * A bank nobody has opened is unknown, not empty. The client does not hold
	 * the bank until it has been opened once, so counts taken before that are
	 * inventory only -- and a row reading "none" would claim you do not own
	 * something sitting in a bank nobody looked in.
	 */
	@Test
	public void anUnreadBankIsFlaggedRatherThanReportedAsEmpty() throws Exception
	{
		QuestItems unread = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), false);
		assertFalse(unread.bankSeen());

		QuestItems read = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);
		assertTrue(read.bankSeen());
	}

	@Test
	public void optionalLinesDoNotDecideReadiness()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Cook's Assistant");
		needs.lines().add(new QuestItems.Line("Bucket of milk", 0, 1, false));
		needs.lines().add(new QuestItems.Line("Bucket", 0, 0, true));

		assertEquals(1, needs.needed());
		assertEquals(1, needs.have());
		assertTrue("an unheld optional item must not block readiness", needs.ready());
	}

	/**
	 * One shopping list across every quest. Coins wanted by two quests is one
	 * trip to the bank for the sum, not two lines to add up in your head.
	 */
	@Test
	public void totalsSumTheSameItemAcrossQuests() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		QuestItems.Total coins = null;
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equalsIgnoreCase("Coins"))
			{
				coins = total;
			}
		}

		assertTrue("several doable quests want coins", coins != null && coins.quests() > 1);

		int perQuest = 0;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equalsIgnoreCase("Coins") && !line.optional())
				{
					perQuest += line.wanted();
				}
			}
		}
		assertTrue("the total must be the sum of the lines, not one of them",
			coins.describe().startsWith("0 / " + perQuest));
		assertTrue(coins.sharedAcrossQuests());
	}

	/**
	 * Optional lines are advice, not shopping. Folding them into the totals
	 * would inflate every one with things nobody has to bring.
	 */
	@Test
	public void totalsExcludeOptionalLines() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true);

		for (QuestItems.Total total : items.totals())
		{
			int required = 0;
			for (QuestItems.QuestNeeds needs : items.quests())
			{
				for (QuestItems.Line line : needs.lines())
				{
					if (line.name().equals(total.name()) && !line.optional())
					{
						required++;
					}
				}
			}
			assertEquals(total.name() + " counts a quest that only wants it optionally",
				required, total.quests());
		}
	}

	/**
	 * A total whose lines did not all state a count is a floor, not a guess.
	 * "61+" means at least 61; rounding an unstated line up to 1 would invent
	 * a number the wiki never gave.
	 */
	/**
	 * The bug that produced "Ghostspeak amulet x3": three quests want one
	 * amulet, and one is all anybody needs. What the wiki leaves uncounted is
	 * almost always a tool you keep rather than something you spend, so
	 * unstated lines contribute one no matter how many quests want them.
	 */
	@Test
	public void anUnstatedItemCountsOnceHoweverManyQuestsWantIt()
	{
		QuestItems.Total tool = new QuestItems.Total("Ghostspeak amulet", 0);
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));
		tool.add(new QuestItems.Line("Ghostspeak amulet", 0, 0, false));

		assertEquals("three quests wanting one amulet is one amulet", 1, tool.required());
		assertEquals("0 / 1", tool.describe());
		assertTrue(tool.sharedAcrossQuests());
	}

	/**
	 * Stated counts do add up. The wiki states a number for what a quest
	 * consumes, and two quests each spending coins really do want the sum.
	 */
	@Test
	public void statedCountsAddUpAcrossQuests()
	{
		QuestItems.Total coins = new QuestItems.Total("Coins", 500);
		coins.add(new QuestItems.Line("Coins", 60, 500, false));
		coins.add(new QuestItems.Line("Coins", 1, 500, false));

		assertEquals(61, coins.required());
		assertEquals("500 / 61", coins.describe());
		assertTrue(coins.satisfied());
	}

	/** A mix adds the stated counts and one for the uncounted tool. */
	@Test
	public void aMixOfStatedAndUnstatedAddsOneForTheTool()
	{
		QuestItems.Total mixed = new QuestItems.Total("Logs", 0);
		mixed.add(new QuestItems.Line("Logs", 10, 0, false));
		mixed.add(new QuestItems.Line("Logs", 0, 0, false));

		assertEquals(11, mixed.required());
		assertEquals("0 / 11", mixed.describe());
	}

	/** Totals put what you are missing first; a long tail of things you have would bury it. */
	@Test
	public void totalsPutWhatIsMissingFirst() throws Exception
	{
		Map<String, Long> bank = new HashMap<>();
		bank.put("coins", 1_000_000L);

		QuestItems items = QuestItems.of(graph(), freshAccount(), bank, true);
		boolean seenSatisfied = false;
		for (QuestItems.Total total : items.totals())
		{
			if (total.satisfied())
			{
				seenSatisfied = true;
			}
			else
			{
				assertFalse("an unsatisfied total must not sit below a satisfied one", seenSatisfied);
			}
		}
	}

	/**
	 * The pickup list is only worth anything while you are inside the quest,
	 * so it is driven by what the client calls in progress -- not by Quest
	 * Helper's selection, which lives in another plugin's memory with no
	 * config key, no event and no shared class to reach it through.
	 */
	@Test
	public void nothingToGrabWhenNoQuestIsUnderWay() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), Collections.emptyMap(), true,
			Collections.emptySet());
		assertTrue(items.pickups().isEmpty());
	}

	/**
	 * Something you can pick up inside a quest you are doing, that another
	 * quest on the list also wants, is the one thing worth interrupting the
	 * shopping list for.
	 */
	@Test
	public void aQuestUnderWaySurfacesWhatItCanSupply() throws Exception
	{
		QuestGraph graph = graph();

		// Find a quest with an obtainable item that something else wants.
		String questName = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (item.obtainable())
				{
					questName = quest.name();
					break;
				}
			}
			if (questName != null)
			{
				break;
			}
		}
		assertNotNull("the bundled graph carries no obtainable items at all", questName);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true,
			Collections.singleton(questName));

		for (QuestItems.Pickup pickup : items.pickups())
		{
			assertEquals(questName, pickup.questName());
			assertFalse(pickup.itemName().isEmpty());
			assertTrue("a pickup nobody wants is noise", pickup.alsoWantedBy() >= 1);
		}
	}

	/** An item already held is not worth a detour. */
	@Test
	public void nothingSatisfiedIsSuggestedAsAPickup() throws Exception
	{
		QuestGraph graph = graph();
		Map<String, Long> stocked = new HashMap<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				stocked.put(item.name().toLowerCase(), 10_000L);
			}
		}

		java.util.Set<String> everything = new java.util.HashSet<>();
		for (QuestGraph.Quest quest : graph.quests())
		{
			everything.add(quest.name());
		}

		QuestItems items = QuestItems.of(graph, freshAccount(), stocked, true, everything);
		assertTrue("with a full bank there is nothing to detour for", items.pickups().isEmpty());
	}

	/**
	 * The bundled graph carries obtain sources for most quest items (questpath
	 * only omits one when it genuinely knows none), and QuestItems must carry
	 * them through onto both the per-quest line and the merged total -- that
	 * is the whole path a hover in the Items tab reads from.
	 */
	@Test
	public void obtainSourcesReachBothTheLineAndItsTotal() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithSources = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.sources().isEmpty())
				{
					itemWithSources = item.name();
					break;
				}
			}
			if (itemWithSources != null)
			{
				break;
			}
		}
		assertNotNull("the bundled graph carries no item with a known source at all", itemWithSources);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);

		boolean lineHadSources = false;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithSources))
				{
					assertFalse(line.sources().isEmpty());
					for (String source : line.sources())
					{
						assertTrue(source.startsWith("Shop: ") || source.startsWith("Drop: "));
					}
					lineHadSources = true;
				}
			}
		}
		assertTrue("the item was in the graph with sources but no line carried them", lineHadSources);

		boolean totalHadSources = false;
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equals(itemWithSources))
			{
				assertFalse(total.sources().isEmpty());
				totalHadSources = true;
			}
		}
		assertTrue("the item's total did not carry its sources", totalHadSources);
	}

	/**
	 * An item the wiki gives no obtain source for must carry none through --
	 * never an empty placeholder mistaken for "not loaded" and never an
	 * invented one.
	 */
	@Test
	public void anItemWithNoKnownSourceCarriesAnEmptyListNotNull() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithoutSources = null;
		outer:
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (item.sources().isEmpty())
				{
					itemWithoutSources = item.name();
					break outer;
				}
			}
		}
		assertNotNull("the bundled graph has no item lacking a source; nothing to pin here", itemWithoutSources);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithoutSources))
				{
					assertNotNull(line.sources());
					assertTrue(line.sources().isEmpty());
				}
			}
		}
	}

	/**
	 * The bundled graph carries shop names -- resolvable against {@link
	 * QuestGraph#shop} -- for most items with a known shop source, and
	 * QuestItems must carry them through onto both the per-quest line and
	 * the merged total, the same path {@link #obtainSourcesReachBothTheLineAndItsTotal}
	 * pins for the display-string sources. This is the path {@link
	 * QuestpathPanel}'s shop-route button reads to build a real {@code
	 * Set<WorldPoint>} for Shortest Path.
	 */
	@Test
	public void shopNamesReachBothTheLineAndItsTotal() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithShop = null;
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (!item.shopNames().isEmpty())
				{
					itemWithShop = item.name();
					break;
				}
			}
			if (itemWithShop != null)
			{
				break;
			}
		}
		assertNotNull("the bundled graph carries no item with a known shop at all", itemWithShop);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);

		boolean lineHadShop = false;
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithShop))
				{
					assertFalse(line.shopNames().isEmpty());
					for (String shopName : line.shopNames())
					{
						assertNotNull("every shop name a Line carries must resolve against the graph's own"
							+ " shop table", graph.shop(shopName));
					}
					lineHadShop = true;
				}
			}
		}
		assertTrue("the item was in the graph with a shop but no line carried it", lineHadShop);

		boolean totalHadShop = false;
		for (QuestItems.Total total : items.totals())
		{
			if (total.name().equals(itemWithShop))
			{
				assertFalse(total.shopNames().isEmpty());
				totalHadShop = true;
			}
		}
		assertTrue("the item's total did not carry its shop names", totalHadShop);
	}

	/**
	 * An item the wiki gives no shop for must carry none through -- never an
	 * empty placeholder mistaken for "not loaded" and never an invented one.
	 */
	@Test
	public void anItemWithNoKnownShopCarriesAnEmptyListNotNull() throws Exception
	{
		QuestGraph graph = graph();

		String itemWithoutShop = null;
		outer:
		for (QuestGraph.Quest quest : graph.quests())
		{
			for (QuestGraph.Item item : quest.items())
			{
				if (item.shopNames().isEmpty())
				{
					itemWithoutShop = item.name();
					break outer;
				}
			}
		}
		assertNotNull("the bundled graph has no item lacking a shop; nothing to pin here", itemWithoutShop);

		QuestItems items = QuestItems.of(graph, freshAccount(), Collections.emptyMap(), true);
		for (QuestItems.QuestNeeds needs : items.quests())
		{
			for (QuestItems.Line line : needs.lines())
			{
				if (line.name().equals(itemWithoutShop))
				{
					assertNotNull(line.shopNames());
					assertTrue(line.shopNames().isEmpty());
				}
			}
		}
	}

	/**
	 * A line questpath could not match to an item is listed but not counted.
	 *
	 * It cannot be ticked -- there is nothing to tick it against -- so
	 * counting it as missing would make "4 of 5" a ratio that never reaches
	 * 5 however much you buy. It still appears, and unresolvedCount says how
	 * many there are, because dropping it is what made these lists quietly
	 * short in the first place.
	 */
	@Test
	public void anUnresolvedLineIsListedButNotCounted()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Recipe for Disaster/Another Cook's Quest");
		needs.lines().add(new QuestItems.Line("Ashes", 592, 1, 1, false, false, null));
		needs.lines().add(new QuestItems.Line("Eye of newt", 221, 1, 0, false, false, null));
		needs.lines().add(new QuestItems.Line("Any fruit blast excluding a premade fr' blast",
			0, 0, 0, false, true, null));

		assertEquals(3, needs.lines().size());
		assertEquals("the unresolved line is neither held nor missing", 2, needs.needed());
		assertEquals(1, needs.have());
		assertEquals(1, needs.unresolvedCount());
	}

	/**
	 * With no bank opened but a record on file, questpath's own counts fill
	 * the gap and the list says so.
	 *
	 * The old behaviour collapsed "I have never seen your bank" and "I saw it
	 * two minutes ago" into the same pessimistic answer, which for an account
	 * that syncs constantly was almost always the wrong one.
	 */
	@Test
	public void aBankOnRecordFillsInForOneNotOpenedYet()
	{
		java.util.Map<String, Long> carried = new java.util.HashMap<>();
		carried.put("bucket", 1L);

		java.util.Map<String, Long> known = new java.util.HashMap<>();
		known.put("bucket", 9L);
		known.put("rope", 4L);

		java.util.Map<String, Long> merged =
			QuestItems.effectiveHeldForTest(carried, false, known);

		// The larger of the two, never the sum: questpath's record already
		// includes whatever was carried when it was taken, so adding would
		// double-count the same stack.
		assertEquals(Long.valueOf(9L), merged.get("bucket"));
		// And a bank-only item appears at all, which is the entire point.
		assertEquals(Long.valueOf(4L), merged.get("rope"));
	}

	/**
	 * A live bank wins outright. Letting a stale record raise a count the
	 * client can actually see would resurrect items already spent.
	 */
	@Test
	public void aLiveBankIsNeverRaisedByAnOlderRecord()
	{
		java.util.Map<String, Long> live = new java.util.HashMap<>();
		live.put("bucket", 1L);

		java.util.Map<String, Long> known = new java.util.HashMap<>();
		known.put("bucket", 9L);

		java.util.Map<String, Long> merged = QuestItems.effectiveHeldForTest(live, true, known);

		assertEquals(Long.valueOf(1L), merged.get("bucket"));
	}

	// ------------------------------------------------------------------
	// Category requirements, over the real bundled graph. "A light source"
	// and "Any pickaxe" are not items and never will be; the bundle now
	// carries the set that satisfies each, and these pin what the list makes
	// of that. See docs/prose-requirements.md in the questpath repo.
	// ------------------------------------------------------------------

	/** Levels the account into one skill, leaving everything else at 1. */
	private static PlayerState accountWith(String skill, int level)
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put(skill, level);
		return new PlayerState(levels, new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
	}

	/**
	 * The list for one named quest, whatever the account can currently do.
	 *
	 * <p>Scoped to a plan rather than left to the doable set on purpose:
	 * every quest that wants a pickaxe wants a Mining level too, so a
	 * level-1 account has none of them doable and the list would be empty for
	 * reasons that have nothing to do with what is being tested.
	 */
	private static QuestItems itemsFor(PlayerState account, Map<String, Long> held)
		throws Exception
	{
		java.util.Set<String> plan = new HashSet<String>();
		plan.add("the knight's sword");
		return QuestItems.of(graph(), account, held, true,
			Collections.<String>emptySet(), plan,
			Collections.<String, Long>emptyMap(), Collections.<String, Long>emptyMap());
	}

	/**
	 * The wiki's phrase never reaches the list; a real item does.
	 *
	 * <p>"Any pickaxe" is not something you can search the Grand Exchange
	 * for, tick against a bank, or walk to a shop for. It used to arrive with
	 * no id and get filed under "questpath can't look this up", for a thing
	 * every general store sells.
	 */
	@Test
	public void aCategoryRequirementBecomesARealItem() throws Exception
	{
		QuestItems items = itemsFor(freshAccount(), Collections.<String, Long>emptyMap());

		assertNull("the wiki's phrase must not survive into the list",
			total(items, "Any pickaxe"));
		assertNull(total(items, "Pickaxe"));
		// Iron, not bronze. The wiki gives both Mining 1 and Attack 1, and
		// iron is the later row -- so at the same requirement it is the
		// better of the two, and the tier order is the ranking.
		assertNotNull("a level-1 account is offered the best pickaxe it can use",
			total(items, "Iron pickaxe"));
	}

	/**
	 * The account's levels decide which member, and the wiki's tier order is
	 * the ranking.
	 *
	 * <p>41 Mining and 40 Attack is exactly the rune pickaxe's requirement,
	 * so it is both usable and the best usable.
	 */
	@Test
	public void theBestUsableMemberIsChosen() throws Exception
	{
		Map<String, Integer> levels = new HashMap<>();
		levels.put("Mining", 41);
		levels.put("Attack", 40);
		PlayerState account = new PlayerState(levels, new HashMap<>(),
			new HashSet<String>(), Collections.emptyList());

		QuestItems items = itemsFor(account, Collections.<String, Long>emptyMap());

		assertNotNull(total(items, "Rune pickaxe"));
		assertNull("the level-1 pick is no longer the answer",
			total(items, "Iron pickaxe"));
	}

	/**
	 * A capped Attack does not cap the pickaxe, because you do not wield a
	 * pickaxe to mine with it.
	 *
	 * <p>This test asserted the opposite first, and the opposite was wrong.
	 * The reasoning was that questpath cannot tell which of a pickaxe's two
	 * columns a quest needs, so counting both is the safe direction -- but a
	 * pickaxe in the inventory mines exactly as well as one in the weapon
	 * slot, so Attack never gates using it. An account with 41 Mining and
	 * Attack hard-capped at 1 can use a rune pickaxe, and recommending it an
	 * iron one was simply incorrect.
	 *
	 * <p>That account is not a hypothetical: capping a combat skill while
	 * training a gathering one is the exact build questpath exists to plan
	 * for.
	 */
	@Test
	public void aWieldOnlyRequirementDoesNotCapTheChoice() throws Exception
	{
		QuestItems items = itemsFor(accountWith("Mining", 41),
			Collections.<String, Long>emptyMap());

		assertNotNull("41 Mining is all a rune pickaxe needs to mine with",
			total(items, "Rune pickaxe"));
		assertNull(total(items, "Iron pickaxe"));
	}

	/**
	 * The skill that actually gates use still binds.
	 *
	 * <p>Ignoring the wield requirement must not turn into ignoring the
	 * requirement: 1 Mining is 1 Mining, however much Attack the account has.
	 */
	@Test
	public void theUseRequirementStillBinds() throws Exception
	{
		QuestItems items = itemsFor(accountWith("Attack", 99),
			Collections.<String, Long>emptyMap());

		assertNull("99 Attack mines nothing", total(items, "Rune pickaxe"));
		assertNotNull(total(items, "Iron pickaxe"));
	}

	/**
	 * Holding any member satisfies the whole set.
	 *
	 * <p>This is the rule the player asked for first: check the bank before
	 * sending anyone shopping. A pickaxe already in the bank must tick the
	 * requirement off rather than sit beside a fresh instruction to buy one.
	 */
	@Test
	public void holdingAnyMemberSatisfiesTheSet() throws Exception
	{
		Map<String, Long> held = new HashMap<>();
		held.put("bronze pickaxe", 1L);

		QuestItems items = itemsFor(freshAccount(), held);

		// Named for what is actually in the bank, not for the best one that
		// could be bought: a bronze pickaxe already held beats an iron one
		// to buy, which is the player's own first rule.
		QuestItems.Total pickaxe = total(items, "Bronze pickaxe");
		assertNotNull(pickaxe);
		assertTrue("a pickaxe in the bank means no pickaxe to buy", pickaxe.satisfied());
	}

	@Test
	public void recommendedLinesDoNotBlockReadiness()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Merlin's Crystal");
		// 1 required item held:
		needs.lines().add(new QuestItems.Line("Excalibur", 100, 1, 1L, false, Collections.emptyList()));
		// 1 recommended item NOT held:
		needs.lines().add(new QuestItems.Line("Camelot teleport", 8010, 1, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true));

		assertEquals("only required items count as needed", 1, needs.needed());
		assertEquals("only required items count as have", 1, needs.have());
		assertTrue("an unheld recommended item must not block quest readiness", needs.ready());
	}

	@Test
	public void teleportEquivalenceCoversTotal()
	{
		QuestItems.Total camelot = new QuestItems.Total("Camelot teleport", 8010, 0L, 0L, false,
			Collections.emptyList(), Collections.emptyList());
		QuestItems.Line line = new QuestItems.Line("Camelot teleport", 8010, 2, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true);
		camelot.add(line, "Merlin's Crystal");

		assertFalse(camelot.satisfied());
		assertFalse(camelot.covered());
		assertEquals(2L, camelot.outstanding());

		// 2 casts worth of equivalent runes
		camelot.setEquivalentOwned(2L);
		assertTrue(camelot.satisfied());
		assertTrue(camelot.covered());
		assertEquals(0L, camelot.outstanding());
		assertEquals("2 / 2", camelot.describe());
	}

	@Test
	public void totalsToggleSeparatesRequiredAndRecommended()
	{
		QuestItems.QuestNeeds needs = new QuestItems.QuestNeeds("Test Quest");
		needs.lines().add(new QuestItems.Line("Bucket", 100, 1, 0L, false, Collections.emptyList()));
		needs.lines().add(new QuestItems.Line("Camelot teleport", 8010, 1, 0L, false, false,
			Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList(), true));

		QuestGraph.Quest mockQuest = new QuestGraph.Quest();
		// Test Total accumulation
		QuestItems.Total reqTotal = new QuestItems.Total("Bucket", 100, 0L, 0L, false, Collections.emptyList(), Collections.emptyList());
		reqTotal.add(needs.lines().get(0), "Test Quest");

		QuestItems.Total recTotal = new QuestItems.Total("Camelot teleport", 8010, 0L, 0L, false, Collections.emptyList(), Collections.emptyList());
		recTotal.add(needs.lines().get(1), "Test Quest");

		assertFalse(reqTotal.recommended());
		assertTrue(recTotal.recommended());
	}

	// Recommended items end to end, from a v13 graph. The bundled graph is
	// still v12 and carries none, so these build their own.

	/** One doable quest with a required spade and the wiki's recommended extras. */
	private static QuestGraph recommendedGraph()
	{
		return GSON.fromJson("{\"version\":13,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Spade\",\"item_id\":952,\"quantity\":1},"
			+ "{\"name\":\"Coins\",\"item_id\":995,\"quantity\":60}],"
			+ "\"recommended_items\":[{\"name\":\"Camelot teleport\",\"item_id\":8010,\"quantity\":2},"
			+ "{\"name\":\"Antipoison(4)\",\"item_id\":2446,\"quantity\":1},"
			+ "{\"name\":\"Coins\",\"item_id\":995,\"quantity\":1000}]},"
			+ "{\"id\":2,\"slug\":\"b\",\"name\":\"B\","
			+ "\"items\":[],"
			+ "\"recommended_items\":[{\"name\":\"Coins\",\"item_id\":995,\"quantity\":500}]}],"
			+ "\"diary_tasks\":[]}", QuestGraph.class);
	}

	/** One quest wanting six nails of any tier. */
	private static QuestGraph nailsGraph()
	{
		return GSON.fromJson("{\"version\":13,\"quests\":[{\"id\":1,\"slug\":\"a\",\"name\":\"A\","
			+ "\"items\":[{\"name\":\"Nails\",\"quantity\":6,\"group_name\":\"Nails\","
			+ "\"alternatives\":[{\"name\":\"Bronze nails\",\"item_id\":4819},"
			+ "{\"name\":\"Iron nails\",\"item_id\":4820},"
			+ "{\"name\":\"Steel nails\",\"item_id\":1539}]}]}],"
			+ "\"diary_tasks\":[]}", QuestGraph.class);
	}

	/**
	 * An "any tier" requirement fills from every tier held, not just the one
	 * named: three bronze and four iron nails cover six, and the row lists
	 * every tier with what is held of each.
	 */
	@Test
	public void anyTierFillsFromWhicheverTiersAreOwned()
	{
		Map<String, Long> held = new HashMap<>();
		held.put("bronze nails", 3L);
		held.put("iron nails", 4L);

		QuestItems items = QuestItems.of(nailsGraph(), freshAccount(), held, true);

		assertEquals(1, items.totals().size());
		QuestItems.Total total = items.totals().get(0);
		assertEquals(7L, total.owned());
		assertTrue(total.satisfied());
		assertEquals(3, total.memberCounts().size());
		assertEquals(Long.valueOf(3L), total.memberCounts().get("Bronze nails"));
		assertEquals(Long.valueOf(0L), total.memberCounts().get("Steel nails"));
		assertEquals("Any of: Bronze nails (3), Iron nails (4), Steel nails", total.tiersNote());
	}

	private static QuestItems.Total named(java.util.List<QuestItems.Total> totals, String name)
	{
		for (QuestItems.Total total : totals)
		{
			if (total.name().equalsIgnoreCase(name))
			{
				return total;
			}
		}
		return null;
	}

	/** Off by default: the required list is exactly what it was before the toggle existed. */
	@Test
	public void recommendedItemsStayOffTheRequiredTotals()
	{
		QuestItems items = QuestItems.of(recommendedGraph(), freshAccount(), new HashMap<>(), true);

		assertNotNull(named(items.totals(), "Spade"));
		assertNotNull("required coins are untouched", named(items.totals(), "Coins"));
		assertEquals(60, named(items.totals(), "Coins").required());
		assertNull(named(items.totals(), "Camelot teleport"));
		assertNull(named(items.totals(), "Antipoison(4)"));
		assertTrue(items.graphCarriesRecommended());
	}

	/**
	 * On, the extras join the list flagged as recommended -- and recommended
	 * coins never do. They are a balance to bring, summed across the route,
	 * and the required coin line keeps its own count.
	 */
	@Test
	public void recommendedCoinsAreABalanceNotABuyLine()
	{
		QuestItems items = QuestItems.of(recommendedGraph(), freshAccount(), new HashMap<>(), true);
		java.util.List<QuestItems.Total> all = items.totals(true);

		assertTrue(named(all, "Camelot teleport").recommended());
		assertEquals(2, named(all, "Camelot teleport").required());
		assertTrue(named(all, "Antipoison(4)").recommended());
		assertFalse(named(all, "Spade").recommended());
		assertEquals("recommended coins never inflate the coin row", 60, named(all, "Coins").required());
		assertFalse(named(all, "Coins").recommended());
		assertEquals(1500, items.recommendedCoins());
	}

	/** A v12 graph cannot say what is recommended, and the items say so rather than reading as "nothing". */
	@Test
	public void aGraphWithoutRecommendedItemsSaysItCannotSay() throws Exception
	{
		QuestItems items = QuestItems.of(graph(), freshAccount(), new HashMap<>(), true);
		assertFalse(items.graphCarriesRecommended());
		assertEquals(0, items.recommendedCoins());
		assertEquals(items.totals().size(), items.totals(true).size());
	}

	/**
	 * The Grand Exchange form: tablets by default, runes on request -- but
	 * only for an account that can cast them. A level-1 account asking for
	 * runes still sees the tablet.
	 */
	@Test
	public void grandExchangeTotalsHonourTheTeleportFormAndTheMagicLevel()
	{
		QuestItems novice = QuestItems.of(recommendedGraph(), freshAccount(), new HashMap<>(), true);
		assertNotNull(named(novice.grandExchangeTotals(true, TeleportForm.TABLET), "Camelot teleport"));
		assertNotNull("1 Magic cannot cast Camelot, so the tablet stays",
			named(novice.grandExchangeTotals(true, TeleportForm.RUNES), "Camelot teleport"));
		assertNull(named(novice.grandExchangeTotals(true, TeleportForm.RUNES), "Law rune"));

		QuestItems mage = QuestItems.of(recommendedGraph(), accountWith("magic", 45), new HashMap<>(), true);
		java.util.List<QuestItems.Total> runes = mage.grandExchangeTotals(true, TeleportForm.RUNES);
		assertNull(named(runes, "Camelot teleport"));
		assertEquals(10, named(runes, "Air rune").required());
		assertEquals(2, named(runes, "Law rune").required());
		assertTrue(named(runes, "Law rune").recommended());
		assertEquals("the stored totals are not rewritten",
			2, named(mage.totals(true), "Camelot teleport").required());
		assertNull(named(mage.grandExchangeTotals(false, TeleportForm.RUNES), "Law rune"));
	}
}
