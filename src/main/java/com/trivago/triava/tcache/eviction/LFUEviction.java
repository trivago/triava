package com.trivago.triava.tcache.eviction;

import com.trivago.triava.tcache.core.FreezingEvictor;

/**
 * LFU eviction based on use count
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class 
 */
public class LFUEviction<K,V> extends FreezingEvictor<K,V> 
{
	/**
	 * @return Use count
	 */
	@Override
	public long getFreezeValue(K key, TCacheHolder<V> holder)
	{
		return holder.getUseCount();
	}
}
