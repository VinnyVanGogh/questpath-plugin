package com.vinnyvangogh.questpath;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;

/**
 * The pictures the overlays draw, from the client's own caches.
 *
 * <p>Item art comes from {@link ItemManager} and skill art from
 * {@link SkillIconManager}, which means the game's real sprites, already on
 * disk, at no network cost. Nothing is fetched from the wiki at draw time:
 * an overlay repaints every frame, and a picture that has to arrive over the
 * network is a picture that is missing for the first second and then jumps in.
 *
 * <p>Items are looked up by the id questpath sends in the bundle, never by
 * name. The id is the whole point -- questpath's 16,472-row catalogue has
 * already decided that "Bucket" the quest item is 1925 and not the
 * Meiyerditch bucket or the animation-only one, and a name search in the
 * client would redo that badly. An item questpath has no id for draws no icon
 * at all, which is why every caller must handle null.
 */
@Singleton
class OverlayIcons
{
	/**
	 * The width an item sprite is drawn at. RuneLite's item images are 36x32,
	 * so they are drawn unscaled -- scaling them blurs pixel art that was
	 * authored at this size.
	 */
	static final int ITEM_WIDTH = 36;
	static final int ITEM_HEIGHT = 32;

	/** Skill icons are around 25px square and are drawn scaled to this. */
	static final int SKILL_SIZE = 18;

	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;

	/**
	 * Resolved sprites, so a list of twenty items is twenty cache reads per
	 * frame rather than twenty cache lookups plus twenty rescales.
	 *
	 * Only ever touched from the client thread, where overlays render, so it
	 * needs no synchronisation. A null value is cached too: it records "asked
	 * and there is none", which stops a missing sprite being re-requested
	 * every frame forever.
	 */
	private final Map<Integer, BufferedImage> itemCache = new HashMap<>();
	private final Map<Skill, BufferedImage> skillCache = new HashMap<>();

	@Inject
	OverlayIcons(ItemManager itemManager, SkillIconManager skillIcons)
	{
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
	}

	/**
	 * One item's sprite, or null when there is none to draw.
	 *
	 * Null for id 0, because questpath omits the id rather than sending zero
	 * when its catalogue could not identify the item -- and item 0 is a real
	 * item, so drawing it would be confident, wrong art for something nobody
	 * identified.
	 *
	 * <p><b>Client thread only.</b>
	 */
	BufferedImage item(int itemId)
	{
		if (itemId <= 0 || itemManager == null)
		{
			return null;
		}
		if (itemCache.containsKey(itemId))
		{
			return itemCache.get(itemId);
		}
		BufferedImage image = null;
		try
		{
			// Quantity 1, not stackable: the overlay says how many are wanted
			// in words, and a stack number burnt into the sprite would be a
			// second, smaller, contradicting answer whenever the two differ.
			image = itemManager.getImage(itemId, 1, false);
		}
		catch (RuntimeException e)
		{
			// An id the client's cache does not carry. Nothing to draw, and
			// nothing worth interrupting a frame for -- the text still says
			// what the item is.
			image = null;
		}
		itemCache.put(itemId, image);
		return image;
	}

	/**
	 * One skill's tab icon, scaled to {@link #SKILL_SIZE}, or null.
	 *
	 * <p><b>Client thread only.</b>
	 */
	BufferedImage skill(Skill skill)
	{
		if (skill == null || skillIcons == null)
		{
			return null;
		}
		if (skillCache.containsKey(skill))
		{
			return skillCache.get(skill);
		}
		BufferedImage image = null;
		try
		{
			BufferedImage full = skillIcons.getSkillImage(skill, true);
			if (full != null)
			{
				image = net.runelite.client.util.ImageUtil.resizeImage(full, SKILL_SIZE, SKILL_SIZE);
			}
		}
		catch (RuntimeException e)
		{
			image = null;
		}
		skillCache.put(skill, image);
		return image;
	}

	/**
	 * Drops everything resolved so far.
	 *
	 * Called when the plugin stops. The caches hold client-cache images whose
	 * lifetime is the session, and a plugin that is toggled off and on again
	 * should not keep art from before.
	 */
	void clear()
	{
		itemCache.clear();
		skillCache.clear();
	}
}
