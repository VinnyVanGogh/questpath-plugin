package com.vinnyvangogh.questpath;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import com.google.inject.Provides;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * questpath — build-rule-aware quest planning, in the client.
 *
 * <p>v1 scope. The panel answers what the next combat level costs in every
 * stat, which needs no quest data, no bundled graph, and no server. The quest
 * planner and the state push to the questpath web app follow; the full plan is
 * docs/questpath-plugin-plan.md in the Go repo.
 *
 * <p>There is no config class yet. The first genuine setting is the state-push
 * server URL, which arrives with the feature that needs it — a stub config
 * would only invite guessing at options early.
 */
@Slf4j
@PluginDescriptor(
	name = "questpath",
	description = "What the next combat level costs, and which quests your build can legally do",
	tags = {"quest", "combat", "pure", "ironman", "planner", "build"}
)
public class QuestpathPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private QuestpathConfig config;

	@Inject
	private AccountStateSender sender;

	private QuestpathPanel panel;
	private NavigationButton navButton;

	/**
	 * Fingerprint of the last state sent, so an unchanged account is not
	 * pushed again. Reset on logout because the next login may be a different
	 * account, and its state must not be mistaken for unchanged.
	 */
	private int lastSentFingerprint;
	private boolean haveSent;

	@Override
	protected void startUp()
	{
		panel = new QuestpathPanel();
		navButton = NavigationButton.builder()
			.tooltip("questpath")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// The plugin can be enabled mid-session, so there may already be a
		// logged-in account whose StatChanged burst has been and gone.
		refresh();
		log.debug("questpath started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		haveSent = false;
		log.debug("questpath stopped");
	}

	/**
	 * Levels arrive as a burst of StatChanged at login and one at a time
	 * afterwards. Recomputing on each is cheap — seven integer reads and a
	 * bounded search per skill — and it keeps the panel correct without a
	 * timer.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		refresh();
		maybeSync();
	}

	/**
	 * Quest completion moves varbits, so this is where a finished quest is
	 * noticed. There is no "quest completed" event to subscribe to.
	 *
	 * Every varbit in the game arrives here, which is why the push is gated on
	 * the payload actually differing rather than on the event firing.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		maybeSync();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refresh();
			maybeSync();
			return;
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// A logged-out client reports every skill as level 1, which would
			// render as a confident "combat 3" for an account that is nothing
			// of the sort.
			panel.showLoggedOut();
			// The next login may be a different account, whose state must not
			// be mistaken for "unchanged since last push".
			haveSent = false;
		}
	}

	private void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			panel.showLoggedOut();
			return;
		}
		panel.update(currentStats(), currentXp());
	}

	/**
	 * Real levels, not boosted ones. A combat level is computed from the
	 * account's actual stats, so a dose of super strength must not appear to
	 * move it.
	 */
	private CombatStats currentStats()
	{
		return new CombatStats(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.PRAYER));
	}

	/**
	 * Real experience for the combat seven, validated against the levels the
	 * client reports in the same read.
	 *
	 * Reading XP rather than deriving it from level thresholds is what makes
	 * every figure on the panel exact instead of an upper bound. The
	 * validation matters because LOGGED_IN can fire before the stats packet
	 * arrives, and getSkillExperience answers 0 until it does — see SkillXp.
	 */
	private SkillXp currentXp()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		for (Skill skill : CombatStats.COMBAT_SKILLS)
		{
			levels.put(skill, client.getRealSkillLevel(skill));
			xp.put(skill, client.getSkillExperience(skill));
		}
		return SkillXp.of(levels, xp);
	}

	/**
	 * Pushes account state to questpath when it has actually changed.
	 *
	 * The gate is the payload, not the event. onVarbitChanged fires for every
	 * varbit in the game and StatChanged for every experience drop, so a push
	 * per event would be a request per tick while training. Comparing what
	 * would be sent means the request happens exactly when something the
	 * server cares about moved — a quest finishing, a level going up.
	 *
	 * Reading the state must happen on the client thread (quest varbits), and
	 * the request must not, which is why the read is wrapped and the send is
	 * an enqueue inside it.
	 */
	private void maybeSync()
	{
		if (!config.syncEnabled() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			Map<String, Object> payload = AccountState.read(client);
			int fingerprint = AccountState.fingerprint(payload);
			if (haveSent && fingerprint == lastSentFingerprint)
			{
				return;
			}
			if (sender.send(config.serverUrl(), config.pairingToken(), payload))
			{
				// Recorded on dispatch rather than on a successful response:
				// a questpath that is not running would otherwise mean a
				// retry on every varbit for as long as it stays down. The
				// next real change re-sends anyway.
				lastSentFingerprint = fingerprint;
				haveSent = true;
			}
		});
	}

	@Provides
	QuestpathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuestpathConfig.class);
	}

	/**
	 * Drawn rather than loaded from a resource, deliberately: a placeholder
	 * PNG committed now would be an asset nobody designed, and AGENTS.md warns
	 * about shipping images that are not what they claim to be. Replace with a
	 * real icon.png when there is one.
	 */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(0xC8, 0x8B, 0x2E));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			g.drawString("qp", 2, 18);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
