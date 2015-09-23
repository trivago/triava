package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

public interface TimeSource
{
	/**
	 * Return the time in the given time unit
	 * @return time in milliseconds since epoch
	 */
	long time(TimeUnit tu);
	
	/**
	 * Convenience method, with the same effect as {@link #time(TimeUnit.SECONDS)}
	 * @return time in seconds since epoch
	 */
	long seconds();
	
	/**
	 * Convenience method, with the same effect as {@link #time(TimeUnit.MILLISECONDS)}
	 * @return time in milliseconds since epoch
	 */
	long millis();
	
	/**
	 * Shutdown the time source. Implementations should release all resources they use,
	 * like background threads, memory and connections to hardware clocks.
	 */
	void shutdown();
}
