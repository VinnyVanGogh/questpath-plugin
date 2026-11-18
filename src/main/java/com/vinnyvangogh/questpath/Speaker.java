package com.vinnyvangogh.questpath;

/**
 * The one rule for saying a line, kept apart from the plugin so it can be
 * tested: voice off is silent; otherwise the line plays, and a line with a
 * missing clip plays as a ping with its words in chat -- never silence.
 */
final class Speaker
{
	/** Where a line goes: the player, and the chat box. */
	interface Out
	{
		void play(Utterance line, int volume);

		void chat(String text);
	}

	private Speaker()
	{
	}

	/**
	 * @param chatOnFallback put the words in chat on a fallback; false when
	 *     some other chat line already said them
	 */
	static void speak(boolean voiceOn, int volume, Utterance line, boolean chatOnFallback, Out out)
	{
		if (!voiceOn || line == null)
		{
			return;
		}
		out.play(line, volume);
		if (line.fallback() && chatOnFallback)
		{
			out.chat("Quest Route: " + line.text);
		}
	}
}
