package com.trivago.triava.tcache.event;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;

/**
 * tCache implementation of {@link CacheEntryEvent}.
 * @author cesken
 *
 * @param <K> The Key class
 * @param <V> The Value class
 */
public class TCacheEntryEvent<K,V> extends CacheEntryEvent<K, V>
{
	K key;
	V value;
	V oldValue;
	/**
	 * 
	 */
	private static final long serialVersionUID = 7453522096130191080L;

	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key)
	{
		this(source, eventType, key, null, null);
	}
	
	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key, V value)
	{
		this(source, eventType, key, value, null);
	}
	
	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key, V value, V oldValue)
	{
		super(source, eventType);
		this.key = key;
		this.value = value;
		this.oldValue = oldValue;
	}

	@Override
	public K getKey()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public V getValue()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public V getOldValue()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isOldValueAvailable()
	{
		// TODO Auto-generated method stub
		return false;
	}

}
