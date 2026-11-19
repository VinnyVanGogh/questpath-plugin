package com.vinnyvangogh.questpath;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BankTitleTest
{
	@Test
	public void keepsSuffixAnotherPluginAppended()
	{
		assertEquals("Tab <col=ff9040>Quest Route</col> (GE: 675,889)",
			BankTabLayout.retitle("Tab 9 (GE: 675,889)"));
	}

	@Test
	public void replacesPlainTabLabel()
	{
		assertEquals("Tab <col=ff9040>Quest Route</col>", BankTabLayout.retitle("Tab 9"));
	}

	@Test
	public void leavesForeignTitlesAlone()
	{
		assertEquals("Bank of Gielinor", BankTabLayout.retitle("Bank of Gielinor"));
		assertEquals("Tab x", BankTabLayout.retitle("Tab x"));
		assertEquals(null, BankTabLayout.retitle(null));
	}
}
