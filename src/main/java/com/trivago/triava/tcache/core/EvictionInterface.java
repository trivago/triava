package com.trivago.triava.tcache.core;

import java.util.Comparator;

import com.trivago.triava.tcache.eviction.HolderFreezer;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Eviction interface, that can operate on 4 values: Meta data, key, value and a "frozen value". Values that could
 * change during an eviction run (like use count) must be "frozen" instead of used directly. 
 * Typical implementations will extend {@link FreezingEvictor} instead of directly implementing EvictionInterface,
 * as the former already implements the {@link #evictionComparator()}. 
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class
 */
public interface EvictionInterface<K, V>
{
	/**
	 * Returns the Comparator for the eviction policy. Elements to be evicted earlier have to be sorted to the
	 * front by the returned Comparator.
	 * 
	 * @return
	 */
	Comparator<? super HolderFreezer<K, V>> evictionComparator();

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
	
	/**
	 * Called each time before an eviction cycle is being started.
	 */
	void beforeEviction();
	
	/**
	 * Called each time after an eviction cycle has ended.
	 */
	void afterEviction();
}
