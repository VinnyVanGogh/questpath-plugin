package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
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
 * <p>Only wanted names are tracked: a spawn whose name the route does not
 * need is dropped on the spot, so the per-frame walk is over the handful of
 * outlined things, not every NPC in the scene. When the wanted set changes
 * (the list shrinks as items are found, or the plugin was switched on with a
 * scene already loaded) the scene is read once, through spawn events' own
 * path, rather than kept in a map of everything just in case.
 */
class DropTargetOverlay extends Overlay
{
	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;

	/** The outlined things in the scene, by label. Client thread only. */
	private final Map<NPC, String> npcs = new LinkedHashMap<>();
	private final Map<GameObject, String> objects = new LinkedHashMap<>();

	/** Lower-cased source name to label, rebuilt only when the inputs change. */
	private Map<String, String> wanted = Collections.emptyMap();
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
		String label = labelFor(npc.getName());
		if (label != null)
		{
			npcs.put(npc, label);
		}
	}

	void npcDespawned(NPC npc)
	{
		npcs.remove(npc);
	}

	void objectSpawned(GameObject object)
	{
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		String label = composition == null ? null : labelFor(composition.getName());
		if (label != null)
		{
			objects.put(object, label);
		}
	}

	void objectDespawned(GameObject object)
	{
		objects.remove(object);
	}

	private String labelFor(String name)
	{
		return name == null || "null".equals(name) ? null : wanted.get(name.toLowerCase(Locale.ROOT));
	}

	/** Forget the scene: called on a region change and on shut down. */
	void clear()
	{
		npcs.clear();
		objects.clear();
		haveWanted = false;
	}

	/**
	 * Reads the loaded scene once for the wanted names -- what the spawn
	 * events would have delivered had they fired after the wanted set was
	 * known. Client thread: the scene and object definitions live there.
	 */
	void rescan()
	{
		npcs.clear();
		objects.clear();
		WorldView view = client.getTopLevelWorldView();
		if (wanted.isEmpty() || view == null || view.getScene() == null)
		{
			return;
		}
		for (NPC npc : view.npcs())
		{
			npcSpawned(npc);
		}
		for (Tile[][] plane : view.getScene().getTiles())
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					GameObject[] here = tile == null ? null : tile.getGameObjects();
					if (here == null)
					{
						continue;
					}
					for (GameObject object : here)
					{
						if (object != null)
						{
							objectSpawned(object);
						}
					}
				}
			}
		}
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
		refreshWanted(snapshot);
		if (npcs.isEmpty() && objects.isEmpty())
		{
			return;
		}
		Color colour = config.dropHighlightColour();
		boolean labels = config.dropHighlightLabels();
		for (Map.Entry<NPC, String> entry : npcs.entrySet())
		{
			NPC npc = entry.getKey();
			if (!npc.isDead())
			{
				outline(g, npc.getConvexHull(), colour, labels
					? npc.getCanvasTextLocation(g, entry.getValue(), npc.getLogicalHeight() + 40) : null,
					entry.getValue());
			}
		}
		for (Map.Entry<GameObject, String> entry : objects.entrySet())
		{
			GameObject object = entry.getKey();
			outline(g, object.getConvexHull(), colour,
				labels ? object.getCanvasTextLocation(g, entry.getValue(), 40) : null, entry.getValue());
		}
	}

	private static void outline(Graphics2D g, Shape hull, Color colour, Point at, String label)
	{
		if (hull != null)
		{
			OverlayUtil.renderPolygon(g, hull, colour);
		}
		if (at != null)
		{
			OverlayUtil.renderTextLocation(g, at, label, colour);
		}
	}

	/**
	 * Rebuilds the wanted set only when the needed list, the route's next
	 * quest or the scope setting changes, and re-reads the scene for it then.
	 */
	private void refreshWanted(OverlaySnapshot snapshot)
	{
		String quest = currentQuest(snapshot);
		int signature = snapshot.items().signature() * 31 + (quest == null ? 0 : quest.hashCode());
		if (haveWanted && signature == wantedSignature)
		{
			return;
		}
		Map<String, String> next = new HashMap<>();
		for (DropTargets.Target target : DropTargets.targets(snapshot.items().totals(), quest))
		{
			next.merge(target.sourceName().toLowerCase(Locale.ROOT), target.label(),
				(a, b) -> a + ", " + b.substring("drops ".length()));
		}
		wanted = next;
		wantedSignature = signature;
		haveWanted = true;
		rescan();
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
