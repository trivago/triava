/*********************************************************************************
 * Copyright 2015-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

import com.trivago.triava.logging.TriavaLogger;
import com.trivago.triava.logging.TriavaNullLogger;

/**
 * A TimeSource that provides the value of System.currentTimeMillis() in the given millisecond precision.
 * Target use case for this class is to reduce the overhead of System.currentTimeMillis() or
 * System.nanoTime(), when being called very frequently (thousands or million times per second).
 * An own TimeSource can be given, that can replace System.currentTimeMillis().
 * <p>
 * This implementation uses a background thread to regularly fetch the current time from
 * System.currentTimeMillis(). The value is cached and returned in all methods returning a time. Caching is
 * helpful, as fetching the time includes a potentially expensive crossing of the operating system boundary
 * (calling native, levering privilege, register swapping, ...). If you call it thousands or millions of times
 * per second, this has a very noticeable effect. For these cases the throughput can be massively increased
 * (100% or even 1000%).
 * <p>
 * Users of this class must be aware that all calls within the update interval will return exactly the same
 * time. For example a Cache implementation that uses this class will have more entries with the identical
 * incoming timestamp. This can have an effect on expiration or time-based eviction strategies like LRU.
 * 
 * @author cesken
 *
 */
public class EstimatorTimeSource extends Thread implements TimeSource
{
	private final int UPDATE_INTERVAL_MS;
	private volatile long millisEstimate;
	private volatile long secondsEstimate;

	private volatile boolean running; // volatile. Modified via cancel() from a different thread
	private final TriavaLogger logger;
	private final TimeSource timeSource; 

	/**
	 * Creates a TimeSource with the given update interval, that uses the system time as TimeSource.
	 * 
	 * @param updateIntervalMillis
	 *            Update interval, which defines the approximate precision
	 */
	public EstimatorTimeSource(int updateIntervalMillis)
	{
		this(new SystemTimeSource(), updateIntervalMillis, new TriavaNullLogger());
	}

	/**
	 * Creates a TimeSource with the given update interval. The time is estimated on base of the given
	 * parentTimeSource, so the precision is not better than this and the parentTimeSource. 
	 * 
	 * @param parentTimeSource The TimeSource to estimate
	 * @param updateIntervalMillis
	 *            Update interval, which defines the approximate precision
	 * @param logger
	 *            A non-null triava logger
	 */
	public EstimatorTimeSource(TimeSource parentTimeSource, int updateIntervalMillis, TriavaLogger logger)
	{
		this.logger = logger;
		this.timeSource = parentTimeSource;
		setTimeFields();
		
		this.UPDATE_INTERVAL_MS = updateIntervalMillis;
		setName("EstimatorTimeSource-" + UPDATE_INTERVAL_MS + "ms");
		setPriority(Thread.MAX_PRIORITY);
		setDaemon(true);
		start();

	}

	public void run()
	{
		logger.info("MillisEstimatorThread " + this.getName() + " has entered run()");
		this.running = true;
		while (running)
		{
			try
			{
				sleep(UPDATE_INTERVAL_MS);
				setTimeFields();
			}
			catch (InterruptedException ex)
			{
				// Ignore: The only valid way to exit the loop and end this Thread is a call
				// to shutdown(). In that case "running == false", and the surrounding loop will exit.
			}
		}
		logger.info("MillisEstimatorThread " + this.getName() + " is leaving run()");
	}

	private void setTimeFields()
	{
		millisEstimate = timeSource.millis();
		secondsEstimate = millisEstimate / 1000;
	}

	private void cancel()
	{
		this.running = false;
		this.interrupt();
	}

	@Override
	public void shutdown()
	{
		cancel();

	}

	@Override
	public long time(TimeUnit tu)
	{
		if (tu == TimeUnit.SECONDS)
			return secondsEstimate;
		else if (tu == TimeUnit.MILLISECONDS)
			return millisEstimate;
		else
			return tu.convert(millisEstimate, TimeUnit.MILLISECONDS);
	}

	@Override
	public long seconds()
	{
		return secondsEstimate;
	}

	@Override
	public long millis()
	{
		return millisEstimate;
	}

}
