package com.trivago.triava.tcache.statistics;


public class HitAndMissDifference
{
	private final long hitDifference;
	private final long missDifference;
	
	HitAndMissDifference(long hitDifference, long missDifference)
	{
		this.hitDifference = hitDifference;
		this.missDifference = missDifference;
	}

	public long getHitDifference()
	{
		return hitDifference;
	}

	public long getMissDifference()
	{
		return missDifference;
	}

}
