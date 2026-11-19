package com.vinnyvangogh.questpath.scenarios;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The scenario harness: parsing, refusing what it does not know, Paste JSON, saved scenarios, and the built-in
 * scenarios and documented examples, so none of them can rot.
 */
public class ScenarioHarnessTest
{
	private final EventKinds kinds = EventKinds.core();

	private static String refusal(Runnable r)
	{
		try
		{
			r.run();
		}
		catch (ScenarioException e)
		{
			return e.getMessage();
		}
		fail("expected a ScenarioException");
		return null;
	}

	private List<Object> fire(Scenario s)
	{
		List<Object> posted = new ArrayList<>();
		new ScenarioRun(s, kinds, posted::add).runAll(ms ->
		{
		});
		return posted;
	}

	@Test
	public void everyBuiltInScenarioParsesAndFires()
	{
		List<Scenario> all = ScenarioParser.loadAll(ScenarioParser.DEFAULT_DIR, kinds);
		assertTrue("built-in scenarios exist", all.size() >= 3);
		for (Scenario s : all)
		{
			assertEquals(s.name, s.steps.size(), fire(s).size());
		}
	}

	/** A plugin stand-in: counts the chat messages it is handed. */
	public static class Listener
	{
		int chats;

		@Subscribe
		public void onChatMessage(ChatMessage e)
		{
			chats++;
		}
	}

	@Test
	public void privateBusReachesOnlyTheTargetPlugin()
	{
		Listener target = new Listener();
		Listener bystander = new Listener();
		EventBus clientBus = new EventBus();
		clientBus.register(bystander);

		PrivateBus bus = new PrivateBus(() -> target);
		Scenario s = ScenarioParser.parsePasted("{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"hi\"}", kinds);
		new ScenarioRun(s, kinds, bus).runAll(ms ->
		{
		});

		assertEquals(1, target.chats);
		assertEquals("a plugin on the client's bus never sees a private post", 0, bystander.chats);

		new ScenarioRun(s, kinds, clientBus::post).runAll(ms ->
		{
		});
		assertEquals("the real bus does not reach the private one", 1, target.chats);
		assertEquals(1, bystander.chats);

		bus.close();
		new ScenarioRun(s, kinds, bus).runAll(ms ->
		{
		});
		assertEquals("after close the plugin is registered again on the next post, once", 2, target.chats);
	}

	@Test
	public void privateBusRefusesWhenThePluginIsNotRunning()
	{
		PrivateBus bus = new PrivateBus(() -> null);
		String why = refusal(() -> bus.accept("anything"));
		assertTrue(why, why.contains("not running") && why.contains("All plugins"));
	}

	@Test
	public void dryRunIsOffByDefaultAndBlocksSendsWhenOn()
	{
		DryRun dry = DryRun.shared();
		List<String> sent = new ArrayList<>();
		List<String> logged = new ArrayList<>();
		dry.log(logged::add);
		java.util.function.Consumer<String> webhook = dry.wrap("Discord webhook", sent::add);
		try
		{
			assertTrue("dry run starts off", !dry.enabled());
			webhook.accept("real post");
			assertEquals(1, sent.size());

			dry.enabled(true);
			webhook.accept("must not leave");
			assertEquals("no real send while on", 1, sent.size());
			assertEquals(1, logged.size());
			assertTrue(logged.get(0), logged.get(0).contains("Discord webhook") && logged.get(0).contains("must not leave"));
		}
		finally
		{
			dry.enabled(false);
		}
	}

	@Test
	public void everyKindDocumentsAStepThatWorks()
	{
		for (EventKinds.Doc d : kinds.docs())
		{
			assertTrue(d.kind + " has fields text", !d.fields.isEmpty());
			Scenario s = ScenarioParser.parsePasted(d.example, kinds);
			assertEquals(d.kind, s.steps.get(0).kind);
		}
	}

	@Test
	public void pastedStepListAndScenarioAllWork()
	{
		Scenario one = ScenarioParser.parsePasted("{\"kind\":\"stat\",\"skill\":\"PRAYER\",\"level\":43}", kinds);
		assertEquals(1, fire(one).size());

		Scenario list = ScenarioParser.parsePasted("[{\"kind\":\"stat\",\"skill\":\"PRAYER\",\"level\":43},"
			+ "{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"hi\"}]", kinds);
		List<Object> posted = fire(list);
		assertTrue(posted.get(0) instanceof StatChanged);
		assertTrue(posted.get(1) instanceof ChatMessage);

		Scenario whole = ScenarioParser.parsePasted("{\"name\":\"Mine\",\"steps\":[{\"kind\":\"container\","
			+ "\"id\":\"INVENTORY\",\"items\":{\"SHARK\":3}}]}", kinds);
		assertEquals("Mine", whole.name);
		assertTrue(fire(whole).get(0) instanceof ItemContainerChanged);
	}

	@Test
	public void pastedItemsReachTheContainerAsGiven()
	{
		Scenario s = ScenarioParser.parsePasted("{\"kind\":\"container\",\"id\":95,\"items\":{\"SHARK\":3,\"995\":7}}",
			kinds);
		ItemContainerChanged e = (ItemContainerChanged) fire(s).get(0);
		assertEquals(95, e.getContainerId());
		assertEquals(3, e.getItemContainer().count(385));
		assertEquals(7, e.getItemContainer().count(995));
	}

