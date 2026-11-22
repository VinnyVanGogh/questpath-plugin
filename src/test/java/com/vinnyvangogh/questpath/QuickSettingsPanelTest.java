package com.vinnyvangogh.questpath;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JCheckBox;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link QuickSettingsPanel}: a click writes the real key, and a value
 * changed elsewhere shows up after {@link QuickSettingsPanel#refresh()}.
 */
public class QuickSettingsPanelTest
{
	/** Stands in for ConfigManager: the stored values, and every write in order. */
	private static final class MemoryConfig implements QuickSettingsPanel.Store
	{
		final Map<String, Boolean> values = new HashMap<>();
		final List<String> writes = new ArrayList<>();

		@Override
		public void write(String keyName, boolean value)
		{
			values.put(keyName, value);
			writes.add(keyName + "=" + value);
		}

		QuickSettingsPanel.Row row(String key, String warning)
		{
			return new QuickSettingsPanel.Row(key, key, key, () -> values.getOrDefault(key, false), warning);
		}
	}

	private static QuickSettingsPanel panel(MemoryConfig config, String... keys)
	{
		List<QuickSettingsPanel.Row> rows = new ArrayList<>();
		for (String key : keys)
		{
			rows.add(config.row(key, null));
		}
		return new QuickSettingsPanel(rows, config, () -> {});
	}

	@Test
	public void buildingThePanelWritesNothing()
	{
		MemoryConfig config = new MemoryConfig();
		config.values.put("a", true);
		panel(config, "a", "b");
		assertTrue("opening the panel must not stamp defaults as saved settings", config.writes.isEmpty());
	}

	@Test
	public void clickingAToggleWritesItsKey()
	{
		MemoryConfig config = new MemoryConfig();
		QuickSettingsPanel panel = panel(config, "a", "b");
		JCheckBox box = panel.boxFor("b");
		assertFalse(box.isSelected());
		box.doClick();
		assertEquals(Arrays.asList("b=true"), config.writes);
		box.doClick();
		assertEquals(Arrays.asList("b=true", "b=false"), config.writes);
	}

	@Test
	public void configChangedElsewhereUpdatesTheBoxWithoutWriting()
	{
		MemoryConfig config = new MemoryConfig();
		QuickSettingsPanel panel = panel(config, "a");
		assertFalse(panel.boxFor("a").isSelected());
		config.values.put("a", true);
		panel.refresh();
		assertTrue(panel.boxFor("a").isSelected());
		assertTrue("a refresh is not a click", config.writes.isEmpty());
	}

	@Test
	public void everyRowIsARealConfigKeyAndEachKeyIsOnlyOnce() throws Exception
	{
		Set<String> real = new HashSet<>();
		for (Method method : QuestpathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && method.getReturnType() == boolean.class)
			{
				real.add(item.keyName());
			}
		}
		Set<String> seen = new HashSet<>();
		List<QuickSettingsPanel.Row> rows = QuickSettingsPanel.rows(new QuestpathConfig() {});
		assertTrue("6-10 quick settings", rows.size() >= 6 && rows.size() <= 10);
		for (QuickSettingsPanel.Row row : rows)
		{
			assertTrue(row.keyName + " is not a boolean config key", real.contains(row.keyName));
			assertTrue(row.keyName + " listed twice", seen.add(row.keyName));
			assertNotNull(row.tooltip);
		}
	}
}
