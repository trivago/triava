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

import static org.junit.Assert.*;

import java.io.Serializable;
import java.util.Iterator;
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
public class CacheListenerTestBase implements Serializable
{
	private static final long serialVersionUID = -7258104888811145129L;

	transient static CacheManager cacheManager;
	
	volatile NamedAtomicInteger createdListenerFiredCount = new NamedAtomicInteger("created");
	volatile NamedAtomicInteger updatedListenerFiredCount = new NamedAtomicInteger("updated");
	volatile NamedAtomicInteger removedListenerFiredCount = new NamedAtomicInteger("removed");
	volatile NamedAtomicInteger expiredListenerFiredCount = new NamedAtomicInteger("expired");
	
	enum RunMode { SYNC, ASYNC }
	
	final RunMode runMode;
	// For Async mode, we wait a maximum time to wait for the notification
	final int maxWait; //	max time
	final TimeUnit unit; // Unit
	
	final static boolean printEventCounts = false; // for debugging purposes

	/**
	 * Constructor for SYNC mode
	 */
	CacheListenerTestBase()
	{
		runMode = RunMode.SYNC;
		maxWait = 0; // irrelevant
		unit = TimeUnit.MILLISECONDS;  // irrelevant 
	}
	
	/**
	 * Constructor for ASYNC mode
	 * @param maxWait Max time to wait for result 
	 * @param unit Unit of maxTime
	 */
	CacheListenerTestBase(int maxWait, TimeUnit unit)
	{
		runMode = RunMode.ASYNC;
		this.maxWait = maxWait;
		this.unit = unit; 		
	}
	
	/**
	 * Creates a Cache via plain JSR107 API. The Cache is configured with a default MutableConfiguration.
	 * @param cacheName Cache name
	 * @param expirationMillis 
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, String> createJsr107Cache(String cacheName, Integer size, Integer expirationMillis)
	{
		CacheManager cm = cacheManager();
		Builder<Integer, String> mc = createCacheBuilder(size);
		if (expirationMillis != null)
		{
			mc.setMaxCacheTime(expirationMillis, TimeUnit.MILLISECONDS);
		}
		javax.cache.Cache<Integer, String> cache = cm.createCache(cacheName, mc); // Build
		
		return cache;
	}
	
	Builder<Integer, String> createCacheBuilder(Integer size)
	{
		Builder<Integer, String> builder = TCacheFactory.standardFactory().builder();
		if (size != null)
		{
			builder.setExpectedMapSize(size);
		}
		return builder;
	}
	

	/**
	 * Returns a CacheManager  
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

	/**
	 * Waits a maximum time of {@link #maxWait} in the Unit {@link #unit} for actual value to get equal with expected or bigger.
	 * A JUnit #assertEquals() is run when it is equal or the timeout has passed.
	 * 
	 * @param atLeast The expected value
	 * @param actual The actual value.
	 */
	void checkEventCountAtLeast(int atLeast, NamedAtomicInteger actual)
	{
		if (maxWait > 0)
		{
			boolean done = false;
			long timeLimit = System.currentTimeMillis() + unit.toMillis(maxWait);
			while (!done)
			{
				if (actual.get() >= atLeast)
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

		assertTrue(actual.name() + " listener has not fired the expected number of times. atLeast=" + atLeast + ", actual=" + actual, actual.get() >= atLeast);
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
			String msg = "EXPIRED!";
			int eventCount = countEvents(events, msg);
			expiredListenerFiredCount.addAndGet(eventCount);
		}

		
	}
	
	static int countEvents(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events, String msg)
	{
		int eventCount = 0;
		Iterator<CacheEntryEvent<? extends Integer, ? extends String>> iterator = events.iterator();
		for ( ; iterator.hasNext() ; ++eventCount ) iterator.next();
		if (printEventCounts)
		{
			System.out.println(msg + " Events: " + eventCount);
		}
		return eventCount;
	}
	
	class UpdateListener implements Serializable, CacheEntryUpdatedListener<Integer, String>, CacheEntryCreatedListener<Integer, String>, CacheEntryRemovedListener<Integer, String>, CacheEntryExpiredListener<Integer, String> 
	{
		private static final long serialVersionUID = -9003765582158554780L;
		
		@Override
		public void onUpdated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			String msg = "UPDATED";
			int eventCount = countEvents(events, msg);
			updatedListenerFiredCount.addAndGet(eventCount);
		}

		@Override
		public void onCreated(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			String msg = "CREATED";
			int eventCount = countEvents(events, msg);
			createdListenerFiredCount.addAndGet(eventCount);
		}

		@Override
		public void onRemoved(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			String msg = "REMOVED";
			int eventCount = countEvents(events, msg);
			removedListenerFiredCount.addAndGet(eventCount);
		}

		@Override
		public void onExpired(Iterable<CacheEntryEvent<? extends Integer, ? extends String>> events)
				throws CacheEntryListenerException
		{
			String msg = "EXPIRED";
			int eventCount = countEvents(events, msg);
			expiredListenerFiredCount.addAndGet(eventCount);
		}
		
	}
	
}
