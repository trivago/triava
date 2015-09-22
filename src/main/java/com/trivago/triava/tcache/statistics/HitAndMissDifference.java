package com.trivago.triava.tcache.statistics;


/**
 * Holds a count of cache hits and cache misses.
 * 
 * @author cesken
 *
 */
public class HitAndMissDifference
{
	private final long hitDifference;
	private final long missDifference;
	
	/**
	 * Constructs an instance with the given hits and misses.
	 * 
	 * @param hits
	 * @param misses
	 */
	HitAndMissDifference(long hits, long misses)
	{
		this.hitDifference = hits;
		this.missDifference = misses;
	}

	public long getHits()
	{
		return hitDifference;
	}

	public long getMisses()
	{
		return missDifference;
	}

}
