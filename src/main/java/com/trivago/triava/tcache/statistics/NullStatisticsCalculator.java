package com.trivago.triava.tcache.statistics;

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
	public HitAndMissDifference updateDifference()
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
