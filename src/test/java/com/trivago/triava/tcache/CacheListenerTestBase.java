/*********************************************************************************
 * Copyright 2016-present trivago GmbH
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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.core.Builder;

/**
 * Base class for implementing unit tests for Cache Listeners. Contains helper methods
 * but no tests.
 * 
 * @author cesken
 *
 */
public class CacheListenerTestBase
{
	CacheManager cacheManager;
	
	volatile NamedAtomicInteger createdListenerFiredCount = new NamedAtomicInteger("created");
	volatile NamedAtomicInteger updatedListenerFiredCount = new NamedAtomicInteger("updated");
	volatile NamedAtomicInteger removedListenerFiredCount = new NamedAtomicInteger("removed");
	volatile NamedAtomicInteger expiredListenerFiredCount = new NamedAtomicInteger("expired");
	
	 // For Async mode, we wait a maximum time to wait for the notification
	int maxWait = 0; //	max time
	TimeUnit unit = TimeUnit.MILLISECONDS; // Unit

	
	/**
	 * Creates a Cache via plain JSR107 API. The Cache is configured with a default MutableConfiguration.
	 * @param cacheName Cache name
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, String> createJsr107Cache(String cacheName, Integer size)
	{
		CacheManager cm = cacheManager();
		
		Builder<Integer, String> mc = TCacheFactory.standardFactory().builder();
		if (size != null)
		{
			mc.setExpectedMapSize(size);
		}
		javax.cache.Cache<Integer, String> cache = cm.createCache(cacheName, mc); // Build
		
		return cache;
	}
	
	
	/**
	 * Returns a tCache CacheManager.  
	 * @return
	 */
	public synchronized CacheManager cacheManager()
	{
		if (cacheManager == null)
		{
			CachingProvider cachingProvider = Caching.getCachingProvider();
			cacheManager = cachingProvider.getCacheManager();
		}
		
		return cacheManager;
	}
	

	/**
	 * Checks the created count: expected vs actual 
	 * @param expected
	 */
	void checkCreated(int expected)
	{
		checkEventCount(expected, createdListenerFiredCount);
	}
	
	/**
	 * Checks the updated count: expected vs actual 
	 * @param expected
	 */
	void checkUpdated(int expected)
	{
		checkEventCount(expected, updatedListenerFiredCount);
	}

	/**
	 * Checks the removeded count: expected vs actual 
	 * @param expected
	 */
	void checkRemoved(int expected)
	{
		checkEventCount(expected, removedListenerFiredCount);
	}
	
	/**
	 * Checks the expired count: expected vs actual 
	 * @param expected
	 */
	void checkExpired(int expected)
	{
		checkEventCount(expected, expiredListenerFiredCount);
	}
	
	/**
	 * Waits a maximum time of {@link #maxWait} in the Unit {@link #unit} for actual value to get equal with expected.
	 * A JUnit #assertEquals() is run when it is equal or the timeout has passed.
	 * 
	 * @param expected The expected value
	 * @param actual The actual value
	 */
	void checkEventCount(int expected, NamedAtomicInteger actual)
	{
		if (maxWait > 0)
		{
			boolean done = false;
			long timeLimit = System.currentTimeMillis() + unit.toMillis(maxWait);
			while (!done)
			{
				if (expected == actual.get())
					break;
				
				if (System.currentTimeMillis() >= timeLimit)
					break;
				
				try
				{
					Thread.sleep(1);
				}
				catch (InterruptedException e)
				{
				}
			}
			
		}

		assertEquals(actual.name() + " listener has not fired the expected number of times", expected, actual.get());
	}

	void resetListenerCounts()
	{
		createdListenerFiredCount.set(0);
		updatedListenerFiredCount.set(0);
		removedListenerFiredCount.set(0);
		expiredListenerFiredCount.set(0);
	}


	
	class MyExpiredListener implements Serializable, CacheEntryExpiredListener<Integer, String>
	{
		private static final long serialVersionUID = 3609429653606739998L;

		@Override
		public void onExpired(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			expiredListenerFiredCount.incrementAndGet();
		}
		
	}
	
	class UpdateListener implements Serializable, CacheEntryUpdatedListener<Integer, String>, CacheEntryCreatedListener<Integer, String>, CacheEntryRemovedListener<Integer, String>, CacheEntryExpiredListener<Integer, String> 
	{
		private static final long serialVersionUID = -9003765582158554780L;
		
		@Override
		public void onUpdated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			updatedListenerFiredCount.incrementAndGet();
		}

		@Override
		public void onCreated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			createdListenerFiredCount.incrementAndGet();
		}

		@Override
		public void onRemoved(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			removedListenerFiredCount.incrementAndGet();
		}

		@Override
		public void onExpired(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			expiredListenerFiredCount.incrementAndGet();
		}
		
	}
	
}
