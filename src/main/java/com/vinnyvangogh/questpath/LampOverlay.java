package com.vinnyvangogh.questpath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Marks up the experience lamp's skill-choice interface: greys the squares a
 * build rule closes, and boxes the ones worth choosing.
 *
 * <p><b>The problem it solves.</b> That interface shows twenty-four identical
 * squares and asks for a decision that cannot be taken back. For an account
 * holding Attack and Defence at 1 on purpose, two of those squares end the
 * build, and nothing on screen distinguishes them from the other twenty-two.
 *
 * <p><b>Compliance.</b> Purely additive drawing over components that are
 * already on screen. The nearest rule is the Interface Restrictions group —
 * "No unhiding hidden interface components (special attack bar, minimap)" and
 * "No moving or resizing click zones for combat options, inventory, equipment,
 * or spellbook". This unhides nothing, moves nothing, resizes no click zone,
 * and removes no menu entry; every square stays exactly as clickable as the
 * game made it. The precedent is the base client's own <b>Attack Styles</b>
 * plugin ({@code net.runelite.client.plugins.attackstyles}), which takes
 * per-skill user configuration — {@code warnForDefence}, {@code warnForAttack},
 * {@code warnForStrength}, {@code warnForRanged}, {@code warnForMagic} — warns
 * when a warned skill is selected, and with {@code removeWarnedStyles} goes
 * further and <em>hides</em> the offending widgets outright
 * ({@code hideWidget}, {@code getHiddenWidgets}). This is strictly narrower
 * than what RuneLite itself ships: it only draws.
 *
 * <p>Drawn on {@link OverlayLayer#ABOVE_WIDGETS}, which is what puts it over
 * an interface rather than under it.
 */
class LampOverlay extends Overlay
{
	/** Thick enough to read against the interface's own square borders. */
	private static final Stroke BOX_STROKE = new BasicStroke(2f);

	/**
	 * The wash drawn over a blocked square.
	 *
	 * <p>Translucent rather than opaque on purpose: the skill icon underneath
	 * has to stay recognisable. A square the player cannot identify is not a
	 * warning, it is a hole in the interface, and they will click it to find
	 * out what it was.
	 */
	private static final Color GREY_WASH = new Color(0, 0, 0, 150);

	/**
	 * The tint for a risky square — amber, and deliberately not the grey.
	 * "You told me to warn you about this" and "this breaks your build" are
	 * different sentences, and if the second is drawn the same way as the
	 * first it stops being heard.
	 */
	private static final Color RISKY_TINT = new Color(255, 170, 0, 70);

	private final QuestpathPlugin plugin;
	private final QuestpathConfig config;
	private final Client client;
	private final TooltipManager tooltips;

	LampOverlay(QuestpathPlugin plugin, QuestpathConfig config, Client client, TooltipManager tooltips)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.tooltips = tooltips;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * Draws one frame.
	 *
	 * <p>Nothing here may throw. This runs inside RuneLite's render loop, and
	 * an uncaught exception does not lose one frame quietly — it can take the
	 * overlay down for the rest of the session. The same guard
	 * {@link BankNeedsOverlay} carries, for the same reason.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			paint(graphics);
		}
		catch (RuntimeException e)
		{
			// Draw nothing this frame; the next one gets a fresh attempt.
		}
		return null;
	}

	private void paint(Graphics2D graphics)
	{
		if (config == null || !config.lampAdvisor())
		{
			return;
		}
		List<LampVerdict> verdicts = plugin.lampVerdicts();
		if (verdicts == null || verdicts.isEmpty())
		{
			return;
		}

		LampSuggestions mode = config.lampSuggestions();
		int suggestLimit = mode.count();

		OverlayTheme.prepare(graphics);
		for (LampVerdict verdict : verdicts)
		{
			int component = LampSkillWidgets.componentFor(verdict.skill());
			if (component < 0)
			{
				continue;
			}
			Widget widget = client.getWidget(component);
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			Rectangle bounds = widget.getBounds();
			if (bounds == null || bounds.isEmpty())
			{
				continue;
			}
			drawVerdict(graphics, bounds, verdict, suggestLimit);
			describeOnHover(bounds, verdict);
		}
	}

	private void drawVerdict(Graphics2D graphics, Rectangle bounds, LampVerdict verdict, int suggestLimit)
	{
		switch (verdict.state())
		{
			case BLOCKED:
				if (config.lampGreyBlocked())
				{
					graphics.setColor(GREY_WASH);
					graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				}
				return;
			case RISKY:
				graphics.setColor(RISKY_TINT);
				graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				return;
			case UNKNOWN:
				// Deliberately undrawn. The skill may be a perfectly good
				// target; questpath simply cannot price this lamp against its
				// cap. Tinting it would assert a doubt the overlay does not
				// have grounds for -- the tooltip says "amount unknown", which
				// is the honest version and is available on hover.
				return;
			default:
				break;
		}

		if (verdict.rank() > 0 && verdict.rank() <= suggestLimit)
		{
			// The best gets one colour and the next two another, so the top
			// choice is still identifiable once three squares are boxed.
			graphics.setColor(verdict.rank() == 1
				? config.lampBestColour()
				: config.lampNextColour());
			Stroke previous = graphics.getStroke();
			graphics.setStroke(BOX_STROKE);
			graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
			graphics.setStroke(previous);

			String badge = verdict.gainBadge();
			if (!badge.isEmpty())
			{
				int x = bounds.x + 3;
				int y = bounds.y + bounds.height - 3;
				graphics.setColor(Color.BLACK);
				graphics.drawString(badge, x + 1, y + 1);
				graphics.setColor(Color.WHITE);
				graphics.drawString(badge, x, y);
			}
		}
	}

	/**
	 * The reason, on hover.
	 *
	 * <p>The colours say which squares to avoid; only this says why. A player
	 * who cannot find out why will eventually decide the overlay is wrong and
	 * stop reading it — and the one time it was right is the time that costs
	 * them the account.
	 */
	private void describeOnHover(Rectangle bounds, LampVerdict verdict)
	{
		if (client == null || tooltips == null)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		String text = tooltipFor(verdict);
		if (!text.isEmpty())
		{
			tooltips.add(new Tooltip(text));
		}
	}

	private String tooltipFor(LampVerdict verdict)
	{
		if (!verdict.reason().isEmpty())
		{
			return verdict.reason();
		}
		if (verdict.state() == LampVerdict.State.UNKNOWN)
		{
			return "amount unknown";
		}
		if (verdict.rank() > 0)
		{
			String levels = String.format("%.1f", verdict.levelsGained());
			String tier = verdict.tier() == LampVerdict.Tier.ROUTE
				? " -- a level your route is waiting on"
				: verdict.tier() == LampVerdict.Tier.GOAL ? " -- towards your goal" : "";
			String badge = verdict.gainBadge();
			return "#" + verdict.rank() + ": " + (badge.isEmpty() ? "" : badge + " level -- ")
				+ levels + " levels here"
				+ " (" + verdict.levelNow() + " to " + verdict.levelAfter() + ")" + tier;
		}
		return "";
	}
}
