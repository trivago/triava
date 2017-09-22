/*********************************************************************************
 * Copyright 2017-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache.statistics;

import java.util.concurrent.atomic.LongAdder;

/**
 * Implementation for StatisticsCalculator, that implements all statistics, using LongAdder counters.
 * 
 * @author cesken
 *
 */
public class LongAdderStatisticsCalculator implements StatisticsCalculator, java.io.Serializable
{
    private static final long serialVersionUID = 2529491687236918155L;
    private final LongAdder cacheHitCount = new LongAdder();
    private final LongAdder cacheMissCount = new LongAdder();
    private final LongAdder cachePutCount = new LongAdder();
    private final LongAdder cacheRemoveCount = new LongAdder();
    private final LongAdder cacheDropCount = new LongAdder();

    private volatile long cacheHitCountPrevious = 0;
    private volatile long cacheMissCountPrevious = 0;

    @Override
    public HitAndMissDifference tick()
    {
        long hitDifference = 0;
        long missDifference = 0;

        long cacheHitsPrevious = cacheHitCountPrevious;
        long cacheMissesPrevious = cacheMissCountPrevious;

        long cacheHitsCurrent = cacheHitCount.sum();
        long cacheMissesCurrent = cacheMissCount.sum();

        hitDifference = cacheHitsCurrent - cacheHitsPrevious;
        missDifference = cacheMissesCurrent - cacheMissesPrevious;

        // -2- Make current values the previous ones
        cacheHitCountPrevious = cacheHitsCurrent;
        cacheMissCountPrevious = cacheMissesCurrent;

        return new HitAndMissDifference(hitDifference, missDifference);
    }

    @Override
    public void clear()
    {
        cacheHitCount.reset();
        cacheMissCount.reset();
        cachePutCount.reset();
        cacheRemoveCount.reset();
        cacheDropCount.reset();
        cacheHitCountPrevious = 0;
        cacheMissCountPrevious = 0;
    }

    /**
     * @return the cacheHitCount
     */
    @Override
    public long getHitCount()
    {
        return cacheHitCount.sum();
    }

    /**
     * @return the cacheMissCount
     */
    @Override
    public long getMissCount()
    {
        return cacheMissCount.sum();
    }

    /**
     * @return the cachePutCount
     */
    @Override
    public long getPutCount()
    {
        return cachePutCount.sum();
    }

    @Override
    public void incrementHitCount()
    {
        cacheHitCount.increment();
    }

    @Override
    public void incrementMissCount()
    {
        cacheMissCount.increment();
    }

    @Override
    public void incrementPutCount()
    {
        cachePutCount.increment();
    }

    @Override
    public void incrementDropCount()
    {
        cacheDropCount.increment();
    }

    @Override
    public long getDropCount()
    {
        return cacheDropCount.sum();
    }

    @Override
    public void incrementRemoveCount()
    {
        cacheRemoveCount.increment();
    }

    @Override
    public void incrementRemoveCount(int removedCount)
    {
        cacheRemoveCount.add(removedCount);
    }

    @Override
    public long getRemoveCount()
    {
        return cacheRemoveCount.sum();
    }

    @Override
    public String toString()
    {
        return "StandardStatisticsCalculator [cacheHitCount=" + cacheHitCount + ", cacheMissCount=" + cacheMissCount
                + ", cachePutCount=" + cachePutCount + ", cacheRemoveCount=" + cacheRemoveCount + ", cacheDropCount="
                + cacheDropCount + "]";
    }

}
