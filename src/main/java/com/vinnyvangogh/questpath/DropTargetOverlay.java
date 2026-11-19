package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines the NPCs and objects that drop something the route still needs,
 * with an optional "drops White bead" label.
 *
 * <p>Highlighting only, in the manner of the base NPC Indicators plugin and
 * Quest Helper's NPC highlights: nothing is clicked, no menu entry is added or
 * removed, and nothing about the creature's behaviour is predicted. Which
 * names to mark comes from {@link DropTargets}; this class only finds them in
 * the scene.
 *
 * <p>Scene tracking is by spawn/despawn event, never a scene scan, per the
 * plugin's performance rules. Object names are read once at spawn, on the
 * client thread, because looking one up needs the client.
 */
class DropTargetOverlay extends Overlay
{
	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;

	private final Map<NPC, String> npcs = new LinkedHashMap<>();
	private final Map<GameObject, String> objects = new LinkedHashMap<>();

	/** Lower-cased source name to label, rebuilt only when the inputs change. */
	private Map<String, String> wanted = new HashMap<>();
	private int wantedSignature;
	private boolean haveWanted;

	DropTargetOverlay(QuestpathPlugin plugin, QuestpathConfig config, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	void npcSpawned(NPC npc)
	{
		String name = npc.getName();
		if (name != null)
		{
			npcs.put(npc, name.toLowerCase(Locale.ROOT));
		}
	}

	void npcDespawned(NPC npc)
	{
		npcs.remove(npc);
	}

	void objectSpawned(GameObject object)
	{
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition == null)
		{
			return;
		}
		String name = composition.getName();
		if (name != null && !name.isEmpty() && !"null".equals(name))
		{
			objects.put(object, name.toLowerCase(Locale.ROOT));
		}
	}

	void objectDespawned(GameObject object)
	{
		objects.remove(object);
	}

	/** Forget the scene: called on a region change and on shut down. */
	void clear()
	{
		npcs.clear();
		objects.clear();
		haveWanted = false;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		try
		{
			paint(g);
		}
		catch (RuntimeException e)
		{
			// Draw nothing this frame; the next one gets a fresh attempt.
		}
		return null;
	}

	private void paint(Graphics2D g)
	{
		if (config == null || !config.dropHighlight())
		{
			return;
		}
		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		if (snapshot == null || snapshot.items() == null)
		{
			return;
		}
		Map<String, String> names = wantedFor(snapshot);
		if (names.isEmpty())
		{
			return;
		}
		Color colour = config.dropHighlightColour();
		boolean labels = config.dropHighlightLabels();
		for (Map.Entry<NPC, String> entry : npcs.entrySet())
		{
			String label = names.get(entry.getValue());
			NPC npc = entry.getKey();
			if (label == null || npc.isDead())
			{
				continue;
			}
			Shape hull = npc.getConvexHull();
			if (hull != null)
			{
				OverlayUtil.renderPolygon(g, hull, colour);
			}
			if (labels)
			{
				Point at = npc.getCanvasTextLocation(g, label, npc.getLogicalHeight() + 40);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(g, at, label, colour);
				}
			}
		}
		for (Map.Entry<GameObject, String> entry : objects.entrySet())
		{
			String label = names.get(entry.getValue());
			if (label == null)
			{
				continue;
			}
			GameObject object = entry.getKey();
			Shape hull = object.getConvexHull();
			if (hull != null)
			{
				OverlayUtil.renderPolygon(g, hull, colour);
			}
			if (labels)
			{
				Point at = object.getCanvasTextLocation(g, label, 40);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(g, at, label, colour);
				}
			}
		}
	}

	/**
	 * Lower-cased source name to label, rebuilt only when the needed list, the
	 * route's next quest or the scope setting changes: this runs every frame.
	 */
	private Map<String, String> wantedFor(OverlaySnapshot snapshot)
	{
		String quest = currentQuest(snapshot);
		int signature = snapshot.items().signature() * 31 + (quest == null ? 0 : quest.hashCode());
		if (haveWanted && signature == wantedSignature)
		{
			return wanted;
		}
		Map<String, String> next = new HashMap<>();
		List<DropTargets.Target> targets = DropTargets.targets(snapshot.items().totals(), quest);
		for (DropTargets.Target target : targets)
		{
			next.merge(target.sourceName().toLowerCase(Locale.ROOT), target.label(),
				(a, b) -> a + ", " + b.substring("drops ".length()));
		}
		wanted = next;
		wantedSignature = signature;
		haveWanted = true;
		return wanted;
	}

	/**
	 * The quest to restrict to, or null for the whole route. With the
	 * restriction on but no quest to name (route finished, none saved) the
	 * whole list is used rather than silently marking nothing.
	 */
	private String currentQuest(OverlaySnapshot snapshot)
	{
		if (config.dropHighlightWholeRoute())
		{
			return null;
		}
		NextQuestStatus route = snapshot.route();
		if (route == null || route.state() != NextQuestStatus.State.NEXT)
		{
			return null;
		}
		return route.questName();
	}
}
