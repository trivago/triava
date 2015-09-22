package com.trivago.triava.tcache.statistics;

/**
 * Implementation for StatisticsCalculator, that does not count and all getters return zero values. 
 * Target usage of this class is to benchmark the overhead of {@link StandardStatisticsCalculator}
 * and for measuring the maximum throughput of tCache.
 * 
 * @author cesken
 *
 */
public class NullStatisticsCalculator implements StatisticsCalculator
{
	static HitAndMissDifference dummyDifference = new HitAndMissDifference(0, 0);
	@Override
	public void incrementHitCount()
	{
	}

	@Override
	public void incrementMissCount()
	{
	}

	@Override
	public void incrementPutCount()
	{
	}

	@Override
	public void incrementDropCount()
	{
	}

	@Override
	public HitAndMissDifference tick()
	{
		return dummyDifference;
	}

	@Override
	public long getHitCount()
	{
		return 0;
	}

	@Override
	public long getMissCount()
	{
		return 0;
	}

	@Override
	public long getPutCount()
	{
		return 0;
	}

	@Override
	public long getDropCount()
	{
		return 0;
	}

}
