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

import static org.junit.Assert.fail;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CompletionListenerFuture;
import javax.cache.spi.CachingProvider;

import org.junit.Test;

public class CacheLoaderTest
{
	CacheManager cacheManager;
	
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
	
	@Test
	public void testCompletionListenerFuture()
	{
		Cache<Integer, String> cache = createJsr107Cache("CacheLoaderTest-testAdd");
		Set<Integer> ints = new HashSet<>();
		for (int i=1; i<=100; i++)
		{
			ints.add(i);
		}
		
		CompletionListenerFuture completionListener = new CompletionListenerFuture();
		cache.loadAll(ints, false, completionListener );
		
		try
		{
			completionListener.get();
		}
		catch (InterruptedException e)
		{
			fail(e.getMessage());
		}
		catch (ExecutionException e)
		{
			fail(e.getMessage());
		}
		
	}

	
	
	/**
	 * Creates a Cache via plain JSR107 API. The Cache is configured with a default MutableConfiguration.
	 * @param cacheName Cache name
	 * @return The Cache
	 */
	javax.cache.Cache<Integer, String> createJsr107Cache(String cacheName)
	{
		MutableConfiguration<Integer, String> mc = new MutableConfiguration<>();
		mc.setCacheLoaderFactory(FactoryBuilder.factoryOf(new NumberCacheLoader()));
		javax.cache.Cache<Integer, String> cache = cacheManager().createCache(cacheName, mc); // Build
		return cache;
	}

	public class NumberCacheLoader extends com.trivago.triava.tcache.core.CacheLoader<Integer, String> implements Serializable
	{
		private static final long serialVersionUID = -3956726199291290826L;

		public NumberCacheLoader()
		{	
		}
		
		@Override
		public String load(Integer key) throws CacheLoaderException
		{
			return "Number " + key;
		}
	}


}
