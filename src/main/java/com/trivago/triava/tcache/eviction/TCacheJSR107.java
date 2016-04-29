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

package com.trivago.triava.tcache.eviction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.event.EventType;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.TCacheConfigurationBean;
import com.trivago.triava.tcache.core.TCacheJSR107Entry;
import com.trivago.triava.tcache.core.TCacheJSR107MutableEntry;
import com.trivago.triava.tcache.event.TCacheEntryEvent;
import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;
import com.trivago.triava.tcache.statistics.TCacheStatisticsBean;
import com.trivago.triava.tcache.statistics.TCacheStatisticsBean.StatisticsAveragingMode;

/**
 * 
 * @author cesken
 *
 * @param <K> The Key type
 * @param <V> The Value type
 */
public class TCacheJSR107<K, V> implements javax.cache.Cache<K, V>
{
	final Cache<K,V> tcache;
	final CacheManager cacheManager;
	final TCacheConfigurationBean<K,V> configurationBean;

	TCacheJSR107(Cache<K,V> tcache, CacheManager cacheManager)
	{
		this.tcache = tcache;
		this.cacheManager = cacheManager;
		this.configurationBean = new TCacheConfigurationBean<K,V>(tcache);
	}
	
	@Override
	public void clear()
	{
		throwISEwhenClosed();
		tcache.clear();	
	}

	@Override
	public void close()
	{
		tcache.shutdown();
	}

	@Override
	public boolean containsKey(K key)
	{
		throwISEwhenClosed();
		return tcache.containsKey(key);
	}

