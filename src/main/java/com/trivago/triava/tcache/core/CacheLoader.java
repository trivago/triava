package com.trivago.triava.tcache.core;

/**
 * A CacheLoader implements on-the-fly retrieval of values which are not yet in the Cache.  
 * @author cesken
 *
 * @param <K>
 * @param <V>
 */
public interface CacheLoader<K, V>
{
	/**
	 * Computes or retrieves the value corresponding to {@code key}.
	 *
	 * @param key the non-null key whose value should be loaded
	 * @return the value associated with {@code key}; <b>must not be null</b>
	 */
    V load(K key) throws Exception;
}
