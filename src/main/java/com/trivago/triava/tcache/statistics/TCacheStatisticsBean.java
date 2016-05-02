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

import java.io.Serializable;

import javax.cache.management.CacheStatisticsMXBean;

import com.trivago.triava.tcache.eviction.Cache;

public class TCacheStatisticsBean implements CacheStatisticsMXBean, Serializable
{
	private static final long serialVersionUID = -2459622086065310568L;
	
	final private StatisticsCalculator statistics;
	final transient private Cache<?,?> tcache;
	final private StatisticsAveragingMode averagingMode; 
	
	public enum StatisticsAveragingMode { JSR107, PER_MINUTE } 
	
	public TCacheStatisticsBean(Cache<?,?> tcache, StatisticsCalculator statisticsCalculator, StatisticsAveragingMode averagingMode)
	{
		this.tcache = tcache;
		this.statistics = statisticsCalculator;
		this.averagingMode = averagingMode;
	}

	@Override
	public void clear()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public long getCacheHits()
	{
		return statistics.getHitCount();
	}

	@Override
	public float getCacheHitPercentage()
	{
		if (averagingMode == StatisticsAveragingMode.PER_MINUTE)
		{
			TCacheStatistics statistics2 = tcache.statistics();
			return statistics2.getHitRatio();
		}
		else
		{
			return safeRate(getCacheHits(), getCacheGets(), 0);
		}
	}

	/**
	 * Divide divident by divisor and return it. If divisor is 0, return the defaultValue instead.
	 */
	private float safeRate(long divident, long divisor, int defaultValue)
	{
		if (divisor == 0)
			return defaultValue;
		else
			return 100F * divident / divisor;
	}

	@Override
	public long getCacheMisses()
	{
		return statistics.getMissCount();
	}

	@Override
	public float getCacheMissPercentage()
	{
		if (averagingMode == StatisticsAveragingMode.PER_MINUTE)
		{
			TCacheStatistics statistics2 = tcache.statistics();
			return 100F - statistics2.getHitRatio();
		}
		else
		{
			return safeRate(getCacheMisses(), getCacheGets(), 0);
		}
	}

	@Override
	public long getCacheGets()
	{
		return statistics.getHitCount() + statistics.getMissCount();
	}

	@Override
	public long getCachePuts()
	{
		return statistics.getPutCount();
	}

	@Override
	public long getCacheRemovals()
	{
		return statistics.getRemoveCount();
	}

	/**
	 * Implementation note:
	 * Evictions in TCache are "evictions + drops".
	 * Drops occur, when the Cache is full and the Cache is configured with DROP as {@link com.trivago.triava.tcache.JamPolicy}.
	 * Drops can be considered as "eviction of the newest element".
	 */
	@Override
	public long getCacheEvictions()
	{
		TCacheStatistics statistics2 = tcache.statistics();
		return statistics2.getEvictionCount() + statistics2.getDropCount();
	}

	@Override
	public float getAverageGetTime()
	{
		// not measured, as this can limit throughput, as calls like System.nanoTime() can be very expensive
		return 0;
	}

	@Override
	public float getAveragePutTime()
	{
		// not measured, as this can limit throughput, as calls like System.nanoTime() can be very expensive
		return 0;
	}

	@Override
	public float getAverageRemoveTime()
	{
		// not measured, as this can limit throughput, as calls like System.nanoTime() can be very expensive
		return 0;
	}

}
