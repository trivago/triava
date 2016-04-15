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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.spi.CachingProvider;

public class CacheListenerTestBase
{
	CacheManager cacheManager;
	
	class NamedAtomicInteger extends AtomicInteger
	{
		private static final long serialVersionUID = 6608822331361354835L;
		
		private final String name;
		
		NamedAtomicInteger(String name)
		{
			this.name = name;
		}

		public String name()
		{
			return name;
		}
		
	}
	volatile NamedAtomicInteger createdListenerFiredCount = new NamedAtomicInteger("created");
	volatile NamedAtomicInteger updatedListenerFiredCount = new NamedAtomicInteger("updated");
	volatile NamedAtomicInteger removedListenerFiredCount = new NamedAtomicInteger("removed");
	volatile NamedAtomicInteger expiredListenerFiredCount = new NamedAtomicInteger("expired");
	
	 // For Async mode, we wait a maximum time to wait for the notification
	int maxWait = 0; //	max time
	TimeUnit unit = TimeUnit.MILLISECONDS; // Unit
	

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
	

	void checkCreated(int expected)
	{
		checkEventCount(expected, createdListenerFiredCount);
	}
	

	void checkUpdated(int expected)
	{
		assertEquals("Updated listener has not fired the expected number of times", expected, updatedListenerFiredCount.get());
	}

	void checkEventCount(int expected, NamedAtomicInteger actual)
	{
		boolean equals;
		if (maxWait > 0)
		{
			boolean done = false;
			long timeLimit = System.currentTimeMillis() + unit.toMillis(maxWait);
			while (!done)
			{
				equals = equals(expected, actual.get());
				if (equals)
					break;
				
				if (System.currentTimeMillis() >= timeLimit)
					break;
				
				try
				{
					Thread.sleep(10);
				}
				catch (InterruptedException e)
				{
				}
			}
			
		}

		assertEquals(actual.name() + " listener has not fired the expected number of times", expected, createdListenerFiredCount.get());
	}

	private boolean equals(int expected, int actual)
	{
		return expected == actual;
	}



	void resetListenerCounts()
	{
		createdListenerFiredCount.set(0);
		updatedListenerFiredCount.set(0);
		removedListenerFiredCount.set(0);
		expiredListenerFiredCount.set(0);
	}

	
	/**
	 * Creates a Cache via plain JSR107 API. The Cache is configured with a default MutableConfiguration.
	 * @param cacheName Cache name
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, String> createJsr107Cache(String cacheName)
	{
		MutableConfiguration<Integer, String> mc = new MutableConfiguration<>();
		javax.cache.Cache<Integer, String> cache = cacheManager().createCache(cacheName, mc); // Build
		return cache;
	}

	
	
	class UpdateListener implements CacheEntryUpdatedListener<Integer, String>, CacheEntryCreatedListener<Integer, String>, CacheEntryRemovedListener<Integer, String>, CacheEntryExpiredListener<Integer, String> 
	{
		UlFactory factory = new UlFactory();
		
		class UlFactory implements Factory<CacheEntryListener<Integer, String>>
		{
			private static final long serialVersionUID = -6664979334102671325L;

			@Override
			public CacheEntryListener<Integer, String> create()
			{
				return new UpdateListener();
			}

			
		}
		
		UlFactory factory()
		{
			return factory;
	
		}
		
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
