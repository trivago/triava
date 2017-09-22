/*********************************************************************************
 * Copyright 2015-present trivago GmbH
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

package com.trivago.triava.tcache.integration;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.TCacheFactory;

/**
 * DISCLAIMER: THESE TESTS ARE NOT PART OF THE REGULAR UNIT TESTS. THEY WILL NOT BE EXECUTED IN THE MAVEN TEST
 * SCOPE. ONLY RUN THEM IF YOU KNOW THE INNER WORKINGS OF TRIAVA CACHE.
 * <p>
 * These are quick check benchmarks. They serve for doing basic tests before running triava Cache  through a proper JMH microbenchmark.
 * Official benchmarks are currently (2017) done with the Caffeine JMH GetPutBenchmark. 
 * 
 * @author cesken
 *
 */
public class CacheThroughputBenchmark
{
    private static final int ELEMENTS_TO_WRITE = 100_000_000;
    private static final int maxIdleTimeMins = 5;
    private static final int maxCacheTimeMins = 5;

    @AfterClass
    public static void tearDown()
    {
    }

    @Before
    public void setUpEach()
    {
    }

    private Cache<Integer, Integer> createCache(String name, int size)
    {
        Cache<Integer, Integer> cache = TCacheFactory.standardFactory().<Integer, Integer> builder()
                .setId(name)
                .setMaxElements(size).setMaxCacheTime(maxCacheTimeMins, TimeUnit.MINUTES)
                .setMaxIdleTime(maxIdleTimeMins, TimeUnit.MINUTES).build();
        
        return cache;
    }

    @Test
    public void testWriteOnly10K()
    {
        testWriteOnly("10K", 10_000);
    }

    @Test
    public void testWriteOnly10M()
    {
        testWriteOnly("10Mio", 10_000_000);
    }

    public void testWriteOnly(String name, int capacity)
    {
        Cache<Integer, Integer> cache = createCache(name, capacity);

        long start = System.nanoTime();
        int elems = ELEMENTS_TO_WRITE;
        for (int i = 0; i < elems; i++)
        {
            cache.put(i, i + 1);
        }
        long duration = System.nanoTime() - start;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(duration);
        System.out.println(durationMillis + "ms. " + cache.statistics());
        cache.close();
    }
}