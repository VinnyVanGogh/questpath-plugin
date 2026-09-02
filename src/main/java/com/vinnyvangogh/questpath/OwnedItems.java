package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/**
 * What the account is carrying and what is in its bank.
 *
 * This is the one thing no external quest tool can know. The wiki knows what a
 * quest needs and where to buy it, the hiscores know your levels, WikiSync
 * knows your quests — none of them can see your bank, which is why questpath's
 * item guide has been a checklist ticked by hand.
 *
 * <p>The bank is only readable once it has been opened: the client does not
 * hold its contents until then. So the contents are remembered from the last
 * time it was seen rather than re-read on demand, and a bank that has never
 * been opened this session is reported as unknown rather than as empty. Those
 * are different claims and questpath renders them differently.
 */
class OwnedItems
{
	/** Which container a stack came from, matching what questpath stores. */
	enum Container
	{
		BANK("bank", InventoryID.BANK),
		INVENTORY("inventory", InventoryID.INV),
		EQUIPMENT("equipment", InventoryID.WORN);

		private final String wireName;
		private final int containerId;

		Container(String wireName, int containerId)
		{
			this.wireName = wireName;
			this.containerId = containerId;
		}

		String wireName()
		{
			return wireName;
		}

		int containerId()
		{
			return containerId;
		}

		static Container forId(int id)
		{
			for (Container c : values())
			{
				if (c.containerId == id)
				{
					return c;
				}
			}
			return null;
		}
	}

	/**
	 * The last seen contents of each container. Only containers actually seen
	 * appear: an absent one is unknown, and questpath leaves what it has
	 * rather than treating the silence as an empty bank.
	 */
	private final Map<Container, List<Map<String, Object>>> seen = new EnumMap<>(Container.class);

	/**
	 * Records a container the client just reported.
	 *
	 * <p><b>Must run on the client thread</b> — reads an ItemContainer and
	 * resolves item names through the client's composition cache.
	 *
	 * @return whether this container is one questpath stores
	 */
	boolean record(Client client, ItemContainer container)
	{
		Container which = Container.forId(container.getId());
		if (which == null)
		{
			return false;
		}
		seen.put(which, stacksOf(client, container));
		return true;
	}

	/** Forgets everything. Called on logout: the next login may be another account. */
	void clear()
	{
		seen.clear();
	}

	/** Whether anything has been seen yet. */
	boolean isEmpty()
	{
		return seen.isEmpty();
	}

	/**
	 * Whether the bank has been opened this session.
	 *
	 * The client does not hold the bank until it has been opened once, so an
	 * account with 405 buckets in the bank and a closed bank interface looks
	 * exactly like an account with none. Anything reading heldByName has to be
	 * able to say which, or it reports "you have none of these" about a bank
	 * it has never seen.
	 */
	boolean bankSeen()
	{
		return seen.containsKey(Container.BANK);
	}

	/**
	 * The items block for a push, containing only what has actually been seen.
	 *
	 * A container that has never been opened is omitted entirely rather than
	 * sent as an empty list, because questpath treats an empty list as "this
	 * really is empty" and would wipe what it had.
	 */
	Map<String, List<Map<String, Object>>> forPayload()
	{
		Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
		for (Map.Entry<Container, List<Map<String, Object>>> entry : seen.entrySet())
		{
			out.put(entry.getKey().wireName(), entry.getValue());
		}
		return out;
	}

	/**
	 * Everything held, totalled by lowercased item name across all three
	 * containers.
	 *
	 * Totalled because "do I have three buckets" does not care which of the
	 * three places they are in. Keyed by name because the quest graph names
	 * items and carries no Jagex ids.
	 */
	Map<String, Long> heldByName()
	{
		Map<String, Long> out = new LinkedHashMap<>();
		for (List<Map<String, Object>> stacks : seen.values())
		{
			for (Map<String, Object> stack : stacks)
			{
				Object name = stack.get("name");
				Object quantity = stack.get("quantity");
				if (!(name instanceof String) || !(quantity instanceof Number))
				{
					continue;
				}
				String key = ((String) name).toLowerCase();
				out.merge(key, ((Number) quantity).longValue(), Long::sum);
			}
		}
		return out;
	}

	/**
	 * A cheap identity for what is held, so a bank that has not changed does
	 * not earn a push.
	 */
	int fingerprint()
	{
		return seen.hashCode();
	}

	private static List<Map<String, Object>> stacksOf(Client client, ItemContainer container)
	{
		Item[] items = container.getItems();
		List<Map<String, Object>> out = new ArrayList<>(items.length);
		for (Item item : items)
		{
			// An empty inventory slot arrives as id -1 with quantity 0.
			// questpath drops these too, but sending them would be a payload
			// of 28 slots for a nearly empty inventory.
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			Map<String, Object> stack = new LinkedHashMap<>();
			stack.put("id", item.getId());
			stack.put("name", nameOf(client, item.getId()));
			stack.put("quantity", item.getQuantity());
			out.add(stack);
		}
		return out;
	}

	/**
	 * The in-game name for an id.
	 *
	 * questpath matches quest requirement lines by name, and it has no table
	 * of Jagex item ids to join against — so the name has to travel with the
	 * id. getItemDefinition is a cache lookup, not a fetch.
	 */
	private static String nameOf(Client client, int itemId)
	{
		ItemComposition composition = client.getItemDefinition(itemId);
		return composition == null ? "" : composition.getName();
	}
}
