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

package com.trivago.triava.tcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.FreezingEvictor;

/**
 * Tests covering LFU eviction
 * 
 * @author cesken
 *
 */
public class CacheLimitLFUTest
{
	
	private static final int maxIdleTime = 1;
	private static final int maxCacheTime = 1;
	private static Cache<String, Integer> cache = buildLfuCache("CacheLFUTest", maxIdleTime, maxCacheTime, 10);

	boolean runAcceptanceTests = false;
	
	@BeforeClass
	public static void setUp()
	{
//		Cache.setLogger(new TriavaConsoleLogger());
		cache.setCleanUpIntervalMillis(300);
		cache.put("", 0);
		
		try 
		{
			Thread.sleep(500);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	private static Cache<String, Integer> buildLfuCache(String string, int maxidletime2, int maxcachetime2, int expectedMapSize)
	{
		Builder<String, Integer> builder = cacheBuilder(string, maxidletime2, maxcachetime2, expectedMapSize);
		builder.setEvictionPolicy(EvictionPolicy.LFU);
		return builder.build();
	}

	private static Builder<String, Integer> cacheBuilder(String string, int maxidletime2, int maxcachetime2, int expectedMapSize)
	{
		Builder<String, Integer> builder = TCacheFactory.standardFactory().builder();
		builder.setId(string).setExpectedMapSize(expectedMapSize);
		builder.setMaxIdleTime(maxidletime2, TimeUnit.SECONDS).setMaxCacheTime(maxcachetime2, TimeUnit.SECONDS);
		return builder;
	}

	@AfterClass
	public static void tearDown()
	{
		// Not closing, as there is another unit test that also uses the TCacheFactory.standardFactory() 
//		TCacheFactory.standardFactory().close();
	}
	
	@Before
	public void setUpEach()
	{
		cache.clear();
		
		exceedingMaxElements();
	}


	@Test
	public void testKeySet()
	{
		assertTrue("Key set is empty", !cache.keySet().isEmpty());
	}
	
	@Test
	public void endlessExpiration0() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;
		
		Builder<String, Integer> builder = cacheBuilder("endlessExpiration0", 0, 0, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, true);
	}
	
	@Test
	public void endlessExpirationMAXINT() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;

		Builder<String, Integer> builder = cacheBuilder("endlessExpiration1", Integer.MAX_VALUE, Integer.MAX_VALUE, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, true);
	}

	@Test
	public void expirationAfter1second() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;

