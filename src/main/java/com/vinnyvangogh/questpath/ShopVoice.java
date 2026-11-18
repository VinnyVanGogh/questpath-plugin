package com.vinnyvangogh.questpath;

import java.util.ArrayList;
import java.util.List;

/** What a shop line asks for, out of the plan's totals. */
final class ShopVoice
{
	private ShopVoice()
	{
	}

	/**
	 * One want per still-needed item, how many still to get: anything already
	 * covered by what is owned (or can be made from it) is left out, and an
	 * item with nothing outstanding is never said as "0".
	 */
	static List<VoiceLines.Want> wants(List<QuestItems.Total> totals)
	{
		final List<VoiceLines.Want> out = new ArrayList<>();
		for (QuestItems.Total total : totals)
		{
			if (total.covered() || total.outstanding() <= 0)
			{
				continue;
			}
			out.add(new VoiceLines.Want(total.name(), total.outstanding()));
		}
		return out;
	}
}
