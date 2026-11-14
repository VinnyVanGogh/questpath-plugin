package com.vinnyvangogh.questpath.groupshop;

/** A spare handed from one member to another. */
public final class Transfer
{
	private final String from;
	private final String to;
	private final long count;

	public Transfer(String from, String to, long count)
	{
		this.from = from;
		this.to = to;
		this.count = count;
	}

	public String from()
	{
		return from;
	}

	public String to()
	{
		return to;
	}

	public long count()
	{
		return count;
	}
}
