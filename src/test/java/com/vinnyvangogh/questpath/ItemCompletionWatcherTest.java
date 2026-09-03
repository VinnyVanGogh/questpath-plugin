package com.vinnyvangogh.questpath;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * When the chat is allowed to say "you now have all of these", and -- mostly
 * -- when it is not.
 *
 * <p>The negative cases are the point. A completion line is trivial to emit
 * and obnoxious to emit wrongly: logging in, or opening a bank, changes the
 * answer for a hundred items at once, and a plugin that announced all of them
 * would be switched off within a minute of being switched on.
 */
public class ItemCompletionWatcherTest
{
	private static final Gson GSON = new Gson();

	private static PlayerState freshAccount()
	{
		return new PlayerState(new HashMap<>(), new HashMap<>(), new HashSet<String>(),
			Collections.emptyList());
	}

	private static QuestItems items(Map<String, Long> held, Map<String, Long> carried,
		boolean bankSeen) throws Exception
	{
		return QuestItems.of(QuestGraph.load(GSON), freshAccount(), held, bankSeen,
			Collections.<String>emptySet(), Collections.<String>emptySet(),
			Collections.<String, Long>emptyMap(), carried);
	}

	private static Map<String, Long> clay(long quantity)
	{
		Map<String, Long> out = new HashMap<>();
		out.put("clay", quantity);
		return out;
	}

	private static ItemCompletionWatcher.Completion only(List<ItemCompletionWatcher.Completion> got,
		String name)
	{
		for (ItemCompletionWatcher.Completion completion : got)
		{
			if (completion.itemName().equalsIgnoreCase(name))
			{
				return completion;
			}
		}
		return null;
	}

	/**
	 * The first pass is a baseline, not news. Without this, logging in with a
	 * stocked bank announces every item on the plan at once.
	 */
	@Test
	public void theFirstObservationSaysNothing() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		assertTrue(watcher.observe(items(clay(50), clay(50), true)).isEmpty());
	}

	/** The case it exists for: five clay, mine a sixth, hear about it. */
	@Test
	public void announcesTheMomentACarriedItemCompletes() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		watcher.observe(items(clay(5), clay(5), true));

		List<ItemCompletionWatcher.Completion> got = watcher.observe(items(clay(6), clay(6), true));
		ItemCompletionWatcher.Completion done = only(got, "Clay");
		assertTrue("mining the sixth clay is worth a line", done != null);
		assertEquals(6, done.required());
		assertEquals("Doric's Quest", done.quests());
	}

	/** Said once. The next pass has nothing new to report about the same pile. */
	@Test
	public void announcesEachCompletionOnlyOnce() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		watcher.observe(items(clay(5), clay(5), true));
		assertEquals(1, watcher.observe(items(clay(6), clay(6), true)).size());
		assertTrue(watcher.observe(items(clay(6), clay(6), true)).isEmpty());
	}

	/**
	 * questpath learning about a bank is not the player collecting anything.
	 * This is the flood case: one carried clay plus a bank reveal satisfies
	 * dozens of items in a single pass.
	 */
	@Test
	public void openingTheBankIsNotCollecting() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		// Carrying one, bank never opened, so almost everything reads short.
		watcher.observe(items(clay(1), clay(1), false));

		// The bank opens and turns out to hold plenty of everything.
		Map<String, Long> stocked = new HashMap<>();
		stocked.put("clay", 100L);
		stocked.put("bones", 100L);
		assertTrue("a bank opening announces nothing",
			watcher.observe(items(stocked, clay(1), true)).isEmpty());
	}

	/**
	 * A sync arriving, or a stack appearing in a bank the player is not
	 * standing in, satisfies an item without any of it being on them. True
	 * about the account, false about the last few seconds.
	 */
	@Test
	public void somethingSatisfiedWithNoneOnYouIsNotCollected() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		watcher.observe(items(clay(5), Collections.<String, Long>emptyMap(), true));

		List<ItemCompletionWatcher.Completion> got =
			watcher.observe(items(clay(6), Collections.<String, Long>emptyMap(), true));
		assertTrue("nothing was picked up, so nothing is announced",
			only(got, "Clay") == null);
	}

	/** Logging out drops the baseline: the next login may be another account. */
	@Test
	public void resetForgetsTheBaseline() throws Exception
	{
		ItemCompletionWatcher watcher = new ItemCompletionWatcher();
		watcher.observe(items(clay(5), clay(5), true));
		watcher.reset();
		assertTrue("the first pass after a reset is a baseline again",
			watcher.observe(items(clay(6), clay(6), true)).isEmpty());
	}
}
