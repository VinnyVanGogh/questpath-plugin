package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Marks, on the ground near the account, exactly the items its quest plan
 * still needs and does not yet have enough of.
 *
 * <p>The alternative is the same bookkeeping ShopItemOverlay exists to
 * remove, one screen over: reading a shopping list off a separate panel and
 * cross-checking it by eye against whatever happens to be lying around. This
 * overlay answers "is that worth picking up" the instant it is standing next
 * to it.
 *
 * <p>Scope is deliberately narrow: this marks items {@link Tile#getGroundItems()}
 * actually reports, nothing more. Trees, ore rocks and other harvestable game
 * objects are out of bounds on purpose -- they are {@code GameObject}s, not
 * ground items, and questpath's bundle carries no mapping from an item to the
 * object that would yield it. Turning "the wiki says bones drop here" into a
 * marker would be a kill order, not a pickup, and that is exactly the feature
 * this overlay must not become.
 *
 * <p>Matching is by item id only, never by name, for the same reason
 * ShopItemOverlay matches by id: questpath's own catalogue already resolved
 * which "Bucket" a quest needs, and string-comparing ground item names would
 * undo that.
 */
class GroundItemOverlay extends Overlay
{
	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;
	private final TooltipManager tooltips;

	/**
	 * How many tiles out from the local player to sweep, in each direction.
	 *
	 * The scene holds up to {@link Constants#SCENE_SIZE} (104) tiles per
	 * side; walking that whole grid every frame for a check that almost
	 * always finds nothing is the cost this overlay exists to avoid. A
	 * radius this size covers what a player can plausibly read on screen
	 * without paying for tiles behind the camera or across the map.
	 */
	private static final int SCAN_RADIUS = 15;

	/**
	 * Height, in local units, the item label floats above its tile.
	 *
	 * Local units are 1/128th of a tile; this clears a ground item's own
	 * sprite and the game's own "look here" glow without drifting into the
	 * tile behind it.
	 */
	private static final int LABEL_HEIGHT = 40;

	/**
	 * The needed-items lookup, keyed by item id, rebuilt only when the
	 * underlying list changes.
	 *
	 * Mirrors ShopItemOverlay's cache for the same reason: this runs once a
	 * frame while a scene is loaded, and QuestItems.totals() rarely changes
	 * between frames, so rebuilding a HashMap out of it every frame would be
	 * work for a picture that did not change.
	 */
	private Map<Integer, QuestItems.Total> lookup = java.util.Collections.emptyMap();
	private int lookupSignature;
	private boolean haveLookup;

	GroundItemOverlay(QuestpathPlugin plugin, QuestpathConfig config, Client client,
		TooltipManager tooltips)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.tooltips = tooltips;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * Nothing here may throw: this runs inside RuneLite's render loop, once a
	 * frame, and an uncaught exception here does not fail one frame quietly,
	 * it can take the overlay down for the rest of the session.
	 */
	@Override
	public Dimension render(Graphics2D g)
	{
		try
		{
			paint(g);
		}
		catch (RuntimeException e)
		{
			// Whatever went wrong, the safe answer is to draw nothing this
			// frame. The next frame gets a fresh attempt.
		}
		// This overlay owns no panel space -- it draws directly onto
		// whichever scene tiles happen to match, wherever they are -- so
		// there is no preferred size to report back.
		return null;
	}

	private void paint(Graphics2D g)
	{
		if (config == null || !config.groundHighlight())
		{
			return;
		}

		OverlaySnapshot snapshot = plugin.overlaySnapshot();
		if (snapshot == null)
		{
			// Logged out, or nothing read yet. Nothing to check the ground
			// against, so nothing is marked -- not the same claim as "you
			// need none of this".
			return;
		}

		QuestItems items = snapshot.items();
		if (items == null)
		{
			// No bundle loaded, or no container seen this session. Same
			// reasoning: silence here, not a false "nothing needed".
			return;
		}

		Map<Integer, QuestItems.Total> needed = lookupFor(items);
		if (needed.isEmpty())
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		Scene scene = worldView.getScene();
		if (scene == null)
		{
			return;
		}

		Tile[][][] tiles = scene.getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		LocalPoint playerLocation = localPlayer == null ? null : localPlayer.getLocalLocation();
		if (playerLocation == null)
		{
			// No player position to centre the sweep on -- nothing on
			// screen is worth walking the whole scene for.
			return;
		}

		int centreX = playerLocation.getSceneX();
		int centreY = playerLocation.getSceneY();
		int minX = Math.max(0, centreX - SCAN_RADIUS);
		int maxX = Math.min(Constants.SCENE_SIZE - 1, centreX + SCAN_RADIUS);
		int minY = Math.max(0, centreY - SCAN_RADIUS);
		int maxY = Math.min(Constants.SCENE_SIZE - 1, centreY + SCAN_RADIUS);

		// bankSeen() false means every owned() count on the snapshot is
		// inventory and equipment only -- the bank has not been opened this
		// session, so an item lying here may already be sitting in the bank
		// and this claim could be wrong. Still worth marking (silently
		// dropping the tile would be exactly the "we don't know" -> "there
		// is nothing" failure this project forbids), but MUTED rather than
		// WARN so the mark itself carries the caveat: a visibly weaker claim
		// instead of a confident one that might be wrong.
		// See ShopItemOverlay: only CARRIED_ONLY is a weakened claim.
		Color markColor = items.freshness() == QuestItems.Freshness.CARRIED_ONLY
			? OverlayTheme.MUTED
			: config.groundHighlightColour();

		OverlayTheme.prepare(g);

		Tile[][] planeTiles = tiles[plane];
		for (int x = minX; x <= maxX; x++)
		{
			Tile[] column = planeTiles[x];
			if (column == null)
			{
				continue;
			}
			for (int y = minY; y <= maxY; y++)
			{
				Tile tile = column[y];
				if (tile == null)
				{
					continue;
				}
				paintTile(g, tile, needed, markColor);
			}
		}
	}

	/**
	 * Draws one tile's outline and label, if it holds a needed, unsatisfied
	 * item.
	 *
	 * Only what {@link Tile#getGroundItems()} actually reports is ever a
	 * candidate -- see the class doc for why trees, rocks and the like are
	 * never considered here.
	 */
	private void paintTile(Graphics2D g, Tile tile, Map<Integer, QuestItems.Total> needed, Color markColor)
	{
		List<TileItem> groundItems = tile.getGroundItems();
		if (groundItems == null || groundItems.isEmpty())
		{
			return;
		}

		// A tile can hold more than one needed item (a bones pile and a
		// bucket dropped on the same square). One is named on the label;
		// the rest are folded into a "+N" rather than dropped silently, so
		// the count on screen never quietly under-reports what is there.
		QuestItems.Total match = null;
		int extra = 0;
		for (TileItem groundItem : groundItems)
		{
			QuestItems.Total total = needed.get(groundItem.getId());
			if (total == null || total.satisfied())
			{
				// Either not on the list, or the account already has
				// enough -- a ground item marked for something already
				// owned is noise, not help, same rule ShopItemOverlay
				// applies to shop slots.
				continue;
			}
			if (match == null)
			{
				match = total;
			}
			else
			{
				extra++;
			}
		}
		if (match == null)
		{
			return;
		}

		LocalPoint location = tile.getLocalLocation();
		Polygon poly = Perspective.getCanvasTilePoly(client, location);
		if (poly == null)
		{
			// Off camera, behind the player, or otherwise not on screen
			// this frame -- Perspective already made that call.
			return;
		}

		g.setColor(markColor);
		g.draw(poly);

		describeOnHover(poly, match, extra);

		String label = match.name();
		if (extra > 0)
		{
			label = label + " +" + extra;
		}
		if (config.groundHighlightCounts())
		{
			long outstanding = Math.max(0L, match.required() - match.owned());
			label = label + " (" + outstanding + ")";
		}

		g.setFont(OverlayTheme.smallFont());
		Point textLocation = Perspective.getCanvasTextLocation(client, g, location, label, LABEL_HEIGHT);
		if (textLocation != null)
		{
			OverlayTheme.shadowed(g, label, textLocation.getX(), textLocation.getY(), markColor);
		}
	}

	/**
	 * Says what the marked tile is for, while the pointer is over it.
	 *
	 * The label already on the tile is the item's name, which answers "what
	 * is that" and not "why do I care" -- and the second question is the one
	 * worth walking over for. Drawing the quest name onto every marked tile
	 * instead would put a sentence on the ground for each of them, which is
	 * unreadable the moment there are three; the pointer already names the one
	 * tile being considered.
	 */
	private void describeOnHover(Polygon poly, QuestItems.Total match, int extra)
	{
		if (client == null || tooltips == null)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !poly.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		String quests = match.questSummary();
		if (quests.isEmpty())
		{
			return;
		}
		StringBuilder text = new StringBuilder();
		text.append(match.name()).append(' ').append(match.describe())
			.append("</br>For ").append(quests);
		if (extra > 0)
		{
			// The tile's own label already says "+N"; without this the
			// tooltip would look like the full account of the tile and
			// silently describe only one of the things on it.
			text.append("</br>+").append(extra).append(" more needed item")
				.append(extra == 1 ? "" : "s").append(" on this tile");
		}
		tooltips.add(new Tooltip(text.toString()));
	}

	/**
	 * The needed-items lookup rebuilt as an id map, or the cached one from a
	 * prior frame when nothing has changed.
	 *
	 * Duplicated from ShopItemOverlay.lookupFor() rather than shared: the
	 * two overlays are owned by different files under the one-file-per-agent
	 * rule, and neither may edit the other.
	 */
	private Map<Integer, QuestItems.Total> lookupFor(QuestItems items)
	{
		int signature = items.signature();
		if (haveLookup && signature == lookupSignature)
		{
			return lookup;
		}

		Map<Integer, QuestItems.Total> built = new HashMap<>();
		for (QuestItems.Total total : items.totals())
		{
			if (total.itemId() <= 0)
			{
				// Never index under key 0: an item questpath's catalogue
				// could not resolve must never match a ground item by
				// accident.
				continue;
			}
			// Totals are already merged one-per-name; the first one seen
			// for a shared id is as good a choice as any, same as
			// ShopItemOverlay.
			built.putIfAbsent(total.itemId(), total);
		}

		lookup = built;
		lookupSignature = signature;
		haveLookup = true;
		return lookup;
	}
}
