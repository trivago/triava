package com.trivago.triava.tcache.statistics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation for StatisticsCalculator, that implements all statistics, using Atomic counters.
 * @author cesken
 *
 */
public class StandardStatisticsCalculator implements StatisticsCalculator
{
	private final AtomicLong cacheHitCount  = new AtomicLong();
	private final AtomicLong cacheMissCount = new AtomicLong();
	private final AtomicLong cachePutCount = new AtomicLong();
	private final AtomicLong cacheDropCount = new AtomicLong();

	private final AtomicLong cacheHitCountPrevious  = new AtomicLong();
	private final AtomicLong cacheMissCountPrevious = new AtomicLong();

	 @Override
	public HitAndMissDifference updateDifference()
	{
		long hitDifference = 0;
		long missDifference = 0;

		long cacheHitsPrevious   = cacheHitCountPrevious.get();
		long cacheMissesPrevious = cacheMissCountPrevious.get();
		
		long cacheHitsCurrent   = cacheHitCount.get();
		long cacheMissesCurrent = cacheMissCount.get();
		
		hitDifference = cacheHitsCurrent - cacheHitsPrevious;
		missDifference = cacheMissesCurrent - cacheMissesPrevious;

		// -2- Make current values the previous ones
		cacheHitCountPrevious.set(cacheHitsCurrent);
		cacheMissCountPrevious.set(cacheMissesCurrent);

		return new HitAndMissDifference(hitDifference, missDifference);
	}
	
	/**
	 * @return the cacheHitCount
	 */
	 @Override
	 public long getHitCount()
	{
		return cacheHitCount.get();
	}

	/**
	 * @return the cacheMissCount
	 */
	 @Override
	 public long getMissCount()
	{
		return cacheMissCount.get();
	}

	/**
	 * @return the cachePutCount
	 */
	 @Override
	public long getPutCount()
	{
		return cachePutCount.get();
	}

	@Override
	public void incrementHitCount()
	{
		cacheHitCount.incrementAndGet();
	}

	@Override
	public void incrementMissCount()
	{
		cacheMissCount.incrementAndGet();
	}

	@Override
	public void incrementPutCount()
	{
		cachePutCount.incrementAndGet();
	}

	@Override
	public void incrementDropCount()
	{
		cacheDropCount.incrementAndGet();
	}

	@Override
	public long getDropCount()
	{
		return cacheDropCount.get();
	}

}
