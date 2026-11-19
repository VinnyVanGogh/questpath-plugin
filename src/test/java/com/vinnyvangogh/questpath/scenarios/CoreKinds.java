package com.vinnyvangogh.questpath.scenarios;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.party.events.UserPart;

/**
 * RuneLite events that are plain values, so a scenario can build them with no game behind them. Each is built
 * with the constructor or setters RuneLite's own code uses (Lombok {@code @Value} / {@code @Data} classes in
 * runelite-api and runelite-client); see SCENARIOS.md for what can and cannot be faked.
 *
 * <p>Not here, on purpose: {@code ActorDeath} and {@code NpcLootReceived} need a live {@code Actor} / {@code NPC},
 * and plugins compare them to {@code client.getLocalPlayer()}; a stand-in would never be "you". Fake those at the
 * plugin's own seam (a plugin kind) instead.
 */
final class CoreKinds
{
	private static final String NO_TICKS = "a fake GameTick would run every plugin an extra tick; the game ticks for "
		+ "you in the client";
	private static final String NO_GAME_STATE = "a fake game state change would make every plugin think you logged "
		+ "in or out";

	private CoreKinds()
	{
	}

	static void register(EventKinds kinds)
	{
		registerConstants(kinds);
		registerValues(kinds);
		registerWidgets(kinds);
		kinds.add("tick", "(no fields)", "{\"kind\":\"tick\"}", EventFactory.headless(NO_TICKS, f -> new GameTick()));
		kinds.add("gameState", "state: GameState name", "{\"kind\":\"gameState\",\"state\":\"LOGGED_IN\"}",
			EventFactory.headless(NO_GAME_STATE, f ->
			{
				GameStateChanged e = new GameStateChanged();
				e.setGameState(f.enumValue("state", GameState.class));
				return e;
			}));
	}

	/** Container ids (InventoryID), the bank interface, and a few item ids so scenarios read as words. */
	private static void registerConstants(EventKinds kinds)
	{
		kinds.constant("INVENTORY", 93).constant("EQUIPMENT", 94).constant("BANK", 95).constant("LOOTING_BAG", 516)
			.constant("BANK_GROUP", 12)
			.constant("COINS", 995).constant("SHARK", 385).constant("LOBSTER", 379).constant("LOGS", 1511)
			.constant("OAK_LOGS", 1521).constant("WILLOW_LOGS", 1519).constant("YEW_LOGS", 1515)
			.constant("DRAGON_BONES", 536).constant("ABYSSAL_WHIP", 4151).constant("RUNE_SCIMITAR", 1333)
			.constant("LOOTING_BAG_ITEM", 11941).constant("RING_OF_DUELING_8", 2552)
			.constant("AMULET_OF_GLORY_6", 11978).constant("PRAYER_POTION_4", 2434);
	}

	private static void registerValues(EventKinds kinds)
	{
		kinds.add("chat", "type: ChatMessageType name; message; name?; sender?; timestamp?",
			"{\"kind\":\"chat\",\"type\":\"GAMEMESSAGE\",\"message\":\"Your ring of dueling has 7 charges left.\"}",
			f -> new ChatMessage(null, f.enumValue("type", ChatMessageType.class), f.string("name", ""),
				f.string("message"), f.string("sender", null), f.integer("timestamp", 0)));
		kinds.add("stat", "skill: Skill name; level; xp?; boosted? (default level)",
			"{\"kind\":\"stat\",\"skill\":\"WOODCUTTING\",\"level\":61,\"xp\":272000}", f ->
			{
				int level = f.integer("level");
				return new StatChanged(f.enumValue("skill", Skill.class), f.integer("xp", 0), level,
					f.integer("boosted", level));
			});
		kinds.add("container", "id: container id or constant; items: {item: quantity}",
			"{\"kind\":\"container\",\"id\":\"INVENTORY\",\"items\":{\"SHARK\":10,\"COINS\":25000}}",
			f -> new ItemContainerChanged(f.integer("id"),
				new FakeItemContainer(f.integer("id"), items(f.items("items")))));
		kinds.add("varbit", "varbit: id; value", "{\"kind\":\"varbit\",\"varbit\":4070,\"value\":1}",
			f -> varbit(f.integer("varbit"), -1, f.integer("value")));
		kinds.add("varp", "varp: id; value", "{\"kind\":\"varp\",\"varp\":281,\"value\":10}",
			f -> varbit(-1, f.integer("varp"), f.integer("value")));
		kinds.add("userPart", "memberId?: long", "{\"kind\":\"userPart\",\"memberId\":7}",
			f -> new UserPart(f.longValue("memberId", 0)));
	}

	private static void registerWidgets(EventKinds kinds)
	{
		kinds.add("widgetLoaded", "group: interface id or constant", "{\"kind\":\"widgetLoaded\",\"group\":12}", f ->
		{
			WidgetLoaded e = new WidgetLoaded();
			e.setGroupId(f.integer("group"));
			return e;
		});
		kinds.add("widgetClosed", "group: interface id or constant; modalMode?; unload? (default true)",
			"{\"kind\":\"widgetClosed\",\"group\":12}",
			f -> new WidgetClosed(f.integer("group"), f.integer("modalMode", 0), f.bool("unload", true)));
	}

	private static VarbitChanged varbit(int varbitId, int varpId, int value)
	{
		VarbitChanged e = new VarbitChanged();
		e.setVarbitId(varbitId);
		e.setVarpId(varpId);
		e.setValue(value);
		return e;
	}

	private static List<Item> items(Map<Integer, Integer> counts)
	{
		List<Item> out = new ArrayList<>();
		counts.forEach((id, qty) -> out.add(new Item(id, qty)));
		return out;
	}
}
