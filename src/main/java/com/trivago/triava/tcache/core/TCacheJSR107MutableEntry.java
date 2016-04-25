package com.trivago.triava.tcache.core;

import javax.cache.processor.MutableEntry;

import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;

/**
 * tCache implementation of {@link javax.cache.processor.MutableEntry}. It is based on the native tCache entry.
 * All called methods have immediate impact.<p>
 * TODO Check whether the "immediate impact" is compatible with the requirement that "effects are visible after EntryProcessor has finished".
 *      Because the effects are visible, but they might already be visible while it is running.
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public final class TCacheJSR107MutableEntry<K, V> extends TCacheJSR107Entry<K, V> implements MutableEntry<K, V>
{
	final Cache<K, V> tcache;
	
	/**
	 * Creates an instance based on the native tCache entry plus the key.
	 * The constructor will likely change, and the tcache parameter will be removed.
	 * 
	 * @param key The key
	 * @param holder The holder
	 * @param tcache A reference to the tcache instance.
	 */
	public TCacheJSR107MutableEntry(K key, AccessTimeObjectHolder<V> holder, Cache<K, V> tcache)
	{
		super(key, holder);
		this.tcache = tcache;
	}

	@Override
	public boolean exists()
	{
		return tcache.containsKey(key);
	}

	@Override
	public void remove()
	{
		// TODO Check if "immediate impact" is allowed
		tcache.remove(key);
	}

	@Override
	public void setValue(V value)
	{
		// TODO Check if "immediate impact" is allowed
		// TODO This is not yet JSR107 compliant. put() will change the holder, but TCacheJSR107Entry does not reflect this and keeps the old holder.
		tcache.put(key, value);
	}

}