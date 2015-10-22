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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;

/**
 * The TCacheFactory allows to create Cache instances via {@link #builder()}, and also supplies administrative methods for the
 * managed caches, like shutting down all registered Caches. The preferred way of obtaining a TCacheFactory
 * instance for application code is a call to {@link #standardFactory()}. Library code should instantiate a new
 * TCacheFactory instance, so it can manage its own Cache collection.
 * 
 * @author cesken
 * @since 2015-03-10
 *
 */
public class TCacheFactory
{
	private final CopyOnWriteArrayList<Cache<?, ?>> CacheInstances = new CopyOnWriteArrayList<>();

	static final TCacheFactory standardFactory = new TCacheFactory();

	/**
	 * Returns the standard factory. The factory can produce Builder instances via {@link #builder()}.
	 * <br>
	 * Library code should not use this method, but create a new TCacheFactory
	 * instance for managing its own Cache collection.
	 * 
	 * @return The standard cache factory.
	 */
	public static TCacheFactory standardFactory()
	{
		return standardFactory;
	}

	/**
	 * Returns a Builder 
	 * @return A Builder
	 */
	public <K, V> Builder<K, V> builder()
	{
		return new Builder<>(this);
	}

	/**
	 * Registers a Cache to this factory. Registered caches will be used for bulk operations like
	 * {@link #shutdownAll()}.
	 * 
	 * @param cache
	 */
	public void registerCache(Cache<?, ?> cache)
	{
		// Hint: "cache" cannot escape. It is safely published, as it is put in a concurrent collection
		CacheInstances.add(cache);
	}

	/**
	 * Shuts down all Cache instances, which were registered via {@link #registerCache(Cache)}. It waits until
	 * all cleaners have stopped.
	 */
	public void shutdownAll()
	{
		for (Cache<?, ?> cache : CacheInstances)
		{
			cache.shutdown();
		}
	}

	/**
	 * Reports size of all Cache instances, which were registered via {@link #registerCache(Cache)}. Using
	 * this method can create high load, and may require particular permissions, depending on the used object
	 * size calculator.
	 * <p>
	 * <b>USE WITH CARE!!!</b>
	 */
	public Map<String, CacheSizeInfo> reportAllCacheSizes(ObjectSizeCalculatorInterface objectSizeCalculator)
	{
		Map<String, CacheSizeInfo> infoMap = new HashMap<>();
		for (Cache<?, ?> cache : CacheInstances)
		{
			infoMap.put(cache.id(), cache.reportSize(objectSizeCalculator));
		}
		return infoMap;
	}

	/**
	 * Returns the list of Caches that have been registered via {@link #registerCache(Cache)}.
	 * 
	 * @return The cache list
	 */
	public List<Cache<?, ?>> instances()
	{
		return new ArrayList<>(CacheInstances);
	}

}
