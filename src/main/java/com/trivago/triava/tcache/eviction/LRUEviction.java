package com.trivago.triava.tcache.eviction;

import com.trivago.triava.tcache.core.FreezingEvictor;

/**
 * LRU eviction based on last access time
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class 
 */
public class LRUEviction<K,V> extends FreezingEvictor<K,V> 
{
	/**
	 * @return The last access time
	 */
	@Override
	public long getFreezeValue(K key, TCacheHolder<V> holder)
	{
		return holder.getLastAccess();
	}
}
