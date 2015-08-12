package com.trivago.triava.tcache.statistics;

public interface StatisticsCalculator
{
	// --- Methods for counting --- 
	void incrementHitCount();
	void incrementMissCount();
	void incrementPutCount();
	void incrementDropCount();

	// --- Methods for calculating the change --- 
	HitAndMissDifference updateDifference();

	// --- Methods for raw statistics --- 
	long getHitCount();
	long getMissCount();
	long getPutCount();
	long getDropCount();
	
}