	@Override
	public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		tcache.deregisterCacheEntryListener(listenerConfiguration);
	}

	@Override
	public V get(K key)
	{
		throwISEwhenClosed();

		return tcache.get(key);
	}

	@Override
	public Map<K, V> getAll(Set<? extends K> keys)
	{
		throwISEwhenClosed();

		Map<K, V> result = new HashMap<>(keys.size());
		for (K key : keys)
		{
			V value = tcache.get(key);
			if (value != null)
			{
				result.put(key, value);
			}
		}
		
		return result;
	}

	@Override
	public V getAndPut(K key, V value)
	{
		throwISEwhenClosed();

		AccessTimeObjectHolder<V> holder = tcache.putToMap(key, value, tcache.getDefaultMaxIdleTime(), tcache.cacheTimeSpread(), false, false);
		
		if (holder == null)
		{
			return null;
		}
		
		return holder.peek(); // TCK-WORK JSR107 check statistics effect
	}

	@Override
	public V getAndRemove(K key)
	{
		throwISEwhenClosed();

		return tcache.remove(key);
	}

	@Override
	public V getAndReplace(K key, V value)
	{
		throwISEwhenClosed();

		return tcache.getAndReplace(key, value);
	}

	@Override
	public CacheManager getCacheManager()
	{
		return cacheManager;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz)
	{
		Builder<K, V> builder = tcache.builder;

		if (clazz.isAssignableFrom(javax.cache.configuration.Configuration.class))
		{
			return (C)builder;
		}
		if (clazz.isAssignableFrom(CompleteConfiguration.class))
		{
			return (C)builder;
		}
		
		throw new IllegalArgumentException("Unsupported configuration class: " + clazz.toString());
	}

	@Override
	public String getName()
	{
		return tcache.id();
	}

	@Override
	public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... args) throws EntryProcessorException
	{
		throwISEwhenClosed();

		AccessTimeObjectHolder<V> holder = tcache.objects.get(key);
		if (holder == null)
		{
			CacheLoader<K, V> loader = tcache.loader;
			if (loader != null)
			{
				V value = loader.load(key);
				if (value != null)
				{
					put(key, value);
					holder = tcache.objects.get(key); // Future: Use a put() that returns the holder
				}
			}
		}
		MutableEntry<K, V> me = new TCacheJSR107MutableEntry<K,V>(key, holder, tcache);
		T result = entryProcessor.process(me, args);
		return result;
	}

	// TODO The effects of entryProcessor.process() should be visible only after returning from that message according to JSR107 Spec
	//      The entry should be replaced atomically, instead of TCacheJSR107MutableEntry directly manipulating the Cache.
	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
			Object... args)
	{
		throwISEwhenClosed();

		Map<K, EntryProcessorResult<T>> resultMap = new HashMap<>();
		for (java.util.Map.Entry<K, AccessTimeObjectHolder<V>> entry : tcache.objects.entrySet())
		{
			try
			{
				MutableEntry<K, V> me = new TCacheJSR107MutableEntry<K,V>(entry.getKey(), entry.getValue(), tcache);
				T result = entryProcessor.process(me, args);
				if (result != null)
				{
					resultMap.put(entry.getKey(), new EntryProcessorResultTCache<T>(result));
				}
			}
			catch (Exception exc)
			{
				resultMap.put(entry.getKey(), new EntryProcessorResultTCache<T>(exc));
			}
		}
		
		return resultMap;
	}

	private static class EntryProcessorResultTCache<T> implements EntryProcessorResult<T>
	{
		Object result;
		
		EntryProcessorResultTCache(T result)
		{
			this.result = result;
		}

		EntryProcessorResultTCache(Exception exc)
		{
			this.result = new EntryProcessorException(exc);
		}

		@Override
		public T get() throws EntryProcessorException
		{
			if (result instanceof EntryProcessorException)
				throw (EntryProcessorException)result;
			else
			{
				@SuppressWarnings("unchecked")
				T ret = (T)result;
				return ret;
			}
		}
		
	}
	
	@Override
	public boolean isClosed()
	{
		return tcache.isClosed();
	}

	@Override
	public Iterator<javax.cache.Cache.Entry<K, V>> iterator()
	{
		throwISEwhenClosed();

		List<javax.cache.Cache.Entry<K,V>> entries = new ArrayList<>();
		for (java.util.Map.Entry<K, AccessTimeObjectHolder<V>> entry : tcache.objects.entrySet())
		{
			entries.add(new TCacheJSR107Entry<K, V>(entry.getKey(), entry.getValue()));
		}
		return entries.iterator();
	}

	// TODO #loadAll() must be implemented ASYNCHRONOUSLY according to JSR107 Spec
	@Override
	public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener listener)
	{
		throwISEwhenClosed();

		CacheLoader<K, V> loader = tcache.loader;
		
		if (loader == null)
		{
			if (listener != null)
				listener.onException(new UnsupportedOperationException("Cache does not support loadAll as no CacheLoader is defined: " + this.getName()));
			return;
		}

		final Set<K> finalKeys;

		try
		{
			if (replaceExistingValues)
			{
				loader.loadAll(keys);
			}
			else
			{
				finalKeys = new HashSet<>();
				
				// Only a single Thread may iterate keys (may be a not thread-safe Set)
				for (K key : keys)
				{
					if (!containsKey(key))
					{
						finalKeys.add(key);
					}
				}

				loader.loadAll(finalKeys);
			}
			
			if (listener != null)
				listener.onCompletion();
		}
		catch (Exception exc)
		{
			if (listener != null)
				listener.onException(exc);
		}
		
		

	}

	@Override
	public void put(K key, V value)
	{
		throwISEwhenClosed();

		//defaultMaxIdleTime, cacheTimeSpread()
		AccessTimeObjectHolder<V> holder = tcache.putToMap(key, value, tcache.getDefaultMaxIdleTime(), tcache.cacheTimeSpread(), false, false);
//		tcache.put(key, value);
		if (holder == null)
			dispatchEvent(EventType.CREATED, key, value);
		else
		{
			dispatchEvent(EventType.UPDATED, key, value);
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries)
	{
		throwISEwhenClosed();

		if (entries.isEmpty())
			return;

		// JSR107 spec clarification needed. The TCK check PutTest.putAll_NullKey() requires a total failure, and disallows partial success.
		// This is not explicitly in the Specs. Until this clarification is done, we do an explicit key and value null check before starting to put values in the cache.
		for (java.util.Map.Entry<? extends K, ? extends V> entry : entries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();
			tcache.verifyKeyAndValueNotNull(key, value);
		}
		
		for (java.util.Map.Entry<? extends K, ? extends V> entry : entries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();
			tcache.put(key, value);
			dispatchEvent(EventType.CREATED, key, value); // TOOO Do a multi-dispatch here
		}
	}

	@Override
	public boolean putIfAbsent(K key, V value)
	{
		throwISEwhenClosed();

		V oldValue = tcache.putIfAbsent(key, value);
		// For JSR107 putIfAbsent() should return whether a value was set.
		// As tcache.putIfAbsent() has the semantics of ConcurrentMap#putIfAbsent(), we can check for
		// oldValue == null.
		boolean changed = oldValue == null;

		if (changed)
			dispatchEvent(EventType.CREATED, key, value);
		
		return changed;
	}

	@Override
	public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		tcache.registerCacheEntryListener(listenerConfiguration);
	}

	void dispatchEvent(EventType eventType, K key)
	{
		dispatchEventToListeners(new TCacheEntryEvent<K, V>(this, eventType, key));
	}

	void dispatchEvent(EventType eventType, K key, V value)
	{
		dispatchEventToListeners(new TCacheEntryEvent<K, V>(this, eventType, key, value));
	}

	void dispatchEvent(EventType eventType, K key, V value, V oldValue)
	{
		dispatchEventToListeners(new TCacheEntryEvent<K, V>(this, eventType, key, value,oldValue));
	}

	private void dispatchEventToListeners(TCacheEntryEvent<K, V> event)
	{
		tcache.dispatchEventToListeners(event);
	}


	/**
	 * Returns normally with no side effects if this cache is open. Throws IllegalStateException if it is closed.
	 */
	private void throwISEwhenClosed()
	{
		if (isClosed())
			throw new IllegalStateException("Cache already closed: " + tcache.id());
	}

	@Override
	public boolean remove(K key)
	{
		throwISEwhenClosed();
		
		V oldValue = tcache.remove(key);
		boolean removed = oldValue != null;
		if (removed)
			dispatchEvent(EventType.REMOVED, key, null, oldValue);
		
		// JSR107 Return whether a value was removed
		return removed;
	}

	@Override
	public boolean remove(K key, V value)
	{
		throwISEwhenClosed();
		
		if (value == null)
		{
			// The TCK test demands that we throw a NPE, which is IMO not required by the JSR107 Spec.
			// While a JCache may not contain null values, this does not mean to throw NPE. I would expect to return false.
			throw new NullPointerException("value is null");
		}
		
		boolean removed = tcache.remove(key, value);
		if (removed)
			dispatchEvent(EventType.REMOVED, key, value);
		
		return removed;
	}

	@Override
	public void removeAll()
	{
		throwISEwhenClosed();
		
		removeAll(tcache.objects.keySet());
	}

	@Override
	public void removeAll(Set<? extends K> keys)
	{
		throwISEwhenClosed();
		
		for (K key : keys)
		{
			V oldValue = tcache.remove(key);
			boolean removed = oldValue != null;
			if (removed)
			{
				// Future direction: This could be optimized to do dispatchEvents(Event-List).
				// To be done with the next major refactoring, as event lists need to be supported until the very bottom of the call-stack, namely until ListenerCacheEventManager.
				dispatchEvent(EventType.REMOVED, key, null, oldValue);
			}
		}
	}

	@Override
	public boolean replace(K key, V value)
	{
		throwISEwhenClosed();
		
		return tcache.replace(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		throwISEwhenClosed();
		
		return tcache.replace(key, oldValue, newValue);
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (!(clazz.isAssignableFrom(Cache.class) || clazz.isAssignableFrom(TCacheJSR107.class)))
			throw new IllegalArgumentException("Cannot unwrap Cache to unsupported Class " + clazz);
		
		@SuppressWarnings("unchecked")
		T cacheCasted = (T)tcache;
		return cacheCasted;
	}

	public Object getCacheConfigMBean()
	{
		return configurationBean;
	}

	public Object getCacheStatisticsMBean()
	{
		return new TCacheStatisticsBean(tcache, tcache.statisticsCalculator, StatisticsAveragingMode.JSR107);
	}

}
