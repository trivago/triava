package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

import com.trivago.triava.annotations.Alpha;

/**
 * A TimeSource that produces times with an offset to the actual time.
 * Target use case for this class is to simulate running at a different time, e.g. when replaying
 * an error scenario.
 * 
 * @author cesken
 *
 */
@Alpha(comment="Class naming and implementation check pending")
public class OffsetTimeSource implements TimeSource
{
	final TimeSource parentTimeSource;
	final long offsetMillis;
	
	/**
	 * Creates a TimeSource that produces times starting with the given startTimeMillis.
	 * All times returned are using an offset of "startTimeMillis - now", with now being
	 * the time of construction.
	 * 
	 * @param startTimeMillis The start timestamp (artificial "now time")
	 * @param parentTimeSource The TimeSource on which the offset will be applied
	 */
	public OffsetTimeSource(long startTimeMillis, TimeSource parentTimeSource)
	{
		if (parentTimeSource == null)
			throw new NullPointerException("parentTimeSource must not be null");
		this.parentTimeSource = parentTimeSource;
		this.offsetMillis = startTimeMillis - getMillisFromSource();
	}

	private long getMillisFromSource()
	{
		return parentTimeSource.millis() + offsetMillis;
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
