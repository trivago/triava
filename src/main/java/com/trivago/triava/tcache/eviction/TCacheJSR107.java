package com.trivago.triava.tcache.eviction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;

public class TCacheJSR107<K, V> implements javax.cache.Cache<K, V>
{
	final Cache<K,V> tcache;
	final CacheManager cacheManager;
	
	TCacheJSR107(Cache<K,V> tcache, CacheManager cacheManager)
	{
		this.tcache = tcache;
		this.cacheManager = cacheManager;
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
	public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> arg0)
	{
		// TODO Auto-generated method stub
		
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
		
		return holder.peek(); // TODO JSR107 check statistics effect
	}

	@Override
	public V getAndRemove(K key)
	{
		return tcache.remove(key);
	}

	@Override
	public V getAndReplace(K arg0, V arg1)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CacheManager getCacheManager()
	{
		return cacheManager;
	}

	@Override
	public <C extends Configuration<K, V>> C getConfiguration(Class<C> arg0)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName()
	{
		return tcache.id();
	}

	@Override
	public <T> T invoke(K arg0, EntryProcessor<K, V, T> arg1, Object... arg2) throws EntryProcessorException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> arg0, EntryProcessor<K, V, T> arg1,
			Object... arg2)
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
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries)
	{
		for (java.util.Map.Entry<? extends K, ? extends V> entry : entries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();
			tcache.put(key, value);
		}
	}

	@Override
	public boolean putIfAbsent(K key, V value)
	{
		V oldValue = tcache.putIfAbsent(key, value);
		// For JSR107 putIfAbsent() should return whether a value was set.
		// As tcache.putIfAbsent() has the semantics of ConcurrentMap#putIfAbsent(), we can check for
		// oldValue == null.
		return oldValue == null;
	}

	@Override
	public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> arg0)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean remove(K key)
	{
		V oldValue = tcache.remove(key);
		// JSR107 Return whether a value was removed
		return oldValue != null;
	}

	@Override
	public boolean remove(K key, V value)
	{
		return tcache.remove(key, value);
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
			tcache.remove(key);
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
	public <T> T unwrap(Class<T> arg0)
	{
		// TODO Auto-generated method stub
		return null;
	}

}
