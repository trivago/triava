/*********************************************************************************
 * Copyright 2016-present trivago GmbH
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

package com.trivago.examples;

import java.io.IOException;
import java.io.PrintStream;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.FreezingEvictor;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Examples for creating a Cache using the Java Caching API (JSR107). This example shows basic operations like
 * creating a Cache, put and get. Additionaly it does an introduction into the Cache statistics, available via
 * MBean or a native tCache statistics object.
 *
 * @author cesken
 *
 */
public class CacheJSR107Example
{

	PrintStream out = System.out;
	CacheManager cacheManager;
	String cacheName = "simpleCache";
	String cacheName2 = "builderCache";
	int elementsToWrite = 20_000;

	public static void main(String[] args)
	{
		new CacheJSR107Example().example();
	}
	
	void example()
	{
		out.println("Test for a stock JSR107 Cache");
		runTest(createJsr107Cache(cacheName));
		out.println();
		out.println("Test for a JSR107 Cache, using a Builder as Configuration object");
		runTest(createJsr107CacheWithBuilder(cacheName2));
	}

	void runTest(javax.cache.Cache<Integer, Integer> cache)
	{
		out.println("Created Cache: " + cache);

		int countGood = 0;
		int countBad = 0;
		for (int i = 1; i <= elementsToWrite; i++)
		{
			cache.put(i, i + 1);
			if (i % 1000 == 0)
			{
				out.println("Written " + i + " entries");
			}
			// Each 10th loop iteration, do a get 
			if (i % 10 == 0)
			{
				// The following will create a 60% hit ratio in the statistics
				if (i % 30 == 0)
				{
					// entering for i in 0,30,60,90
					cache.get(i + 5);
					countBad++;
				}
				else
				{
					// entering for i in 10,20,40,50,70,80
					cache.get(i);
					countGood++;
				}
			}
		}

		float expectedHitRate = countGood / ((float)countGood + countBad);
		
		out.println("Use JVisualVM or any other tool to inspect the MBeans in javax.cache");
		out.println();

		// For demonstrating the two statistics (native and JSR107 MBean), we need to access the native tCache implementation => unwrap
		Cache<?,?> tcache = cache.unwrap(Cache.class);
		out.println(tcache.statistics());
		
		out.println();
		out.println("The statistics in the MBeans should match the one above execpt for different naming and the 'hit rate':");
		out.println(" - hitRatio is the last minute average (floating average, tCache specific, always 0.0% in the first 10 seconds)");
		out.println(" - The CacheHitPercentage in the MBean is an overall value, as defined in JSR107");
		out.println(" - The theoretically expected hit rate is " + 100*expectedHitRate + "%"); 
		out.println("Press return to continue");
		waitForCR();		
		out.println("Destroying cache");
		cacheManager.destroyCache(cache.getName());
		out.println("MBeans should be unregistered now. Press return to continue");
		waitForCR();
	}

	/**
	 * Wait for CR (actually ANYKEY) and return.
	 */
	private void waitForCR()
	{
		try
		{
			int readByte;
			do
			{
				readByte = System.in.read();
			}
			while (readByte != 10);
		}
		catch (IOException e1)
		{
		}
	}

	/**
	 * Creates a Cache via plain JSR107 API. The Cache is configured with a default MutableConfiguration.
	 * @param cacheName Cache name
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, Integer> createJsr107Cache(String cacheName)
	{
		// Build JSR107 Cache instance
		CachingProvider cachingProvider = Caching.getCachingProvider();
		cacheManager = cachingProvider.getCacheManager();

		MutableConfiguration<Integer, Integer> mc = new MutableConfiguration<Integer, Integer>();
		mc.setManagementEnabled(true);
		mc.setStatisticsEnabled(true);
		javax.cache.Cache<Integer, Integer> cache = cacheManager.createCache(cacheName, mc); // Build

		return cache;
	}
	
	/**
	 * Creates a Cache via JSR107 API, but an extended Configuration object. Namely a tCache Builder is used, that extends the JSR107 Configuration.
	 * 
	 * @param cacheName Cache name
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, Integer> createJsr107CacheWithBuilder(String cacheName)
	{
		// Build JSR107 Cache instance
		CachingProvider cachingProvider = Caching.getCachingProvider();
		cacheManager = cachingProvider.getCacheManager();
		
		// Get a Builder from the native CacheManager implementation 
		TCacheFactory cacheFactory = cacheManager.unwrap(TCacheFactory.class);
		Builder<Integer, Integer> builder = cacheFactory.builder();
		
		builder.setEvictionClass(new EvictByNumber());

		// As Builder extends Configuration, it can be directly used in the JSR107 createCache() call
		javax.cache.Cache<Integer, Integer> cache = cacheManager.createCache(cacheName, builder); // Build
		cacheManager.enableManagement(cacheName, true);

		return cache;
	}
	
	class EvictByNumber extends FreezingEvictor<Integer, Integer>
	{

		@Override
		public long getFreezeValue(Integer key, TCacheHolder<Integer> holder)
		{
			return key.longValue();
		}
		
	}
}