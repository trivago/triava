package com.trivago.triava.tcache.core;

import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * tCache implementation of {@link javax.cache.Cache.Entry}. It is based on the native tCache entry
 * and can be unwrapped to it via unwrap(TCacheHolder.class)
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class TCacheJSR107Entry<K, V> implements javax.cache.Cache.Entry<K, V>
{
	final K key;
	final AccessTimeObjectHolder<V> holder;
	
	
	/**
	 * Creates an instance based on the native tCache entry plus the key.
	 * 
	 * @param key The key
	 * @param holder The holder
	 */
	public TCacheJSR107Entry(K key, AccessTimeObjectHolder<V> holder)
	{
		this.key = key;
		this.holder = holder;
	}

	@Override
	public K getKey()
	{
		return key;
	}

	@Override
	public V getValue()
	{
		return holder.peek();
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (!(clazz.isAssignableFrom(TCacheHolder.class)))
			throw new IllegalArgumentException("Cannot unwrap to unsupported Class " + clazz);

		@SuppressWarnings("unchecked")
		T holderCast = (T) holder;
		return holderCast;
	}
}
