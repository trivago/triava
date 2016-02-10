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
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.TCacheConfigurationBean;
import com.trivago.triava.tcache.event.DispatchMode;
import com.trivago.triava.tcache.event.ListenerEntry;
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
	private Set<ListenerEntry<K,V> > listeners = new HashSet<>();

	TCacheJSR107(Cache<K,V> tcache, CacheManager cacheManager)
	{
		this.tcache = tcache;
		this.cacheManager = cacheManager;
		this.configurationBean = new TCacheConfigurationBean<K,V>(tcache);
	}
	
	@Override
	public void clear()
	{
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
		return tcache.containsKey(key);
	}

	@Override
	public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		Iterator<ListenerEntry<K, V>> it = listeners.iterator();
		while (it.hasNext())
		{
			ListenerEntry<K, V> listenerEntry = it.next();
			if (listenerConfiguration.equals(listenerEntry.getConfig()))
			{
				it.remove();
				break; // Can be only one, as it is in the Spec that Listeners must not added twice.
			}
		}
	}

	@Override
	public V get(K key)
	{
		return tcache.get(key);
	}

	@Override
	public Map<K, V> getAll(Set<? extends K> keys)
	{
		Map<K, V> result = new HashMap<>(keys.size());
		for (K key : keys)
		{
			result.put(key, tcache.get(key));
		}
		
		return result;
	}

	@Override
	public V getAndPut(K key, V value)
	{
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
		return tcache.remove(key);
	}

	@Override
	public V getAndReplace(K key, V value)
	{
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
			Object... args)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isClosed()
	{
		return tcache.isClosed();
	}

	@Override
	public Iterator<javax.cache.Cache.Entry<K, V>> iterator()
	{
//		Iterator<java.util.Map.Entry<K, AccessTimeObjectHolder<V>>> iterator = tcache.objects.entrySet().iterator();
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void loadAll(Set<? extends K> arg0, boolean arg1, CompletionListener arg2)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void put(K key, V value)
	{
		tcache.put(key, value);
		dispatchEvent(EventType.CREATED, key, value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries)
	{
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
		
		DispatchMode dispatchMode = listenerConfiguration.isSynchronous() ? DispatchMode.SYNC : DispatchMode.ASYNC_TIMED;
		boolean added = listeners.add(new ListenerEntry<K, V>(listenerConfiguration, tcache, dispatchMode));
		if (!added)
		{
			throw new IllegalArgumentException("Cache entry listener may not be added twice to " + tcache.id() + ": "+ listenerConfiguration);
		}
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
		for (ListenerEntry<K, V> listener : listeners)
		{
			listener.dispatch(event);
		}
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
		boolean removed = tcache.remove(key, value);
		if (removed)
			dispatchEvent(EventType.REMOVED, key, value);
		
		return removed;
	}

	@Override
	public void removeAll()
	{
		tcache.clear();
	}

	@Override
	public void removeAll(Set<? extends K> keys)
	{
		for (K key : keys)
		{
			V oldValue = tcache.remove(key);
			boolean removed = oldValue != null;
			// TODO optimize to dispatch all events in one call
			if (removed)
				dispatchEvent(EventType.REMOVED, key, null, oldValue);
		}
	}

	@Override
	public boolean replace(K key, V value)
	{
		return tcache.replace(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		return tcache.replace(key, oldValue, newValue);
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (!(clazz.isAssignableFrom(Cache.class)))
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
