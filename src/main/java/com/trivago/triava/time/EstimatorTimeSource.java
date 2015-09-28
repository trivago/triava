package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

import com.trivago.triava.logging.TriavaLogger;
import com.trivago.triava.logging.TriavaNullLogger;

/**
 * A TimeSource that provides the value of System.currentTimeMillis() in 10ms precision. 
 * Target use case for this class is to reduce overhead of time methods like System.currentTimeMillis() or
 * System.nanoTime(). like thousand or million times per second.
 * <p>
 * Short story: It increases the write throughput to the Cache massively (100% or even 1000%).
 * <p>
 * Details: The implementation implements "read-once", "provide-often": The value is fetched from the
 * operating system once by a background thread. Fetching includes a potentially expensive crossing of the
 * operating system boundary (calling native, levering privilege, register swapping, ...). If you call it
 * thousands or millions of times per second, this has a very noticeable effect. Regarding the correctness
 * this means that more writes are done with the same timestamp. For expiration this does not have any side
 * effect. For eviction it only effects LRU - more entries would be possible eviction targets - but within
 * 10ms it is usually not relevant which entry is evicted first.
 * 
 * 
 * @author cesken
 *
 */
public class EstimatorTimeSource extends Thread implements TimeSource
{
	final int UPDATE_INTERVAL_MS;
	private volatile long millisEstimate = System.currentTimeMillis();
	private volatile long secondsEstimate = millisEstimate /1000;

	private volatile boolean running;  // Must be volatile, as it is modified via cancel() from a different thread
	private final TriavaLogger logger;

	/**
	 * Creates a TimeSource with the given update interval.
	 * @param updateIntervalMillis Update interval, which defines the approximate precision
	 */
	public EstimatorTimeSource(int updateIntervalMillis)
	{
		this(updateIntervalMillis, new TriavaNullLogger());
	}
	
	/**
	 * Creates a TimeSource with the given update interval.
	 * @param updateIntervalMillis Update interval, which defines the approximate precision
	 * @param logger A non-null triava logger
	 */
	public EstimatorTimeSource(int updateIntervalMillis, TriavaLogger logger)
	{
		this.logger = logger;
		this.UPDATE_INTERVAL_MS = updateIntervalMillis;
		setName("MillisEstimatorThread-" + UPDATE_INTERVAL_MS + "ms");
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
				millisEstimate = System.currentTimeMillis();
				secondsEstimate = millisEstimate / 1000;
//				System.out.println("seconds=" + secondsEstimate);
			}
			catch (InterruptedException ex)
			{
			}
		}
		logger.info("MillisEstimatorThread " + this.getName() + " is leaving run()");
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
		throw new UnsupportedOperationException("time() is not implemented by TimeSource: MillisEstimatorThread");
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

