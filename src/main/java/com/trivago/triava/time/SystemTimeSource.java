package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

/**
 * A TimeSource that uses System.currentTimeMillis() as time source.
 * 
 * @author cesken
 *
 */
public class SystemTimeSource implements TimeSource
{

	private long getMillisFromSource()
	{
		return System.currentTimeMillis();
	}

	@Override
	public long time(TimeUnit tu)
	{
		return tu.convert(getMillisFromSource(), TimeUnit.MILLISECONDS);
	}

	@Override
	public long seconds()
	{
		return time(TimeUnit.SECONDS);
	}

	@Override
	public long millis()
	{
		return getMillisFromSource();
	}

	@Override
	public void shutdown()
	{
	}

}
