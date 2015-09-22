package com.trivago.triava.tcache.statistics;

public interface StatisticsCalculator
{
	// --- Methods for counting --- 
	void incrementHitCount();
	void incrementMissCount();
	void incrementPutCount();
	void incrementDropCount();

	// --- Methods for calculating the change --- 
	/**
	 * Ends the current measurement interval, and returns the hits and misses since the
	 * last measurement.
	 * @return The absolute count of hits and misses since the last measurement
	 */
	HitAndMissDifference tick();

	// --- Methods for raw statistics --- 
	long getHitCount();
	long getMissCount();
	long getPutCount();
	long getDropCount();
	
}