		Builder<String, Integer> builder = cacheBuilder("endlessExpiration1", 1, 1, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, false);
	}
	
	private void checkAfterTime(Cache<String, Integer> cache2, String key, int value, int delayMills, boolean shouldBePresent) throws InterruptedException
	{
		cache2.put(key, value);
		Thread.sleep(delayMills);
		Integer val = cache2.get(key);
		
		if (shouldBePresent)
		{
			if (val == null)
			{
				fail("Cache " + cache2.id() + " must have a value for key=" + key + " after delay of " + delayMills + "ms");
			}
			else
			{
				assertEquals("Cache " + cache2.id() + " value for key=" + key + " not present or same after delay of " + delayMills + "ms", value, val.intValue());
			}
		}
		else
		{
			if (val != null)
			{
				fail("Cache " + cache2.id() + " must not have a value for key=" + key + " after delay of " + delayMills + "ms");
			}
		}
	}

	private void exceedingMaxElements()
	{
		int count = 12000;
		
		for (int i = 0; i < count; i++)
		{
			cache.putIfAbsent(String.valueOf(i), i, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		}
	}

	// ----------------------------- CUSTOM EVICITON TEST FOLLOWS BEWLOW -----------------------------
	
	/**
	 * Example for a custom eviction implementation.
	 */
	static class CustomerClassEvictor extends FreezingEvictor<Integer, CustomerType>
	{
		private static final long serialVersionUID = 4123354872819263514L;

		@Override
		public long getFreezeValue(Integer userId, TCacheHolder<CustomerType> customerType)
		{
			int priority = customerType.peek().getPriority();
			return priority;
		}
	}
	
	enum CustomerType
	{
		Guest(0), Registered(5), Premium(9);
		
		public int getPriority() { return priority;	}

		int priority;
		CustomerType(int priority) { this.priority = priority; };
	}
		
//	@Ignore("Test is not reliable due to the asynchronous nature of evictions")
//	@Test
//	public void customEvictionTestForward()
//	{
//		// Test currently fails from time to time, e.g. 4 "bad" entries.
//		// Likely this test cannot work in 100% of the cases, due to the
//		// asynchronous nature of evictions.
//		customEvictionTest(false);
//	}
	
	@Ignore("Failure reason to be determined. A limit of 11% fails, but 26% works.")
	@Test
	public void customEvictionTestReverse()
	{
		// TODO Test currently fails, if we set a reasonable limit of ACCEPTABLE_NON_PREMIUM_RATE = 11;
		// 11% would be acceptable, but we often end up with 25-26%. This may indicate a bug.
		// Using 26% works, but this is way too high, as it includes both "overfill" and "underfill" space.
		// "Overfill" should go away after the eviction thread is done, so there should be not more than 10-11%.
		customEvictionTest(true);
	}
	
	/**
	 * Tests that elements are evicted in the correct order, when using a custom eviction implementation.
	 * 
	 * @param reverse
	 */
	private void customEvictionTest(boolean reverse)
	{
		// -1- Create Cache with custom eviction implementation
		int MAP_SIZE = 1000;		
		int WRITES_PER_TYPE = 100 * MAP_SIZE;
		/**
		 * 25% is the theoretical limit for the tCache design: Overfill with 15% is allowed, and after
		 * eviction there is "underfill" of 10% extra space. This makes 25% in sum, and using 26% for
		 * rounding issues. Practically, after an eviction run there should only be 10%.
		 */
		int ACCEPTABLE_NON_PREMIUM_RATE = 11;
//		int ACCEPTABLE_NON_PREMIUM_RATE = 26;
		
		Builder<Integer, CustomerType> builder = TCacheFactory.standardFactory().builder();
		builder.setId("customEviction-reverse=" + reverse).setExpectedMapSize(MAP_SIZE);
		builder.setEvictionClass(new CustomerClassEvictor());
		Cache<Integer, CustomerType> ccache = builder.build();
		
		// -2- Add Elements to the Cache, in ascending or descending order (depending on the #reverse param)
		int autoindex = 0;
		CustomerType[] ctForward = { CustomerType.Guest, CustomerType.Premium};
		CustomerType[] ctBackward = { CustomerType.Premium, CustomerType.Guest};
		
		CustomerType[] customerTypes = reverse ?  ctBackward : ctForward;
		
		for (CustomerType custType : customerTypes)
		{
			for (int i=0; i<WRITES_PER_TYPE; i++)
			{
				ccache.put(autoindex++, custType);
//				try
//				{
//					Thread.sleep(0,1);
//				}
//				catch (InterruptedException e)
//				{
//				}
			}
		}
		
		// -3- Sleep a bit, to let the tCache internal eviction thread finish its work
		try
		{
			Thread.sleep(100);
		}
		catch (InterruptedException e)
		{
		}


		// -4- Count the "Premium" elements in the Cache
		int countNonPremium = 0;
		for (Integer key : ccache.keySet())
		{
			CustomerType customerType = ccache.get(key);
			if (customerType != CustomerType.Premium)
			{
//				System.out.println(key + " => " + customerType);
				// At this point sometimes elements like the following remain:
				// 16383 => Guest   16383 = 0x3FFF
				// 18431 => Guest   18431 = 0x47FF
				countNonPremium++;
			}
		}
		
//		System.out.println(ccache.statistics());
//		int size1 = ccache.size();
//		int allowedNonPremium1 = size1 * ACCEPTABLE_NON_PREMIUM_RATE / 100;
//		System.out.println("countNonPremium=" + countNonPremium + ", allowedNonPremium=" + allowedNonPremium1);
		// -5- Check whether the outcome is acceptable
		if (reverse)
		{
			// By tCache design, there can be non-optimal elements in the Cache. Main reasons are the strategies of overfill and
			// underfill (evicting more than required), which may make space for non-optimal elements. Overfill usually does not
			// hold for long, as eviction kicks in as soon as overfill occurs.
			int size = ccache.size();
			int allowedNonPremium = size * ACCEPTABLE_NON_PREMIUM_RATE / 100;
			assertTrue("Most CustomerType != Premium must be evicted for type 'backward'. countNonPremium=" + countNonPremium + ", allowedNonPremium=" + allowedNonPremium, countNonPremium < allowedNonPremium);
		}
		else
		{
			int allowedNonPremium = 0;
			assertTrue("All CustomerType != Premium must be evicted for type 'forward'. countNonPremium=" + countNonPremium + ", allowedNonPremium=" + allowedNonPremium, countNonPremium == 0);			
		}
		
	}
}
