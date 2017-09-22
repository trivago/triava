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
import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.TCacheFactory;

/**
 * DISCLAIMER: THESE TESTS ARE NOT PART OF THE REGULAR UNIT TESTS. THEY WILL NOT BE EXECUTED IN THE MAVEN TEST
 * SCOPE. ONLY RUN THEM IF YOU KNOW THE INNER WORKINGS OF TRIAVA CACHE.
 * <p>
 * These are quick check benchmarks. They serve for doing basic tests before running triava Cache through a proper JMH microbenchmark.
 * Official benchmarks are currently (2017) done with the Caffeine JMH GetPutBenchmark. 
 * 
 * @author cesken
 *
 */
public class CacheReadOnlyBenchmark
{
    private static final int ELEMENTS_TO_READ = 1_000_000_000;
    private static final int ELEMENTS_TO_WRITE = 1_000_000;
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

    /**
     * Single threaded read-only test
     * @throws Exception
     */
    @Test
    public void testReadOnly1Mrd() throws Exception
    {
        Cache<Integer, Integer> cache = createCache("Read-100Mio", ELEMENTS_TO_WRITE);
        fill(cache, ELEMENTS_TO_WRITE);
        testReadOnly(cache, ELEMENTS_TO_READ, ELEMENTS_TO_WRITE);
        cache.close();
    }

 
    private void fill(Cache<Integer, Integer> cache, int elements)
    {
        for (int i = 0; i < elements; i++)
        {
            cache.put(i, i + 1);
        }
        
    }

    public void testReadOnly(Cache<Integer,Integer> cache, int readCount, int maxKey) throws Exception
    {
        long start = System.nanoTime();
        for (int i = 0; i < readCount; i++)
        {
            int key = i % maxKey;
            int value = cache.get(key);
            if (value != key+1) {
                throw new Exception("Key does not hold the correct value: " + i + " = " + value);
            }
        }
        long duration = System.nanoTime() - start;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(duration);
        System.out.println(durationMillis + "ms. " + cache.statistics());
    }
}