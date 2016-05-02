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
	final K key;
	final V value;
	final V oldValue;
	final boolean oldValueAvailable;
	/**
	 *
	 */
	private static final long serialVersionUID = 7453522096130191080L;


	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key, V value)
	{
		this(source, eventType, key, value, null, false);
	}

	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key, V value, V oldValue)
	{
		this(source, eventType, key, value, oldValue, true);
	}

	public TCacheEntryEvent(Cache<K,V> source, EventType eventType, K key, V value, V oldValue, boolean oldValueAvailable)
	{
		super(source, eventType);
		this.key = key;
		this.value = value;
		this.oldValue = oldValue;
		this.oldValueAvailable = oldValueAvailable;
	}

	@Override
	public K getKey()
	{
		return key;
	}

	@Override
	public V getValue()
	{
		return value;
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
        if (!(clazz.isAssignableFrom(TCacheEntryEvent.class)))
            throw new IllegalArgumentException("Cannot unwrap CacheManager to unsupported Class " + clazz);

        @SuppressWarnings("unchecked")
        T thisCasted = (T)this;
        return thisCasted;
	}

	@Override
	public V getOldValue()
	{
		return oldValue;
	}

	@Override
	public boolean isOldValueAvailable()
	{
		return oldValueAvailable;
	}

}
