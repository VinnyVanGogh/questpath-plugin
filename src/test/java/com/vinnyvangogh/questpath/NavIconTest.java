package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class NavIconTest
{
	@Test
	public void iconIsDrawnAndNotBlank()
	{
		BufferedImage icon = QuestpathPlugin.icon();
		assertEquals(24, icon.getWidth());
		assertEquals(24, icon.getHeight());

		int opaque = 0;
		for (int x = 0; x < icon.getWidth(); x++)
		{
			for (int y = 0; y < icon.getHeight(); y++)
			{
				if ((icon.getRGB(x, y) >>> 24) != 0)
				{
					opaque++;
				}
			}
		}
		// A cape plus scroll fills well over a third of the tile; a blank or
		// text-only glyph would not.
		assertTrue("icon mostly transparent: " + opaque, opaque > 200);
		// Corners stay transparent so it reads as a shape, not a square.
		assertEquals(0, icon.getRGB(0, 0) >>> 24);
	}
}
