package com.vinnyvangogh.questpath;

import java.util.Collections;
import java.util.List;

/**
 * One thing Quest Route says: the sentence as text, and the clips that say
 * it. No clips means some part of the sentence has no recording, and the
 * plugin plays a ping and shows the text instead -- never silence.
 */
final class Utterance
{
	final String text;
	/** Class resource paths, in order; empty when any clip is missing. */
	final List<String> clips;

	Utterance(String text, List<String> clips)
	{
		this.text = text;
		this.clips = Collections.unmodifiableList(clips);
	}

	static Utterance textOnly(String text)
	{
		return new Utterance(text, Collections.emptyList());
	}

	/** Whether this plays a ping and shows the text rather than speaking. */
	boolean fallback()
	{
		return clips.isEmpty();
	}

	@Override
	public String toString()
	{
		return text + (fallback() ? " [ping]" : " " + clips);
	}
}
