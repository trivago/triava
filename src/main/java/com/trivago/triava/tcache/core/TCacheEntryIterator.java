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

package com.trivago.triava.tcache.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentMap;

import javax.cache.Cache;
import javax.cache.Cache.Entry;

import com.trivago.triava.tcache.eviction.AccessTimeObjectHolder;
import com.trivago.triava.tcache.expiry.TCacheExpiryPolicy;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;

/**
 * An Iterator for Cache Entries.
 * If {@link #remove()} is called it has the same effects, as calling cache.remove(key). This includes effects on statistics and the CacheListener REMOVE notification.
 * 
 * @author cesken
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class TCacheEntryIterator<K, V> implements Iterator<Entry<K,V>>
{
	private final Iterator<java.util.Map.Entry<K, AccessTimeObjectHolder<V>>> mapIterator;
	Entry<K, V> currentElement = null;
	java.util.Map.Entry<K, AccessTimeObjectHolder<V>> nextElement = null;
	final Cache<K,V> cache;
	final StatisticsCalculator statisticsCalculator;
	final TCacheExpiryPolicy expiryPolicy;
	
	public TCacheEntryIterator(com.trivago.triava.tcache.eviction.Cache<K, V> tcache, ConcurrentMap<K, AccessTimeObjectHolder<V>> objects, TCacheExpiryPolicy expiryPolicy)
	{
		this.expiryPolicy = expiryPolicy;
		mapIterator = objects.entrySet().iterator();

		this.statisticsCalculator = tcache.statisticsCalculator();
		this.cache = tcache.jsr107cache();
	}

	@Override
	public boolean hasNext()
	{
		java.util.Map.Entry<K, AccessTimeObjectHolder<V>> entry = peekNext();
		if (entry == null)
			return false;
		return ! entry.getValue().isInvalid();
	}

	@Override
	public Entry<K, V> next()
	{
		try
		{
			java.util.Map.Entry<K, AccessTimeObjectHolder<V>> entry = peekNext();
			nextElement = null;
			if (entry == null)
				throw new NoSuchElementException();

			AccessTimeObjectHolder<V> holder = entry.getValue();
			holder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());
			V value = holder.get();
			// To be considered: value could be null now, if it got invalid between the peekNext() and peek() calls
			// This means entry.value can be null. This is likely not a good design decision. Future directions: Rethink this
			currentElement = new TCacheJSR107Entry<K, V>(entry.getKey(), value);
			if (statisticsCalculator != null)
				statisticsCalculator.incrementHitCount();
			return currentElement;
			
		}
		catch (NoSuchElementException nsee)
		{
			currentElement = null;
			throw nsee;
		}
	}

	/**
	 * Peeks the next element. Calling this method multiple times will always return
	 * the same value, until {@link #next()} is called. This method skips all invalid
	 * Holders.   
	 * 
	 * @return The next element, or null if there is no nbext element
	 */
	private java.util.Map.Entry<K, AccessTimeObjectHolder<V>> peekNext()
	{
		if (nextElement != null)
			return nextElement;
		
		while (true)
		{
			if (!mapIterator.hasNext())
				return null;
			java.util.Map.Entry<K, AccessTimeObjectHolder<V>> entry = mapIterator.next();
			AccessTimeObjectHolder<V> holder = entry.getValue();
			if (!holder.isInvalid())
			{
				nextElement = entry;
				return nextElement;
			}
		}
	}
	
	@Override
	public void remove()
	{
		if (currentElement == null)
			throw new IllegalStateException("No element to remove");
		cache.remove(currentElement.getKey());
		// Done: Actions like statistics and write-through are done by cache.remove()
	}
}
