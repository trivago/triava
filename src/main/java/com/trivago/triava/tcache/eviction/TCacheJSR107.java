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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.TCacheConfigurationBean;
import com.trivago.triava.tcache.core.TCacheEntryIterator;
import com.trivago.triava.tcache.core.TCacheJSR107MutableEntry;
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
		
		tcache.listeners.deregisterCacheEntryListener(listenerConfiguration);
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
			tcache.listeners.dispatchEvent(EventType.CREATED, key, value);
			return null;
		}
		else
		{
			tcache.listeners.dispatchEvent(EventType.UPDATED, key, value);
			return holder.peek(); // TCK-WORK JSR107 check statistics effect
		}
	}

	@Override
	public V getAndRemove(K key)
	{
		throwISEwhenClosed();

		
		V oldValue = tcache.remove(key);
		boolean removed = oldValue != null;
		if (removed)
		{
			// TCK CHALLENGE oldValue needs to be passed as (old)Value, otherwise NPE at org.jsr107.tck.event.CacheListenerTest$MyCacheEntryEventFilter.evaluate(CacheListenerTest.java:344)
			tcache.listeners.dispatchEvent(EventType.REMOVED, key, oldValue);
		}
		
		return oldValue;
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
		if (entryProcessor == null)
		{
			// TCK CHALLENGE: While not explicitly mentioned in the Javadocs, the TCK requires a NullPointerException.
			// This makes sense, but should be added to the Javadocs.
			throw new NullPointerException("entryProcessor is null");
		}
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
					holder = tcache.objects.get(key); // Future directions: Use a put() that returns the holder
				}
			}
		}
		
		V value = holder != null ? holder.peek() : null; // Create surrogate "null" if not existing (JSR107)
		TCacheJSR107MutableEntry<K, V> me = new TCacheJSR107MutableEntry<K, V>(key, value);
		try
		{
			T result = processEntryProcessor(entryProcessor, me, args);
			return result;
		}
		catch (EntryProcessorException epe)
		{
			// Do not wrap EntryProcessorException, to pass the JSR107 TCK. This is a likely a TCK bug. See https://github.com/jsr107/jsr107tck/issues/85
			throw epe;
		}
		catch (Exception exc)
		{
			throw new EntryProcessorException(exc);
		}

	}

	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
			Object... args)
	{
		throwISEwhenClosed();
		if (entryProcessor == null)
		{
			// TCK CHALLENGE: While not explicitly mentioned in the Javadocs, the TCK requires a NullPointerException.
			// This makes sense, but should be added to the Javadocs.
			throw new NullPointerException("entryProcessor is null");
		}
		Map<K, EntryProcessorResult<T>> resultMap = new HashMap<>();
		for (K key : keys)
		{
			try
			{
				AccessTimeObjectHolder<V> holder = tcache.objects.get(key);
				V value = holder != null ? holder.peek() : null; // Create surrogate "null" if not existing (JSR107)
				TCacheJSR107MutableEntry<K, V> me = new TCacheJSR107MutableEntry<K,V>(key, value);
				T result = processEntryProcessor(entryProcessor, me, args);
				if (result != null)
				{
					resultMap.put(key, new EntryProcessorResultTCache<T>(result));
				}
			}
			catch (Exception exc)
			{
				resultMap.put(key, new EntryProcessorResultTCache<T>(exc));
			}
		}
		
		return resultMap;
	}

	/**
	 * Process the given EntryProcessor and apply the change (delete or setValue) requested by that EntryProcessor.
	 * Mutable changes have no direct impact, but are be applied after after the EntryProcessor has returned, but
	 * before this method returns.
	 * 
	 * @param entryProcessor The entry processor to execute
	 * @param me The MutableEntry
	 * @param args The client arguments
	 * @return The result from the EntryProcessor
	 */
	private <T> T processEntryProcessor(EntryProcessor<K, V, T> entryProcessor, TCacheJSR107MutableEntry<K, V> me, Object... args)
	{
		T result = entryProcessor.process(me, args);
		switch (me.operation())
		{
			case REMOVE:
				remove(me.getKey());
				break;
			case SET:
				put(me.getKey(), me.getValue());
				break;
			default:
				break;
		}
		return result;
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
			if (exc instanceof EntryProcessorException)
			{
				// Do not wrap EntryProcessorException, to pass the JSR107 TCK. This is a likely a TCK bug. See https://github.com/jsr107/jsr107tck/issues/85
				this.result = exc;
			}
			else
			{
				this.result = new EntryProcessorException(exc);
			}
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

		TCacheEntryIterator<K,V> it = new TCacheEntryIterator<K,V>(this, tcache.objects);
		return it;
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
			tcache.listeners.dispatchEvent(EventType.CREATED, key, value);
		else
		{
			tcache.listeners.dispatchEvent(EventType.UPDATED, key, value);
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
			AccessTimeObjectHolder<V> holder = tcache.putToMap(key, value, tcache.getDefaultMaxIdleTime(), tcache.cacheTimeSpread(), false, false);
			if (holder == null)
				tcache.listeners.dispatchEvent(EventType.CREATED, key, value); // Future directions: Do a multi-dispatch here
			else
				tcache.listeners.dispatchEvent(EventType.UPDATED, key, value); // Future directions: Do a multi-dispatch here
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
			tcache.listeners.dispatchEvent(EventType.CREATED, key, value);
		
		return changed;
	}

	@Override
	public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		tcache.listeners.registerCacheEntryListener(listenerConfiguration);
	}

//	void dispatchEvent(EventType eventType, K key, V value)
//	{
//		tcache.listeners.dispatchEvent(eventType, key, value);
//	}
//
//	void dispatchEvent(EventType eventType, K key, V value, V oldValue)
//	{
//		tcache.listeners.dispatchEvent(eventType, key, value, oldValue);
//	}

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
		{
			// According to the JSR107 RI, we need to use oldValue as value. The Spec is not clear about this, but the TCK bombs
			// us with NPE when we would use null as "value" and oldValue as "old value".
			tcache.listeners.dispatchEvent(EventType.REMOVED, key, oldValue);
		}
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
			tcache.listeners.dispatchEvent(EventType.REMOVED, key, value);
		
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
				tcache.listeners.dispatchEvent(EventType.REMOVED, key, null, oldValue);
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
