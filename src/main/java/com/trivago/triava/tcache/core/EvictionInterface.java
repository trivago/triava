package com.trivago.triava.tcache.core;

import java.util.Comparator;

import com.trivago.triava.tcache.eviction.HolderFreezer;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Eviction interface, that can operate on 4 values: Meta data, key, value and a "frozen value". Typical implementations
 * like will use Meta data (LFU is based use count). But as the use count may change, it can be "frozen".
 * Typical implementations will extend FreezingEvictor, instead of directly implementing EvictionInterface. 
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class
 * @param <H> Holder class
 */
public interface EvictionInterface<K,V>
{
	/**
	 * Returns the Comparator for the eviction policy. Elements to be evicted earlier have to be sorted to the
	 * front by the returned Comparator.
	 * 
	 * @return
	 */
	Comparator<? super HolderFreezer<K, V>> evicvtionComparator();

	/**
	 * Returns a value that is required by the actual implementation for eviction. Implement this if you rely on a
	 * value that may change over the course of the eviction sorting process. Examples of changing values are the usage
	 * count or the last use timestamp. 
	 * 
	 * @param key
	 * @param holder
	 * @return
	 */
	long getFreezeValue(K key, TCacheHolder<V> holder);
}
