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
@lombok.extern.slf4j.Slf4j
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
		reportDoubleCounts(which);
		return true;
	}

	/**
	 * Names anything currently counted in more than one container.
	 *
	 * <p>Temporary, and pointed at one specific report: a single pigeon cage
	 * moved from bank to inventory read as two. heldByName sums the three
	 * containers, which is right only while all three snapshots describe the
	 * same instant -- an item in transit is in the new container and, until
	 * the old one's own event arrives, still in the old one too.
	 *
	 * <p>Legitimate cases exist and will show up here as well (coins in the
	 * bank and coins in your pocket really are both yours), so this only says
	 * what is doubled, not that anything is wrong. What it settles is whether
	 * the bank snapshot goes stale after a withdrawal, which is the thing
	 * guessing cannot answer.
	 */
	private void reportDoubleCounts(Container justRecorded)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		Map<String, String> firstSeenIn = new LinkedHashMap<>();
		for (Map.Entry<Container, List<Map<String, Object>>> entry : seen.entrySet())
		{
			for (Map<String, Object> stack : entry.getValue())
			{
				Object name = stack.get("name");
				if (!(name instanceof String) || ((String) name).isEmpty())
				{
					continue;
				}
				String key = ((String) name).toLowerCase();
				String previous = firstSeenIn.put(key, entry.getKey().wireName());
				if (previous != null)
				{
					log.debug("questpath items: after {} changed, \"{}\" is counted in both {}"
						+ " and {}", justRecorded.wireName(), name, previous,
						entry.getKey().wireName());
				}
			}
		}
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
	 * How many distinct stacks are held across every container seen so far.
	 *
	 * The size of the claim a push is making about this account's items,
	 * which is what the Sync tab reports. Containers never opened contribute
	 * nothing rather than zero -- the two are the same number here and very
	 * different facts, and the tab says which separately.
	 */
	int stackCount()
	{
		int count = 0;
		for (List<Map<String, Object>> stacks : seen.values())
		{
			count += stacks.size();
		}
		return count;
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
		return totalByName(Container.values());
	}

	/**
	 * What is on the account right now: inventory and worn equipment, never
	 * the bank.
	 *
	 * <p>Deliberately a narrower question than {@link #heldByName()}, and the
	 * two must not be conflated. "You own five of these" and "five of these
	 * are on you" support different claims: the first says a shopping trip is
	 * unnecessary, the second says you are in the middle of gathering them.
	 * Eleven oak logs sitting in a bank is a solved requirement; one oak log
	 * in your inventory is a woodcutting trip in progress, and only the second
	 * is worth interrupting you about.
	 *
	 * <p>Unlike heldByName this is never widened by what questpath remembered
	 * from a previous session -- a stack recorded last week is not in your
	 * bags now, and treating it as though it were would pin a stale row to the
	 * top of the shopping list forever.
	 */
	Map<String, Long> carriedByName()
	{
		return totalByName(Container.INVENTORY, Container.EQUIPMENT);
	}

	private Map<String, Long> totalByName(Container... from)
	{
		Map<String, Long> out = new LinkedHashMap<>();
		for (Container which : from)
		{
			List<Map<String, Object>> stacks = seen.get(which);
			if (stacks == null)
			{
				continue;
			}
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
			ItemComposition composition = client.getItemDefinition(item.getId());
			if (isPlaceholder(composition))
			{
				continue;
			}
			Map<String, Object> stack = new LinkedHashMap<>();
			stack.put("id", item.getId());
			stack.put("name", composition == null ? "" : composition.getName());
			stack.put("quantity", item.getQuantity());
			out.add(stack);
		}
		return out;
	}

	/**
	 * Whether this is a bank placeholder rather than an item you own.
	 *
	 * <p>A placeholder is the greyed-out marker the bank leaves in a slot
	 * after the last of something is withdrawn. It is a separate item with
	 * its own id and <em>the same name</em>, which is precisely what makes it
	 * dangerous here: questpath merges by name, so withdrawing the last
	 * pigeon cage left one cage in the inventory and its ghost in the bank,
	 * and the shopping list read "2 / 5" for an account holding one.
	 *
	 * <p>Filtering on quantity does not catch it -- the placeholder observed
	 * arrived as id 17281, quantity 1, name "Pigeon cage", against the real
	 * item's 424 (full) and 425 (empty). getPlaceholderTemplateId is the
	 * client's own answer to the question and does not depend on that.
	 */
	private static boolean isPlaceholder(ItemComposition composition)
	{
		return composition != null && composition.getPlaceholderTemplateId() != -1;
	}
}
