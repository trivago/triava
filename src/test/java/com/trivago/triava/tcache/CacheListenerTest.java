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

package com.trivago.triava.tcache;

import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;

public class CacheListenerTest extends CacheListenerTestBase
{
	private static final long serialVersionUID = -7831605500466126577L;

	CacheListenerTest()
	{
	}
	
	CacheListenerTest(int maxWait, TimeUnit unit)
	{
		super(maxWait, unit);
	}

	public void testListener()
	{
		try
		{
			javax.cache.Cache<Integer, String> cache = createCache("testListener", null);

			resetListenerCounts();
			cache.put(1, "One");
			// Synchronous => Listener must have been executed before leaving put()
			checkCreated(1);

			resetListenerCounts();
			cache.put(2, "Two");
			cache.put(3, "Three");
			checkCreated(2);

			resetListenerCounts();
			cache.put(2, "Two updated");
			checkUpdated(1);

			resetListenerCounts();
			cache.put(1, "One");
			checkCreated(0);
			checkUpdated(1); // Unchanged, but touched => updated

		}
		catch (Exception e)
		{
			fail(e.getMessage() + ": " + e.getCause());
		}
	}

	private javax.cache.Cache<Integer, String> createCache(String name, Integer size)
	{
		javax.cache.Cache<Integer, String> cache = createJsr107Cache(name + "-sync-" + runMode, size);
		Factory<UpdateListener> ulFactory = FactoryBuilder.factoryOf(new UpdateListener());
		CacheEntryListenerConfiguration<Integer, String> listenerConf = new MutableCacheEntryListenerConfiguration<>(ulFactory, null, false, runMode == RunMode.SYNC);
		cache.registerCacheEntryListener(listenerConf);
		return cache;
	}
	
	private javax.cache.Cache<Integer, String> createCacheWithExpiredListener(String name, Integer size)
	{
		javax.cache.Cache<Integer, String> cache = createJsr107Cache(name + "-" + runMode, size);
		Factory<MyExpiredListener> ulFactory = FactoryBuilder.factoryOf(new MyExpiredListener());
		CacheEntryListenerConfiguration<Integer, String> listenerConf = new MutableCacheEntryListenerConfiguration<>(ulFactory, null, false, runMode == RunMode.SYNC);
		cache.registerCacheEntryListener(listenerConf);
		return cache;
	}
	
	public void testWriteMoreThanCapacity()
	{
//		Cache.setLogger(new TriavaConsoleLogger());
		int capacity = 10000;
		javax.cache.Cache<Integer, String> cache = createCacheWithExpiredListener("testWriteMoreThanCapacity", capacity);

		resetListenerCounts();

		for (int i = 0; i < 100 * capacity; i++)
		{
			cache.put(i, "Value " + i);
		}
		
		int expireAtLeast = 10 * capacity; // Actually 99*capacity or at least 90*capacity could be checked
		checkEventCountAtLeast(expireAtLeast, expiredListenerFiredCount); 
	}

}
