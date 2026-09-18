package com.vinnyvangogh.questpath;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Every type {@link QuestpathConfig} hands back has to be reachable from a
 * dynamic proxy.
 *
 * <p><b>The bug this exists for shipped.</b> {@code LampSuggestions} was
 * declared package-private, like most classes in this plugin, and everything
 * looked fine: it compiled, the whole test suite passed, {@code ./gradlew
 * build} was green. Then the plugin refused to start at all:
 *
 * <pre>
 * java.lang.IllegalAccessError: failed to access class
 *   com.vinnyvangogh.questpath.LampSuggestions from class jdk.proxy2.$Proxy179
 * </pre>
 *
 * <p>RuneLite does not implement a {@code Config} interface with a class of
 * its own — it generates a JDK dynamic proxy, and that proxy lives in the
 * {@code jdk.proxy2} module rather than in this package. A package-private
 * return type is invisible to it, so the first read of that setting throws.
 * The first read happened in {@code OverlaySettingsPanel}'s constructor, which
 * {@code startUp} calls, so one non-public enum took down the entire plugin —
 * including the sidebar, the overlays, and the quest verdicts that have
 * nothing to do with lamps.
 *
 * <p>Nothing else can catch this. It is not a compile error, because within
 * this package the access is perfectly legal. It is not a unit-test failure,
 * because no test constructs the proxy. It only appears in a running client,
 * which is exactly the thing {@code AGENTS.md} says cannot be verified here —
 * "a clean JVM start is not a passing test", and this was not even that.
 *
 * <p>So the rule gets asserted structurally instead: reflect over the config
 * interface, and require every {@code @ConfigItem}'s return type to be public.
 */
public class QuestpathConfigProxyTest
{
	/**
	 * Every {@code @ConfigItem} return type must be public, or the proxy
	 * cannot hand it back.
	 *
	 * <p>Primitives and JDK types pass trivially; what this really polices is
	 * the enums this plugin declares itself, which are the ones a developer
	 * writes package-private out of habit because everything else here is.
	 */
	@Test
	public void everyConfigItemReturnTypeIsReachableFromAProxy()
	{
		List<String> offenders = new ArrayList<>();

		for (Method method : QuestpathConfig.class.getDeclaredMethods())
		{
			if (method.getAnnotation(ConfigItem.class) == null)
			{
				continue;
			}
			Class<?> returned = method.getReturnType();
			if (returned.isPrimitive())
			{
				continue;
			}
			if (!Modifier.isPublic(returned.getModifiers()))
			{
				offenders.add(method.getName() + " returns " + returned.getSimpleName()
					+ ", which is not public");
			}
		}

		assertTrue("a RuneLite Config is implemented by a JDK dynamic proxy in another"
			+ " module, so a non-public return type throws IllegalAccessError on first"
			+ " read and takes the whole plugin down at startUp: " + offenders,
			offenders.isEmpty());
	}

	/**
	 * The config interface itself, and the enum the bug was found in, pinned
	 * by name.
	 *
	 * <p>Named explicitly as well as covered by the sweep above, because the
	 * sweep only looks at methods carrying {@code @ConfigItem} — and a future
	 * refactor that reached this enum by some other route would still hit the
	 * same wall.
	 */
	@Test
	public void theConfigInterfaceAndItsEnumsArePublic()
	{
		assertTrue("QuestpathConfig must be public for RuneLite to proxy it",
			Modifier.isPublic(QuestpathConfig.class.getModifiers()));
		assertTrue("LampSuggestions is a @ConfigItem return type and must be public",
			Modifier.isPublic(LampSuggestions.class.getModifiers()));
	}
}
