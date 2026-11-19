package com.vinnyvangogh.questpath.scenarios;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Developer client only: a "Scenarios" sidebar that posts a scenario's events, or any JSON pasted into it, into the
 * client's real EventBus on the client thread, as the game would. Lives in src/test, so it is never in the Plugin
 * Hub jar (HubRulesTest and preflight check this).
 *
 * <p>Started by the plugin's {@code *PluginTest} launcher, which calls {@link #use} first:
 * <pre>
 *   ScenariosPlugin.use(MyEventKinds.kinds(), MyPlugin.class);
 *   ExternalPluginManager.loadBuiltin(MyPlugin.class, ScenariosPlugin.class);
 * </pre>
 * No game input is sent and nothing reaches Jagex: an event is only a Java object handed to subscribers. Saved
 * scenarios live in this plugin's own folder, {@code .runelite/plugin-data/quest-route-scenarios/scenarios}.
 */
@PluginDescriptor(
	name = "Scenarios (dev)",
	// Not the plugin's own name: getPluginDirectory() gives this plugin its own folder.
	internalName = "quest-route-scenarios",
	description = "Posts made-up game events, or pasted JSON, into the developer client",
	developerPlugin = true
)
public class ScenariosPlugin extends Plugin
{
	/** Set by the launcher before RuneLite starts; read once when the plugin starts. */
	private static volatile EventKinds kinds = EventKinds.core();

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	/** Set by the launcher: the plugin "This plugin only" sends to. Null means only "All plugins" works. */
	private static volatile Class<? extends Plugin> owner;

	private NavigationButton button;
	private PrivateBus privateBus;

	/** The plugin's kinds (core plus its own), for the panel to read scenarios with. */
	public static void use(EventKinds pluginKinds)
	{
		kinds = pluginKinds;
	}

	/** The plugin under test: "This plugin only" posts to a private bus with just this plugin registered. */
	public static void use(EventKinds pluginKinds, Class<? extends Plugin> plugin)
	{
		kinds = pluginKinds;
		owner = plugin;
	}

	@Override
	protected void startUp()
	{
		ScenarioStore store = new ScenarioStore(new ScenarioFiles.OnDisk(this::getPluginDirectory), kinds);
		privateBus = new PrivateBus(this::ownerInstance);
		ScenarioPanel[] holder = new ScenarioPanel[1];
		Consumer<Object> everyone = event -> clientThread.invokeLater(() -> eventBus.post(event));
		Consumer<Object> theOwner = event -> clientThread.invokeLater(() ->
		{
			try
			{
				privateBus.accept(event);
			}
			catch (ScenarioException e)
			{
				SwingUtilities.invokeLater(() -> holder[0].report(e.getMessage()));
			}
		});
		ScenarioPanel panel = new ScenarioPanel(kinds, store, everyone, owner == null ? null : theOwner,
			owner == null ? null : owner.getSimpleName());
		holder[0] = panel;
		button = NavigationButton.builder()
			.tooltip("Scenarios (dev)")
			.icon(icon())
			.priority(99)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(button);
		SwingUtilities.invokeLater(panel::reload);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(button);
		button = null;
		privateBus.close();
		privateBus = null;
		DryRun.shared().enabled(false);
	}

	/** The running instance of the plugin under test, found through the PluginManager (no reflection). */
	private Object ownerInstance()
	{
		Class<? extends Plugin> wanted = owner;
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p.getClass() == wanted && pluginManager.isPluginEnabled(p))
			{
				return p;
			}
		}
		return null;
	}

	/** A drawn "S" so the harness needs no image resource. */
	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x2E7D32));
		g.fillRoundRect(0, 0, 16, 16, 5, 5);
		g.setColor(Color.WHITE);
		g.drawString("S", 4, 13);
		g.dispose();
		return image;
	}
}
