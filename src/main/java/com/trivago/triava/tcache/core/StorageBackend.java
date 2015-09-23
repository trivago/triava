package com.trivago.triava.tcache.core;

import java.util.concurrent.ConcurrentMap;

import com.trivago.triava.tcache.eviction.Cache;

public interface StorageBackend<K,V>
{
	/**
	 * Returns a ConcurrentMap conforming to the configuration specified in the builder.
	 * The sizeFactor is a hint that the Map should be sized bigger due to the Cache implementation. For example
	 * if eviction starts at 95% fill rate, sizeFactor could be 1/0.95 = 1,0526316 
	 * 
	 * @param builder The configuration parameter for the Map
	 * @param sizeFactor Sizing factor to take into account
	 * @return An instance of the sorage.
	 */
	ConcurrentMap<K, Cache.AccessTimeObjectHolder<V>> createMap(Builder<K,V> builder, double sizeFactor);
}
