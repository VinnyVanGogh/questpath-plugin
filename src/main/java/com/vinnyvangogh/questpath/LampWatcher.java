package com.vinnyvangogh.questpath;

/**
 * Which lamp the player is actually holding when the skill-choice interface
 * opens.
 *
 * <p><b>The interface cannot tell you.</b> Every experience lamp opens the
 * same {@code InterfaceID.XPREWARD} with the same title, and the amount
 * differs by two orders of magnitude between them — an "Antique lamp" is 300
 * experience from X Marks the Spot and 100,000 from Desert Treasure II.
 * Advising on the interface alone would mean advising on an unknown amount
 * while looking like it knew, which is the one thing the advisor must never
 * do.
 *
 * <p>So the lamp is identified by the item the player used. This records the
 * last item a menu option was clicked on, and the interface opening shortly
 * afterwards is taken to be that item's.
 *
 * <p><b>Deliberately not a list of menu verbs.</b> Most lamps are "Rub" and a
 * Book of Knowledge is "Read", and a hardcoded verb list is a thing that goes
 * quietly out of date the first time Jagex ships a lamp with a fourth word on
 * it — leaving the advisor silent on exactly the new content a player is most
 * likely to be holding something unfamiliar from. Any item click is recorded;
 * it is the interface opening that decides one of them was a lamp.
 *
 * <p>Pure, and tested as such: ticks in, item id out, no client.
 */
class LampWatcher
{
	/**
	 * How many ticks a remembered item stays eligible to be the lamp.
	 *
	 * <p>Six ticks is about 3.6 seconds — comfortably longer than the gap
	 * between clicking an item and its interface appearing, and short enough
	 * that an item clicked, ignored, and then followed a minute later by a
	 * lamp claimed from an NPC does not get mistaken for it.
	 *
	 * <p>The failure this bounds is asymmetric, which is why it errs short:
	 * forgetting a lamp means the advisor says "amount unknown", which is
	 * true and harmless. Remembering the wrong item means pricing one lamp as
	 * another and ranking confidently on it, which is the expensive kind of
	 * wrong.
	 */
	static final int MEMORY_TICKS = 6;

	private int itemId;
	private String itemName = "";
	private int clickedAtTick = Integer.MIN_VALUE;

	private boolean interfaceOpen;
	private int lampItemId;
	private String lampItemName = "";

	/** Records a menu click on an inventory item. */
	void itemClicked(int itemId, String itemName, int tick)
	{
		this.itemId = itemId;
		this.itemName = itemName == null ? "" : itemName;
		this.clickedAtTick = tick;
	}

	/**
	 * Records that the skill-choice interface has opened, and fixes which
	 * item it belongs to.
	 *
	 * <p>Fixed at open rather than read live on every frame, so a click
	 * elsewhere while the interface is up cannot change what the overlay
	 * thinks it is advising on mid-decision.
	 */
	void interfaceOpened(int tick)
	{
		interfaceOpen = true;
		if (tick - clickedAtTick <= MEMORY_TICKS && clickedAtTick != Integer.MIN_VALUE)
		{
			lampItemId = itemId;
			lampItemName = itemName;
		}
		else
		{
			// The interface is open and questpath does not know what opened
			// it. An honest unknown: zero is not a real item id here, and
			// LampCatalogue.forItem turns it into an unmodelled lamp, which
			// still greys NO_XP skills and still refuses to rank.
			lampItemId = 0;
			lampItemName = "";
		}
	}

	/** Records that the interface has closed. */
	void interfaceClosed()
	{
		interfaceOpen = false;
		lampItemId = 0;
		lampItemName = "";
	}

	boolean open()
	{
		return interfaceOpen;
	}

	/**
	 * The lamp the open interface belongs to, looked up in the catalogue, or
	 * null when the interface is not open.
	 *
	 * <p>Never a null lamp while open: an item the catalogue has not heard
	 * of, and the case where questpath could not tell which item it was, both
	 * come back as an unmodelled lamp rather than as nothing. Returning
	 * nothing would let a caller skip the advisor, and skipping it is wrong —
	 * a NO_XP skill must be greyed whether or not the amount is known.
	 */
	Lamp lamp(LampCatalogue catalogue)
	{
		if (!interfaceOpen || catalogue == null)
		{
			return null;
		}
		return catalogue.forItem(lampItemId, lampItemName);
	}

	/** The item id the open interface was attributed to, or 0. */
	int lampItemId()
	{
		return lampItemId;
	}
}
