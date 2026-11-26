package com.vinnyvangogh.questpath;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins a promise this project has already broken once: every option in
 * {@link QuestpathConfig} must be reachable from questpath's own sidebar
 * Settings tab ({@link OverlaySettingsPanel}), not only from RuneLite's own
 * config panel behind a different icon. Three additions -- two since
 * removed with the Quest Helper integration, and {@code shopArrivalPrompt}
 * -- went only to the first, invisibly to a player who only ever opens the
 * second, and a fourth ({@code pickupsLookahead}) had been missing since the
 * pickups overlay itself was added. This test exists so a fifth cannot do
 * the same without failing loudly, here, by name.
 *
 * <p><b>What this proves, and what it does not.</b> It confirms every
 * non-exempt {@code @ConfigItem} keyName declared on {@link QuestpathConfig}
 * -- found by reflecting over the interface itself, so a renamed or newly
 * added key is picked up automatically rather than requiring this test to be
 * hand-updated -- appears as a quoted string literal somewhere in {@code
 * OverlaySettingsPanel.java}'s own source, which is where every control in
 * that file hands its key to {@code ConfigManager#setConfiguration}. It does
 * <b>not</b> prove the control behind that literal actually renders, is
 * wired to the right widget, or writes the right type -- confirming that
 * still needs a human looking at the running sidebar, or a future test that
 * drives the real Swing tree. What this closes is the one failure mode
 * already shipped: an option that exists for RuneLite's own panel with
 * nothing in this file even mentioning its key.
 */
public class OverlaySettingsPanelConfigParityTest
{
	/**
	 * Config keys this file is not expected to carry, named explicitly
	 * rather than filtered by some property they happen to share -- so a
	 * third or fourth exemption is something the next person has to type
	 * here, deliberately, not something that falls out of a rule nobody
	 * notices growing.
	 *
	 * <p>{@code buildRulesShowAll} is exempt: its
	 * control does not belong on the Settings tab at all. It lives inline in
	 * the Combat tab's own Build rules section, next to the rules it
	 * filters, the same way the quest search box and status filter live next
	 * to the quest list rather than on a settings screen. See {@code
	 * QuestpathPanel}'s build-rules-block rendering.
	 */
	private static final Set<String> EXEMPT_KEYS = Set.of("buildRulesShowAll",
		// Update notes: a chat toggle that lives in RuneLite's config panel
		// next to the other chat settings (the replay is the sidebar's own
		// What's new button), plus two hidden bookkeeping values the plugin
		// writes itself.
		"showUpdateNotes", "lastSeenVersion", "lastSeenBuild");

	private static final Path PANEL_SOURCE =
		Path.of("src/main/java/com/vinnyvangogh/questpath/OverlaySettingsPanel.java");

	/**
	 * The test the sidebar's own drift keeps calling for: every real
	 * {@code @ConfigItem} key, except the two named exemptions, must be
	 * quoted somewhere in {@link OverlaySettingsPanel}'s source.
	 */
	@Test
	public void everyConfigItemExceptTheNamedExemptionsAppearsInTheSidebar() throws IOException
	{
		String panelSource = readPanelSource();

		List<String> missing = new ArrayList<>();
		for (String keyName : configItemKeyNames())
		{
			if (EXEMPT_KEYS.contains(keyName))
			{
				continue;
			}
			if (!panelSource.contains('"' + keyName + '"'))
			{
				missing.add(keyName);
			}
		}

		assertTrue("OverlaySettingsPanel is missing these QuestpathConfig keys: " + missing
			+ " -- add each to the sidebar section it belongs to, or add it to"
			+ " OverlaySettingsPanelConfigParityTest.EXEMPT_KEYS with a reason if it genuinely"
			+ " should not appear there.", missing.isEmpty());
	}

	/**
	 * Guards the guard. If a name in {@link #EXEMPT_KEYS} were misspelled or
	 * the key it names were ever removed from {@link QuestpathConfig}, the
	 * test above would keep passing for the wrong reason -- silently
	 * exempting a key that no longer needs exempting, or that was never
	 * real. This pins that both exempt names are genuine, current
	 * {@code @ConfigItem} keys.
	 */
	@Test
	public void theExemptKeysAreThemselvesRealConfigItems()
	{
		Set<String> real = configItemKeyNames();
		for (String exempt : EXEMPT_KEYS)
		{
			assertTrue(exempt + " is listed in EXEMPT_KEYS but is not a real QuestpathConfig key -- "
				+ "the exemption no longer means anything", real.contains(exempt));
		}
	}

	/**
	 * A cross-check on the check itself: a key this test invents, that
	 * genuinely appears nowhere in the real panel source, must fail the
	 * literal-search the main test relies on. Without this, a bug in the
	 * literal search (matching everything, or matching nothing) would let
	 * the main test pass or fail for reasons that have nothing to do with
	 * the sidebar actually being complete.
	 */
	@Test
	public void aFabricatedKeyThatIsNotInThePanelIsCorrectlyReportedMissing() throws IOException
	{
		String panelSource = readPanelSource();
		assertFalse("test setup is broken: this fabricated key must not appear in the real panel source",
			panelSource.contains("\"totallyNotARealConfigKeyXyz\""));
	}

	private static Set<String> configItemKeyNames()
	{
		Set<String> keys = new TreeSet<>();
		for (Method method : QuestpathConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
			}
		}
		return keys;
	}

	private static String readPanelSource() throws IOException
	{
		return Files.readString(PANEL_SOURCE);
	}
}
