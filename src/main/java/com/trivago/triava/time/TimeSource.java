package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

/**
 * The TimeSource interface allows the integration of arbitrary time sources. Simple implementations can deliver
 * the system time from System.nanoTime(), more complex implementations can cache the time, read it from
 * custom hardware devices, or skew the time to check the effects of the system time going faster, slower or backwards.
 * <p>
 * Any implementation must be able to deliver the time in seconds and milliseconds since epoch, and may
 * support more TimeUnit's.
 *  
 * @author cesken
 *
 */
public interface TimeSource
{
	/**
	 * Return the time in the given time unit. It is recommended for unsupported TimeUnit values to throw
	 * an UnsupportedOperation exception.
	 * 
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
