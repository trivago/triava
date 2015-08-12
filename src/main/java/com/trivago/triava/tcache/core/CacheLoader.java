package com.trivago.triava.tcache.core;

public interface CacheLoader<K, V>
{
	  /**
	   * Computes or retrieves the value corresponding to {@code key}.
	   *
	   * @param key the non-null key whose value should be loaded
	   * @return the value associated with {@code key}; <b>must not be null</b>
	   */
	  public V load(K key) throws Exception;
}
