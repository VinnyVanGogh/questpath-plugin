package com.vinnyvangogh.questpath.scenarios;

/** A scenario or pasted payload that cannot be used. The message is written to be read in the dev panel. */
public final class ScenarioException extends RuntimeException
{
	public ScenarioException(String message)
	{
		super(message);
	}

	public ScenarioException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
