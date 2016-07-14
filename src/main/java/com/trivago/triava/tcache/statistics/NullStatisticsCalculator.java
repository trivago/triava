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
 * Implementation for StatisticsCalculator, that does not count and all getters return zero values. 
 * Target usage of this class is to benchmark the overhead of {@link StandardStatisticsCalculator}
 * and for measuring the maximum throughput of tCache.
 * 
 * @author cesken
 *
 */
public class NullStatisticsCalculator implements StatisticsCalculator, java.io.Serializable
{
	private static final long serialVersionUID = 1291619594880844249L;
	
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
	public void incrementRemoveCount()
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

	@Override
	public long getRemoveCount()
	{
		return 0;
	}

}