	@Test
	public void badPasteIsRefusedWithAMessageThatSaysWhatToFix()
	{
		assertTrue(refusal(() -> ScenarioParser.parsePasted("{oops", kinds)).contains("not valid JSON"));
		assertTrue(refusal(() -> ScenarioParser.parsePasted("  ", kinds)).contains("empty"));
		assertTrue(refusal(() -> ScenarioParser.parsePasted("42", kinds)).contains("expected a step"));
		assertTrue(refusal(() -> ScenarioParser.parsePasted("{\"hello\":1}", kinds)).contains("expected a step"));

		String unknown = refusal(() -> ScenarioParser.parsePasted("{\"kind\":\"teleport\"}", kinds));
		assertTrue(unknown, unknown.contains("step 1") && unknown.contains("unknown kind 'teleport'")
			&& unknown.contains("stat"));

		String missing = refusal(() -> ScenarioParser.parsePasted("{\"kind\":\"stat\",\"skill\":\"PRAYER\"}", kinds));
		assertTrue(missing, missing.contains("step 1 (stat)") && missing.contains("missing field 'level'"));

		String enumName = refusal(() -> ScenarioParser.parsePasted("{\"kind\":\"stat\",\"skill\":\"PRAYR\",\"level\":5}",
			kinds));
		assertTrue(enumName, enumName.contains("'PRAYR'") && enumName.contains("PRAYER"));

		String constant = refusal(() -> ScenarioParser.parsePasted("{\"kind\":\"container\",\"id\":\"VAULT\","
			+ "\"items\":{}}", kinds));
		assertTrue(constant, constant.contains("unknown constant 'VAULT'") && constant.contains("INVENTORY"));

		String wait = refusal(() -> ScenarioParser.parsePasted("{\"kind\":\"stat\",\"skill\":\"PRAYER\",\"level\":5,"
			+ "\"wait\":\"soon\"}", kinds));
		assertTrue(wait, wait.contains("'wait' must be milliseconds"));
	}

	@Test
	public void aKindAddedByThePluginIsUsableAndCannotReplaceACoreOne()
	{
		EventKinds mine = EventKinds.core().add("ping", "text", "{\"kind\":\"ping\",\"text\":\"a\"}",
			f -> f.string("text"));
		Scenario s = ScenarioParser.parsePasted("{\"kind\":\"ping\",\"text\":\"hello\"}", mine);
		List<Object> posted = new ArrayList<>();
		new ScenarioRun(s, mine, posted::add).step();
		assertEquals("hello", posted.get(0));

		String dup = refusal(() -> mine.add("stat", "x", "{}", f -> null));
		assertTrue(dup, dup.contains("already registered"));
	}

	@Test
	public void headlessOnlyKindsAreSkippedLiveAndPostedHeadless()
	{
		Scenario s = ScenarioParser.parsePasted("{\"kind\":\"tick\"}", kinds);
		List<Object> posted = new ArrayList<>();
		ScenarioRun.Fired live = new ScenarioRun(s, kinds, posted::add).live(true).step();
		assertTrue(live.skipped, live.skipped.contains("headless only"));
		assertTrue(posted.isEmpty());
		assertEquals(1, fire(s).size());
	}

	@Test
	public void savedScenariosRoundTripAndAreValidatedBeforeTheyAreWritten() throws Exception
	{
		ScenarioFiles.InMemory files = new ScenarioFiles.InMemory();
		ScenarioStore store = new ScenarioStore(files, kinds);

		String file = store.save("Looting Bag 2!", "{\"kind\":\"stat\",\"skill\":\"PRAYER\",\"level\":43}");
		assertEquals("looting-bag-2.json", file);
		Scenario loaded = store.load("Looting Bag 2!");
		assertEquals("Looting Bag 2!", loaded.name);
		assertEquals(1, fire(loaded).size());

		refusal(() -> save(store, "bad", "{\"kind\":\"stat\"}"));
		assertEquals("a refused save writes nothing", 1, files.names().size());

		List<String> problems = new ArrayList<>();
		files.write("broken.json", "{\"name\":\"x\",\"steps\":[{\"kind\":\"nope\"}]}");
		files.write("notes.txt", "ignored");
		List<Scenario> listed = store.list(problems);
		assertEquals(1, listed.size());
		assertEquals(1, problems.size());
		assertTrue(problems.get(0), problems.get(0).contains("broken.json"));

		assertTrue(store.delete("looting bag 2"));
		assertTrue(refusal(() -> load(store, "looting bag 2")).contains("no saved scenario"));
	}

	@Test
	public void aSavedNameCanNeverEscapeTheFolder()
	{
		for (String name : new String[] {"../../etc/passwd", "..", "", "   ", "!!!", null})
		{
			if (name != null && name.contains("passwd"))
			{
				assertEquals("etc-passwd.json", ScenarioStore.fileName(name));
				continue;
			}
			refusal(() -> ScenarioStore.fileName(name));
		}
		assertTrue(!ScenarioStore.fileName("a/b\\c").contains("/") && !ScenarioStore.fileName("a/b\\c").contains("\\"));
	}

	private static void save(ScenarioStore store, String name, String json)
	{
		try
		{
			store.save(name, json);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static void load(ScenarioStore store, String name)
	{
		try
		{
			store.load(name);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
