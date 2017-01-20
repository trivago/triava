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

package com.trivago.triava.tcache.integration;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import javax.cache.Cache.Entry;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.TCacheHolder;
import com.trivago.triava.tcache.core.TCacheHolderIterator;

/**
 * DISCLAIMER: THESE TESTS ARE NOT PART OF THE REGULAR UNIT TESTS. THEY WILL NOT BE EXECUTED IN THE MAVEN TEST SCOPE.
 *             ONLY RUN THEM IF YOU KNOW THE INNER WORKINGS OF TRIAVA CACHE.
 * <p> 
 * These tests verify tCache expiration behavior. They are important for catching critical bugs before a release.
 * They are not tested in the Maven test scope, as the class name does not start or end with "Test".
 * 
 * Please note that some tests are highly implementation specific and exploit knowledge of the internal workings.
 * They may fail at any time, and additionally some of the tests have time based race conditions, e.g. may fail if the test machine is overloaded, does GC or JIT is running.
 * This means: Sporadic test failures are not of concern. But if a test fails all the time, this is an indication of an issue and should be addressed.
 * <p>
 * 
 * <pre>
 * Important to keep in mind:
 *  - The internal time precision in Triava Cache is currently 10ms.
 *  - Expiration and Eviciton are both running in batches.
 *  - The Cache can slightly overfill during batch eviction (size is a soft limit), but never above a hard limit.
 *  </pre>
 * 
 * @author cesken
 *
 */
public class CacheExpirationChecks
{
	private static final int DEFAULT_CAPACITY = 10;
	private static final int maxIdleTimeMillis = 100_000;
	private static final int maxCacheTimeMillis = 1000_000;
	private Cache<String, Integer> cache;

	boolean runAcceptanceTests = false;

	
	@AfterClass
	public static void tearDown()
	{
		// Not closing, as there is another unit test that also uses the TCacheFactory.standardFactory() 
		//TCacheFactory.standardFactory().close();
	}
	
	@Before
	public void setUpEach()
	{
		cache = TCacheFactory.standardFactory().<String,Integer>builder().setExpectedMapSize(DEFAULT_CAPACITY).setMaxCacheTime(maxCacheTimeMillis, TimeUnit.MILLISECONDS).setMaxIdleTime(maxIdleTimeMillis, TimeUnit.MILLISECONDS) .build();
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
	}
	
	static final int TIME_PRECISION_MILLIS = 10;
	@Test
	public void testExpireUntil()
	{
		testExpireUntil(0);
	}

	@Test
	public void testExpireUntilWithWait()
	{
		testExpireUntil(500);
	}

	public void testExpireUntil(int sleepMillis)
	{
		int customIdleTime = 10000;
		int customCacheTime = 30000;
		
		long timePutAtOrAfter = System.currentTimeMillis();
		cache.put("2", 2, customIdleTime, customCacheTime, TimeUnit.MILLISECONDS);
		long timePutAtOrBefore = System.currentTimeMillis();
		int untilMillis = 1000;
		if (sleepMillis > 0)
		{
			try
			{
				Thread.sleep(sleepMillis);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		long timeExpireUntilAtOrAfter = System.currentTimeMillis();
		cache.expireUntil("2", untilMillis , TimeUnit.MILLISECONDS);
		
		TCacheHolderIterator<String, Integer> iterator = cache.iterator();
		Entry<String, TCacheHolder<Integer>> first = iterator.next();
		TCacheHolder<Integer> holder = first.getValue();
		
//		long timeNow = System.currentTimeMillis();
		long timeMustExpireBefore = timePutAtOrBefore + untilMillis;
		long timeMustExpireBefore2 = timeExpireUntilAtOrAfter + untilMillis;
		long expTime = holder.getExpirationTime();
		
		assertTrue("expirationTime later than expected for put: " + expTime + " >= " + timeMustExpireBefore, expTime < timeMustExpireBefore);
		assertTrue("expirationTime later than expected for expireUntil: " + expTime + " >= " + timeMustExpireBefore, expTime < timeMustExpireBefore2);
		assertTrue("expirationTime before put: " + expTime + " < " + timePutAtOrAfter, expTime >= timePutAtOrAfter);
		
	}
	
	/**
	 * Simple expiration time check.
	 * 
	 * <pre>
	 * -|-|-------------------------|-|---------------------> t
	 *  | |                         | |
	 *  | timePutAtOrBefore         | timeMustExpireBefore = timePutAtOrBefore + maxIdleTime
	 *  |                           |
	 *  cache.put()                 actual expiration time (10 ms precision)
	 * </pre>
	 */
	@Test
	public void testExpire()
	{
		cache.put("1", 2);
		long timePutAtOrBefore = System.currentTimeMillis();
		
		TCacheHolderIterator<String, Integer> iterator = cache.iterator();
		Entry<String, TCacheHolder<Integer>> first = iterator.next();
		TCacheHolder<Integer> holder = first.getValue();
		
//		long timeNow = System.currentTimeMillis();
		long timeMustExpireBefore = timePutAtOrBefore + maxIdleTimeMillis*1000;
		long expTime = holder.getExpirationTime();
		
		assertTrue("expirationTime later than expected: " + holder.getExpirationTime() + " >= " + timeMustExpireBefore, expTime < timeMustExpireBefore);
	}
	

}
