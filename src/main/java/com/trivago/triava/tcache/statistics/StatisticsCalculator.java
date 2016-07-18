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

package com.trivago.triava.tcache.statistics;

/**
 * The StatisticsCalculator interface contains methods for easy statistics management.
 * There are three types of methods: First the incrementX() methods that increase the
 * corresponding counter. Second, the getX() methods that return the raw statistic counts.
 * Third, the tick() method, that returns the count difference between the last tick and the
 * current tick.
 * 
 * @author cesken
 *
 */
public interface StatisticsCalculator extends java.io.Serializable
{
	// Clear all statistics
	void clear();
	
	// --- Methods for counting --- 
	void incrementHitCount();
	void incrementMissCount();
	void incrementPutCount();
	void incrementDropCount();
	void incrementRemoveCount();
	void incrementRemoveCount(int removedCount);

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
	long getRemoveCount();
	
}
